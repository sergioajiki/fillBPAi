package br.gov.ses.fillbpai.service;

import java.util.ArrayList;
import java.util.List;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
/**
 * Representa o resumo final da importação.
 */
public class ImportacaoResultado {

    private int totalProcessados;
    private int totalSucesso;
    private int totalErro;

    private List<String> erros = new ArrayList<>();

    private List<AtendimentoBPAi> registrosImportados;

    public void adicionarSucesso() {
        totalProcessados++;
        totalSucesso++;
    }

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

