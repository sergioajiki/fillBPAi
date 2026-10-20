package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.config.DadosUnidade;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Janela "Sobre" (20/10/2026). */
class SobreServiceTest {

	@Test
	void carregarTrazVersaoDoPomDadosDaUnidadeEPastas() {
		SobreService.InformacoesSobre info = new SobreService(Path.of("logs"), Path.of("src/main/resources/dados")).carregar();

		// os testes rodam pelo Maven, que grava a versão do pom em versao.properties
		assertThat(info.versao()).matches("\\d+\\.\\d+\\.\\d+.*");
		assertThat(info.compilado()).isNotNull();
		assertThat(info.cnes()).isEqualTo(DadosUnidade.CNES);
		assertThat(info.orgao()).isEqualTo(DadosUnidade.ORGAO);
		assertThat(info.cnpj()).isEqualTo("02.955.271/0001-26");
		assertThat(info.versaoHeader()).isEqualTo(DadosUnidade.VERSAO_HEADER);
		assertThat(info.banco()).startsWith("H2 2.");
		assertThat(info.pastas()).extracting(SobreService.PastaDados::rotulo).containsExactly("Logs", "Cadastros");
		assertThat(info.pastas().get(0).caminho().isAbsolute()).isTrue();
		assertThat(info.pastas().get(0).caminho().getFileName()).hasToString("logs");
	}

	@Test
	void textoParaCopiarTemUmaInformacaoPorLinha() {
		SobreService.InformacoesSobre info = new SobreService.InformacoesSobre("1.0.0",
				LocalDateTime.of(2026, 10, 20, 15, 10), "6970451", "NUCLEO DE TELESSAUDE DE MS", "NTMS",
				"02.955.271/0001-26", "Exportação BPA 2026", "ED04.10", "21.0.8", "Windows 11 10.0", "H2 2.2.224",
				List.of(new SobreService.PastaDados("Logs", Path.of("C:/PonteBPA/logs"))));

		assertThat(info.textoParaCopiar().split("\n")).containsExactly(
				"PonteBPA 1.0.0 (compilado em 20/10/2026 15:10)",
				"Unidade: CNES 6970451 — NUCLEO DE TELESSAUDE DE MS (NTMS)",
				"Layout BPA-I: Exportação BPA 2026 — versão no header ED04.10",
				"Java 21.0.8 · Windows 11 10.0 · H2 2.2.224",
				"Logs: " + Path.of("C:/PonteBPA/logs"));
	}

	@Test
	void semDataDeCompilacaoOTextoNaoMostraParenteses() {
		SobreService.InformacoesSobre info = new SobreService.InformacoesSobre("desenvolvimento", null,
				"1", "O", "S", "C", "L", "V", "J", "SO", "B", List.of());

		assertThat(info.textoParaCopiar()).startsWith("PonteBPA desenvolvimento\n");
		assertThat(info.compiladoFormatado()).isEmpty();
	}

	@Test
	void valorNaoFiltradoPeloMavenEhTratadoComoAusente() {
		assertThat(SobreService.valorOu("${project.version}", "desenvolvimento")).isEqualTo("desenvolvimento");
		assertThat(SobreService.valorOu(" 1.0.0 ", "x")).isEqualTo("1.0.0");
		assertThat(SobreService.dataCompilacao("${maven.build.timestamp}")).isNull();
		assertThat(SobreService.dataCompilacao("ontem")).isNull();
		assertThat(SobreService.dataCompilacao("2026-10-20T18:10:00Z")).isNotNull();
	}

	@Test
	void formatarCnpj() {
		assertThat(SobreService.formatarCnpj("02955271000126")).isEqualTo("02.955.271/0001-26");
		assertThat(SobreService.formatarCnpj("123")).isEqualTo("123");
	}
}
