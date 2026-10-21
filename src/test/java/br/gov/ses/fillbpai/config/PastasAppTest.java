package br.gov.ses.fillbpai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Pastas de dados do programa (21/10/2026). */
class PastasAppTest {

	@TempDir
	Path temp;

	@Test
	void desenvolvimentoUsaAsPastasDoProjeto() {
		PastasApp p = PastasApp.de(false, "C:\\Users\\x\\AppData\\Local", "C:\\Users\\x");

		assertThat(p.instalado()).isFalse();
		assertThat(p.database()).isEqualTo(Path.of("database"));
		assertThat(p.logs()).isEqualTo(Path.of("logs"));
		assertThat(p.dados()).isEqualTo(Path.of("src", "main", "resources", "dados"));
		assertThat(p.config()).isEqualTo(Path.of("config"));
	}

	@Test
	void programaInstaladoUsaAPastaDoUsuario() {
		PastasApp p = PastasApp.de(true, temp.toString(), "ignorado");

		Path base = temp.resolve("PonteBPA");
		assertThat(p.instalado()).isTrue();
		assertThat(p.database()).isEqualTo(base.resolve("database"));
		assertThat(p.logs()).isEqualTo(base.resolve("logs"));
		assertThat(p.dados()).isEqualTo(base.resolve("dados"));
		assertThat(p.config()).isEqualTo(base.resolve("config"));
	}

	@Test
	void semLocalAppDataUsaAppDataLocalDoUsuario() {
		PastasApp p = PastasApp.de(true, null, temp.toString());

		assertThat(p.database()).isEqualTo(temp.resolve("AppData").resolve("Local").resolve("PonteBPA").resolve("database"));
	}

	@Test
	void prepararPastasCriaAsPastasECopiaOCadastroDeMedicosDoPacote() throws IOException {
		PastasApp p = PastasApp.de(true, temp.toString(), "");

		p.prepararPastas();

		assertThat(p.database()).isDirectory();
		assertThat(p.logs()).isDirectory();
		assertThat(p.config()).isDirectory();
		assertThat(p.dados().resolve("medicos_cns.csv")).exists();
		// nomes de colunas: o arquivo da pasta de dados guarda só os acrescentados pelo usuário
		assertThat(p.dados().resolve("colunas_aliases.csv")).doesNotExist();
	}

	@Test
	void prepararPastasNaoSobrescreveOCadastroJaExistente() throws IOException {
		PastasApp p = PastasApp.de(true, temp.toString(), "");
		Files.createDirectories(p.dados());
		Files.writeString(p.dados().resolve("medicos_cns.csv"), "FULANO;700000000000000\n", StandardCharsets.UTF_8);

		p.prepararPastas();

		assertThat(p.dados().resolve("medicos_cns.csv")).hasContent("FULANO;700000000000000");
	}

	@Test
	void desenvolvimentoNaoCriaNemCopiaNada() throws IOException {
		PastasApp p = new PastasApp(temp.resolve("db"), temp.resolve("lg"), temp.resolve("dd"), temp.resolve("cf"), false);

		p.prepararPastas();

		assertThat(temp.resolve("db")).doesNotExist();
		assertThat(temp.resolve("dd")).doesNotExist();
	}
}
