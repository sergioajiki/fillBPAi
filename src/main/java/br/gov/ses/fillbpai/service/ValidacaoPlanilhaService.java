package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.CboUtils;
import br.gov.ses.fillbpai.util.CepUtils;
import br.gov.ses.fillbpai.util.CnsProfissionalUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.EspecialidadeUtils;
import br.gov.ses.fillbpai.util.EtniaUtils;
import br.gov.ses.fillbpai.util.IbgeUtils;
import br.gov.ses.fillbpai.util.RacaUtils;
import br.gov.ses.fillbpai.util.SimNaoUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.TextoUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Serviço responsável por validar uma planilha Excel antes da importação.
 * <p>
 * Percorre todas as linhas da planilha e aplica regras de validação
 * bloqueantes e não bloqueantes sobre os dados brutos. Nenhum dado é
 * persistido — o objetivo é apenas identificar e reportar inconsistências
 * para correção manual.
 * <p>
 * Regras validadas:
 * <ul>
 *   <li>Data de agendamento ausente ou em formato não reconhecido — ERRO bloqueante</li>
 *   <li>Tipo de serviço ausente (exceto nutricionista/psicólogo) ou não reconhecido — ERRO bloqueante</li>
 *   <li>CNS do paciente ausente ou com menos de 15 dígitos: AVISO (não bloqueia)</li>
 *   <li>CNS do paciente com mais de 15 dígitos: AVISO (não bloqueia)</li>
 *   <li>CEP: não pode ser ausente ou vazio — ERRO bloqueante</li>
 *   <li>CPF do paciente: não pode ser ausente ou vazio — ERRO bloqueante</li>
 *   <li>CPF do médico ausente ou com tamanho diferente de 11 dígitos — ERRO bloqueante</li>
 *   <li>CBO do médico ausente ou sem 6 dígitos (máscara aceita) — ERRO bloqueante</li>
 *   <li>Especialidade ausente — ERRO bloqueante</li>
 *   <li>Hora de atendimento preenchida mas não reconhecida: AVISO (não bloqueia)</li>
 *   <li>Médico (grafia) sem CNS em Configurações → CNS de Médicos: AVISO por grafia (não bloqueia)</li>
 *   <li>Raça do paciente ausente ou não reconhecida (grafia incorreta): ERRO bloqueante</li>
 *   <li>Coluna opcional (ex.: COD_LOGRADOURO, SITUACAO_RUA, PACIENTE_SEM_CPF) ausente do cabeçalho: AVISO (não bloqueia)</li>
 *   <li>Situação de rua preenchida mas não reconhecida (não é S/N, Sim/Não ou 1/0): AVISO (não bloqueia)</li>
 *   <li>Paciente sem CPF preenchido mas não reconhecido (não é S/N, Sim/Não ou 1/0): AVISO (não bloqueia)</li>
 *   <li>Estabelecimento preenchido mas sem separador "código - nome" reconhecido: AVISO (não bloqueia)</li>
 * </ul>
 *
 * @see ErroValidacao
 * @see ExcelImportService
 */
public class ValidacaoPlanilhaService {

	private static final Logger log = LoggerFactory.getLogger(ValidacaoPlanilhaService.class);

	/** Serviço de leitura de linhas Excel, reutilizado da importação. */
	private final ExcelImportService excelService = new ExcelImportService();

	/** Mesmo processamento da importação — usado para montar a chave de duplicidade. */
	private final AtendimentoProcessor processor = new AtendimentoProcessor();

	/** Resolve os campos canônicos a partir do cabeçalho, por nome. */
	private final PlanilhaColumnMapper columnMapper = new PlanilhaColumnMapper();

	/**
	 * CPF do médico (11 dígitos) → CNS já conhecido no banco local
	 * ({@code Medico.cns}), ou {@code null}. Usado para dizer, no aviso de CNS
	 * do profissional, se uma grafia não cadastrada vai herdar o CNS do CPF.
	 */
	private final Function<String, String> cnsConhecidoPorCpf;

	/**
	 * (CEP normalizado, município) → código IBGE pelo CEP (CEPs já conhecidos +
	 * APIs de CEP), ou {@code null}. Só é chamado quando o município não está na
	 * tabela de MS. Substituível nos testes, para não usar a rede.
	 */
	private final BiFunction<String, String, String> ibgePorCep;

	/** Sem acesso ao banco: a herança de CNS só considera a própria planilha. */
	public ValidacaoPlanilhaService() {
		this(cpf -> null);
	}

	/**
	 * @param cnsConhecidoPorCpf busca o CNS já associado a um CPF de médico no
	 *                           banco local (ex.: via {@code MedicoRepository})
	 */
	public ValidacaoPlanilhaService(Function<String, String> cnsConhecidoPorCpf) {
		this(cnsConhecidoPorCpf, (cep, municipio) -> IbgeUtils.resolver(cep, municipio).getCodigoIbge());
	}

	/**
	 * @param cnsConhecidoPorCpf ver {@link #ValidacaoPlanilhaService(Function)}
	 * @param ibgePorCep         busca do código IBGE pelo CEP (padrão: {@code IbgeUtils.resolver})
	 */
	public ValidacaoPlanilhaService(Function<String, String> cnsConhecidoPorCpf,
			BiFunction<String, String, String> ibgePorCep) {
		this.cnsConhecidoPorCpf = cnsConhecidoPorCpf;
		this.ibgePorCep = ibgePorCep;
	}

	/** Busca o IBGE pelo CEP; falha inesperada conta como "não encontrado" (não interrompe a análise). */
	private String buscarIbgeNoCep(String cep, String municipio) {
		try {
			return ibgePorCep.apply(cep, municipio);
		} catch (RuntimeException e) {
			log.warn("Falha ao buscar o IBGE pelo CEP {}: {}", cep, e.getMessage());
			return null;
		}
	}

	/** Ocorrências de uma grafia de médico na planilha (agrupadas sem acento/caixa). */
	private static final class GrafiaMedico {
		final String nome;
		final String cnsCadastro;
		final List<Integer> linhas = new ArrayList<>();
		final Set<String> cpfs = new LinkedHashSet<>();

		GrafiaMedico(String nome, String cnsCadastro) {
			this.nome = nome;
			this.cnsCadastro = cnsCadastro;
		}
	}

	/**
	 * Lê e valida todas as linhas da planilha Excel informada.
	 * <p>
	 * O cabeçalho (linha 0) é ignorado. Para cada linha de dados, as 3 regras
	 * bloqueantes são verificadas. Linhas que causem exceção durante a leitura
	 * são logadas e puladas sem interromper o processo.
	 *
	 * @param caminhoArquivo caminho absoluto para o arquivo {@code .xlsx}
	 * @return lista de erros encontrados; vazia se a planilha estiver válida
	 */
	public List<ErroValidacao> validar(String caminhoArquivo) {

		List<ErroValidacao> erros = new ArrayList<>();

		try (FileInputStream fis = new FileInputStream(caminhoArquivo);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			// Considera sempre a primeira aba da planilha
			Sheet sheet = workbook.getSheetAt(0);

			Row cabecalho = sheet.getRow(0);

			if (cabecalho == null) {
				erros.add(new ErroValidacao(1, ErroValidacao.Severidade.ERRO,
						ErroValidacao.ESTRUTURA_INVALIDA,
						"Planilha sem cabecalho — impossivel validar estrutura"));
				return erros;
			}

			// Mapeia os campos canônicos pelo nome do cabeçalho — independente
			// da ordem das colunas e ignorando colunas extras não reconhecidas.
			PlanilhaColumnMapper.ResultadoMapeamento mapeamento = columnMapper.mapear(cabecalho);

			if (!reportarEstrutura(mapeamento, erros)) {
				return erros;
			}

			Map<String, Integer> colunas = mapeamento.indices();

			// Planilha legado: captura o primeiro valor bruto (ainda combinado,
			// "ESPECIALIDADE - NOME") encontrado na coluna compartilhada, para
			// exibir como exemplo no aviso de formato legado.
			String exemploLegado = null;

			// CNS do profissional: avaliado por grafia de médico (não por linha),
			// depois de ler a planilha toda — a herança pelo CPF não depende da
			// ordem das linhas (mesma regra da importação).
			Map<String, GrafiaMedico> grafiasMedico = new LinkedHashMap<>();
			Map<String, String> cnsPorCpfNaPlanilha = new HashMap<>();

			// Linhas do mesmo atendimento (paciente + médico + data + procedimento)
			DetectorDuplicidade duplicidade = new DetectorDuplicidade();

			for (Row row : sheet) {

				// Pula o cabeçalho (linha de índice 0) e linhas sem nenhum valor
				// (formatação ou conteúdo apagado) — mesma regra da importação
				if (row.getRowNum() == 0 || excelService.isLinhaVazia(row)) {
					continue;
				}

				// Número da linha no relatório (1-based, inclui cabeçalho)
				int numeroLinha = row.getRowNum() + 1;

				try {
					LinhaImportacaoDTO dto = excelService.importarLinha(row, colunas);

					if (mapeamento.especialidadeMedicoCombinados() && exemploLegado == null
							&& dto.getMedico() != null && !dto.getMedico().isBlank()) {
						exemploLegado = dto.getMedico();
					}

					validarLinha(dto, numeroLinha, erros);
					registrarMedico(dto, numeroLinha, mapeamento.especialidadeMedicoCombinados(),
							grafiasMedico, cnsPorCpfNaPlanilha);
					verificarDuplicidade(row, colunas, numeroLinha, duplicidade, erros);
				} catch (Exception e) {
					// Toda linha com dados precisa ser importada: linha ilegível bloqueia
					log.warn("Erro ao ler linha {}: {}", numeroLinha, e.getMessage());
					erros.add(new ErroValidacao(numeroLinha, ErroValidacao.Severidade.ERRO,
							ErroValidacao.LINHA_ILEGIVEL,
							"Linha nao pode ser lida: " + e.getMessage()));
				}
			}

			reportarCnsProfissional(grafiasMedico, cnsPorCpfNaPlanilha, erros);

			if (mapeamento.especialidadeMedicoCombinados()) {
				erros.add(new ErroValidacao(1, ErroValidacao.Severidade.AVISO,
						ErroValidacao.FORMATO_LEGADO_ESPECIALIDADE_MEDICO,
						construirDetalheFormatoLegado(exemploLegado)));
			}

		} catch (IOException e) {
			log.error("Falha ao abrir o arquivo Excel para validação: {}", caminhoArquivo, e);
		}

		return erros;
	}

	/**
	 * Aplica as 3 regras de validação bloqueantes sobre um DTO já lido.
	 * Cada violação gera um {@link ErroValidacao} adicionado à lista.
	 *
	 * @param dto        dados brutos lidos da linha
	 * @param linha      número da linha (1-based) para referência no relatório
	 * @param erros      lista acumuladora de erros
	 */
	private void validarLinha(LinhaImportacaoDTO dto, int linha, List<ErroValidacao> erros) {

		// Valor exibido nos avisos sobre dados do paciente (agrupa as linhas
		// do mesmo paciente na tela e no log — ver AgrupamentoValidacao). Com o
		// rótulo "Paciente:" — só o nome solto no item causava estranheza.
		String paciente = "Paciente: " + (dto.getPaciente() != null && !dto.getPaciente().isBlank()
				? dto.getPaciente().trim() : "(sem nome)");

		// -------------------------------------------------------
		// Regra 0a: Data de agendamento (= data do atendimento; define
		// prd-dtaten e a competência) — ERRO bloqueante. Mesma conversão
		// da importação (DateUtils.parse); antes a análise não olhava a data
		// e a linha só era descartada na importação.
		// -------------------------------------------------------
		String data = dto.getDataAgendamentoString();

		if (data == null || data.isBlank()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.DATA_AUSENTE, "Data de agendamento (data do atendimento) nao informada",
					paciente));
		} else {
			try {
				DateUtils.parse(data);
			} catch (IllegalArgumentException e) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
						ErroValidacao.DATA_INVALIDA,
						"Data de agendamento \"" + data.trim() + "\" em formato nao reconhecido"
								+ " (use dd/MM/aaaa, ex.: 25/12/2024)",
						"\"" + data.trim() + "\""));
			}
		}

		// -------------------------------------------------------
		// Regra 0c: Nome do paciente e data de nascimento — a importação
		// rejeitaria a linha (AtendimentoProcessor); antes a análise não
		// olhava esses campos e a linha sumia na importação. ERRO bloqueante.
		// Nascimento vazio continua aceito (só o formato é conferido).
		// -------------------------------------------------------
		if (dto.getPaciente() == null || dto.getPaciente().isBlank()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.PACIENTE_AUSENTE, "Nome do paciente nao informado",
					"Paciente: (sem nome) — CPF " + (dto.getCpfPaciente() == null || dto.getCpfPaciente().isBlank()
							? "não informado" : dto.getCpfPaciente().trim())));
		}

		String nascimento = dto.getDataNascimentoString();

		if (nascimento != null && !nascimento.isBlank()) {
			try {
				DateUtils.parse(nascimento);
			} catch (IllegalArgumentException e) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
						ErroValidacao.DATA_NASCIMENTO_INVALIDA,
						"Data de nascimento \"" + nascimento.trim() + "\" em formato nao reconhecido"
								+ " (use dd/MM/aaaa, ex.: 25/12/1980)",
						paciente + " — \"" + nascimento.trim() + "\""));
			}
		}

		// -------------------------------------------------------
		// Regra 0b: Tipo de serviço — define o SIGTAP (prd-pa). ERRO
		// bloqueante; vazio é aceito só para nutricionista/psicólogo, que
		// usam procedimento fixo (decisão de 08/10/2026). Mesma regra de
		// AtendimentoProcessor.definirSigtap.
		// -------------------------------------------------------
		String tipo = dto.getTipoServico();

		if (tipo == null || tipo.isBlank()) {
			String especialidade = especialidadeDaLinha(dto);

			if (EspecialidadeUtils.usaProcedimentoFixo(especialidade)) {
				// Não bloqueia (procedimento fixo), mas indica a coluna vazia;
				// o valor é a especialidade, para agrupar as linhas por ela
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO,
						"Tipo de servico nao informado - para " + EspecialidadeUtils.padronizar(especialidade)
								+ " sera usado o procedimento fixo 0301010315",
						EspecialidadeUtils.padronizar(especialidade)));
			} else {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
						ErroValidacao.TIPO_SERVICO_AUSENTE,
						"Tipo de servico nao informado (aceitos: " + AtendimentoProcessor.TIPOS_SERVICO_ACEITOS + ")",
						paciente));
			}
		} else if (AtendimentoProcessor.codigoSigtap(tipo) == null) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.TIPO_SERVICO_INVALIDO,
					"Tipo de servico \"" + tipo.trim() + "\" nao reconhecido (aceitos: "
							+ AtendimentoProcessor.TIPOS_SERVICO_ACEITOS + ")",
					"\"" + tipo.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 1: CNS do paciente
		// - Ausente ou < 15 dígitos → AVISO, não bloqueia
		// - > 15 dígitos (formato legado) → AVISO, não bloqueia
		// -------------------------------------------------------
		String cnsNormalizado = CnsUtils.normalizar(dto.getCnsPaciente());

		if (cnsNormalizado == null || cnsNormalizado.isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INVALIDO, "CNS do paciente não informado — registrado com aviso (CNS_INVALIDO)",
					paciente + " — não informado"));
		} else if (cnsNormalizado.length() < 15) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INVALIDO,
					"CNS inválido (apenas " + cnsNormalizado.length()
							+ " dígitos, mínimo 15) — registrado com aviso (CNS_INVALIDO)",
					paciente + " — " + cnsNormalizado));
		} else if (cnsNormalizado.length() > 15) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INCOMUM,
					"CNS com formato incomum (" + cnsNormalizado.length()
							+ " dígitos, esperado 15): " + cnsNormalizado,
					paciente + " — " + cnsNormalizado));
		}

		// -------------------------------------------------------
		// Regra 1b: Município — usado para achar o código IBGE (prd-ibge,
		// obrigatório no BPA-I). Decisão de 08/10/2026:
		// - vazio: ERRO (sem o nome não há a primeira busca, pela tabela de MS)
		// - fora da tabela de MS: a análise já faz a busca completa pelo CEP
		//   (CEPs do banco + APIs de CEP); só é ERRO se o IBGE não for
		//   encontrado, pedindo para conferir a grafia do município e o CEP
		// -------------------------------------------------------
		String municipio = dto.getMunicipio();

		if (municipio == null || municipio.isBlank()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.MUNICIPIO_AUSENTE,
					"Municipio do paciente vazio na planilha - preencha o municipio",
					paciente + " — município ausente"));
		} else if (IbgeUtils.buscarPorNome(municipio) == null) {
			String cepNormalizado = CepUtils.normalizar(dto.getCep());
			String ibgePeloCep = buscarIbgeNoCep(cepNormalizado, municipio.trim());

			if (ibgePeloCep != null) {
				// Encontrado pelo CEP: não bloqueia, mas mostra o município que o
				// CEP indica, para conferir se é o mesmo da planilha
				String encontrado = IbgeUtils.nomeMunicipio(ibgePeloCep);
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.MUNICIPIO_PELO_CEP,
						IbgeUtils.avisoMunicipioPeloCep(municipio.trim(), cepNormalizado, ibgePeloCep),
						"\"" + municipio.trim() + "\" — CEP " + cepNormalizado + " → "
								+ (encontrado != null ? encontrado : "IBGE " + ibgePeloCep)));
			} else {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
						ErroValidacao.MUNICIPIO_NAO_ENCONTRADO,
						"Município \"" + municipio.trim() + "\" não encontrado e o CEP "
								+ (cepNormalizado != null ? cepNormalizado : "(vazio)")
								+ " também não foi encontrado - verifique se a grafia do município e o CEP"
								+ " estão corretos",
						"\"" + municipio.trim() + "\" — CEP " + (cepNormalizado != null ? cepNormalizado : "vazio")));
			}
		}

		// -------------------------------------------------------
		// Regra 2: CEP do endereço — ERRO bloqueante
		// -------------------------------------------------------
		String cep = dto.getCep();

		if (cep == null || cep.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CEP_AUSENTE, "CEP do endereço não informado", paciente));
		} else if (!CepUtils.isValido(cep)) {
			String cepNormalizado = CepUtils.normalizar(cep);
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CEP_INVALIDO,
					"CEP com tamanho inválido (" + (cepNormalizado != null ? cepNormalizado.length() : 0)
							+ " dígitos, esperado 8): " + cep.trim(),
					paciente + " — \"" + cep.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 3: CPF do paciente — ERRO bloqueante (decisão de 08/10/2026):
		// - vazio: erro, exceto com a coluna "Paciente sem CPF" = Sim
		// - menos ou mais de 11 dígitos: erro (sem completar zeros — não dá
		//   para garantir que o dígito que falta é um zero à esquerda)
		// - dígitos todos iguais (00000000000, 11111111111...): CPF falso, erro
		// -------------------------------------------------------
		String cpf = dto.getCpfPaciente();

		if (cpf == null || cpf.trim().isEmpty()) {
			if (!"S".equals(SimNaoUtils.normalizar(dto.getPacienteSemCpf()))) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
						ErroValidacao.CPF_AUSENTE,
						"CPF do paciente não informado - para paciente sem CPF, marque a coluna"
								+ " \"Paciente sem CPF\" = Sim",
						paciente));
			}
		} else if ("S".equals(SimNaoUtils.normalizar(dto.getPacienteSemCpf()))) {
			// CPF preenchido com "Paciente sem CPF" = Sim: contradição (decisão de 08/10/2026)
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_CONFLITO_SEM_CPF,
					"CPF do paciente preenchido (" + cpf.trim() + ") com \"Paciente sem CPF\" = Sim - se o"
							+ " paciente tem CPF, marque Nao; se nao tem, deixe o CPF vazio",
					paciente));
		} else if (!CpfUtils.isValido(cpf)) {
			String cpfNormalizado = CpfUtils.normalizar(cpf);
			int digitos = cpfNormalizado != null ? cpfNormalizado.length() : 0;
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_INVALIDO,
					"CPF com tamanho inválido (" + digitos + " dígitos, esperado 11): " + cpf.trim()
							+ (digitos > 0 && digitos < 11
									? " - se o CPF começa com zero, formate a coluna como texto e digite os 11 dígitos"
									: ""),
					paciente + " — \"" + cpf.trim() + "\""));
		} else if (CpfUtils.isFalso(cpf)) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_FALSO,
					"CPF do paciente com dígitos repetidos (" + cpf.trim() + ") - para paciente sem CPF,"
							+ " deixe o CPF vazio e marque \"Paciente sem CPF\" = Sim",
					paciente + " — \"" + cpf.trim() + "\""));
		} else if (!CpfUtils.isDvValido(cpf)) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_DV_INVALIDO,
					"CPF do paciente invalido - digitos verificadores nao conferem (" + cpf.trim()
							+ "); confira se algum digito foi digitado errado",
					paciente + " — \"" + cpf.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 3b: CPF do médico — chave do cadastro de médicos. ERRO
		// bloqueante (mesma regra de AtendimentoProcessor.validarCpfMedico).
		// -------------------------------------------------------
		String cpfMedico = dto.getCpfMedico();

		if (cpfMedico == null || cpfMedico.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_MEDICO_AUSENTE,
					"CPF do medico nao informado (medico: " + dto.getMedico() + ")",
					"Médico: " + dto.getMedico()));
		} else if (!CpfUtils.isValido(cpfMedico)) {
			String cpfMedicoNormalizado = CpfUtils.normalizar(cpfMedico);
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_MEDICO_INVALIDO,
					"CPF do medico com tamanho invalido ("
							+ (cpfMedicoNormalizado != null ? cpfMedicoNormalizado.length() : 0)
							+ " digitos, esperado 11): " + cpfMedico.trim()
							+ " (medico: " + dto.getMedico() + ")",
					"Médico: " + dto.getMedico() + " — \"" + cpfMedico.trim() + "\""));
		} else if (CpfUtils.isFalso(cpfMedico) || !CpfUtils.isDvValido(cpfMedico)) {
			// Dígitos repetidos ou verificadores errados (decisão de 08/10/2026)
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_MEDICO_DV_INVALIDO,
					"CPF do medico invalido ("
							+ (CpfUtils.isFalso(cpfMedico) ? "digitos repetidos" : "digitos verificadores nao conferem")
							+ "): " + cpfMedico.trim() + " (medico: " + dto.getMedico() + ")",
					"Médico: " + dto.getMedico() + " — \"" + cpfMedico.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 3c: CBO do médico — vai para o BPA-I (prd-cbo, 6 posições).
		// ERRO bloqueante (decisão de 08/10/2026). Máscara é aceita (2251-25):
		// a importação reduz aos dígitos (CboUtils).
		// -------------------------------------------------------
		String medico = "Médico: " + dto.getMedico();
		String cbo = dto.getCboMedico();

		if (cbo == null || cbo.isBlank()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CBO_AUSENTE, "CBO do medico nao informado (medico: " + dto.getMedico() + ")",
					medico));
		} else if (!CboUtils.isValido(cbo)) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CBO_INVALIDO,
					"CBO do medico \"" + cbo.trim() + "\" invalido - esperado 6 digitos (ex.: 225125)"
							+ " (medico: " + dto.getMedico() + ")",
					medico + " — \"" + cbo.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 3d: Especialidade — organiza árvore, folha e procedimento
		// fixo. ERRO bloqueante (decisão de 08/10/2026): sem ela o
		// atendimento sumia da árvore da tela.
		// -------------------------------------------------------
		String especialidadeLinha = EspecialidadeUtils.normalizar(especialidadeDaLinha(dto));

		if (especialidadeLinha == null || especialidadeLinha.isBlank()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.ESPECIALIDADE_AUSENTE,
					"Especialidade nao informada (medico: " + dto.getMedico() + ")",
					medico));
		}

		// -------------------------------------------------------
		// Regra 4: Raça do paciente — campo obrigatório no layout do BPA-I
		// (seq 21 prd-raca). Ausente ou não reconhecida (grafia incorreta) é
		// ERRO bloqueante.
		// -------------------------------------------------------
		String raca = dto.getRacaPaciente();
		String etnia = dto.getEtniaPaciente();

		if (raca == null || raca.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.RACA_AUSENTE, "Raca do paciente nao informada", paciente));
		} else if (RacaUtils.resolverCodigo(raca) == null) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.RACA_INVALIDA,
					"Raca do paciente \"" + raca.trim() + "\" nao reconhecida - verifique a grafia "
							+ "na planilha (aceito: Branca, Preta, Parda, Amarela, Indigena)",
					"\"" + raca.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 5: Etnia do paciente — só é considerada quando a raça é
		// Indígena (código 5 do BPA-I); para as demais raças o conteúdo da
		// coluna Etnia é ignorado, mesmo que preenchido. AVISO, não bloqueia.
		// -------------------------------------------------------
		if (RacaUtils.isIndigena(raca)) {
			if (etnia == null || etnia.trim().isEmpty()) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.RACA_INDIGENA,
						"Raca informada como Indigena - e necessario preencher a etnia do paciente",
						paciente));
			} else if (EtniaUtils.resolver(etnia) == null) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.ETNIA_NAO_ENCONTRADA,
						"Etnia \"" + etnia.trim() + "\" nao encontrada na tabela oficial - verifique o texto informado",
						"\"" + etnia.trim() + "\""));
			}
		}

		// -------------------------------------------------------
		// Regra 6: Situação de rua — valor presente mas não reconhecido
		// (coluna ausente ou célula em branco não geram aviso aqui; a
		// ausência da própria coluna já é avisada uma única vez em
		// reportarEstrutura()). AVISO, não bloqueia.
		// -------------------------------------------------------
		if (SimNaoUtils.isInvalido(dto.getSituacaoRua())) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.SITUACAO_RUA_INVALIDA,
					"Situacao de rua \"" + dto.getSituacaoRua().trim()
							+ "\" nao reconhecida (use Sim/Nao ou S/N) - sera enviado 'N' na remessa",
					"\"" + dto.getSituacaoRua().trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 7: Paciente sem CPF/Registro Civil — valor presente mas não
		// reconhecido (coluna ausente ou célula em branco não geram aviso
		// aqui — nesse caso o valor é derivado automaticamente a partir do
		// CPF do paciente na geração do BPA-I). AVISO, não bloqueia.
		// -------------------------------------------------------
		if (SimNaoUtils.isInvalido(dto.getPacienteSemCpf())) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.PACIENTE_SEM_CPF_INVALIDO,
					"Paciente sem CPF \"" + dto.getPacienteSemCpf().trim()
							+ "\" nao reconhecido (use Sim/Nao ou S/N) - o valor sera derivado"
							+ " automaticamente a partir do CPF informado",
					"\"" + dto.getPacienteSemCpf().trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 8: Estabelecimento — célula preenchida mas sem separador
		// "código - nome" reconhecido (o código não é identificado). AVISO,
		// não bloqueia — na importação o vínculo é tentado por nome, se já
		// houver um estabelecimento cadastrado com esse nome.
		// -------------------------------------------------------
		String estabelecimentoBruto = dto.getEstabelecimento();
		String[] estabelecimento = StringUtils.separarCodigoENome(estabelecimentoBruto);

		if (StringUtils.isEstabelecimentoNaoInformado(estabelecimentoBruto)) {

			// Vazio, só o separador ou código zero — valor fixo para agrupar
			// todas as linhas num item só na tela/log
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.ESTABELECIMENTO_AUSENTE,
					"Estabelecimento nao informado - o atendimento ficara sem estabelecimento",
					"não informado"));

		} else if (estabelecimento[0] != null && estabelecimento[1] == null) {

			// Só o código ("1234567", "12345 -"): vincula se já cadastrado,
			// sem apagar o nome; senão o atendimento fica sem estabelecimento
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.ESTABELECIMENTO_SEM_NOME,
					"Estabelecimento informado so pelo codigo " + estabelecimento[0]
							+ " - sera vinculado se o codigo ja estiver cadastrado; caso contrario, o"
							+ " atendimento ficara sem estabelecimento",
					"código " + estabelecimento[0]));

		} else if (estabelecimentoBruto != null && !estabelecimentoBruto.isBlank()
				&& estabelecimento[0] == null) {

			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.ESTABELECIMENTO_SEM_CODIGO,
					"Estabelecimento \"" + estabelecimentoBruto.trim() + "\" nao traz codigo reconhecido "
							+ "(formato esperado: \"codigo - nome\") - sera vinculado por nome, se ja "
							+ "houver um estabelecimento cadastrado com esse nome; caso contrario, o "
							+ "atendimento ficara sem estabelecimento",
					"\"" + estabelecimentoBruto.trim() + "\""));
		}

		// -------------------------------------------------------
		// Regra 9: Hora de atendimento — célula preenchida mas não
		// reconhecida como horário (ex.: "-", "--:--", "SEM HORARIO").
		// AVISO, não bloqueia — a hora não vai para o BPA-I, então o
		// atendimento é importado sem hora (AtendimentoProcessor).
		// -------------------------------------------------------
		String hora = dto.getHoraAtendimentoString();

		if (hora != null && !hora.isBlank()) {
			try {
				TimeUtils.parse(hora);
			} catch (IllegalArgumentException e) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.HORA_INVALIDA,
						"Horario de atendimento \"" + hora.trim() + "\" nao reconhecido (use HH:mm, ex.: 08:30)"
								+ " - como a remessa BPA-I nao utiliza o horario, o atendimento podera ser"
								+ " importado com o horario vazio",
						"\"" + hora.trim() + "\""));
			}
		}
	}

	/**
	 * Linhas do mesmo atendimento na planilha ({@link DetectorDuplicidade}),
	 * com a mesma chave da importação: o DTO é lido de novo e passado pelo
	 * {@link AtendimentoProcessor} (CPF, SIGTAP, data e hora normalizados).
	 * Linha que o processador rejeita já tem o erro apontado pelas outras
	 * regras e fica fora da comparação.
	 * <ul>
	 *   <li>Mesmo horário → ERRO {@code LINHA_DUPLICADA} (uma linha seria perdida)</li>
	 *   <li>Horário diferente → AVISO {@code SUSPEITA_DUPLICIDADE} (importadas separadas)</li>
	 * </ul>
	 */
	private void verificarDuplicidade(Row row, Map<String, Integer> colunas, int linha,
			DetectorDuplicidade duplicidade, List<ErroValidacao> erros) {

		LinhaImportacaoDTO dto = excelService.importarLinha(row, colunas);

		try {
			processor.processar(dto);
		} catch (IllegalArgumentException e) {
			return;
		}

		DetectorDuplicidade.Ocorrencia ocorrencia = duplicidade.registrar(linha, dto);

		if (ocorrencia == null) {
			return;
		}

		String valor = "Paciente: " + dto.getPaciente().trim() + " — " + dto.getMedico() + ", "
				+ dto.getDataAgendamento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
				+ " (linhas " + ocorrencia.linhaAnterior() + " e " + linha + ")";

		if (ocorrencia.tipo() == DetectorDuplicidade.Tipo.REPETIDA) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO, ErroValidacao.LINHA_DUPLICADA,
					DetectorDuplicidade.mensagemRepetida(ocorrencia), valor));
		} else {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO, ErroValidacao.SUSPEITA_DUPLICIDADE,
					DetectorDuplicidade.mensagemSuspeita(ocorrencia, dto.getHoraAtendimento()),
					valor + " — " + DetectorDuplicidade.descreverHora(ocorrencia.horaAnterior()) + " × "
							+ DetectorDuplicidade.descreverHora(dto.getHoraAtendimento())));
		}
	}

	/**
	 * Especialidade da linha, já separada do nome do médico na planilha legado
	 * (célula única "ESPECIALIDADE - NOME" mapeada para os dois campos) — mesma
	 * regra de {@code AtendimentoProcessor.separarEspecialidadeEMedico}.
	 */
	private String especialidadeDaLinha(LinhaImportacaoDTO dto) {

		String especialidade = dto.getEspecialidadeMedico();

		if (especialidade != null && especialidade.equals(dto.getMedico())) {
			return StringUtils.separarEspecialidadeEMedico(especialidade)[0];
		}

		return especialidade;
	}

	/**
	 * Registra a grafia do médico da linha para a regra de CNS do profissional
	 * (avaliada no fim, em {@link #reportarCnsProfissional}). O CNS é buscado
	 * por nome no cadastro ({@code medicos_cns.csv}, com apelidos), como na
	 * importação; quando encontrado, fica associado ao CPF da linha para que
	 * outras grafias do mesmo CPF possam herdá-lo.
	 */
	private void registrarMedico(LinhaImportacaoDTO dto, int linha, boolean especialidadeMedicoCombinados,
			Map<String, GrafiaMedico> grafias, Map<String, String> cnsPorCpfNaPlanilha) {

		String nome = dto.getMedico();

		// Planilha legado: a célula traz "ESPECIALIDADE - NOME" — usa só o nome,
		// como AtendimentoProcessor.separarEspecialidadeEMedico faz na importação.
		if (especialidadeMedicoCombinados && nome != null) {
			nome = StringUtils.separarEspecialidadeEMedico(nome)[1];
		}

		if (nome == null || nome.isBlank()) {
			return;
		}

		String nomeFinal = nome.trim();

		GrafiaMedico grafia = grafias.computeIfAbsent(TextoUtils.normalizar(nomeFinal),
				k -> new GrafiaMedico(nomeFinal, CnsProfissionalUtils.buscar(nomeFinal).getCns()));

		grafia.linhas.add(linha);

		String cpf = dto.getCpfMedico();

		if (cpf != null && CpfUtils.isValido(cpf)) {
			String cpfNormalizado = CpfUtils.normalizar(cpf);
			grafia.cpfs.add(cpfNormalizado);

			if (grafia.cnsCadastro != null) {
				cnsPorCpfNaPlanilha.putIfAbsent(cpfNormalizado, grafia.cnsCadastro);
			}
		}
	}

	/**
	 * Regra 10: CNS do profissional — um AVISO por grafia de médico que não está
	 * em Configurações → CNS de Médicos. Não bloqueia (a importação segue e o
	 * CNS pode ser completado depois), mas a geração do BPA-I bloqueia
	 * atendimentos sem CNS. Quando o mesmo CPF tem CNS conhecido (outra grafia
	 * cadastrada nesta planilha, ou {@code Medico.cns} no banco), o aviso diz
	 * que o CNS será herdado e sugere cadastrar a grafia como apelido.
	 */
	private void reportarCnsProfissional(Map<String, GrafiaMedico> grafias,
			Map<String, String> cnsPorCpfNaPlanilha, List<ErroValidacao> erros) {

		for (GrafiaMedico grafia : grafias.values()) {

			if (grafia.cnsCadastro != null) {
				continue;
			}

			String cnsHerdado = null;

			for (String cpf : grafia.cpfs) {
				cnsHerdado = cnsPorCpfNaPlanilha.get(cpf);
				if (cnsHerdado == null) {
					cnsHerdado = buscarCnsNoBanco(cpf);
				}
				if (cnsHerdado != null) {
					break;
				}
			}

			String ocorrencias = AgrupamentoValidacao.descreverLinhas(grafia.linhas);

			String detalhe = cnsHerdado != null
					? "Medico \"" + grafia.nome + "\" nao encontrado em Configuracoes > CNS de Medicos ("
							+ ocorrencias + ") - o CNS sera herdado do mesmo CPF (" + cnsHerdado
							+ "); considere cadastrar esta grafia como apelido"
					: "Medico \"" + grafia.nome + "\" sem CNS cadastrado em Configuracoes > CNS de Medicos ("
							+ ocorrencias + ") - os atendimentos serao importados sem CNS do profissional e a"
							+ " geracao do BPA-I ficara bloqueada ate o CNS ser informado. Cadastre o medico"
							+ " (ou esta grafia como apelido de um medico ja cadastrado) antes de importar";

			String valor = "\"" + grafia.nome + "\""
					+ (cnsHerdado != null ? " — herdará o CNS " + cnsHerdado + " do mesmo CPF" : "");

			// Um aviso por linha (como as demais regras), todos com o mesmo
			// valor: a tela e o log juntam as linhas num item só.
			for (int linha : grafia.linhas) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO, detalhe, valor));
			}
		}
	}

	/** Consulta o CNS já conhecido no banco para o CPF; falha na consulta não interrompe a análise. */
	private String buscarCnsNoBanco(String cpf) {
		try {
			return cnsConhecidoPorCpf.apply(cpf);
		} catch (Exception e) {
			log.warn("Falha ao consultar CNS conhecido para o CPF do medico: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Lê apenas a linha de cabeçalho da planilha e retorna o mapeamento
	 * estrutural completo (campos encontrados, ausentes, duplicados e
	 * colunas do cabeçalho que não bateram com nenhum alias cadastrado).
	 * <p>
	 * Usado para montar um diagnóstico detalhado quando {@link #validar}
	 * já indicou um problema de estrutura — evita carregar essa informação
	 * em todo fluxo de validação, já que só é necessária nesse caso.
	 *
	 * @param caminhoArquivo caminho absoluto para o arquivo {@code .xlsx}
	 */
	public PlanilhaColumnMapper.ResultadoMapeamento obterMapeamentoEstrutura(String caminhoArquivo) {

		try (FileInputStream fis = new FileInputStream(caminhoArquivo);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);
			return columnMapper.mapear(sheet.getRow(0));

		} catch (IOException e) {
			log.error("Falha ao reler cabeçalho da planilha: {}", caminhoArquivo, e);
			return columnMapper.mapear(null);
		}
	}

	/**
	 * Reporta problemas estruturais do cabeçalho a partir do mapeamento por
	 * nome: campos obrigatórios não encontrados em nenhuma coluna, e campos
	 * que casaram com mais de uma coluna (cabeçalho ambíguo). A ordem das
	 * colunas não importa — só a presença e a unicidade de cada campo.
	 *
	 * @return true se a estrutura estiver válida; false se houver qualquer problema
	 */
	private boolean reportarEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, List<ErroValidacao> erros) {

		boolean valida = true;

		for (String campo : mapeamento.camposFaltando()) {
			erros.add(new ErroValidacao(1, ErroValidacao.Severidade.ERRO,
					ErroValidacao.ESTRUTURA_INVALIDA,
					"Coluna obrigatória não encontrada no cabeçalho: " + campo));
			valida = false;
		}

		for (String campo : mapeamento.camposOpcionaisFaltando()) {
			erros.add(new ErroValidacao(1, ErroValidacao.Severidade.AVISO,
					ErroValidacao.COLUNA_OPCIONAL_AUSENTE,
					mensagemColunaOpcionalAusente(campo)));
		}

		for (String campo : mapeamento.camposDuplicados()) {
			erros.add(new ErroValidacao(1, ErroValidacao.Severidade.ERRO,
					ErroValidacao.ESTRUTURA_INVALIDA,
					"Mais de uma coluna do cabeçalho corresponde ao campo: " + campo));
			valida = false;
		}

		return valida;
	}

	/**
	 * Monta a mensagem do aviso de coluna opcional ausente ({@link ErroValidacao#COLUNA_OPCIONAL_AUSENTE}),
	 * específica por campo — cada campo opcional tem um comportamento de
	 * fallback diferente, então o texto genérico não serve para todos.
	 */
	private String mensagemColunaOpcionalAusente(String campo) {

		return switch (campo) {

			case "COD_LOGRADOURO" -> "Coluna \"Cód. Logradouro\" não encontrada no cabeçalho — será considerada "
					+ "vazia. O sistema segue a importação normalmente (o código do logradouro, quando "
					+ "aplicável, ainda pode ser derivado automaticamente a partir do prefixo do endereço: "
					+ "Rua, Avenida, Travessa).";

			case "SITUACAO_RUA" -> "Coluna \"Situação de Rua\" não encontrada no cabeçalho — será considerada "
					+ "vazia. O sistema segue a importação normalmente e a remessa BPA-I usará o valor padrão "
					+ "'N' (não está em situação de rua) para todos os pacientes desta planilha.";

			case "PACIENTE_SEM_CPF" -> "Coluna \"Paciente sem CPF\" não encontrada no cabeçalho — será "
					+ "considerada vazia. O sistema segue a importação normalmente e a remessa BPA-I "
					+ "derivará o valor automaticamente a partir do CPF do paciente: 'N' quando o CPF "
					+ "estiver preenchido, 'S' quando estiver vazio.";

			default -> "Coluna \"" + campo + "\" não encontrada no cabeçalho — será considerada vazia. "
					+ "O sistema segue a importação normalmente.";
		};
	}

	/**
	 * Monta a mensagem do aviso de formato legado (coluna "Especialidade/Médico"
	 * combinada). Quando há um exemplo real da planilha, anexa o valor bruto
	 * encontrado já separado nos dois campos — mesmo texto validado no mockup
	 * de "Analisar Planilha".
	 */
	private String construirDetalheFormatoLegado(String exemploBruto) {

		String mensagem = "A coluna \"Especialidade/Médico\" veio com especialidade e nome "
				+ "do profissional combinados numa única célula — um formato antigo de "
				+ "planilha. O sistema irá separar os dois campos automaticamente ao importar.";

		if (exemploBruto == null) {
			return mensagem;
		}

		String[] partes = StringUtils.separarEspecialidadeEMedico(exemploBruto);

		return mensagem + " Exemplo encontrado: \"" + exemploBruto + "\" → Especialidade: "
				+ partes[0] + " · Médico: " + partes[1];
	}

	/**
	 * Gera um relatório TXT formatado a partir da lista de erros de validação.
	 * <p>
	 * O relatório exibe cabeçalho, contagem total de erros e uma tabela com
	 * linha, tipo de erro e detalhe para cada ocorrência encontrada.
	 * <p>
	 * Se a lista de erros estiver vazia, o relatório indica que a planilha
	 * está válida e apta para importação.
	 *
	 * @param erros lista de erros retornada por {@link #validar(String)}
	 * @return string com o relatório formatado, pronto para exibição ou gravação em arquivo
	 */
	public String gerarLogTxt(List<ErroValidacao> erros, String nomeArquivo) {

		List<ErroValidacao> bloqueantes = erros.stream()
				.filter(ErroValidacao::isBloqueante)
				.collect(java.util.stream.Collectors.toList());

		List<ErroValidacao> avisos = erros.stream()
				.filter(e -> !e.isBloqueante())
				.collect(java.util.stream.Collectors.toList());

		String dataHora = LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

		StringBuilder sb = new StringBuilder();
		sb.append("=== RELATÓRIO DE VALIDAÇÃO DA PLANILHA ===\n");
		sb.append("Arquivo  : ").append(nomeArquivo).append("\n");
		sb.append("Data/Hora: ").append(dataHora).append("\n");
		sb.append("Erros: ").append(bloqueantes.size()).append("\n");
		sb.append("Avisos: ").append(avisos.size()).append("\n");

		if (erros.isEmpty()) {
			sb.append("\nNenhum problema encontrado. A planilha está apta para importação.\n");
			return sb.toString();
		}

		if (!bloqueantes.isEmpty()) {
			sb.append("\n--- ERROS (necessário corrigir na planilha para gerar a remessa BPA-I) ---\n");
			anexarGrupos(sb, bloqueantes);
		}

		if (!avisos.isEmpty()) {
			sb.append("\n--- AVISOS (não impedem a importação) ---\n");
			anexarGrupos(sb, avisos);
		}

		return sb.toString();
	}

	/**
	 * Escreve os grupos de {@link AgrupamentoValidacao} — mesma organização da
	 * tela: por tipo, explicação uma vez, ocorrências iguais juntas.
	 */
	private void anexarGrupos(StringBuilder sb, List<ErroValidacao> erros) {

		for (AgrupamentoValidacao.Grupo grupo : AgrupamentoValidacao.agrupar(erros)) {

			sb.append("\n[").append(grupo.tipo()).append("] ").append(grupo.titulo())
					.append(" (").append(grupo.ocorrencias()).append(")\n");

			if (!grupo.explicacao().isEmpty()) {
				sb.append("  ").append(grupo.explicacao()).append("\n");
			}

			for (AgrupamentoValidacao.Item item : grupo.itens()) {
				sb.append("  - ").append(AgrupamentoValidacao.formatarItem(item)).append("\n");
			}
		}
	}
}
