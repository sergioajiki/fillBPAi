package br.gov.ses.fillbpai.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Registro dos erros inesperados do programa instalado (21/10/2026). */
class RegistroErrosTest {

	@TempDir
	Path temp;

	@Test
	void gravaOErroComLocalEPilhaEAcrescentaOsSeguintes() throws IOException {
		RegistroErros registro = new RegistroErros(temp.resolve("logs"));

		assertThat(registro.registrar("janela Sobre", new IllegalStateException("falhou ao abrir"))).isTrue();
		assertThat(registro.registrar("outro ponto", new RuntimeException("segundo erro"))).isTrue();

		String conteudo = Files.readString(registro.arquivo(), StandardCharsets.UTF_8);
		assertThat(conteudo)
				.contains("janela Sobre", "IllegalStateException: falhou ao abrir", "RegistroErrosTest")
				.contains("outro ponto", "segundo erro");
		assertThat(registro.arquivo().getFileName()).hasToString("erros_programa.log");
	}

	@Test
	void arquivoGrandeEhRecomecadoGuardandoOAnterior() throws IOException {
		RegistroErros registro = new RegistroErros(temp);
		Files.writeString(registro.arquivo(), "x".repeat((int) RegistroErros.TAMANHO_MAXIMO + 10), StandardCharsets.UTF_8);

		registro.registrar("depois de cheio", new RuntimeException("novo"));

		assertThat(temp.resolve("erros_programa.log.1")).exists();
		assertThat(Files.readString(registro.arquivo(), StandardCharsets.UTF_8)).contains("depois de cheio").doesNotContain("xxxx");
	}

	@Test
	void falhaAoGravarNaoLancaExcecao() throws IOException {
		// "pasta" de logs que na verdade é um arquivo: não dá para gravar dentro
		Path naoEhPasta = Files.writeString(temp.resolve("arquivo"), "", StandardCharsets.UTF_8);

		assertThat(new RegistroErros(naoEhPasta).registrar("x", new RuntimeException())).isFalse();
	}
}
