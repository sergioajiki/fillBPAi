package br.gov.ses.fillbpai.util;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Usa {@link ColunaAliasUtils#usarCaminhoParaTeste} para redirecionar a
 * camada "local" (gravável) para um arquivo temporário — nunca escreve no
 * CSV real do projeto. A camada "padrão" continua vindo do classpath real
 * (somente leitura aqui, portanto seguro).
 */
class ColunaAliasUtilsTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void apontarParaArquivoTemporario() {
		Path arquivo = tempDir.resolve("colunas_aliases_teste.csv");
		ColunaAliasUtils.usarCaminhoParaTeste(arquivo.toString());
	}

	@AfterEach
	void restaurarCaminhoRealEDescarregarCache() {
		ColunaAliasUtils.usarCaminhoParaTeste("src/main/resources/dados/colunas_aliases.csv");
	}

	@Test
	void resolverCampoComAliasPadraoConhecido() {
		assertThat(ColunaAliasUtils.resolverCampo("CEP")).isEqualTo("CEP");
		assertThat(ColunaAliasUtils.resolverCampo("PACIENTE")).isEqualTo("PACIENTE");
	}

	@Test
	void resolverCampoIgnoraAcentoECaixa() {
		assertThat(ColunaAliasUtils.resolverCampo("cep")).isEqualTo("CEP");
	}

	@Test
	void resolverCampoSemCorrespondenciaDevolveNull() {
		assertThat(ColunaAliasUtils.resolverCampo("COLUNA QUE NAO EXISTE")).isNull();
	}

	@Test
	void obterCamposCanonicosContemCamposConhecidos() {
		List<String> campos = ColunaAliasUtils.obterCamposCanonicos();

		assertThat(campos).contains("CEP", "PACIENTE", "TIPO_SERVICO", "SEXO_PACIENTE");
	}

	@Test
	void obterAliasesDeCampoPadraoTrazOrigemPadrao() {
		List<ColunaAliasUtils.AliasInfo> aliases = ColunaAliasUtils.obterAliases("CEP");

		assertThat(aliases).isNotEmpty();
		assertThat(aliases).allSatisfy(alias ->
				assertThat(alias.origem()).isEqualTo(ColunaAliasUtils.Origem.PADRAO));
	}

	@Test
	void salvarCadastraAliasLocalQuePassaAAparecer() {
		ColunaAliasUtils.salvar("CEP", "CODIGO POSTAL");

		List<ColunaAliasUtils.AliasInfo> aliases = ColunaAliasUtils.obterAliases("CEP");

		assertThat(aliases)
				.anySatisfy(alias -> {
					assertThat(alias.nome()).isEqualTo("CODIGO POSTAL");
					assertThat(alias.origem()).isEqualTo(ColunaAliasUtils.Origem.LOCAL);
				});
		assertThat(ColunaAliasUtils.resolverCampo("CODIGO POSTAL")).isEqualTo("CEP");
	}

	@Test
	void removerTiraApenasOAliasLocal() {
		ColunaAliasUtils.salvar("CEP", "CODIGO POSTAL");

		ColunaAliasUtils.remover("CEP", "CODIGO POSTAL");

		assertThat(ColunaAliasUtils.resolverCampo("CODIGO POSTAL")).isNull();
		assertThat(ColunaAliasUtils.resolverCampo("CEP")).isEqualTo("CEP");
	}

	@Test
	void removerNaoAfetaAliasPadrao() {
		ColunaAliasUtils.remover("CEP", "CEP");

		assertThat(ColunaAliasUtils.resolverCampo("CEP")).isEqualTo("CEP");
	}

	@Test
	void restaurarPadraoLimpaSoOsAliasesLocaisDoCampo() {
		ColunaAliasUtils.salvar("CEP", "CODIGO POSTAL");
		ColunaAliasUtils.salvar("PACIENTE", "NOME DO PACIENTE");

		ColunaAliasUtils.restaurarPadrao("CEP");

		assertThat(ColunaAliasUtils.resolverCampo("CODIGO POSTAL")).isNull();
		assertThat(ColunaAliasUtils.resolverCampo("CEP")).isEqualTo("CEP");
		assertThat(ColunaAliasUtils.resolverCampo("NOME DO PACIENTE")).isEqualTo("PACIENTE");
	}

	@Test
	void limparCacheForcaReleituraDoArquivo() {
		ColunaAliasUtils.salvar("CEP", "CODIGO POSTAL");

		ColunaAliasUtils.limparCache();

		assertThat(ColunaAliasUtils.resolverCampo("CODIGO POSTAL")).isEqualTo("CEP");
	}
}
