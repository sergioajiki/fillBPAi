package br.gov.ses.fillbpai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Histórico dos logs de importação (20/10/2026). */
class HistoricoLogsServiceTest {

	@TempDir
	Path pasta;

	private Path gravar(String nome, String conteudo) throws IOException {
		Path arquivo = pasta.resolve(nome);
		Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
		return arquivo;
	}

	private static String log(String planilha, String quando, int linhas, int avisos) {
		return "Planilha: " + planilha + "\nLocal: C:\\BPA\\Setembro\nImportada em: " + quando + "\n\n"
				+ "Linhas da planilha: " + linhas + " · Importadas: " + linhas + " (" + linhas
				+ " novas, 0 atualizadas) ✓ conferido\nAvisos: " + avisos + "\n\nImportação concluída sem avisos.\n";
	}

	@Test
	void listarLeOCabecalhoEOrdenaDoMaisRecenteParaOMaisAntigo() throws IOException {
		gravar("log_importacao_09 - Setembro_2026-10-16_0915.txt", log("09 - Setembro.xlsx", "16/10/2026 às 09:15", 305, 14));
		gravar("log_importacao_09 - Setembro_2026-10-20_1432.txt", log("09 - Setembro.xlsx", "20/10/2026 às 14:32", 312, 0));
		gravar("outro_arquivo.txt", "não é log");

		List<HistoricoLogsService.EntradaLog> entradas = new HistoricoLogsService(pasta).listar();

		assertThat(entradas).hasSize(2);
		HistoricoLogsService.EntradaLog recente = entradas.get(0);
		assertThat(recente.quando()).isEqualTo(LocalDateTime.of(2026, 10, 20, 14, 32));
		assertThat(recente.planilha()).isEqualTo("09 - Setembro.xlsx");
		assertThat(recente.local()).isEqualTo("C:\\BPA\\Setembro");
		assertThat(recente.linhas()).isEqualTo(312);
		assertThat(recente.avisos()).isZero();
		assertThat(recente.antigo()).isFalse();
		assertThat(recente.quandoFormatado()).isEqualTo("20/10/2026 às 14:32");
		assertThat(entradas.get(1).avisos()).isEqualTo(14);
	}

	@Test
	void logAntigoSemCabecalhoUsaONomeEADataDoArquivo() throws IOException {
		Path antigo = gravar("log_importacao_07 - BPAi Julho 2026.txt",
				"Linhas da planilha: 276 · Importadas: 276 (276 novas, 0 atualizadas) ✓ conferido\nAvisos: 8\n");
		LocalDateTime dataArquivo = LocalDateTime.of(2026, 9, 2, 11, 5);
		Files.setLastModifiedTime(antigo, FileTime.from(dataArquivo.atZone(ZoneId.systemDefault()).toInstant()));

		HistoricoLogsService.EntradaLog entrada = new HistoricoLogsService(pasta).listar().get(0);

		assertThat(entrada.antigo()).isTrue();
		assertThat(entrada.planilha()).isEqualTo("07 - BPAi Julho 2026");
		assertThat(entrada.quando()).isEqualTo(dataArquivo);
		assertThat(entrada.local()).isNull();
		assertThat(entrada.linhas()).isEqualTo(276);
		assertThat(entrada.avisos()).isEqualTo(8);
	}

	@Test
	void novoArquivoTemDataEHoraNoNomeENuncaSobrescreve() throws IOException {
		HistoricoLogsService servico = new HistoricoLogsService(pasta);
		LocalDateTime quando = LocalDateTime.of(2026, 10, 20, 13, 49);

		Path primeiro = servico.novoArquivo("09 - Setembro", quando);
		Files.writeString(primeiro, "x");
		Path segundo = servico.novoArquivo("09 - Setembro", quando);

		assertThat(primeiro.getFileName().toString()).isEqualTo("log_importacao_09 - Setembro_2026-10-20_1349.txt");
		assertThat(segundo.getFileName().toString()).isEqualTo("log_importacao_09 - Setembro_2026-10-20_1349_2.txt");
		assertThat(HistoricoLogsService.planilhaDoNomeDoArquivo(segundo)).isEqualTo("09 - Setembro");
	}

	@Test
	void pastaInexistenteOuSemLogsDevolveListaVazia() throws IOException {
		assertThat(new HistoricoLogsService(pasta.resolve("nao_existe")).listar()).isEmpty();
		assertThat(new HistoricoLogsService(pasta).listar()).isEmpty();
	}

	@Test
	void conteudoDevolveOTextoDoLog() throws IOException {
		gravar("log_importacao_x_2026-10-20_1000.txt", log("x.xlsx", "20/10/2026 às 10:00", 1, 0));
		HistoricoLogsService servico = new HistoricoLogsService(pasta);

		assertThat(servico.conteudo(servico.listar().get(0))).startsWith("Planilha: x.xlsx");
	}
}
