package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import org.apache.poi.ss.usermodel.*;

import java.text.DecimalFormat;

/**
 * Serviço responsável por:
 * - Ler uma linha da planilha Excel
 * - Converter todas as células para String
 * - NÃO realizar validações ou conversões definitivas
 *
 * A conversão para LocalDate/LocalTime será feita posteriormente
 * pela classe de processamento.
 */
public class ExcelImportService {

    /**
     * Converte uma linha do Excel em um objeto AtendimentoBPAi
     * Todos os valores são carregados inicialmente como String.
     */
    public AtendimentoBPAi importarLinha(Row row) {

        AtendimentoBPAi atendimento = new AtendimentoBPAi();

        atendimento.setTipoServico(getString(row.getCell(0)));
        atendimento.setDataAgendamentoString(getString(row.getCell(1)));
        atendimento.setHoraAtendimentoString(getString(row.getCell(2)));
        atendimento.setEstabelecimento(getString(row.getCell(3)));
        atendimento.setEspecialidadeMedico(getString(row.getCell(4)));
        atendimento.setCpfMedico(getString(row.getCell(5)));
        atendimento.setCboMedico(getString(row.getCell(6)));
        atendimento.setMunicipio(getString(row.getCell(7)));
        atendimento.setCpfPaciente(getString(row.getCell(8)));
        atendimento.setPaciente(getString(row.getCell(9)));
        atendimento.setCnsPaciente(getString(row.getCell(10)));
        atendimento.setRacaPaciente(getString(row.getCell(11)));

        // Data de nascimento agora usa campo auxiliar String
        atendimento.setDataNascimentoString(getString(row.getCell(12)));

        atendimento.setCidConsulta(getString(row.getCell(13)));
        atendimento.setTelefone(getString(row.getCell(14)));
        atendimento.setTipoZona(getString(row.getCell(15)));
        atendimento.setEnderecoCompleto(getString(row.getCell(16)));

        return atendimento;
    }

    /**
     * Método auxiliar para converter qualquer tipo de célula em String
     * sem perder informação.
     */
    private String getString(Cell cell) {

        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {

            case STRING:
                return cell.getStringCellValue().trim();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Se for data, converte para texto padrão ISO (yyyy-MM-dd)
                    return cell.getLocalDateTimeCellValue()
                            .toLocalDate()
                            .toString();
                } else {
                    // Mantém formato sem notação científica
                    DecimalFormat df = new DecimalFormat("0");
                    df.setMaximumFractionDigits(0);
                    return df.format(cell.getNumericCellValue());
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                return cell.getCellFormula();

            case BLANK:
                return null;

            default:
                return cell.toString().trim();
        }
    }
}
