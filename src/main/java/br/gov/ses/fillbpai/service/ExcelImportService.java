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
		dto.setHoraAtendimentoString(getString(row, colunas, "HORA_ATENDIMENTO"));
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
	 * Método auxiliar responsável por converter qualquer tipo de célula
	 * do Excel em String, preservando o valor original.
	 *
	 * Este método trata corretamente:
	 * - Strings
	 * - Números
	 * - Datas
	 * - Horas (incluindo padrão 1899 do Excel)
	 * - Booleanos
	 * - Fórmulas
	 */
	private String getString(Cell cell) {

		if (cell == null) {
			return null;
		}

		switch (cell.getCellType()) {

			// ===============================
			// TEXTO NORMAL
			// ===============================
			case STRING:
				return cell.getStringCellValue().trim();

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
			// FÓRMULA
			// ===============================
			case FORMULA:
				/*
				 * Mantemos a fórmula como texto.
				 * Se quiser futuramente avaliar o resultado da fórmula,
				 * podemos usar FormulaEvaluator.
				 */
				return cell.getCellFormula();

			// ===============================
			// CÉLULA EM BRANCO
			// ===============================
			case BLANK:
				return null;

			// ===============================
			// OUTROS TIPOS
			// ===============================
			default:
				return cell.toString().trim();
		}
	}
}
