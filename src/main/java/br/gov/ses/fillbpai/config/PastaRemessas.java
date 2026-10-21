package br.gov.ses.fillbpai.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Pasta onde a janela "Salvar" do BPA-I abre (21/10/2026). Antes não havia
 * pasta inicial: a janela abria na pasta padrão do Windows e o usuário não
 * sabia onde a remessa tinha ido parar.
 * <ul>
 *   <li>Padrão: {@code Documentos\PonteBPA\Remessas}, criada quando falta;</li>
 *   <li>depois da primeira geração, a última pasta escolhida (guardada em
 *       {@code <config>/preferencias.properties}); se ela não existir mais,
 *       volta ao padrão.</li>
 * </ul>
 */
public final class PastaRemessas {

	/** Arquivo de preferências, na pasta de configuração ({@link PastasApp#config()}). */
	static final String ARQUIVO = "preferencias.properties";

	static final String CHAVE = "pasta.remessas";

	private final Path padrao;
	private final Path preferencias;

	PastaRemessas(Path padrao, Path preferencias) {
		this.padrao = padrao;
		this.preferencias = preferencias;
	}

	/** Pasta padrão em Documentos do usuário e preferências na pasta de configuração do programa. */
	public static PastaRemessas atual() {
		return new PastaRemessas(documentos().resolve("PonteBPA").resolve("Remessas"),
				PastasApp.atual().config().resolve(ARQUIVO));
	}

	/** Pasta "Documentos" do Windows (inclusive quando redirecionada, ex.: OneDrive). */
	static Path documentos() {
		try {
			java.io.File pasta = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
			if (pasta != null && pasta.isDirectory()) {
				return pasta.toPath();
			}
		} catch (RuntimeException e) {
			// sem acesso à pasta do sistema: usa o caminho comum abaixo
		}
		return Path.of(System.getProperty("user.home"), "Documents");
	}

	/** Pasta padrão das remessas ({@code Documentos\PonteBPA\Remessas}). */
	public Path padrao() {
		return padrao;
	}

	/**
	 * Pasta em que a janela "Salvar" deve abrir: a última usada, se ainda
	 * existir; senão a padrão (criada aqui). {@code null} se nem a padrão
	 * puder ser criada — a janela abre na pasta do Windows, como antes.
	 */
	public Path inicial() {

		Path ultima = ultimaUsada();
		if (ultima != null && Files.isDirectory(ultima)) {
			return ultima;
		}

		try {
			Files.createDirectories(padrao);
			return padrao;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Guarda a pasta do arquivo gerado, para a próxima geração abrir nela. Falha ao gravar não atrapalha a geração. */
	public void lembrar(Path arquivoGerado) {

		Path pasta = arquivoGerado == null ? null : arquivoGerado.toAbsolutePath().getParent();
		if (pasta == null) {
			return;
		}

		try {
			Properties p = lerPreferencias();
			p.setProperty(CHAVE, pasta.toString());
			Files.createDirectories(preferencias.getParent());
			try (Writer w = Files.newBufferedWriter(preferencias, StandardCharsets.UTF_8)) {
				p.store(w, "Preferencias do PonteBPA");
			}
		} catch (IOException | RuntimeException e) {
			// sem preferências gravadas, a próxima geração abre na pasta padrão
		}
	}

	private Path ultimaUsada() {
		String valor = lerPreferencias().getProperty(CHAVE);
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return Path.of(valor.trim());
		} catch (InvalidPathException e) {
			return null;
		}
	}

	private Properties lerPreferencias() {
		Properties p = new Properties();
		if (Files.exists(preferencias)) {
			try (Reader r = Files.newBufferedReader(preferencias, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException | RuntimeException e) {
				// arquivo ilegível: como se não houvesse preferência
			}
		}
		return p;
	}
}
