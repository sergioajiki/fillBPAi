package br.gov.ses.fillbpai.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanilhaColumnMapperTest {

	/**
	 * Cabeçalho com os 27 campos canônicos (24 obrigatórios + COD_LOGRADOURO,
	 * SITUACAO_RUA e PACIENTE_SEM_CPF, todos opcionais), usando aliases reais
	 * de {@code dados/colunas_aliases.csv}.
	 */
	private static final String[] CABECALHO_COMPLETO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"Especialidade", "ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO",
			"END. COMPLEMENTOS", "SEXO", "Situação de Rua", "Paciente sem CPF"
	};

	private final PlanilhaColumnMapper mapper = new PlanilhaColumnMapper();

	private Row criarLinhaCabecalho(String... textos) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet();
		Row row = sheet.createRow(0);
		for (int i = 0; i < textos.length; i++) {
			row.createCell(i).setCellValue(textos[i]);
		}
		return row;
	}

	@Test
	void mapearComCabecalhoCompletoEEmOrdemMapeiaTodosOsCampos() {
		Row cabecalho = criarLinhaCabecalho(CABECALHO_COMPLETO);

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.camposFaltando()).isEmpty();
		assertThat(resultado.camposOpcionaisFaltando()).isEmpty();
		assertThat(resultado.camposDuplicados()).isEmpty();
		assertThat(resultado.indices()).hasSize(27);
		assertThat(resultado.indices()).containsEntry("CEP", 20);
		assertThat(resultado.especialidadeMedicoCombinados()).isFalse();
	}

	@Test
	void mapearSemColunaCodLogradouroNaoBloqueiaEstruturaMasReportaOpcionalFaltando() {
		List<String> semLogradouro = new ArrayList<>(Arrays.asList(CABECALHO_COMPLETO));
		semLogradouro.remove("Log");
		Row cabecalho = criarLinhaCabecalho(semLogradouro.toArray(new String[0]));

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.camposFaltando()).isEmpty();
		assertThat(resultado.camposOpcionaisFaltando()).containsExactly("COD_LOGRADOURO");
		assertThat(resultado.indices()).doesNotContainKey("COD_LOGRADOURO");
	}

	@Test
	void mapearSemColunaPacienteSemCpfNaoBloqueiaEstruturaMasReportaOpcionalFaltando() {
		List<String> semPacienteSemCpf = new ArrayList<>(Arrays.asList(CABECALHO_COMPLETO));
		semPacienteSemCpf.remove("Paciente sem CPF");
		Row cabecalho = criarLinhaCabecalho(semPacienteSemCpf.toArray(new String[0]));

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.camposFaltando()).isEmpty();
		assertThat(resultado.camposOpcionaisFaltando()).containsExactly("PACIENTE_SEM_CPF");
		assertThat(resultado.indices()).doesNotContainKey("PACIENTE_SEM_CPF");
	}

	@Test
	void mapearComApenasColunaCombinadaLegadaBackfillaEspecialidadeMedico() {
		List<String> semEspecialidade = new ArrayList<>(Arrays.asList(CABECALHO_COMPLETO));
		semEspecialidade.remove("Especialidade");
		Row cabecalho = criarLinhaCabecalho(semEspecialidade.toArray(new String[0]));

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.camposFaltando()).isEmpty();
		assertThat(resultado.especialidadeMedicoCombinados()).isTrue();
		assertThat(resultado.indices().get("ESPECIALIDADE_MEDICO"))
				.isEqualTo(resultado.indices().get("MEDICO"));
	}

	@Test
	void mapearComColunasForaDeOrdemMapeiaPorNomeNaoPorPosicao() {
		String[] invertido = new String[CABECALHO_COMPLETO.length];
		for (int i = 0; i < CABECALHO_COMPLETO.length; i++) {
			invertido[i] = CABECALHO_COMPLETO[CABECALHO_COMPLETO.length - 1 - i];
		}
		Row cabecalho = criarLinhaCabecalho(invertido);

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.indices()).containsEntry("CEP", 6);
		assertThat(resultado.indices()).containsEntry("TIPO_SERVICO", 26);
	}

	@Test
	void mapearComColunaExtraNaoReconhecidaIgnoraSemQuebrarOResto() {
		String[] comExtra = new String[CABECALHO_COMPLETO.length + 1];
		System.arraycopy(CABECALHO_COMPLETO, 0, comExtra, 0, CABECALHO_COMPLETO.length);
		comExtra[CABECALHO_COMPLETO.length] = "Observações";
		Row cabecalho = criarLinhaCabecalho(comExtra);

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isTrue();
		assertThat(resultado.indices()).hasSize(27);
		assertThat(resultado.colunasNaoReconhecidas()).containsExactly("Observações");
	}

	@Test
	void mapearComColunaObrigatoriaAusenteReportaCampoFaltando() {
		List<String> semSexo = new ArrayList<>(Arrays.asList(CABECALHO_COMPLETO));
		semSexo.remove("SEXO");
		Row cabecalho = criarLinhaCabecalho(semSexo.toArray(new String[0]));

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isFalse();
		assertThat(resultado.camposFaltando()).containsExactly("SEXO_PACIENTE");
	}

	@Test
	void mapearComColunaDuplicadaReportaCampoDuplicadoComAsColunasConflitantes() {
		String[] comDuplicata = Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		comDuplicata[CABECALHO_COMPLETO.length] = "CEP";
		Row cabecalho = criarLinhaCabecalho(comDuplicata);

		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(cabecalho);

		assertThat(resultado.estruturaValida()).isFalse();
		assertThat(resultado.camposDuplicados()).containsExactly("CEP");
		assertThat(resultado.colunasPorCampoDuplicado().get("CEP")).containsExactly("CEP", "CEP");
	}

	@Test
	void mapearComCabecalhoNuloReportaTodosOsCamposComoFaltando() {
		PlanilhaColumnMapper.ResultadoMapeamento resultado = mapper.mapear(null);

		assertThat(resultado.estruturaValida()).isFalse();
		assertThat(resultado.indices()).isEmpty();
		assertThat(resultado.camposFaltando()).hasSize(24);
		assertThat(resultado.camposOpcionaisFaltando())
				.containsExactly("COD_LOGRADOURO", "SITUACAO_RUA", "PACIENTE_SEM_CPF");
	}
}
