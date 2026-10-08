package br.gov.ses.fillbpai.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.service.ErroValidacao.Severidade;

import static org.assertj.core.api.Assertions.assertThat;

class AgrupamentoValidacaoTest {

	private ErroValidacao aviso(int linha, String tipo, String valor) {
		return new ErroValidacao(linha, Severidade.AVISO, tipo, "detalhe linha " + linha, valor);
	}

	@Test
	void agruparSeparaPorTipoComTituloLegivelEExplicacao() {
		List<AgrupamentoValidacao.Grupo> grupos = AgrupamentoValidacao.agrupar(List.of(
				aviso(2, ErroValidacao.HORA_INVALIDA, "\"-\""),
				aviso(3, ErroValidacao.ESTABELECIMENTO_SEM_CODIGO, "\"HOSPITAL X\""),
				aviso(4, ErroValidacao.HORA_INVALIDA, "\"--:--\"")));

		assertThat(grupos).extracting(AgrupamentoValidacao.Grupo::tipo)
				.containsExactly(ErroValidacao.HORA_INVALIDA, ErroValidacao.ESTABELECIMENTO_SEM_CODIGO);

		AgrupamentoValidacao.Grupo hora = grupos.get(0);
		assertThat(hora.titulo()).isEqualTo("Horário de atendimento não reconhecido");
		assertThat(hora.explicacao()).contains("não utiliza o horário");
		assertThat(hora.ocorrencias()).isEqualTo(2);
		assertThat(hora.itens()).hasSize(2);
	}

	@Test
	void agruparJuntaItensComOMesmoValorEListaAsLinhas() {
		List<AgrupamentoValidacao.Grupo> grupos = AgrupamentoValidacao.agrupar(List.of(
				aviso(9, ErroValidacao.ESTABELECIMENTO_SEM_CODIGO, "\"HOSPITAL X\""),
				aviso(2, ErroValidacao.ESTABELECIMENTO_SEM_CODIGO, "\"HOSPITAL X\""),
				aviso(5, ErroValidacao.ESTABELECIMENTO_SEM_CODIGO, "\"UBS Y\"")));

		assertThat(grupos).singleElement().satisfies(grupo -> {
			assertThat(grupo.ocorrencias()).isEqualTo(3);
			assertThat(grupo.itens()).hasSize(2);
			assertThat(grupo.itens().get(0).texto()).isEqualTo("\"HOSPITAL X\"");
			assertThat(grupo.itens().get(0).linhas()).containsExactly(2, 9);
			assertThat(grupo.itens().get(1).texto()).isEqualTo("\"UBS Y\"");
		});
	}

	@Test
	void agruparSemValorUsaODetalheComoItem() {
		List<AgrupamentoValidacao.Grupo> grupos = AgrupamentoValidacao.agrupar(List.of(
				new ErroValidacao(1, Severidade.ERRO, ErroValidacao.ESTRUTURA_INVALIDA, "Coluna X ausente")));

		assertThat(grupos.get(0).itens()).singleElement()
				.satisfies(item -> assertThat(item.texto()).isEqualTo("Coluna X ausente"));
	}

	@Test
	void agruparOrdenaErrosAntesDeAvisosEPelaPrioridadeDoTipo() {
		List<AgrupamentoValidacao.Grupo> grupos = AgrupamentoValidacao.agrupar(List.of(
				aviso(2, ErroValidacao.CNS_INCOMUM, "MARIA"),
				aviso(3, ErroValidacao.HORA_INVALIDA, "\"-\""),
				new ErroValidacao(4, Severidade.ERRO, ErroValidacao.CEP_AUSENTE, "CEP", "JOSE"),
				aviso(5, ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO, "\"FULANO\"")));

		assertThat(grupos).extracting(AgrupamentoValidacao.Grupo::tipo).containsExactly(
				ErroValidacao.CEP_AUSENTE,
				ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO,
				ErroValidacao.HORA_INVALIDA,
				ErroValidacao.CNS_INCOMUM);
	}

	@Test
	void agruparComTipoDesconhecidoUsaOProprioCodigoComoTitulo() {
		List<AgrupamentoValidacao.Grupo> grupos = AgrupamentoValidacao.agrupar(List.of(
				aviso(2, "TIPO_NOVO", "x")));

		assertThat(grupos.get(0).titulo()).isEqualTo("TIPO_NOVO");
	}

	@Test
	void descreverLinhasNoSingularNoPluralELimitaEmDez() {
		assertThat(AgrupamentoValidacao.descreverLinhas(List.of(5))).isEqualTo("1 linha: 5");
		assertThat(AgrupamentoValidacao.descreverLinhas(List.of(2, 7))).isEqualTo("2 linhas: 2, 7");
		assertThat(AgrupamentoValidacao.descreverLinhas(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
				.isEqualTo("12 linhas: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, ...");
	}
}
