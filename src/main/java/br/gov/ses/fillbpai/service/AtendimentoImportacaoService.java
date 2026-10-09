package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.model.*;
import br.gov.ses.fillbpai.util.CnsProfissionalUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import br.gov.ses.fillbpai.util.IbgeUtils;
import br.gov.ses.fillbpai.util.LogradouroUtils;
import br.gov.ses.fillbpai.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável por:
 * - Ler arquivo Excel
 * - Converter linhas em DTO
 * - Processar regras de negócio
 * - Criar/encontrar entidades normalizadas (Paciente, Medico, Estabelecimento, Endereco)
 * - Persistir atendimento com relacionamentos
 * - Retornar resumo detalhado da importação
 */
public class AtendimentoImportacaoService {

	private final ExcelImportService excelService = new ExcelImportService();
	private final PlanilhaColumnMapper columnMapper = new PlanilhaColumnMapper();
	private final AtendimentoProcessor processor = new AtendimentoProcessor();
	private final AtendimentoBPAiRepository atendimentoRepository;
	private final PacienteRepository pacienteRepository;
	private final MedicoRepository medicoRepository;
	private final EstabelecimentoRepository estabelecimentoRepository;
	private final EntityManager entityManager;

	public AtendimentoImportacaoService(EntityManager entityManager) {
		this.entityManager = entityManager;
		this.atendimentoRepository = new AtendimentoBPAiRepository(entityManager);
		this.pacienteRepository = new PacienteRepository(entityManager);
		this.medicoRepository = new MedicoRepository(entityManager);
		this.estabelecimentoRepository = new EstabelecimentoRepository(entityManager);
	}

	/**
	 * Executa a importação completa.
	 */
	public ImportacaoResultado importar(String caminhoArquivo) {

		ImportacaoResultado resultado = new ImportacaoResultado();
		List<AtendimentoBPAi> importados = new ArrayList<>();

		EntityTransaction transaction = entityManager.getTransaction();

		try (FileInputStream fis = new FileInputStream(caminhoArquivo);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);

			// Mapeia os campos canônicos pelo nome do cabeçalho uma única vez
			// por planilha. A estrutura normalmente já foi validada antes pelo
			// fluxo de "Analisar Planilha" (ValidacaoPlanilhaService), mas a
			// checagem é repetida aqui como proteção: importar com colunas
			// ausentes produziria erros de linha enganosos (ex.: "CPF não
			// informado" em toda linha) em vez de apontar a causa real.
			PlanilhaColumnMapper.ResultadoMapeamento mapeamento =
					columnMapper.mapear(sheet);

			if (!mapeamento.estruturaValida()) {
				throw new IllegalStateException(
						"Estrutura da planilha inválida: " + descreverProblemaEstrutura(mapeamento));
			}

			Map<String, Integer> colunas = mapeamento.indices();

			// ==============================
			// Pré-carrega cache IBGE do banco (CEPs já resolvidos em importações anteriores)
			// ==============================

			IbgeUtils.preCarregarCacheDb(carregarCepIbgeDoBanco());

			// Pré-carrega folhas já atribuídas para propagar a novos atendimentos na importação
			Map<String, String> mapaFolhas =
					atendimentoRepository.buscarMapaFolhaPorEspecialidadeMedico();

			// Código IBGE de todas as linhas ANTES de gravar qualquer coisa: se
			// algum município/IBGE faltar, a importação da planilha inteira é
			// bloqueada (decisão de 08/10/2026 — linhas não podem ser rejeitadas
			// uma a uma por falta do IBGE)
			verificarIbgeDeTodasAsLinhas(sheet, colunas);

			// Linhas sem CNS resolvido, decididas no fim da planilha (herança pelo CPF)
			List<PendenciaCns> pendentesCns = new ArrayList<>();

			// Linhas do mesmo atendimento dentro da planilha (mesmo horário → erro,
			// horário diferente → aviso de suspeita, importadas separadas)
			DetectorDuplicidade duplicidade = new DetectorDuplicidade();

			// Atendimento gravado por linha da planilha — conferência final (B)
			Map<Integer, AtendimentoBPAi> atendimentoPorLinha = new LinkedHashMap<>();
			int linhasComDados = 0;

			transaction.begin();

			for (Row row : sheet) {

				// Ignora cabeçalho e linhas sem nenhum valor (formatação ou
				// conteúdo apagado) — mesma regra da análise
				if (row.getRowNum() == 0 || excelService.isLinhaVazia(row)) {
					continue;
				}

				linhasComDados++;
				int linhaExcel = row.getRowNum() + 1;

				try {

					// 1. Converte linha Excel para DTO de importação
					LinhaImportacaoDTO dto =
							excelService.importarLinha(row, colunas);

					// 2. Processa regras de negócio (validação, normalização, conversão)
					// Retorna lista de avisos — situações atípicas que não impedem a importação
					List<String> avisos = processor.processar(dto);

					// 3. Coleta avisos com referência à linha da planilha
					for (String aviso : avisos) {
						resultado.adicionarAviso(
								"Linha " + linhaExcel + " - Aviso: " + aviso);
					}

					// 4. Mesmo atendimento em outra linha desta planilha
					DetectorDuplicidade.Ocorrencia ocorrencia = duplicidade.registrar(linhaExcel, dto);

					if (ocorrencia != null && ocorrencia.tipo() == DetectorDuplicidade.Tipo.REPETIDA) {
						throw new IllegalArgumentException(DetectorDuplicidade.mensagemRepetida(ocorrencia));
					}

					if (ocorrencia != null) {
						resultado.adicionarAviso("Linha " + linhaExcel + " - Aviso: "
								+ DetectorDuplicidade.mensagemSuspeita(ocorrencia, dto.getHoraAtendimento()));
					}

					// 5. Cria/encontra entidades normalizadas e monta o atendimento
					// findOrUpdate: se já existe atendimento idêntico, atualiza (evita duplicatas)
					AtendimentoBPAi atendimento =
							criarOuAtualizarAtendimento(dto, resultado, linhaExcel, mapaFolhas, pendentesCns);

					// Grava a linha agora: um erro do banco (ex.: valor maior que a
					// coluna) aparece nesta linha, e não numa linha seguinte ou no commit.
					entityManager.flush();

					importados.add(atendimento);
					atendimentoPorLinha.put(linhaExcel, atendimento);

					resultado.adicionarSucesso();

				} catch (Exception e) {

					// Captura erro específico da linha
					String mensagemErro =
							"Linha " + linhaExcel
									+ " - Erro: " + e.getClass().getSimpleName()
									+ " -> " + e.getMessage();

					resultado.adicionarErro(mensagemErro);

					// Erro do banco deixa a transação (única para a planilha) marcada
					// para rollback: o commit desfaria TODAS as linhas sem lançar erro,
					// e o log diria "Sucesso". Interrompe e avisa em vez de mentir.
					if (transaction.getRollbackOnly()) {
						transaction.rollback();
						throw new IllegalStateException(
								mensagemErro + " — erro ao gravar no banco; a importação foi cancelada"
										+ " e nenhuma linha foi importada. Corrija a linha e importe novamente.");
					}
				}
			}

			// (A) Tudo ou nada: todas as linhas da planilha devem ser importadas
			// (decisão de 09/10/2026). Uma linha com erro cancela a planilha inteira.
			if (!resultado.getErros().isEmpty()) {
				transaction.rollback();
				throw new IllegalStateException("Importação bloqueada — " + resultado.getErros().size()
						+ " linha(s) com erro; nenhuma linha foi importada. Corrija a planilha e importe"
						+ " novamente.\n" + String.join("\n", resultado.getErros()));
			}

			// (B) Conferência: cada linha com dados virou um atendimento próprio
			conferirLinhasImportadas(linhasComDados, atendimentoPorLinha);

			resolverCnsPendentes(pendentesCns, resultado);

			transaction.commit();

			resultado.setLinhasPlanilha(linhasComDados);

		} catch (IOException e) {

			if (transaction.isActive()) {
				transaction.rollback();
			}

			throw new RuntimeException(
					"Erro ao ler arquivo: " + e.getMessage()
			);

		} catch (Exception e) {

			if (transaction.isActive()) {
				transaction.rollback();
			}

			throw new RuntimeException(
					"Erro na importação: " + e.getMessage()
			);
		}

		resultado.setRegistrosImportados(importados);

		return resultado;
	}

	/**
	 * Cria ou atualiza um AtendimentoBPAi a partir do DTO processado.
	 *
	 * Lógica de deduplicação: busca atendimento existente pela chave natural
	 * (paciente + médico + data + sigtap). Se encontrado, atualiza os campos
	 * (inclusive CNS profissional). Se não, cria novo registro.
	 *
	 * Isso evita duplicatas quando a mesma planilha é reimportada
	 * (ex: após configurar o DATASUS para preencher CNS profissional).
	 */
	private AtendimentoBPAi criarOuAtualizarAtendimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel, Map<String, String> mapaFolhas, List<PendenciaCns> pendentesCns) {

		// ==============================
		// Código IBGE (antes de gravar qualquer coisa): tabela de MS pelo nome,
		// CEPs já conhecidos, APIs de CEP. Sem IBGE a linha é rejeitada — mesma
		// regra da análise (MUNICIPIO_NAO_ENCONTRADO, decisão de 08/10/2026).
		// ==============================

		IbgeUtils.IbgeResultado ibge = IbgeUtils.resolver(dto.getCep(), dto.getMunicipio());

		if (ibge.getCodigoIbge() == null) {
			// Não deveria acontecer: verificarIbgeDeTodasAsLinhas já bloqueou a
			// planilha antes. Proteção caso a busca mude entre as duas passadas.
			throw new IllegalArgumentException(mensagemIbgeNaoEncontrado(dto.getMunicipio(), dto.getCep()));
		}

		// ==============================
		// Paciente (findOrCreate por CPF)
		// ==============================

		Paciente paciente = buscarOuCriarPaciente(dto);

		// ==============================
		// Endereço (atualiza sempre com dados mais recentes)
		// ==============================

		atualizarEndereco(paciente, dto, ibge.getCodigoIbge());
		if (ibge.getAviso() != null) {
			resultado.adicionarAviso("Linha " + linhaExcel + " - Aviso: " + ibge.getAviso());
		}

		// ==============================
		// Médico (findOrCreate por CPF)
		// ==============================

		Medico medico = buscarOuCriarMedico(dto);

		// ==============================
		// Estabelecimento (findOrCreate por código)
		// ==============================

		Estabelecimento estabelecimento = buscarOuCriarEstabelecimento(dto, resultado, linhaExcel);

		// ==============================
		// Deduplicação: busca atendimento existente
		// Chave natural: paciente + médico + data + sigtap + horário — horário
		// diferente é outro atendimento (decisão de 09/10/2026); a mesma
		// planilha reimportada continua atualizando em vez de duplicar
		// ==============================

		AtendimentoBPAi atendimento = atendimentoRepository
				.buscarDuplicata(paciente, medico, dto.getDataAgendamento(), dto.getSigtap(), dto.getHoraAtendimento())
				.orElse(null);

		boolean atualizacao = atendimento != null;

		if (atualizacao) {

			resultado.contarAtualizado();
			resultado.adicionarAviso(
					"Linha " + linhaExcel + " - Aviso: Atendimento já existente atualizado"
							+ " (paciente: " + paciente.getNome()
							+ ", médico: " + medico.getNome()
							+ ", data: " + dto.getDataAgendamento() + ")");
		} else {

			resultado.contarNovo();
			atendimento = new AtendimentoBPAi();
			atendimento.setPaciente(paciente);
			atendimento.setMedico(medico);
			atendimento.setEstabelecimento(estabelecimento);
			atendimentoRepository.salvar(atendimento);
		}

		// ==============================
		// Atualiza campos (tanto para novo quanto para existente)
		// ==============================

		atendimento.setEstabelecimento(estabelecimento);
		atendimento.setTipoServico(dto.getTipoServico());
		atendimento.setSigtap(dto.getSigtap());
		atendimento.setDataAgendamento(dto.getDataAgendamento());
		atendimento.setHoraAtendimento(dto.getHoraAtendimento());
		atendimento.setEspecialidadeMedico(dto.getEspecialidadeMedico());
		atendimento.setCboMedico(dto.getCboMedico());
		atendimento.setCidConsulta(dto.getCidConsulta());
		atendimento.setPacienteSemCpf(dto.getPacienteSemCpf());

		// ==============================
		// Herança de folha para novos atendimentos
		// Se o médico já possui folha atribuída para este mês, propaga para o novo registro
		// ==============================

		if (!atualizacao && (atendimento.getFolha() == null || atendimento.getFolha().isBlank())) {
			String chave = resolverChaveFolha(
					dto.getEspecialidadeMedico(), medico.getId());
			String folhaExistente = mapaFolhas.get(chave);
			if (folhaExistente != null) {
				atendimento.setFolha(folhaExistente);
			}
		}

		// ==============================
		// CNS do profissional (nome no cadastro, reserva pelo CPF)
		// ==============================

		String avisoCns = resolverCnsProfissional(dto, medico, atendimento, linhaExcel, pendentesCns);

		if (avisoCns != null) {
			resultado.adicionarAviso("Linha " + linhaExcel + " - Aviso: " + avisoCns);
		}

		return atendimento;
	}

	/**
	 * Resolve o CNS do profissional da linha e grava no atendimento.
	 * <p>
	 * Fonte principal: o cadastro por nome ({@code medicos_cns.csv}, com
	 * apelidos) — o mesmo médico chega com grafias diferentes, por isso a
	 * busca é por nome e não por CPF. Quando o nome é encontrado, o CNS também
	 * fica guardado no {@link Medico} (chave CPF, só no banco local).
	 * <p>
	 * Reserva: grafia não cadastrada, mas o mesmo CPF já tem CNS conhecido
	 * → usa esse CNS e avisa para cadastrar a grafia como apelido.
	 *
	 * @return aviso para o log de importação, ou {@code null}
	 */
	private String resolverCnsProfissional(LinhaImportacaoDTO dto, Medico medico, AtendimentoBPAi atendimento,
			int linhaExcel, List<PendenciaCns> pendentesCns) {

		CnsProfissionalUtils.CnsResultado cnsResultado =
				CnsProfissionalUtils.buscar(dto.getMedico());

		String cnsPorNome = cnsResultado.getCns();
		String cnsDoCpf = medico.getCns();

		if (cnsPorNome != null) {

			atendimento.setCnsProfissional(cnsPorNome);
			medico.setCns(cnsPorNome);

			if (cnsDoCpf != null && !cnsDoCpf.equals(cnsPorNome)) {
				return "CNS do cadastro para \"" + dto.getMedico() + "\" (" + cnsPorNome
						+ ") difere do CNS já associado a este CPF de médico (" + cnsDoCpf
						+ ") — usado o CNS do cadastro. Confira se são o mesmo médico"
						+ " em Configurações → CNS de Médicos.";
			}

			return null;
		}

		if (cnsDoCpf != null) {
			atendimento.setCnsProfissional(cnsDoCpf);
			return avisoCnsHerdado(dto.getMedico(), cnsDoCpf);
		}

		// CPF ainda sem CNS conhecido: uma linha mais abaixo, com grafia
		// cadastrada do mesmo CPF, pode preenchê-lo — decide no fim da planilha
		// (resolverCnsPendentes), para a herança não depender da ordem das linhas.
		pendentesCns.add(new PendenciaCns(linhaExcel, dto.getMedico(), medico, atendimento, cnsResultado.getAviso()));
		return null;
	}

	/**
	 * Fim da planilha: linhas cuja grafia não estava cadastrada e cujo CPF não
	 * tinha CNS no momento. Se outra linha do mesmo CPF trouxe o CNS depois,
	 * herda; senão, mantém o aviso "CNS do profissional não encontrado".
	 */
	private void resolverCnsPendentes(List<PendenciaCns> pendentes, ImportacaoResultado resultado) {

		for (PendenciaCns p : pendentes) {

			String cnsDoCpf = p.medico().getCns();

			if (cnsDoCpf != null) {
				p.atendimento().setCnsProfissional(cnsDoCpf);
				resultado.adicionarAviso("Linha " + p.linha() + " - Aviso: " + avisoCnsHerdado(p.nome(), cnsDoCpf));
			} else if (p.avisoNaoEncontrado() != null) {
				resultado.adicionarAviso("Linha " + p.linha() + " - Aviso: " + p.avisoNaoEncontrado());
			}
		}
	}

	private String avisoCnsHerdado(String nome, String cns) {
		return "Grafia \"" + nome + "\" não cadastrada — CNS herdado do mesmo CPF ("
				+ cns + "). Considere cadastrá-la como apelido em Configurações → CNS de Médicos.";
	}

	/** Linha aguardando o fim da planilha para decidir a herança de CNS pelo CPF. */
	private record PendenciaCns(int linha, String nome, Medico medico, AtendimentoBPAi atendimento,
			String avisoNaoEncontrado) {
	}

	/**
	 * Busca paciente pelo CPF. Se não existir, cria novo.
	 * Se existir, atualiza os dados com a importação mais recente.
	 */
	private Paciente buscarOuCriarPaciente(LinhaImportacaoDTO dto) {

		// Paciente sem CPF ("Paciente sem CPF" = Sim, já validado no processador):
		// o CPF é a chave do paciente no banco, então usa uma chave interna
		// estável derivada do nome + nascimento. Nunca vai para o BPA-I.
		if (dto.getCpfPaciente() == null || dto.getCpfPaciente().isBlank()) {
			dto.setCpfPaciente(CpfUtils.chaveSemCpf(dto.getPaciente(), dto.getDataNascimento()));
		}

		return pacienteRepository.buscarPorCpf(dto.getCpfPaciente())
				.map(paciente -> {
					// Atualiza dados com a importação mais recente
					paciente.setNome(dto.getPaciente());
					paciente.setCns(dto.getCnsPaciente());
					paciente.setSexo(dto.getSexoPaciente());
					paciente.setRaca(dto.getRacaPaciente());
					paciente.setEtnia(dto.getEtniaPaciente());
					paciente.setDataNascimento(dto.getDataNascimento());
					paciente.setTelefone(dto.getTelefone());
					if (dto.getSituacaoRua() != null) {
						paciente.setSituacaoRua(dto.getSituacaoRua());
					}
					return paciente;
				})
				.orElseGet(() -> {
					Paciente novo = new Paciente();
					novo.setCpf(dto.getCpfPaciente());
					novo.setNome(dto.getPaciente());
					novo.setCns(dto.getCnsPaciente());
					novo.setSexo(dto.getSexoPaciente());
					novo.setRaca(dto.getRacaPaciente());
					novo.setEtnia(dto.getEtniaPaciente());
					novo.setDataNascimento(dto.getDataNascimento());
					novo.setTelefone(dto.getTelefone());
					novo.setSituacaoRua(dto.getSituacaoRua());
					pacienteRepository.salvar(novo);
					return novo;
				});
	}

	/**
	 * Atualiza o endereço do paciente com os dados mais recentes.
	 * Se não existir, cria um novo vinculado ao paciente.
	 */
	private void atualizarEndereco(Paciente paciente, LinhaImportacaoDTO dto, String codigoIbge) {

		Endereco endereco = paciente.getEndereco();

		if (endereco == null) {
			endereco = new Endereco();
			endereco.setPaciente(paciente);
			paciente.setEndereco(endereco);
		}

		endereco.setMunicipio(dto.getMunicipio());
		endereco.setTipoZona(dto.getTipoZona());
		endereco.setCep(dto.getCep());

		// Deriva tipo de logradouro a partir do prefixo do endereço
		LogradouroUtils.LogradouroResultado logr = LogradouroUtils.resolver(dto.getEndereco());
		endereco.setCodLogradouro(
				logr.getCodLogradouro() != null ? logr.getCodLogradouro() : dto.getCodLogradouro());
		endereco.setEndereco(logr.getEndereco());
		endereco.setComplemento(dto.getComplemento());
		endereco.setNumero(dto.getNumero());
		endereco.setBairro(dto.getBairro());

		// Código IBGE já resolvido (e obrigatório) em criarOuAtualizarAtendimento
		endereco.setCodigoIbge(codigoIbge);
	}

	/**
	 * Conferência final da importação (decisão de 09/10/2026 — todas as linhas
	 * da planilha devem ser importadas): o número de linhas com dados tem que
	 * bater com o número de linhas gravadas e com o número de atendimentos
	 * distintos. Duas linhas que caíssem no mesmo atendimento (uma
	 * sobrescrevendo a outra) fariam a conta não fechar.
	 *
	 * @throws IllegalStateException se a conta não fechar — a transação é desfeita
	 */
	private void conferirLinhasImportadas(int linhasComDados, Map<Integer, AtendimentoBPAi> atendimentoPorLinha) {

		Map<AtendimentoBPAi, Integer> primeiraLinha = new java.util.IdentityHashMap<>();
		List<String> sobrepostas = new ArrayList<>();

		atendimentoPorLinha.forEach((linha, atendimento) -> {
			Integer anterior = primeiraLinha.putIfAbsent(atendimento, linha);
			if (anterior != null) {
				sobrepostas.add("Linha " + linha + " gravou no mesmo atendimento da linha " + anterior);
			}
		});

		int distintos = primeiraLinha.size();

		if (atendimentoPorLinha.size() != linhasComDados || distintos != linhasComDados) {
			throw new IllegalStateException("Importação bloqueada — conferência de linhas não bate: a planilha tem "
					+ linhasComDados + " linha(s) com dados, seriam gravados " + distintos
					+ " atendimento(s); nenhuma linha foi importada."
					+ (sobrepostas.isEmpty() ? "" : "\n" + String.join("\n", sobrepostas)));
		}
	}

	/**
	 * Confere, antes de gravar qualquer linha, se todas têm município e código
	 * IBGE (tabela de MS pelo nome, CEPs já conhecidos, APIs de CEP — mesma
	 * busca da análise e da importação). Se alguma não tiver, lança exceção
	 * com a lista de linhas e a importação da planilha inteira é bloqueada:
	 * nenhuma linha é gravada. Os resultados ficam no cache do
	 * {@link IbgeUtils}, então a importação em seguida não consulta de novo.
	 *
	 * @throws IllegalStateException com as linhas sem município ou sem IBGE
	 */
	private void verificarIbgeDeTodasAsLinhas(Sheet sheet, Map<String, Integer> colunas) {

		List<String> problemas = new ArrayList<>();

		for (Row row : sheet) {

			if (row.getRowNum() == 0 || excelService.isLinhaVazia(row)) {
				continue;
			}

			LinhaImportacaoDTO dto;
			try {
				dto = excelService.importarLinha(row, colunas);
			} catch (Exception e) {
				continue; // linha ilegível: o erro aparece no processamento normal da linha
			}

			String municipio = dto.getMunicipio();
			int linha = row.getRowNum() + 1;

			if (municipio == null || municipio.isBlank()) {
				problemas.add("Linha " + linha + ": Município do paciente não informado.");
				continue;
			}

			String cep = br.gov.ses.fillbpai.util.CepUtils.normalizar(dto.getCep());

			if (IbgeUtils.resolver(cep, municipio).getCodigoIbge() == null) {
				problemas.add("Linha " + linha + ": " + mensagemIbgeNaoEncontrado(municipio, cep));
			}
		}

		if (!problemas.isEmpty()) {
			throw new IllegalStateException("Importação bloqueada — código IBGE do município não encontrado em "
					+ problemas.size() + " linha(s); nenhuma linha foi importada. Corrija a planilha e importe"
					+ " novamente.\n" + String.join("\n", problemas));
		}
	}

	private static String mensagemIbgeNaoEncontrado(String municipio, String cep) {
		return "Município \"" + municipio + "\" não encontrado e o CEP " + cep + " também não foi encontrado"
				+ " — verifique se a grafia do município e o CEP estão corretos.";
	}

	/**
	 * Pré-carrega no cache do {@link IbgeUtils} os CEPs já resolvidos no banco,
	 * para a busca do IBGE pelo CEP não ir à internet à toa. Usado antes da
	 * importação e antes do "Analisar Planilha" ({@code MainController}).
	 */
	public void preCarregarCacheIbge() {
		IbgeUtils.preCarregarCacheDb(carregarCepIbgeDoBanco());
	}

	/**
	 * Busca médico pelo CPF. Se não existir, cria novo.
	 * Se existir, atualiza o nome.
	 * <p>
	 * O CPF já chega validado (11 dígitos) por
	 * {@code AtendimentoProcessor.validarCpfMedico} — linha sem CPF do médico
	 * é rejeitada antes de chegar aqui.
	 */
	private Medico buscarOuCriarMedico(LinhaImportacaoDTO dto) {

		return medicoRepository.buscarPorCpf(dto.getCpfMedico())
				.map(medico -> {
					medico.setNome(dto.getMedico());
					return medico;
				})
				.orElseGet(() -> {
					Medico novo = new Medico();
					novo.setCpf(dto.getCpfMedico());
					novo.setNome(dto.getMedico());
					medicoRepository.salvar(novo);
					return novo;
				});
	}

	/**
	 * Busca estabelecimento pelo código (sempre numérico — ver
	 * {@code StringUtils.separarCodigoENome}). Se existir, atualiza o nome
	 * quando a linha traz um; se não existir, cria — desde que a linha traga
	 * o nome.
	 * <ul>
	 *   <li>Sem código: vínculo por nome com um já cadastrado (aviso
	 *       ESTABELECIMENTO_SEM_CODIGO já emitido pelo processador).</li>
	 *   <li>Só o código ({@code "1234567"}, {@code "12345 -"}): vincula se o
	 *       código já estiver cadastrado, <b>sem apagar o nome</b>; se não
	 *       estiver, o atendimento fica sem estabelecimento, com aviso (não dá
	 *       para cadastrar sem nome).</li>
	 * </ul>
	 */
	private Estabelecimento buscarOuCriarEstabelecimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado,
			int linhaExcel) {

		String codigo = dto.getCodEstabelecimento();
		String nome = dto.getEstabelecimento();
		boolean temNome = nome != null && !nome.isBlank();

		if (codigo == null || codigo.isBlank()) {
			// Código não reconhecido na célula — tenta reaproveitar um
			// estabelecimento já cadastrado com o mesmo nome antes de desistir
			// do vínculo (AVISO já emitido em AtendimentoProcessor/ValidacaoPlanilhaService).
			return estabelecimentoRepository.buscarPorNome(nome).orElse(null);
		}

		return estabelecimentoRepository.buscarPorCodigo(codigo)
				.map(estab -> {
					if (temNome) {
						estab.setNome(nome);
					}
					return estab;
				})
				.orElseGet(() -> {
					if (!temNome) {
						resultado.adicionarAviso("Linha " + linhaExcel + " - Aviso: Estabelecimento informado só"
								+ " pelo código " + codigo + ", que não está cadastrado — o atendimento ficou sem"
								+ " estabelecimento. Informe \"código - nome\" na planilha para cadastrá-lo.");
						return null;
					}
					Estabelecimento novo = new Estabelecimento();
					novo.setCodigo(codigo);
					novo.setNome(nome);
					estabelecimentoRepository.salvar(novo);
					return novo;
				});
	}

	/**
	 * Monta a chave de lookup no mapa de folhas existentes.
	 * Formato idêntico ao usado em buscarMapaFolhaPorEspecialidadeMedico().
	 */
	private String resolverChaveFolha(String especialidade, Long medicoId) {
		String esp = especialidade != null ? especialidade : "";
		return esp + "|" + medicoId;
	}

	/**
	 * Monta uma descrição legível dos problemas de estrutura do cabeçalho
	 * (colunas obrigatórias ausentes e/ou duplicadas) para uso em mensagens
	 * de erro voltadas ao usuário.
	 */
	private String descreverProblemaEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento) {

		StringBuilder sb = new StringBuilder();

		if (!mapeamento.camposFaltando().isEmpty()) {
			sb.append("colunas obrigatórias não encontradas no cabeçalho: ")
					.append(String.join(", ", mapeamento.camposFaltando()));
		}

		if (!mapeamento.camposDuplicados().isEmpty()) {
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append("colunas duplicadas no cabeçalho: ")
					.append(String.join(", ", mapeamento.camposDuplicados()));
		}

		if (!mapeamento.colunasSemCabecalho().isEmpty()) {
			sb.append("; colunas com dados mas sem cabeçalho: ")
					.append(String.join(", ", mapeamento.colunasSemCabecalho()))
					.append(" — corrija na planilha: escreva o nome da coluna no cabeçalho");
		}

		return sb.toString();
	}

	/**
	 * Carrega todos os pares CEP → codigoIbge já persistidos na tabela Endereco.
	 * Usado para pré-popular o cache do IbgeUtils antes do loop de importação,
	 * evitando chamadas à API ViaCEP para CEPs já conhecidos.
	 */
	private Map<String, String> carregarCepIbgeDoBanco() {

		Map<String, String> mapa = new HashMap<>();

		try {

			@SuppressWarnings("unchecked")
			List<Object[]> resultado = entityManager
					.createQuery(
							"SELECT e.cep, e.codigoIbge FROM Endereco e" +
							" WHERE e.cep IS NOT NULL AND e.codigoIbge IS NOT NULL")
					.getResultList();

			for (Object[] linha : resultado) {
				String cep   = (String) linha[0];
				String ibge  = (String) linha[1];
				if (cep != null && ibge != null) {
					mapa.put(cep, ibge);
				}
			}

		} catch (Exception e) {
			// Falha não deve impedir a importação — o cache ficará vazio
		}

		return mapa;
	}

}
