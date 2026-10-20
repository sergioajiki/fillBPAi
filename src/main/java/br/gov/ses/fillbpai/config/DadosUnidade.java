package br.gov.ses.fillbpai.config;

/**
 * Dados da unidade que envia o BPA-I — fonte única para o cabeçalho do
 * arquivo ({@code GeradorBPAiService.montarHeader}), o CNES gravado nos
 * atendimentos e a janela "Sobre" (20/10/2026). Antes os valores estavam
 * repetidos dentro do gerador.
 * <p>
 * Ainda fixos no código: para instalar em outra unidade, estes valores
 * passarão para Configurações → Unidade (a implementar).
 */
public final class DadosUnidade {

	/** CNES da unidade (BPA-I seq 2 do registro, {@code prd-cnes}). */
	public static final String CNES = "6970451";

	/** Nome do órgão de origem (header seq 7, 30 posições, sem acento). */
	public static final String ORGAO = "NUCLEO DE TELESSAUDE DE MS";

	/** Sigla do órgão de origem (header seq 8, 6 posições). */
	public static final String SIGLA = "NTMS";

	/** CNPJ do órgão de origem, só dígitos (header seq 9, 14 posições). */
	public static final String CNPJ = "02955271000126";

	/**
	 * INE padrão da unidade (registro seq 37, {@code prd-ine}, 10 dígitos),
	 * enviado em todos os atendimentos. Vazio = 10 brancos na remessa — o caso
	 * do núcleo, que não tem INE (Decisão 30, 20/10/2026). Para unidade com uma
	 * única equipe; com várias equipes o INE varia por atendimento (coluna na
	 * planilha ou cadastro por profissional — a avaliar).
	 */
	public static final String INE = "";

	/** Órgão de destino (header seq 10, 40 posições). */
	public static final String DESTINO = "SECRETARIA ESTADUAL DE SAUDE";

	/** Indicador do órgão de destino: E = estadual (header seq 11). */
	public static final String INDICADOR_DESTINO = "E";

	/** Versão do sistema informada no header (seq 12, 10 posições). */
	public static final String VERSAO_HEADER = "ED04.10";

	/** Nome do layout seguido pelo gerador. */
	public static final String LAYOUT = "Exportação BPA 2026";

	private DadosUnidade() {
	}
}
