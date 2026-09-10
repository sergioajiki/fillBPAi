package br.gov.ses.fillbpai.util;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Usa {@link CnsProfissionalUtils#usarCaminhoParaTeste} para redirecionar a
 * camada "arquivo externo" (gravável) para um arquivo temporário — nunca
 * escreve no CSV real do projeto. A camada classpath (os 47 médicos reais)
 * continua carregando normalmente; por isso os testes usam CNS/nomes
 * obviamente falsos, e reaproveitam o médico real conhecido
 * (RODRIGO SILVA GRILO → CNS 700207960618529) só para os casos de leitura.
 */
class CnsProfissionalUtilsTest {

	private static final String CNS_TESTE = "111111111111111";
	private static final String NOME_TESTE = "FULANO DE TESTE";

	@TempDir
	Path tempDir;

	@BeforeEach
	void apontarParaArquivoTemporario() {
		Path arquivo = tempDir.resolve("medicos_cns_teste.csv");
		CnsProfissionalUtils.usarCaminhoParaTeste(arquivo.toString());
	}

	@AfterEach
	void restaurarCaminhoReal() {
		CnsProfissionalUtils.usarCaminhoParaTeste("src/main/resources/dados/medicos_cns.csv");
	}

	@Test
	void buscarComNomeRealConhecidoEncontraCns() {
		CnsProfissionalUtils.CnsResultado resultado = CnsProfissionalUtils.buscar("RODRIGO SILVA GRILO");

		assertThat(resultado.getCns()).isEqualTo("700207960618529");
		assertThat(resultado.getAviso()).isNull();
	}

	@Test
	void buscarComNomeDesconhecidoDevolveAvisoComONomeENuloNoCns() {
		CnsProfissionalUtils.CnsResultado resultado = CnsProfissionalUtils.buscar("MEDICO QUE NAO EXISTE XYZ");

		assertThat(resultado.getCns()).isNull();
		assertThat(resultado.getAviso()).contains("MEDICO QUE NAO EXISTE XYZ");
	}

	@Test
	void cadastrarNovoMedicoApareceNaListaEEncontravelPeloNome() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThat(CnsProfissionalUtils.buscar(NOME_TESTE).getCns()).isEqualTo(CNS_TESTE);
		assertThat(CnsProfissionalUtils.obterTodosMedicos())
				.anySatisfy(m -> {
					assertThat(m.cns()).isEqualTo(CNS_TESTE);
					assertThat(m.apelidos()).containsExactly(NOME_TESTE);
				});
	}

	@Test
	void cadastrarComCnsJaExistenteLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThatThrownBy(() -> CnsProfissionalUtils.cadastrar(CNS_TESTE, "OUTRO NOME"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(NOME_TESTE);
	}

	@Test
	void cadastrarComNomeJaAssociadoAOutroCnsLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThatThrownBy(() -> CnsProfissionalUtils.cadastrar("222222222222222", NOME_TESTE))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(CNS_TESTE);
	}

	@Test
	void adicionarApelidoAMedicoExistenteResolvePeloNovoNome() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		CnsProfissionalUtils.adicionarApelido(CNS_TESTE, "F. DE TESTE");

		assertThat(CnsProfissionalUtils.buscar("F. DE TESTE").getCns()).isEqualTo(CNS_TESTE);
		assertThat(CnsProfissionalUtils.obterTodosMedicos())
				.filteredOn(m -> m.cns().equals(CNS_TESTE))
				.singleElement()
				.satisfies(m -> assertThat(m.apelidos()).containsExactly(NOME_TESTE, "F. DE TESTE"));
	}

	@Test
	void adicionarApelidoJaCadastradoDoMesmoMedicoNaoLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		CnsProfissionalUtils.adicionarApelido(CNS_TESTE, NOME_TESTE);

		assertThat(CnsProfissionalUtils.obterTodosMedicos())
				.filteredOn(m -> m.cns().equals(CNS_TESTE))
				.singleElement()
				.satisfies(m -> assertThat(m.apelidos()).containsExactly(NOME_TESTE));
	}

	@Test
	void adicionarApelidoComNomeDeOutroMedicoLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThatThrownBy(() -> CnsProfissionalUtils.adicionarApelido(CNS_TESTE, "RODRIGO SILVA GRILO"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("700207960618529");
	}

	@Test
	void removerApelidoQuandoHaMaisDeUmFunciona() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);
		CnsProfissionalUtils.adicionarApelido(CNS_TESTE, "F. DE TESTE");

		CnsProfissionalUtils.removerApelido(CNS_TESTE, "F. DE TESTE");

		assertThat(CnsProfissionalUtils.buscar("F. DE TESTE").getCns()).isNull();
		assertThat(CnsProfissionalUtils.buscar(NOME_TESTE).getCns()).isEqualTo(CNS_TESTE);
	}

	@Test
	void removerApelidoUnicoLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThatThrownBy(() -> CnsProfissionalUtils.removerApelido(CNS_TESTE, NOME_TESTE))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("único apelido");
	}

	@Test
	void alterarCnsDeMedicoExistenteAtualizaResolucaoDosApelidosAntigos() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		CnsProfissionalUtils.alterarCns(CNS_TESTE, "222222222222222");

		assertThat(CnsProfissionalUtils.buscar(NOME_TESTE).getCns()).isEqualTo("222222222222222");
	}

	@Test
	void alterarCnsParaValorJaUsadoPeloMedicoRealLancaExcecao() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		assertThatThrownBy(() -> CnsProfissionalUtils.alterarCns(CNS_TESTE, "700207960618529"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("RODRIGO SILVA GRILO");
	}

	@Test
	void removerMedicoRemoveTodosOsApelidos() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);
		CnsProfissionalUtils.adicionarApelido(CNS_TESTE, "F. DE TESTE");

		CnsProfissionalUtils.removerMedico(CNS_TESTE);

		assertThat(CnsProfissionalUtils.buscar(NOME_TESTE).getCns()).isNull();
		assertThat(CnsProfissionalUtils.buscar("F. DE TESTE").getCns()).isNull();
		assertThat(CnsProfissionalUtils.obterTodosMedicos())
				.noneMatch(m -> m.cns().equals(CNS_TESTE));
	}

	@Test
	void limparCacheForcaReleituraDoArquivoTemporario() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);

		CnsProfissionalUtils.limparCache();

		assertThat(CnsProfissionalUtils.buscar(NOME_TESTE).getCns()).isEqualTo(CNS_TESTE);
	}

	@Test
	void obterTodosMedicosDevolveApelidosEmOrdemDeCadastro() {
		CnsProfissionalUtils.cadastrar(CNS_TESTE, NOME_TESTE);
		CnsProfissionalUtils.adicionarApelido(CNS_TESTE, "F. DE TESTE");

		List<CnsProfissionalUtils.MedicoInfo> medicos = CnsProfissionalUtils.obterTodosMedicos();

		CnsProfissionalUtils.MedicoInfo medicoTeste = medicos.stream()
				.filter(m -> m.cns().equals(CNS_TESTE))
				.findFirst()
				.orElseThrow();

		assertThat(medicoTeste.nomePrincipal()).isEqualTo(NOME_TESTE);
	}
}
