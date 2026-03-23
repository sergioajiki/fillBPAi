package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.model.*;
import br.gov.ses.fillbpai.util.CnsProfissionalUtils;
import br.gov.ses.fillbpai.util.IbgeUtils;
import br.gov.ses.fillbpai.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

			// ==============================
			// Pré-carrega CNS do DATASUS (single-pass para todos os médicos)
			// ==============================

			Set<String> nomesMedicos = coletarNomesMedicos(sheet);

			if (!nomesMedicos.isEmpty()) {
				List<String> avisosDatasus =
						CnsProfissionalUtils.buscarLoteDatasus(nomesMedicos);

				for (String aviso : avisosDatasus) {
					resultado.adicionarAviso("DATASUS - " + aviso);
				}
			}

			transaction.begin();

			for (Row row : sheet) {

				// Ignora cabeçalho
				if (row.getRowNum() == 0) {
					continue;
				}

				try {

					// 1. Converte linha Excel para DTO de importação
					LinhaImportacaoDTO dto =
							excelService.importarLinha(row);

					// 2. Processa regras de negócio (validação, normalização, conversão)
					// Retorna lista de avisos — situações atípicas que não impedem a importação
					List<String> avisos = processor.processar(dto);

					// 3. Coleta avisos com referência à linha da planilha
					for (String aviso : avisos) {
						resultado.adicionarAviso(
								"Linha " + (row.getRowNum() + 1) + " - Aviso: " + aviso);
					}

					// 4. Cria/encontra entidades normalizadas e monta o atendimento
					// findOrUpdate: se já existe atendimento idêntico, atualiza (evita duplicatas)
					AtendimentoBPAi atendimento =
							criarOuAtualizarAtendimento(dto, resultado, row.getRowNum() + 1);

					importados.add(atendimento);

					resultado.adicionarSucesso();

				} catch (Exception e) {

					// Captura erro específico da linha
					String mensagemErro =
							"Linha " + (row.getRowNum() + 1)
									+ " - Erro: " + e.getClass().getSimpleName()
									+ " -> " + e.getMessage();

					resultado.adicionarErro(mensagemErro);
				}
			}

			transaction.commit();

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
	private AtendimentoBPAi criarOuAtualizarAtendimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel) {

		// ==============================
		// Paciente (findOrCreate por CPF)
		// ==============================

		Paciente paciente = buscarOuCriarPaciente(dto);

		// ==============================
		// Endereço (atualiza sempre com dados mais recentes)
		// ==============================

		String avisoIbge = atualizarEndereco(paciente, dto);
		if (avisoIbge != null) {
			resultado.adicionarAviso("Linha " + linhaExcel + " - Aviso: " + avisoIbge);
		}

		// ==============================
		// Médico (findOrCreate por CPF)
		// ==============================

		Medico medico = buscarOuCriarMedico(dto);

		// ==============================
		// Estabelecimento (findOrCreate por código)
		// ==============================

		Estabelecimento estabelecimento = buscarOuCriarEstabelecimento(dto);

		// ==============================
		// Deduplicação: busca atendimento existente
		// Chave natural: paciente + médico + data + sigtap
		// ==============================

		AtendimentoBPAi atendimento = atendimentoRepository
				.buscarDuplicata(paciente, medico, dto.getDataAgendamento(), dto.getSigtap())
				.orElse(null);

		boolean atualizacao = atendimento != null;

		if (atualizacao) {

			resultado.adicionarAviso(
					"Linha " + linhaExcel + " - Aviso: Atendimento já existente atualizado"
							+ " (paciente: " + paciente.getNome()
							+ ", médico: " + medico.getNome()
							+ ", data: " + dto.getDataAgendamento() + ")");
		} else {

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

		// ==============================
		// CNS do profissional (lookup por nome via cache/DATASUS)
		// ==============================

		CnsProfissionalUtils.CnsResultado cnsResultado =
				CnsProfissionalUtils.buscar(dto.getMedico());

		if (cnsResultado.getCns() != null) {
			atendimento.setCnsProfissional(cnsResultado.getCns());
		}

		if (cnsResultado.getAviso() != null) {
			resultado.adicionarAviso(
					"Linha " + linhaExcel + " - Aviso: " + cnsResultado.getAviso());
		}

		return atendimento;
	}

	/**
	 * Busca paciente pelo CPF. Se não existir, cria novo.
	 * Se existir, atualiza os dados com a importação mais recente.
	 */
	private Paciente buscarOuCriarPaciente(LinhaImportacaoDTO dto) {

		return pacienteRepository.buscarPorCpf(dto.getCpfPaciente())
				.map(paciente -> {
					// Atualiza dados com a importação mais recente
					paciente.setNome(dto.getPaciente());
					paciente.setCns(dto.getCnsPaciente());
					paciente.setSexo(dto.getSexoPaciente());
					paciente.setRaca(dto.getRacaPaciente());
					paciente.setDataNascimento(dto.getDataNascimento());
					paciente.setTelefone(dto.getTelefone());
					return paciente;
				})
				.orElseGet(() -> {
					Paciente novo = new Paciente();
					novo.setCpf(dto.getCpfPaciente());
					novo.setNome(dto.getPaciente());
					novo.setCns(dto.getCnsPaciente());
					novo.setSexo(dto.getSexoPaciente());
					novo.setRaca(dto.getRacaPaciente());
					novo.setDataNascimento(dto.getDataNascimento());
					novo.setTelefone(dto.getTelefone());
					pacienteRepository.salvar(novo);
					return novo;
				});
	}

	/**
	 * Atualiza o endereço do paciente com os dados mais recentes.
	 * Se não existir, cria um novo vinculado ao paciente.
	 */
	private String atualizarEndereco(Paciente paciente, LinhaImportacaoDTO dto) {

		Endereco endereco = paciente.getEndereco();

		if (endereco == null) {
			endereco = new Endereco();
			endereco.setPaciente(paciente);
			paciente.setEndereco(endereco);
		}

		endereco.setMunicipio(dto.getMunicipio());
		endereco.setTipoZona(dto.getTipoZona());
		endereco.setCep(dto.getCep());
		endereco.setCodLogradouro(dto.getCodLogradouro());
		endereco.setEndereco(dto.getEndereco());
		endereco.setComplemento(dto.getComplemento());
		endereco.setNumero(dto.getNumero());
		endereco.setBairro(dto.getBairro());

		// Resolve código IBGE do município via CEP (primário) ou nome (fallback)
		IbgeUtils.IbgeResultado ibgeResultado =
				IbgeUtils.resolver(dto.getCep(), dto.getMunicipio());

		endereco.setCodigoIbge(ibgeResultado.getCodigoIbge());

		return ibgeResultado.getAviso();
	}

	/**
	 * Busca médico pelo CPF. Se não existir, cria novo.
	 * Se existir, atualiza o nome.
	 */
	private Medico buscarOuCriarMedico(LinhaImportacaoDTO dto) {

		if (dto.getCpfMedico() == null || dto.getCpfMedico().isBlank()) {
			// Médico sem CPF — cria registro mínimo com nome
			Medico novo = new Medico();
			novo.setCpf("SEM_CPF_" + System.nanoTime());
			novo.setNome(dto.getMedico());
			medicoRepository.salvar(novo);
			return novo;
		}

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
	 * Busca estabelecimento pelo código. Se não existir, cria novo.
	 * Se existir, atualiza o nome.
	 */
	private Estabelecimento buscarOuCriarEstabelecimento(LinhaImportacaoDTO dto) {

		if (dto.getCodEstabelecimento() == null || dto.getCodEstabelecimento().isBlank()) {
			return null;
		}

		return estabelecimentoRepository.buscarPorCodigo(dto.getCodEstabelecimento())
				.map(estab -> {
					estab.setNome(dto.getEstabelecimento());
					return estab;
				})
				.orElseGet(() -> {
					Estabelecimento novo = new Estabelecimento();
					novo.setCodigo(dto.getCodEstabelecimento());
					novo.setNome(dto.getEstabelecimento());
					estabelecimentoRepository.salvar(novo);
					return novo;
				});
	}

	/**
	 * Coleta nomes únicos dos médicos da planilha Excel (primeira passada).
	 * Utilizado para pré-carregar CNS do DATASUS em um único streaming pass.
	 *
	 * @param sheet aba da planilha Excel
	 * @return conjunto de nomes de médicos (únicos)
	 */
	private Set<String> coletarNomesMedicos(Sheet sheet) {

		Set<String> nomes = new LinkedHashSet<>();

		for (Row row : sheet) {

			if (row.getRowNum() == 0) {
				continue;
			}

			try {

				LinhaImportacaoDTO dto = excelService.importarLinha(row);
				processor.processar(dto);

				String nomeMedico = dto.getMedico();

				if (nomeMedico != null && !nomeMedico.isBlank()) {
					nomes.add(nomeMedico);
				}

			} catch (Exception e) {
				// Ignora erros na coleta — serão tratados na segunda passada
			}
		}

		return nomes;
	}
}
