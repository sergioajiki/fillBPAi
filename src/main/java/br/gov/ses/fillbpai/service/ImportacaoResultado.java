package br.gov.ses.fillbpai.service;

import java.util.ArrayList;
import java.util.List;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;

/**
 * Representa o resumo final da importação de uma planilha Excel.
 *
 * Armazena:
 * - Contadores: total processados, sucesso, erros, avisos
 * - Lista detalhada de mensagens de erro (linhas que NÃO foram importadas)
 * - Lista detalhada de avisos (linhas importadas com ressalvas)
 * - Lista de registros válidos importados
 *
 * Diferença entre erro e aviso:
 * - ERRO: linha com problema — a importação da planilha inteira é cancelada (nada é gravado;
 *   todas as linhas da planilha devem ser importadas, decisão de 09/10/2026)
 * - AVISO: linha importada com sucesso, mas com dados atípicos (ex: CNS formato legado)
 */
public class ImportacaoResultado {

	private int totalProcessados;
	private int totalSucesso;
	private int totalErro;
	private int totalAvisos;

	/** Mensagens de erro — linhas que NÃO foram importadas. */
	private List<String> erros = new ArrayList<>();

	/** Mensagens de aviso — linhas importadas mas com dados atípicos. */
	private List<String> avisos = new ArrayList<>();

	/** Registros válidos importados nesta execução. */
	private List<AtendimentoBPAi> registrosImportados = new ArrayList<>();

	/**
	 * Incrementa contadores de sucesso.
	 * Chamado quando uma linha é importada sem erros.
	 */
	public void adicionarSucesso() {
		totalProcessados++;
		totalSucesso++;
	}

	/**
	 * Incrementa contadores de erro e armazena mensagem detalhada.
	 * Chamado quando uma linha é rejeitada por erro de validação.
	 *
	 * @param erro mensagem descrevendo o erro e a linha afetada
	 */
	public void adicionarErro(String erro) {
		totalProcessados++;
		totalErro++;
		erros.add(erro);
	}

	/**
	 * Adiciona um aviso à lista.
	 * Avisos são informativos — a linha foi importada com sucesso,
	 * mas contém dados que merecem atenção do usuário.
	 *
	 * @param aviso mensagem descrevendo a situação atípica
	 */
	public void adicionarAviso(String aviso) {
		totalAvisos++;
		avisos.add(aviso);
	}

	/**
	 * Adiciona múltiplos avisos de uma vez.
	 * Útil para coletar avisos do AtendimentoProcessor.
	 *
	 * @param avisos lista de mensagens de aviso
	 */
	public void adicionarAvisos(List<String> avisos) {
		for (String aviso : avisos) {
			adicionarAviso(aviso);
		}
	}

	/** Linhas com dados na planilha (sem cabeçalho e linhas vazias) — conferência da importação. */
	private int linhasPlanilha;

	/** Atendimentos criados nesta importação. */
	private int totalNovos;

	/** Atendimentos que já existiam (reimportação) e foram atualizados. */
	private int totalAtualizados;

	public void contarNovo() {
		totalNovos++;
	}

	public void contarAtualizado() {
		totalAtualizados++;
	}

	public int getTotalNovos() {
		return totalNovos;
	}

	public int getTotalAtualizados() {
		return totalAtualizados;
	}

	public int getLinhasPlanilha() {
		return linhasPlanilha;
	}

	public void setLinhasPlanilha(int linhasPlanilha) {
		this.linhasPlanilha = linhasPlanilha;
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

	public int getTotalAvisos() {
		return totalAvisos;
	}

	public List<String> getErros() {
		return erros;
	}

	public List<String> getAvisos() {
		return avisos;
	}

	public List<AtendimentoBPAi> getRegistrosImportados() {
		return registrosImportados;
	}

	public void setRegistrosImportados(List<AtendimentoBPAi> registrosImportados) {
		this.registrosImportados = registrosImportados;
	}
}
