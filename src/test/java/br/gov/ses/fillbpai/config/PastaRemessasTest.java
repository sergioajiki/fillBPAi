package br.gov.ses.fillbpai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasta padrão das remessas BPA-I (21/10/2026). */
class PastaRemessasTest {

	@TempDir
	Path temp;

	private PastaRemessas pasta() {
		return new PastaRemessas(temp.resolve("Documentos").resolve("PonteBPA").resolve("Remessas"),
				temp.resolve("config").resolve("preferencias.properties"));
	}

	@Test
	void primeiraGeracaoAbreNaPastaPadraoECriaAPasta() {
		Path inicial = pasta().inicial();

		assertThat(inicial).isEqualTo(temp.resolve("Documentos").resolve("PonteBPA").resolve("Remessas"));
		assertThat(inicial).isDirectory();
	}

	@Test
	void depoisDeGerarAbreNaUltimaPastaUsada() throws IOException {
		Path escolhida = Files.createDirectories(temp.resolve("BPA").resolve("Exportados"));

		pasta().lembrar(escolhida.resolve("BPA-I_COMPLETO_202611.txt"));

		// nova instância: a preferência vem do arquivo, como numa nova abertura do programa
		assertThat(pasta().inicial()).isEqualTo(escolhida.toAbsolutePath());
	}

	@Test
	void ultimaPastaApagadaVoltaParaAPadrao() throws IOException {
		Path escolhida = Files.createDirectories(temp.resolve("temporaria"));
		pasta().lembrar(escolhida.resolve("x.txt"));
		Files.delete(escolhida);

		assertThat(pasta().inicial()).isEqualTo(pasta().padrao());
	}

	@Test
	void preferenciaIlegivelVoltaParaAPadrao() throws IOException {
		Files.createDirectories(temp.resolve("config"));
		Files.writeString(temp.resolve("config").resolve("preferencias.properties"),
				"pasta.remessas=\u0000:::caminho invalido\n", StandardCharsets.UTF_8);

		assertThat(pasta().inicial()).isEqualTo(pasta().padrao());
	}

	@Test
	void pastaPadraoFicaEmDocumentosPonteBpaRemessas() {
		assertThat(PastaRemessas.atual().padrao().endsWith(Path.of("PonteBPA", "Remessas"))).isTrue();
	}
}
