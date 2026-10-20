package br.gov.ses.fillbpai.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Histórico dos logs de importação (20/10/2026): um arquivo por importação em
 * {@code database/}, com data e hora no nome
 * ({@code log_importacao_<planilha>_<aaaa-MM-dd_HHmm>.txt}) — reimportar a
 * mesma planilha não apaga o log anterior, e o histórico continua disponível
 * depois de reiniciar o programa.
 * <p>
 * Lê a pasta e monta, para cada log, o que a janela "Ver Log Importação"
 * mostra na lista: data/hora, planilha, pasta, linhas e avisos, tirados do
 * cabeçalho do log. Logs antigos, sem cabeçalho (antes de 19/10/2026), usam a
 * data do arquivo e o nome da planilha do nome do arquivo. Sem JavaFX — testável.
 */
public class HistoricoLogsService {

	/** Prefixo dos arquivos de log de importação. */
	public static final String PREFIXO = "log_importacao_";

	private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

	/** Formato da linha "Importada em:" do cabeçalho do log. */
	public static final DateTimeFormatter FORMATO_CABECALHO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

	/** Sufixo de data/hora no nome do arquivo: {@code _2026-10-20_1349} (com {@code _2}, {@code _3}… se repetir). */
	private static final Pattern SUFIXO_DATA = Pattern.compile("_\\d{4}-\\d{2}-\\d{2}_\\d{4}(_\\d+)?$");

	private static final Pattern LINHAS = Pattern.compile("^Linhas da planilha: (\\d+)");
	private static final Pattern AVISOS = Pattern.compile("^Avisos: (\\d+)");

	private final Path pasta;

	public HistoricoLogsService(Path pasta) {
		this.pasta = pasta;
	}

	/**
	 * Uma importação do histórico.
	 *
	 * @param arquivo  arquivo do log
	 * @param planilha nome do arquivo da planilha (do cabeçalho; em log antigo, do nome do arquivo de log)
	 * @param local    pasta da planilha, ou {@code null} em log antigo
	 * @param quando   data/hora da importação (do cabeçalho; em log antigo, da última alteração do arquivo)
	 * @param linhas   linhas importadas, ou {@code null} se não encontradas
	 * @param avisos   número de avisos, ou {@code null} se não encontrado
	 * @param antigo   log sem o cabeçalho de identificação (antes de 19/10/2026)
	 */
	public record EntradaLog(Path arquivo, String planilha, String local, LocalDateTime quando,
			Integer linhas, Integer avisos, boolean antigo) {

		/** Data/hora no formato das mensagens: "20/10/2026 às 13:49". */
		public String quandoFormatado() {
			return quando.format(FORMATO_CABECALHO);
		}
	}

	/** Caminho para gravar o log de uma importação nova — nunca sobrescreve um log existente. */
	public Path novoArquivo(String nomePlanilha, LocalDateTime quando) {

		String base = PREFIXO + nomePlanilha + "_" + quando.format(FORMATO_NOME);
		Path arquivo = pasta.resolve(base + ".txt");

		for (int n = 2; Files.exists(arquivo); n++) {
			arquivo = pasta.resolve(base + "_" + n + ".txt");
		}

		return arquivo;
	}

	/** Todos os logs da pasta, do mais recente para o mais antigo; lista vazia se não houver. */
	public List<EntradaLog> listar() {

		List<EntradaLog> entradas = new ArrayList<>();

		if (!Files.isDirectory(pasta)) {
			return entradas;
		}

		try (Stream<Path> arquivos = Files.list(pasta)) {
			arquivos.filter(p -> {
						String nome = p.getFileName().toString();
						return nome.startsWith(PREFIXO) && nome.endsWith(".txt") && Files.isRegularFile(p);
					})
					.forEach(p -> entradas.add(ler(p)));
		} catch (IOException e) {
			return entradas;
		}

		entradas.sort(Comparator.comparing(EntradaLog::quando).reversed());

		return entradas;
	}

	/** Conteúdo do log; mensagem de erro no lugar se não puder ser lido. */
	public String conteudo(EntradaLog entrada) {
		try {
			return Files.readString(entrada.arquivo(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "Não foi possível ler o log: " + e.getMessage();
		}
	}

	/** Monta a entrada a partir do cabeçalho do log (ou do nome e data do arquivo, em log antigo). */
	EntradaLog ler(Path arquivo) {

		String planilha = null;
		String local = null;
		LocalDateTime quando = null;
		Integer linhas = null;
		Integer avisos = null;

		try (Stream<String> linhasArquivo = Files.lines(arquivo, StandardCharsets.UTF_8)) {
			for (String linha : (Iterable<String>) linhasArquivo.limit(12)::iterator) {
				if (linha.startsWith("Planilha: ")) {
					planilha = linha.substring("Planilha: ".length()).trim();
				} else if (linha.startsWith("Local: ")) {
					local = linha.substring("Local: ".length()).trim();
				} else if (linha.startsWith("Importada em: ")) {
					quando = lerData(linha.substring("Importada em: ".length()).trim());
				} else {
					Matcher ml = LINHAS.matcher(linha);
					if (ml.find()) {
						linhas = Integer.valueOf(ml.group(1));
					}
					Matcher ma = AVISOS.matcher(linha);
					if (ma.find()) {
						avisos = Integer.valueOf(ma.group(1));
					}
				}
			}
		} catch (IOException | java.io.UncheckedIOException e) {
			// arquivo ilegível: fica só com o nome e a data do arquivo
		}

		boolean antigo = planilha == null || quando == null;

		if (planilha == null) {
			planilha = planilhaDoNomeDoArquivo(arquivo);
		}

		if (quando == null) {
			quando = dataDoArquivo(arquivo);
		}

		return new EntradaLog(arquivo, planilha, local, quando, linhas, avisos, antigo);
	}

	private static LocalDateTime lerData(String texto) {
		try {
			return LocalDateTime.parse(texto, FORMATO_CABECALHO);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** "log_importacao_07 - BPAi Julho 2026_2026-09-02_1105.txt" → "07 - BPAi Julho 2026". */
	static String planilhaDoNomeDoArquivo(Path arquivo) {

		String nome = arquivo.getFileName().toString();
		nome = nome.substring(PREFIXO.length(), nome.length() - ".txt".length());

		return SUFIXO_DATA.matcher(nome).replaceFirst("");
	}

	private static LocalDateTime dataDoArquivo(Path arquivo) {
		try {
			return LocalDateTime.ofInstant(Files.getLastModifiedTime(arquivo).toInstant(), ZoneId.systemDefault());
		} catch (IOException e) {
			return LocalDateTime.MIN;
		}
	}
}
