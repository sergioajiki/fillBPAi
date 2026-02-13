package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por:
 * - Ler arquivo Excel
 * - Converter linhas em entidade
 * - Processar regras de negócio
 * - Persistir no banco
 * - Retornar resumo detalhado da importação
 */
public class AtendimentoImportacaoService {

    private final ExcelImportService excelService = new ExcelImportService();
    private final AtendimentoProcessor processor = new AtendimentoProcessor();
    private final AtendimentoBPAiRepository repository;
    private final EntityManager entityManager;

    public AtendimentoImportacaoService(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.repository = new AtendimentoBPAiRepository(entityManager);
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

            transaction.begin();

            for (Row row : sheet) {

                // Ignora cabeçalho
                if (row.getRowNum() == 0) {
                    continue;
                }

                try {

                    // 1️⃣ Converte linha Excel
                    AtendimentoBPAi atendimento =
                            excelService.importarLinha(row);

                    // 2️⃣ Processa regras de negócio
                    processor.processar(atendimento);

                    // 3️⃣ Persiste no banco
                    repository.salvar(atendimento);

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
}
