package br.gov.ses.fillbpai.util;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cobre só os caminhos sem rede de {@link IbgeUtils} — nenhum teste aqui deve
 * disparar uma chamada real à API ViaCEP. Não há {@code limparCache()} nesta
 * classe (o cache de CEP é estático e compartilhado entre todos os testes da
 * suíte), então cada teste que grava no cache usa um CEP fake exclusivo.
 */
class IbgeUtilsTest {

	@Test
	void normalizarRemoveAcentosEMaiusculiza() {
		assertThat(IbgeUtils.normalizar("Campo Grande")).isEqualTo("CAMPO GRANDE");
		assertThat(IbgeUtils.normalizar("São Paulo")).isEqualTo("SAO PAULO");
	}

	@Test
	void normalizarRetornaVazioParaNulo() {
		assertThat(IbgeUtils.normalizar(null)).isEmpty();
	}

	@Test
	void buscarPorNomeEncontraMunicipioConhecido() {
		assertThat(IbgeUtils.buscarPorNome("CAMPO GRANDE")).isEqualTo("5002704");
	}

	@Test
	void buscarPorNomeIgnoraAcentoECaixa() {
		assertThat(IbgeUtils.buscarPorNome("campo grande")).isEqualTo("5002704");
	}

	@Test
	void buscarPorNomeComMunicipioDesconhecidoDevolveNull() {
		assertThat(IbgeUtils.buscarPorNome("MUNICIPIO QUE NAO EXISTE XYZ")).isNull();
	}

	@Test
	void resolverComNomeResolvivelNuncaTentaOCaminhoPorCep() {
		// cep explicitamente null: se o código tentasse o caminho por CEP aqui,
		// a guarda "cep != null" já bloquearia antes de qualquer rede.
		IbgeUtils.IbgeResultado resultado = IbgeUtils.resolver(null, "CAMPO GRANDE");

		assertThat(resultado.getCodigoIbge()).isEqualTo("5002704");
		assertThat(resultado.getAviso()).isNull();
	}

	@Test
	void resolverSemNomeUsaCacheJaCarregadoSemRede() {
		String cepFake = "00000001";
		IbgeUtils.preCarregarCacheDb(Map.of(cepFake, "9999999"));

		IbgeUtils.IbgeResultado resultado = IbgeUtils.resolver(cepFake, null);

		assertThat(resultado.getCodigoIbge()).isEqualTo("9999999");
		assertThat(resultado.getAviso()).contains("não encontrado no CSV por nome");
	}

	@Test
	void resolverSemNomeESemCepConhecidoDevolveAvisoSemCodigo() {
		IbgeUtils.IbgeResultado resultado = IbgeUtils.resolver(null, null);

		assertThat(resultado.getCodigoIbge()).isNull();
		assertThat(resultado.getAviso()).contains("brancos");
	}

	@Test
	void preCarregarCacheDbComMapaNuloOuVazioNaoLancaExcecao() {
		assertThatCode(() -> IbgeUtils.preCarregarCacheDb(null)).doesNotThrowAnyException();
		assertThatCode(() -> IbgeUtils.preCarregarCacheDb(Map.of())).doesNotThrowAnyException();
	}
}
