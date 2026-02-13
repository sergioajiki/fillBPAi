package br.gov.ses.fillbpai.service;

import java.util.ArrayList;
import java.util.List;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;

/**
 * Representa o resumo final da importação.
 *
 * Essa classe armazena:
 * - Total de registros processados
 * - Total com sucesso
 * - Total com erro
 * - Lista detalhada de mensagens de erro
 * - Lista de registros válidos importados
 */
public class ImportacaoResultado {

    private int totalProcessados;
    private int totalSucesso;
    private int totalErro;

    // Lista de mensagens detalhadas de erro
    private List<String> erros = new ArrayList<>();

    // Lista de registros válidos importados nesta execução
    private List<AtendimentoBPAi> registrosImportados = new ArrayList<>();

    /**
     * Incrementa contadores de sucesso.
     */
    public void adicionarSucesso() {
        totalProcessados++;
        totalSucesso++;
    }

    /**
     * Incrementa contadores de erro e armazena mensagem detalhada.
     */
    public void adicionarErro(String erro) {
        totalProcessados++;
        totalErro++;
        erros.add(erro);
    }

    public int getTotalProcessados() {
        return totalProcessados;
    }

    public int getTotalSucesso() {
        return totalSucesso;
    }

    public int getTotalErro() {
        return totalErro;
    }

    public List<String> getErros() {
        return erros;
    }

    public List<AtendimentoBPAi> getRegistrosImportados() {
        return registrosImportados;
    }

    public void setRegistrosImportados(List<AtendimentoBPAi> registrosImportados) {
        this.registrosImportados = registrosImportados;
    }
}
