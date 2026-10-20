package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.config.DadosUnidade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;

/**
 * Informações da janela "Sobre" (20/10/2026): versão e data da compilação
 * (gravadas pelo Maven em {@code versao.properties}), dados da unidade e do
 * layout ({@link DadosUnidade} — os mesmos do cabeçalho do BPA-I), ambiente
 * (Java, sistema, banco) e as pastas de dados. Sem JavaFX — testável; a
 * janela fica em {@code ui.SobreDialog}.
 */
public class SobreService {

	/** Nome exibido do programa (o código continua fillbpai). */
	public static final String NOME = "PonteBPA";

	/** Texto usado quando o programa roda sem passar pelo Maven (ex.: direto na IDE). */
	static final String SEM_VERSAO = "desenvolvimento";

	/** Texto para INE vazio em {@link DadosUnidade#INE} — vai em branco no BPA-I (Decisão 30). */
	static final String INE_NAO_INFORMADO = "não informado";

	private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	/** Uma pasta de dados mostrada na janela, com o botão "Abrir pasta". */
	public record PastaDados(String rotulo, Path caminho) {
	}

	/**
	 * Tudo o que a janela mostra.
	 *
	 * @param compilado data/hora local da compilação, ou {@code null} se desconhecida
	 */
	public record InformacoesSobre(String versao, LocalDateTime compilado,
			String cnes, String orgao, String sigla, String cnpj, String ine,
			String layout, String versaoHeader,
			String java, String sistema, String banco,
			List<PastaDados> pastas) {

		/** "compilado em 20/10/2026 15:10", ou vazio se a data não é conhecida. */
		public String compiladoFormatado() {
			return compilado == null ? "" : "compilado em " + compilado.format(FORMATO_DATA);
		}

		/** Texto do botão "Copiar informações", para colar num pedido de ajuda. */
		public String textoParaCopiar() {

			StringBuilder sb = new StringBuilder();

			sb.append(NOME).append(' ').append(versao);
			if (compilado != null) {
				sb.append(" (").append(compiladoFormatado()).append(')');
			}
			sb.append('\n');
			sb.append("Unidade: CNES ").append(cnes).append(" — ").append(orgao)
					.append(" (").append(sigla).append(") · INE ").append(ine).append('\n');
			sb.append("Layout BPA-I: ").append(layout).append(" — versão no header ").append(versaoHeader).append('\n');
			sb.append("Java ").append(java).append(" · ").append(sistema).append(" · ").append(banco);

			for (PastaDados pasta : pastas) {
				sb.append('\n').append(pasta.rotulo()).append(": ").append(pasta.caminho());
			}

			return sb.toString();
		}
	}

	private final Path pastaLogs;
	private final Path pastaCadastros;

	public SobreService(Path pastaLogs, Path pastaCadastros) {
		this.pastaLogs = pastaLogs;
		this.pastaCadastros = pastaCadastros;
	}

	/** Lê as informações na hora em que a janela abre. */
	public InformacoesSobre carregar() {

		Properties versao = lerVersao();

		return new InformacoesSobre(
				valorOu(versao.getProperty("versao"), SEM_VERSAO),
				dataCompilacao(versao.getProperty("compilado")),
				DadosUnidade.CNES,
				DadosUnidade.ORGAO,
				DadosUnidade.SIGLA,
				formatarCnpj(DadosUnidade.CNPJ),
				DadosUnidade.INE.isBlank() ? INE_NAO_INFORMADO : DadosUnidade.INE,
				DadosUnidade.LAYOUT,
				DadosUnidade.VERSAO_HEADER,
				System.getProperty("java.version"),
				System.getProperty("os.name") + " " + System.getProperty("os.version"),
				"H2 " + org.h2.engine.Constants.VERSION,
				List.of(
						new PastaDados("Logs", pastaLogs.toAbsolutePath().normalize()),
						new PastaDados("Cadastros", pastaCadastros.toAbsolutePath().normalize())));
	}

	private static Properties lerVersao() {

		Properties p = new Properties();

		try (InputStream in = SobreService.class.getResourceAsStream("/versao.properties")) {
			if (in != null) {
				p.load(in);
			}
		} catch (IOException e) {
			// sem o arquivo, a janela mostra "desenvolvimento"
		}

		return p;
	}

	/** Valor gravado pelo Maven; sem filtragem fica o próprio {@code ${...}}, tratado como ausente. */
	static String valorOu(String valor, String padrao) {
		return valor == null || valor.isBlank() || valor.contains("${") ? padrao : valor.trim();
	}

	/** Data da compilação em UTC ({@code 2026-10-20T19:10:00Z}) → horário local; {@code null} se ausente ou inválida. */
	static LocalDateTime dataCompilacao(String texto) {

		if (valorOu(texto, null) == null) {
			return null;
		}

		try {
			return LocalDateTime.ofInstant(Instant.parse(texto.trim()), ZoneId.systemDefault());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** "02955271000126" → "02.955.271/0001-26"; outro tamanho fica como veio. */
	static String formatarCnpj(String cnpj) {

		if (cnpj == null || !cnpj.matches("\\d{14}")) {
			return cnpj;
		}

		return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
				+ cnpj.substring(8, 12) + "-" + cnpj.substring(12);
	}
}
