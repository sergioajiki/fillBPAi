package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import org.apache.poi.ss.usermodel.*;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * Serviço responsável por:
 * - Ler uma linha da planilha Excel
 * - Converter todas as células para String
 * - NÃO realizar validações ou conversões definitivas
 *
 * IMPORTANTE:
 * Esta classe NÃO valida regras de negócio.
 * Apenas extrai dados do Excel para o DTO de importação.
 *
 * A conversão para LocalDate/LocalTime será feita posteriormente
 * pela classe AtendimentoProcessor.
 */
public class ExcelImportService {

	/**
	 * Linha sem nenhum valor (todas as células vazias, só espaços ou fórmula
	 * com resultado vazio) — ex.: linha formatada ou com conteúdo apagado,
	 * que o Excel ainda grava no arquivo. Ignorada do mesmo jeito na análise,
	 * na importação e na contagem de linhas da planilha.
	 */
	public boolean isLinhaVazia(Row row) {

		if (row == null) {
			return true;
		}

		for (org.apache.poi.ss.usermodel.Cell cell : row) {
			String valor = getString(cell);
			if (valor != null && !valor.isBlank()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Converte uma linha do Excel em um LinhaImportacaoDTO.
	 * <p>
	 * Cada célula é buscada pelo campo canônico, não pelo índice fixo — o
	 * mapa vem de {@link PlanilhaColumnMapper#mapear}, montado uma vez por
	 * planilha a partir do cabeçalho. Isso torna a leitura indiferente à
	 * ordem das colunas e a colunas extras não mapeadas.
	 * <p>
	 * Todos os valores são carregados inicialmente como String.
	 * Isso evita perda de informação e delega a validação
	 * para a camada de processamento.
	 */
	public LinhaImportacaoDTO importarLinha(Row row, Map<String, Integer> colunas) {

		LinhaImportacaoDTO dto = new LinhaImportacaoDTO();

		dto.setTipoServico(getString(row, colunas, "TIPO_SERVICO"));
		dto.setDataAgendamentoString(getString(row, colunas, "DATA_AGENDAMENTO"));
		dto.setHoraAtendimentoString(getHora(row, colunas, "HORA_ATENDIMENTO"));
		dto.setEstabelecimento(getString(row, colunas, "ESTABELECIMENTO"));

		// Campos ESPECIALIDADE_MEDICO e MEDICO: especialidade e nome do médico
		// já vêm separados (antes vinham combinados em uma única coluna
		// "ESPECIALIDADE - NOME")
		dto.setEspecialidadeMedico(getString(row, colunas, "ESPECIALIDADE_MEDICO"));
		dto.setMedico(getString(row, colunas, "MEDICO"));

		dto.setCpfMedico(getString(row, colunas, "CPF_MEDICO"));
		dto.setCboMedico(getString(row, colunas, "CBO_MEDICO"));
		dto.setMunicipio(getString(row, colunas, "MUNICIPIO"));
		dto.setCpfPaciente(getString(row, colunas, "CPF_PACIENTE"));
		dto.setPaciente(getString(row, colunas, "PACIENTE"));
		dto.setCnsPaciente(getString(row, colunas, "CNS_PACIENTE"));
		dto.setRacaPaciente(getString(row, colunas, "RACA_PACIENTE"));
		dto.setEtniaPaciente(getString(row, colunas, "ETNIA_PACIENTE"));

		// Data de nascimento armazenada como String (conversão posterior)
		dto.setDataNascimentoString(getString(row, colunas, "DATA_NASCIMENTO"));

		dto.setCidConsulta(getString(row, colunas, "CID_CONSULTA"));
		dto.setTelefone(getString(row, colunas, "TELEFONE"));

		dto.setTipoZona(getString(row, colunas, "TIPO_ZONA"));
		dto.setCodLogradouro(getString(row, colunas, "COD_LOGRADOURO"));
		dto.setEndereco(getString(row, colunas, "ENDERECO"));
		dto.setCep(getString(row, colunas, "CEP"));
		dto.setNumero(getString(row, colunas, "NUMERO"));
		dto.setBairro(getString(row, colunas, "BAIRRO"));
		dto.setComplemento(getString(row, colunas, "COMPLEMENTO"));
		dto.setSexoPaciente(getString(row, colunas, "SEXO_PACIENTE"));
		dto.setSituacaoRua(getString(row, colunas, "SITUACAO_RUA"));
		dto.setPacienteSemCpf(getString(row, colunas, "PACIENTE_SEM_CPF"));

		return dto;
	}

	/**
	 * Busca o valor de um campo canônico na linha, usando o índice de coluna
	 * resolvido para esse campo. Campo ausente do mapa (não encontrado no
	 * cabeçalho) resulta em {@code null} em vez de exceção — a ausência de
	 * campo obrigatório já é reportada por {@link PlanilhaColumnMapper}
	 * antes da leitura das linhas de dados.
	 */
	private String getString(Row row, Map<String, Integer> colunas, String campo) {

		Integer indice = colunas.get(campo);

		if (indice == null) {
			return null;
		}

		return getString(row.getCell(indice));
	}

	/**
	 * Leitura própria da coluna de horário. Célula de data+hora (comum em
	 * planilhas exportadas de sistemas, ex.: {@code 25/12/2024 08:30}) devolve
	 * a <b>hora</b> — a leitura genérica ({@link #getString(Cell)}) devolve só
	 * a data, o que é certo para as colunas de data, mas aqui perdia o
	 * horário. Se a célula tiver só a data (hora 00:00), segue a leitura
	 * genérica (devolve a data, que vira aviso HORA_INVALIDA) para não gravar
	 * um horário 00:00 que não existe.
	 */
	private String getHora(Row row, Map<String, Integer> colunas, String campo) {

		Integer indice = colunas.get(campo);

		if (indice == null) {
			return null;
		}

		Cell cell = row.getCell(indice);

		if (cell != null) {

			CellType tipo = cell.getCellType() == CellType.FORMULA
					? cell.getCachedFormulaResultType() : cell.getCellType();

			if (tipo == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {

				var dateTime = cell.getLocalDateTimeCellValue();

				if (dateTime.getYear() != 1899 && !dateTime.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
					return dateTime.toLocalTime().toString();
				}
			}
		}

		return getString(cell);
	}

	/**
	 * Método auxiliar responsável por converter qualquer tipo de célula
	 * do Excel em String, preservando o valor original.
	 *
	 * Este método trata corretamente:
	 * - Strings
	 * - Números
	 * - Datas
	 * - Horas (incluindo padrão 1899 do Excel)
	 * - Booleanos
	 * - Fórmulas (pelo resultado calculado em cache)
	 * - Espaço não separável (U+00A0) tratado como espaço comum
	 */
	private String getString(Cell cell) {

		if (cell == null) {
			return null;
		}

		// ===============================
		// FÓRMULA
		// ===============================
		/*
		 * Usa o resultado calculado que o Excel salva junto com a fórmula
		 * (ex.: =SE(A2="";"";A2) resultando em vazio), e não o texto da
		 * fórmula — antes o texto "IF(...)" era lido como valor da célula.
		 * Fórmula com erro (#N/D, #REF!...) é tratada como célula vazia.
		 */
		if (cell.getCellType() == CellType.FORMULA) {

			CellType resultado = cell.getCachedFormulaResultType();

			if (resultado == CellType.ERROR) {
				return null;
			}

			return getString(cell, resultado);
		}

		return getString(cell, cell.getCellType());
	}

	/**
	 * Converte o valor da célula em String de acordo com o tipo informado —
	 * o tipo da própria célula, ou o tipo do resultado em cache quando a
	 * célula é uma fórmula.
	 */
	private String getString(Cell cell, CellType tipo) {

		switch (tipo) {

			// ===============================
			// TEXTO NORMAL
			// ===============================
			case STRING:
				return limparTexto(cell.getStringCellValue());

			// ===============================
			// NÚMEROS (incluindo datas/horas)
			// ===============================
			case NUMERIC:

				// Verifica se a célula é formatada como Data/Hora
				if (DateUtil.isCellDateFormatted(cell)) {

					var dateTime = cell.getLocalDateTimeCellValue();

					/*
					 * O Excel armazena horas como fração de dia.
					 * Quando lido como data, aparece como:
					 * 1899-12-31T08:30
					 *
					 * Se o ano for 1899, significa que é apenas hora.
					 */
					if (dateTime.getYear() == 1899) {
						return dateTime.toLocalTime().toString();
					}

					// Caso seja uma data válida
					return dateTime.toLocalDate().toString();
				}

				/*
				 * Caso seja número comum (CPF, CNS etc.)
				 * Utilizamos DecimalFormat para evitar notação científica
				 * e remover casas decimais indesejadas.
				 */
				DecimalFormat df = new DecimalFormat("0");
				df.setMaximumFractionDigits(0);
				return df.format(cell.getNumericCellValue());

			// ===============================
			// BOOLEANO
			// ===============================
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());

			// ===============================
			// CÉLULA EM BRANCO
			// ===============================
			case BLANK:
				return null;

			// ===============================
			// OUTROS TIPOS
			// ===============================
			default:
				return limparTexto(cell.toString());
		}
	}

	/**
	 * Troca o espaço não separável (U+00A0, comum em dados copiados de
	 * sistemas web) por espaço comum e remove espaços das pontas — o
	 * {@code trim()} do Java não remove o U+00A0, e uma célula só com ele
	 * parecia vazia na tela mas chegava preenchida ao processamento.
	 */
	private String limparTexto(String texto) {
		return texto.replace(' ', ' ').strip();
	}
}
