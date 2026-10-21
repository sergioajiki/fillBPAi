package br.gov.ses.fillbpai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Senha do banco H2 (21/10/2026) — com bancos H2 em arquivo, em pasta temporária. */
class SenhaBancoTest {

	@TempDir
	Path temp;

	private Path config() {
		return temp.resolve("config");
	}

	private Path database() {
		return temp.resolve("database");
	}

	/** Cria um banco "antigo", sem senha, com uma linha de dados. */
	private void criarBancoSemSenha() throws SQLException {
		try (Connection c = DriverManager.getConnection(SenhaBanco.url(database()), "sa", "");
				Statement s = c.createStatement()) {
			s.execute("CREATE TABLE paciente (nome VARCHAR(50))");
			s.execute("INSERT INTO paciente VALUES ('MARIA')");
		}
	}

	private boolean conecta(String senha) {
		try (Connection c = DriverManager.getConnection(SenhaBanco.url(database()) + ";IFEXISTS=TRUE", "sa", senha)) {
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	@Test
	void primeiraExecucaoSemBancoGeraEGuardaASenha() throws Exception {
		String senha = SenhaBanco.obter(config(), database());

		assertThat(senha).hasSize(32).matches("[A-Za-z0-9_-]+");
		assertThat(Files.readString(config().resolve("banco.properties"), StandardCharsets.UTF_8))
				.contains("senha=" + senha);
	}

	@Test
	void execucoesSeguintesDevolvemAMesmaSenha() throws Exception {
		String primeira = SenhaBanco.obter(config(), database());

		assertThat(SenhaBanco.obter(config(), database())).isEqualTo(primeira);
	}

	@Test
	void bancoExistenteSemSenhaRecebeASenhaSemPerderDados() throws Exception {
		criarBancoSemSenha();

		String senha = SenhaBanco.obter(config(), database());

		assertThat(conecta("")).isFalse();
		assertThat(conecta(senha)).isTrue();
		try (Connection c = DriverManager.getConnection(SenhaBanco.url(database()), "sa", senha);
				ResultSet rs = c.createStatement().executeQuery("SELECT nome FROM paciente")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("MARIA");
		}
	}

	@Test
	void trocaInterrompidaEhRetomadaNaProximaAbertura() throws Exception {
		// arquivo de senha gravado, mas o banco continuou sem senha
		criarBancoSemSenha();
		Files.createDirectories(config());
		Files.writeString(config().resolve("banco.properties"), "senha=SenhaQueNaoChegouAoBanco123\n", StandardCharsets.UTF_8);

		String senha = SenhaBanco.obter(config(), database());

		assertThat(senha).isEqualTo("SenhaQueNaoChegouAoBanco123");
		assertThat(conecta(senha)).isTrue();
		assertThat(conecta("")).isFalse();
	}

	@Test
	void arquivoDeSenhaPerdidoComBancoProtegidoDaMensagemClara() throws Exception {
		criarBancoSemSenha();
		SenhaBanco.obter(config(), database());
		Files.delete(config().resolve("banco.properties"));

		assertThatThrownBy(() -> SenhaBanco.obter(config(), database()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("senha diferente")
				.hasMessageContaining("cópia de segurança");
	}

	@Test
	void urlNaoUsaAutoServer() {
		assertThat(SenhaBanco.url(Path.of("database"))).startsWith("jdbc:h2:file:").doesNotContain("AUTO_SERVER");
	}

	@Test
	void senhasGeradasSaoDiferentes() throws IOException {
		assertThat(SenhaBanco.gerar()).isNotEqualTo(SenhaBanco.gerar());
	}
}
