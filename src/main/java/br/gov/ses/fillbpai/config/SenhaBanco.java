package br.gov.ses.fillbpai.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Properties;

/**
 * Senha do banco H2 (21/10/2026). Antes o banco usava o usuário {@code sa}
 * sem senha, fixo no {@code persistence.xml}.
 * <p>
 * A senha é gerada na primeira execução (aleatória, 32 caracteres) e
 * guardada em {@code <config>/banco.properties} — fora do código e do Git
 * (no programa instalado, {@code %LOCALAPPDATA%\PonteBPA\config}, que outros
 * usuários do Windows não leem). Um banco que já existia sem senha (o do
 * desenvolvimento) recebe a senha nova na primeira abertura, sem perder dados.
 * <p>
 * A senha não criptografa o arquivo do banco (decisão I4 de 21/10/2026: só
 * senha). A cópia de segurança do banco precisa do {@code banco.properties}
 * junto — sem ele o banco não abre.
 */
public final class SenhaBanco {

	/** Usuário do banco (administrador do H2). */
	public static final String USUARIO = "sa";

	/** Arquivo da senha, dentro da pasta de configuração. */
	static final String ARQUIVO = "banco.properties";

	/** Nome do banco: {@code fillbpai.mv.db}. */
	static final String NOME_BANCO = "fillbpai";

	/** Código de erro do H2 para usuário ou senha errados. */
	private static final int ERRO_SENHA = 28000;

	private SenhaBanco() {
	}

	/** URL JDBC do banco na pasta informada (sem {@code AUTO_SERVER}: só o PonteBPA abre o banco). */
	public static String url(Path pastaDatabase) {
		return "jdbc:h2:file:" + pastaDatabase.resolve(NOME_BANCO).toAbsolutePath();
	}

	/**
	 * Senha do banco: lida de {@code banco.properties}, ou gerada e gravada na
	 * primeira execução. Se o banco já existe sem senha, aplica a senha nele
	 * — também quando uma troca anterior foi interrompida (arquivo gravado,
	 * banco ainda sem senha).
	 *
	 * @throws IllegalStateException banco com uma senha diferente da guardada
	 *                               (arquivo de senha trocado ou perdido)
	 */
	public static String obter(Path pastaConfig, Path pastaDatabase) throws IOException, SQLException {

		Path arquivo = pastaConfig.resolve(ARQUIVO);
		String url = url(pastaDatabase);
		boolean bancoExiste = Files.exists(pastaDatabase.resolve(NOME_BANCO + ".mv.db"));

		String senha = Files.exists(arquivo) ? ler(arquivo) : null;

		if (senha == null) {
			senha = gerar();
			gravar(arquivo, senha);
		}

		if (bancoExiste && !conecta(url, senha)) {
			if (!conecta(url, "")) {
				throw new IllegalStateException("O banco de dados tem uma senha diferente da guardada em "
						+ arquivo.toAbsolutePath() + ". Restaure o arquivo de senha da cópia de segurança.");
			}
			aplicarSenha(url, senha);
		}

		return senha;
	}

	/** 24 bytes aleatórios em Base64 sem preenchimento: 32 caracteres (letras, dígitos, - e _). */
	static String gerar() {
		byte[] bytes = new byte[24];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String ler(Path arquivo) throws IOException {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
			p.load(r);
		}
		String senha = p.getProperty("senha");
		return senha == null || senha.isBlank() ? null : senha.trim();
	}

	private static void gravar(Path arquivo, String senha) throws IOException {
		Files.createDirectories(arquivo.getParent());
		Properties p = new Properties();
		p.setProperty("senha", senha);
		try (Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
			p.store(w, "Senha do banco do PonteBPA - gerada automaticamente. "
					+ "Guarde junto com a copia de seguranca do banco; sem ela o banco nao abre.");
		}
	}

	/** Abre e fecha uma conexão; {@code false} se a senha estiver errada. Outros erros (banco em uso…) sobem. */
	private static boolean conecta(String url, String senha) throws SQLException {
		try (Connection c = DriverManager.getConnection(url + ";IFEXISTS=TRUE", USUARIO, senha)) {
			return true;
		} catch (SQLException e) {
			if (e.getErrorCode() == ERRO_SENHA) {
				return false;
			}
			throw e;
		}
	}

	/** Troca a senha vazia do {@code sa} pela senha nova. A senha gerada só tem letras, dígitos, - e _. */
	private static void aplicarSenha(String url, String senha) throws SQLException {
		try (Connection c = DriverManager.getConnection(url + ";IFEXISTS=TRUE", USUARIO, "");
				Statement s = c.createStatement()) {
			s.execute("ALTER USER " + USUARIO + " SET PASSWORD '" + senha.replace("'", "''") + "'");
		}
	}
}
