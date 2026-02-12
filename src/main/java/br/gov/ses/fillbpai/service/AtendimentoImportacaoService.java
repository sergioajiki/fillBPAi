package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Serviço principal de importação em lote.
 */
public class AtendimentoImportacaoService {

    private final ExcelImportService excelService = new ExcelImportService();
    private final AtendimentoProcessor processor = new AtendimentoProcessor();
    private final AtendimentoBPAiRepository repository;

    public AtendimentoImportacaoService(EntityManager entityManager) {
        this.repository = new AtendimentoBPAiRepository(entityManager);
    }

    public ImportacaoResultado importar(String caminhoArquivo) {

        ImportacaoResultado resultado = new ImportacaoResultado();

        try (FileInputStream fis = new FileInputStream(caminhoArquivo);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {

                if (row.getRowNum() == 0) {
                    continue; // pula cabeçalho
                }

                try {

                    AtendimentoBPAi atendimento =
                            excelService.importarLinha(row);

                    processor.processar(atendimento);

                    repository.salvar(atendimento);

                    resultado.adicionarSucesso();

                } catch (Exception e) {

                    resultado.adicionarErro(
                            "Linha " + (row.getRowNum() + 1) +
                                    ": " + e.getMessage()
                    );
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo: " + e.getMessage());
        }

        return resultado;
    }
}

