package br.gov.ses.fillbpai.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro dos erros inesperados do programa (21/10/2026). O programa
 * instalado não tem janela de console: um erro não tratado (ex.: ao abrir uma
 * janela) sumia sem rastro. Agora vai para {@code <logs>/erros_programa.log}
 * — com data/hora, versão do Java e a pilha do erro — e o usuário vê uma
 * mensagem dizendo onde está o arquivo (ver {@code MainApp}).
 * <p>
 * Sem JavaFX — testável.
 */
public final class RegistroErros {

	/** Nome do arquivo, dentro da pasta de logs. */
	public static final String ARQUIVO = "erros_programa.log";

	/** Acima disso, o arquivo é recomeçado (o anterior vira {@code .1}). */
	static final long TAMANHO_MAXIMO = 1024 * 1024;

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	private final Path arquivo;

	public RegistroErros(Path pastaLogs) {
		this.arquivo = pastaLogs.resolve(ARQUIVO);
	}

	public Path arquivo() {
		return arquivo;
	}

	/**
	 * Acrescenta o erro ao arquivo. Nunca lança exceção: falhar ao registrar
	 * não pode derrubar o programa.
	 *
	 * @return {@code true} se gravou
	 */
	public boolean registrar(String onde, Throwable erro) {
		try {
			Files.createDirectories(arquivo.getParent());
			if (Files.exists(arquivo) && Files.size(arquivo) > TAMANHO_MAXIMO) {
				Files.move(arquivo, arquivo.resolveSibling(ARQUIVO + ".1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			Files.writeString(arquivo, texto(onde, erro), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Bloco gravado no arquivo para um erro. */
	static String texto(String onde, Throwable erro) {
		StringWriter pilha = new StringWriter();
		if (erro != null) {
			erro.printStackTrace(new PrintWriter(pilha));
		}
		return "=== " + LocalDateTime.now().format(FORMATO) + " — " + onde
				+ " (Java " + System.getProperty("java.version") + ")" + System.lineSeparator()
				+ pilha + System.lineSeparator();
	}
}
