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
	void buscarPorNomeToleraSiglaDaUfEEspacosRepetidos() {
		assertThat(IbgeUtils.buscarPorNome("Campo Grande - MS")).isEqualTo("5002704");
		assertThat(IbgeUtils.buscarPorNome("CAMPO GRANDE/MS")).isEqualTo("5002704");
		assertThat(IbgeUtils.buscarPorNome("Campo Grande (MS)")).isEqualTo("5002704");
		assertThat(IbgeUtils.buscarPorNome("Campo Grande – MS")).isEqualTo("5002704");
		assertThat(IbgeUtils.buscarPorNome("Campo  Grande")).isEqualTo("5002704");
		assertThat(IbgeUtils.buscarPorNome("  Três   Lagoas - ms ")).isEqualTo("5008305");
	}

	@Test
	void nomeMunicipioDevolveNomeOficialEUfDeQualquerMunicipioDoBrasil() {
		assertThat(IbgeUtils.nomeMunicipio("5002704")).isEqualTo("Campo Grande/MS");
		assertThat(IbgeUtils.nomeMunicipio("5103403")).isEqualTo("Cuiabá/MT");
		assertThat(IbgeUtils.nomeMunicipio("3550308")).isEqualTo("São Paulo/SP");
		assertThat(IbgeUtils.nomeMunicipio("9999999")).isNull();
		assertThat(IbgeUtils.nomeMunicipio(null)).isNull();
	}

	@Test
	void resolverPeloCepInformaNoAvisoOMunicipioEncontrado() {
		try {
			IbgeUtils.usarBuscadorHttpParaTeste(url -> new IbgeUtils.RespostaHttp(200, "{\"ibge\": \"5103403\"}"));

			IbgeUtils.IbgeResultado resultado = IbgeUtils.resolver("91000099", "Cuiaba centro");

			assertThat(resultado.getCodigoIbge()).isEqualTo("5103403");
			assertThat(resultado.getAviso())
					.contains("\"Cuiaba centro\"")
					.contains("Cuiabá/MT")
					.contains("confira");
		} finally {
			IbgeUtils.usarBuscadorHttpParaTeste(null);
		}
	}

	@Test
	void buscarPorNomeComMunicipioDeOutraUfContinuaNaoEncontrado() {
		// A tabela só tem MS: tirar a sigla não pode fazer "Cuiabá - MT" bater com nada
		assertThat(IbgeUtils.buscarPorNome("Cuiabá - MT")).isNull();
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
		assertThat(resultado.getAviso()).contains("pelo CEP 00000001").contains("código IBGE 9999999");
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

	// ===== APIs de CEP: ViaCEP com reservas (BrasilAPI, OpenCEP) — HTTP simulado, sem rede =====

	private final java.util.List<String> urlsConsultadas = new java.util.ArrayList<>();

	/** Simula as APIs: cada URL que contém a chave recebe a resposta; demais lançam erro de conexão. */
	private void simularApis(Map<String, IbgeUtils.RespostaHttp> respostas) {
		IbgeUtils.usarBuscadorHttpParaTeste(url -> {
			urlsConsultadas.add(url);
			return respostas.entrySet().stream()
					.filter(e -> url.contains(e.getKey()))
					.map(Map.Entry::getValue)
					.findFirst()
					.orElseThrow(() -> new RuntimeException("sem conexão"));
		});
	}

	@org.junit.jupiter.api.AfterEach
	void restaurarHttpReal() {
		IbgeUtils.usarBuscadorHttpParaTeste(null);
	}

	@Test
	void buscarPorCepUsaViaCepQuandoResponde() {
		simularApis(Map.of("viacep", new IbgeUtils.RespostaHttp(200, "{\"localidade\":\"X\",\"ibge\": \"5002704\"}")));

		assertThat(IbgeUtils.buscarPorCep("91000001")).isEqualTo("5002704");
		assertThat(urlsConsultadas).singleElement().satisfies(u -> assertThat(u).contains("viacep"));
	}

	@Test
	void buscarPorCepComViaCepForaDoArUsaBrasilApi() {
		simularApis(Map.of("brasilapi", new IbgeUtils.RespostaHttp(200,
				"{\"cep\":\"91000002\",\"city\":\"Campo Grande\",\"ibge\":{\"city\":\"5002704\",\"state\":\"50\"}}")));

		assertThat(IbgeUtils.buscarPorCep("91000002")).isEqualTo("5002704");
		assertThat(urlsConsultadas).hasSize(2);
	}

	@Test
	void buscarPorCepComViaCepEBrasilApiForaDoArUsaOpenCep() {
		simularApis(Map.of("opencep", new IbgeUtils.RespostaHttp(200, "{\"ibge\": \"5008305\"}")));

		assertThat(IbgeUtils.buscarPorCep("91000003")).isEqualTo("5008305");
		assertThat(urlsConsultadas).hasSize(3);
	}

	@Test
	void buscarPorCepNaoEncontradoNoViaCepTentaAsReservas() {
		// ViaCEP responde "erro": "true" (com aspas) para CEP inexistente; a reserva pode ter o CEP
		simularApis(Map.of(
				"viacep", new IbgeUtils.RespostaHttp(200, "{\n  \"erro\": \"true\"\n}"),
				"brasilapi", new IbgeUtils.RespostaHttp(200, "{\"ibge\":{\"city\":\"5002704\"}}")));

		assertThat(IbgeUtils.buscarPorCep("91000004")).isEqualTo("5002704");
	}

	@Test
	void buscarPorCepNaoEncontradoEmTodasGuardaResultadoNegativoENaoConsultaDeNovo() {
		simularApis(Map.of(
				"viacep", new IbgeUtils.RespostaHttp(200, "{\"erro\": true}"),
				"brasilapi", new IbgeUtils.RespostaHttp(404, "{\"message\":\"CEP INVALIDO\"}"),
				"opencep", new IbgeUtils.RespostaHttp(404, "{\"error\": true}")));

		assertThat(IbgeUtils.buscarPorCep("91000005")).isNull();
		assertThat(IbgeUtils.buscarPorCep("91000005")).isNull();
		assertThat(urlsConsultadas).hasSize(3); // segunda chamada veio do cache
	}

	@Test
	void buscarPorCepComTodasForaDoArNaoGuardaResultadoNegativo() {
		// Falha de rede é passageira: a próxima importação deve tentar de novo
		simularApis(Map.of());

		assertThat(IbgeUtils.buscarPorCep("91000006")).isNull();
		assertThat(IbgeUtils.buscarPorCep("91000006")).isNull();
		assertThat(urlsConsultadas).hasSize(6);
	}
}
