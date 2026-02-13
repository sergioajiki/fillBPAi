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
 * Serviço principal responsável por:
 * - Ler arquivo Excel
 * - Converter linhas em entidade
 * - Processar regras de negócio
 * - Persistir em lote
 * - Retornar resultado da importação
 */
public class AtendimentoImportacaoService {

    private final ExcelImportService excelService = new ExcelImportService();
    private final AtendimentoProcessor processor = new AtendimentoProcessor();
    private final AtendimentoBPAiRepository repository;
    private final EntityManager entityManager;

    /**
     * Recebe EntityManager da camada superior
     */
    public AtendimentoImportacaoService(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.repository = new AtendimentoBPAiRepository(entityManager);
    }

    /**
     * Executa a importação completa do arquivo Excel.
     *
     * @param caminhoArquivo caminho absoluto do arquivo
     * @return resultado detalhado da importação
     */
    public ImportacaoResultado importar(String caminhoArquivo) {

        ImportacaoResultado resultado = new ImportacaoResultado();

        // Lista para armazenar apenas registros válidos desta sessão
        List<AtendimentoBPAi> importados = new ArrayList<>();

        EntityTransaction transaction = entityManager.getTransaction();

        try (FileInputStream fis = new FileInputStream(caminhoArquivo);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            transaction.begin(); // 🔹 inicia transação

            for (Row row : sheet) {

                // 🔹 Ignora primeira linha (cabeçalho)
                if (row.getRowNum() == 0) {
                    continue;
                }

                try {

                    /*
                     * 1️⃣ Converte linha Excel em entidade
                     */
                    AtendimentoBPAi atendimento =
                            excelService.importarLinha(row);

                    /*
                     * 2️⃣ Processa regras adicionais (validações, ajustes)
                     */
                    processor.processar(atendimento);

                    /*
                     * 3️⃣ Persiste no banco
                     */
                    repository.salvar(atendimento);

                    /*
                     * 4️⃣ Guarda na lista da sessão atual
                     */
                    importados.add(atendimento);

                    resultado.adicionarSucesso();

                } catch (Exception e) {

                    resultado.adicionarErro(
                            "Linha " + (row.getRowNum() + 1)
                                    + ": " + e.getMessage()
                    );
                }
            }

            transaction.commit(); // 🔹 confirma transação

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

        /*
         * Define lista importada no resultado
         * (somente registros válidos desta execução)
         */
        resultado.setRegistrosImportados(importados);

        return resultado;
    }
}
