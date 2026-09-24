package br.gov.ses.fillbpai.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Endereco;
import br.gov.ses.fillbpai.model.Paciente;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa {@code gerarConteudoParcial}/{@code gerarConteudoCompleto} — os dois
 * métodos extraídos de {@link GeradorBPAiService} para permitir verificar o
 * layout posicional do BPA-I sem depender de banco de dados nem de
 * {@code javafx.stage.FileChooser}. Nenhum destes testes usa
 * {@code EntityManager}: {@code montarRegistro} nunca acessa
 * {@code a.getMedico()}, só campos do próprio atendimento e de
 * paciente/endereço, então a lista é construída inteiramente em memória.
 * <p>
 * Posições (0-based, fim exclusivo) de cada campo dentro da linha de
 * registro, calculadas somando os tamanhos de {@code montarRegistro} campo a
 * campo — usadas para extrair e conferir cada seq individualmente:
 * seq1[0,2) seq2[2,9) seq3[9,15) seq4[15,30) seq5[30,36) seq6[36,44)
 * seq7[44,47) seq8[47,49) seq9[49,59) seq10[59,74) seq11[74,75) seq12[75,81)
 * seq13[81,85) seq14[85,88) seq15[88,94) seq16[94,96) seq17[96,109)
 * seq18[109,112) seq19[112,142) seq20[142,150) seq21[150,152) seq22[152,156)
 * seq23[156,159) seq24[159,162) seq25[162,165) seq26[165,173) seq27[173,177)
 * seq28[177,191) seq29[191,199) seq30[199,202) seq31[202,232) seq32[232,242)
 * seq33[242,247) seq34[247,277) seq35[277,288) seq36[288,328) seq37[328,338)
 * seq38[338,349) seq"38"dup[349,350) seq39[350,351)
 */
class GeradorBPAiServiceTest {

	private final GeradorBPAiService service = new GeradorBPAiService(null);

	/** Atendimento com todos os campos relevantes preenchidos, usado como caso "feliz". */
	private AtendimentoBPAi criarAtendimentoCompleto() {

		Paciente paciente = new Paciente();
		paciente.setNome("MARIA DA SILVA SANTOS");
		paciente.setCpf("12345678900");
		paciente.setSexo("F");
		paciente.setRaca("BRANCA");
		paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
		paciente.setTelefone("67999999999");

		Endereco endereco = new Endereco();
		endereco.setCodigoIbge("5002704");
		endereco.setCep("79003020");
		endereco.setCodLogradouro("081");
		endereco.setEndereco("RUA DAS FLORES");
		endereco.setComplemento("APTO 1");
		endereco.setNumero("100");
		endereco.setBairro("CENTRO");
		paciente.setEndereco(endereco);

		AtendimentoBPAi atendimento = new AtendimentoBPAi();
		atendimento.setPaciente(paciente);
		atendimento.setCnesNts("6970451");
		atendimento.setCnsProfissional("700207960618529");
		atendimento.setCboMedico("225125");
		atendimento.setDataAgendamento(LocalDate.of(2024, 12, 25));
		atendimento.setFolha("5");
		atendimento.setSigtap("03.01.01.030-7");
		atendimento.setEspecialidadeMedico("CARDIOLOGIA");
		atendimento.setCidConsulta("I10");

		return atendimento;
	}

	/** Atendimento com o mínimo necessário para não lançar NPE (só a data é obrigatória). */
	private AtendimentoBPAi criarAtendimentoMinimo(LocalDate data) {
		AtendimentoBPAi atendimento = new AtendimentoBPAi();
		atendimento.setDataAgendamento(data);
		return atendimento;
	}

	private String registro(String conteudo, int indice) {
		String[] linhas = conteudo.split("\r\n", -1);
		return linhas[1 + indice];
	}

	// ===== TAMANHO E QUEBRA DE LINHA =====

	@Test
	void gerarConteudoParcialTemHeaderCom130CaracteresERegistroCom351() {
		String conteudo = service.gerarConteudoParcial(List.of(criarAtendimentoCompleto()), "202412");

		String[] linhas = conteudo.split("\r\n", -1);
		assertThat(linhas[0]).hasSize(130);
		assertThat(linhas[1]).hasSize(351);
	}

	@Test
	void gerarConteudoParcialUsaSempreCrlfSemQuebraDeLinhaSolta() {
		String conteudo = service.gerarConteudoParcial(List.of(criarAtendimentoCompleto()), "202412");

		assertThat(conteudo.replace("\r\n", "")).doesNotContain("\n");
	}

	@Test
	void gerarConteudoCompletoUsaSempreCrlfSemQuebraDeLinhaSolta() {
		AtendimentoBPAi a = criarAtendimentoCompleto();
		a.setFolha("1");
		String conteudo = service.gerarConteudoCompleto(List.of(a), "202412", 1);

		assertThat(conteudo.replace("\r\n", "")).doesNotContain("\n");
	}

	// ===== HEADER =====

	@Test
	void gerarConteudoParcialCalculaChecksumDoHeaderCorretamente() {
		// sigtap 0301010307 -> soma = 301010307 + 1 = 301010308
		// 301010308 % 1111 = 412 -> checksum = 412 + 1111 = 1523
		String conteudo = service.gerarConteudoParcial(List.of(criarAtendimentoCompleto()), "202412");

		String header = conteudo.split("\r\n", -1)[0];
		assertThat(header.substring(25, 29)).isEqualTo("1523"); // seq 6 cbc-smt-vrf
	}

	@Test
	void gerarConteudoCompletoUsaTotalFolhasRecebidoPorParametroNoHeaderNaoOTamanhoDaLista() {
		AtendimentoBPAi a1 = criarAtendimentoCompleto();
		a1.setFolha("1");
		AtendimentoBPAi a2 = criarAtendimentoCompleto();
		a2.setFolha("1");

		String conteudo = service.gerarConteudoCompleto(List.of(a1, a2), "202412", 1);

		String header = conteudo.split("\r\n", -1)[0];
		assertThat(header.substring(13, 19)).isEqualTo("000002"); // seq 4 cbc-lin = lista.size()
		assertThat(header.substring(19, 25)).isEqualTo("000001"); // seq 5 cbc-flh = totalFolhas (parâmetro)
	}

	// ===== REGISTRO — CAMPO A CAMPO =====

	@Test
	void gerarConteudoParcialMontaTodosOsCamposDoRegistroNasPosicoesCorretas() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(0, 2)).isEqualTo("03");             // seq 1 prd-ident
		assertThat(linha.substring(2, 9)).isEqualTo("6970451");        // seq 2 prd-cnes
		assertThat(linha.substring(9, 15)).isEqualTo("202412");        // seq 3 prd-cmp
		assertThat(linha.substring(15, 30)).isEqualTo("700207960618529"); // seq 4 prd-cnsmed
		assertThat(linha.substring(30, 36)).isEqualTo("225125");       // seq 5 prd-cbo
		assertThat(linha.substring(36, 44)).isEqualTo("20241225");     // seq 6 prd-dtaten
		assertThat(linha.substring(44, 47)).isEqualTo("005");          // seq 7 prd-flh
		assertThat(linha.substring(47, 49)).isEqualTo("01");           // seq 8 prd-seq
		assertThat(linha.substring(49, 59)).isEqualTo("0301010307");   // seq 9 prd-pa
		assertThat(linha.substring(59, 74)).isEqualTo(" ".repeat(15)); // seq 10 prd-cnspac — sempre em branco
		assertThat(linha.substring(74, 75)).isEqualTo("F");            // seq 11 prd-sexo
		assertThat(linha.substring(75, 81)).isEqualTo("500270");       // seq 12 prd-ibge (7 dígitos truncado p/ 6)
		assertThat(linha.substring(81, 85)).isEqualTo("I10 ");         // seq 13 prd-cid
		int idadeEsperada = Period.between(LocalDate.of(1990, 1, 1), LocalDate.now()).getYears();
		assertThat(linha.substring(85, 88)).isEqualTo(String.format("%03d", idadeEsperada)); // seq 14 prd-idade
		assertThat(linha.substring(88, 94)).isEqualTo("000001");       // seq 15 prd-qt
		assertThat(linha.substring(94, 96)).isEqualTo("01");           // seq 16 prd-caten
		assertThat(linha.substring(96, 109)).isEqualTo(" ".repeat(13)); // seq 17 prd-naut
		assertThat(linha.substring(109, 112)).isEqualTo("BPA");        // seq 18 prd-org
		assertThat(linha.substring(112, 142)).isEqualTo(String.format("%-30s", "MARIA DA SILVA SANTOS")); // seq 19 prd-nmpac
		assertThat(linha.substring(142, 150)).isEqualTo("19900101");   // seq 20 prd-dtnasc
		assertThat(linha.substring(150, 152)).isEqualTo("01");         // seq 21 prd-raca (BRANCA)
		assertThat(linha.substring(152, 156)).isEqualTo(" ".repeat(4)); // seq 22 prd-etnia
		assertThat(linha.substring(156, 159)).isEqualTo("010");        // seq 23 prd-nac
		assertThat(linha.substring(159, 162)).isEqualTo("160");        // seq 24 prd-srv
		assertThat(linha.substring(162, 165)).isEqualTo("006");        // seq 25 prd-clf
		assertThat(linha.substring(165, 173)).isEqualTo(" ".repeat(8)); // seq 26 prd-equipe_seq
		assertThat(linha.substring(173, 177)).isEqualTo(" ".repeat(4)); // seq 27 prd-equipe_area
		assertThat(linha.substring(177, 191)).isEqualTo(" ".repeat(14)); // seq 28 prd-cnpj
		assertThat(linha.substring(191, 199)).isEqualTo("79003020");   // seq 29 prd-cep_pcnte
		assertThat(linha.substring(199, 202)).isEqualTo("081");        // seq 30 prd-lograd_pcnte
		assertThat(linha.substring(202, 232)).isEqualTo(String.format("%-30s", "RUA DAS FLORES")); // seq 31
		assertThat(linha.substring(232, 242)).isEqualTo(String.format("%-10s", "APTO 1"));         // seq 32
		assertThat(linha.substring(242, 247)).isEqualTo(String.format("%-5s", "100"));             // seq 33
		assertThat(linha.substring(247, 277)).isEqualTo(String.format("%-30s", "CENTRO"));         // seq 34
		assertThat(linha.substring(277, 288)).isEqualTo("67999999999"); // seq 35 prd-ddtel_pcnte
		assertThat(linha.substring(288, 328)).isEqualTo(" ".repeat(40)); // seq 36 prd-email_pcnte
		assertThat(linha.substring(328, 338)).isEqualTo(" ".repeat(10)); // seq 37 prd-ine
		assertThat(linha.substring(338, 349)).isEqualTo("12345678900");  // seq 38 prd-cpf_pcnte
		assertThat(linha.substring(349, 350)).isEqualTo("N");            // seq "38" dup. prd-situacao_rua
		assertThat(linha.substring(350, 351)).isEqualTo("N");            // seq 39 prd-sem_cpf (CPF preenchido)
	}

	// ===== SITUAÇÃO DE RUA (seq "38" duplicado) =====

	@Test
	void seqSituacaoRuaSemInformacaoNaPlanilhaUsaPadraoN() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		// paciente.situacaoRua não definido (planilha sem a coluna, ou coluna em branco)

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(349, 350)).isEqualTo("N");
	}

	@Test
	void seqSituacaoRuaComValorSNaPlanilhaUsaOValorInformado() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setSituacaoRua("S");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(349, 350)).isEqualTo("S");
	}

	// ===== PACIENTE SEM CPF (seq 39) =====

	@Test
	void seq39PrdSemCpfSemInformacaoNaPlanilhaDerivaNQuandoCpfPreenchido() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		// atendimento.pacienteSemCpf não definido (planilha sem a coluna) — paciente tem CPF preenchido

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(350, 351)).isEqualTo("N");
	}

	@Test
	void seq39PrdSemCpfSemInformacaoNaPlanilhaDerivaSQuandoCpfVazio() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setCpf(null);

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(350, 351)).isEqualTo("S");
	}

	@Test
	void seq39PrdSemCpfComValorInformadoNaPlanilhaPrevaleceSobreADerivacao() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		// paciente TEM CPF preenchido, mas a planilha trouxe explicitamente "S"
		atendimento.setPacienteSemCpf("S");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(350, 351)).isEqualTo("S");
	}

	@Test
	void especialidadeNutricionistaForcaSigtapFixoEDerivaServicoEClassificacaoDele() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.setEspecialidadeMedico("NUTRICIONISTA");
		atendimento.setSigtap("12.34.56.789-0"); // sigtap original, não deve aparecer no registro

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(49, 59)).isEqualTo("0301010315"); // seq 9 prd-pa forçado
		assertThat(linha.substring(159, 162)).isEqualTo("160");      // seq 24 prd-srv segue o código forçado
		assertThat(linha.substring(162, 165)).isEqualTo("006");      // seq 25 prd-clf segue o código forçado
	}

	// ===== ETNIA (seq 22) =====

	@Test
	void seq22PrdEtniaComEtniaReconhecidaGeraCodigoDeQuatroCaracteres() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setRaca("INDIGENA");
		atendimento.getPaciente().setEtnia("BANIWA");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(152, 156)).isEqualTo("0032");
	}

	@Test
	void seq22PrdEtniaComEtniaNaoReconhecidaGeraQuatroEspacos() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setRaca("INDIGENA");
		atendimento.getPaciente().setEtnia("ETNIA QUE NAO EXISTE XYZ");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String linha = registro(conteudo, 0);

		assertThat(linha.substring(152, 156)).isEqualTo(" ".repeat(4));
	}

	// ===== SEQUENCIAL E FOLHA =====

	@Test
	void gerarConteudoParcialReiniciaSequencialParaUmAoPassarDeNoventaENove() {
		List<AtendimentoBPAi> lista = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			lista.add(criarAtendimentoMinimo(LocalDate.of(2024, 12, 25)));
		}

		String conteudo = service.gerarConteudoParcial(lista, "202412");

		assertThat(registro(conteudo, 98).substring(47, 49)).isEqualTo("99");  // linha 99
		assertThat(registro(conteudo, 99).substring(47, 49)).isEqualTo("01");  // linha 100 — reiniciou
	}

	@Test
	void gerarConteudoCompletoReiniciaSequencialACadaTrocaDeFolha() {
		AtendimentoBPAi a1 = criarAtendimentoMinimo(LocalDate.of(2024, 12, 25));
		a1.setFolha("1");
		AtendimentoBPAi a2 = criarAtendimentoMinimo(LocalDate.of(2024, 12, 25));
		a2.setFolha("1");
		AtendimentoBPAi a3 = criarAtendimentoMinimo(LocalDate.of(2024, 12, 25));
		a3.setFolha("2");

		String conteudo = service.gerarConteudoCompleto(List.of(a1, a2, a3), "202412", 2);

		assertThat(registro(conteudo, 0).substring(47, 49)).isEqualTo("01");
		assertThat(registro(conteudo, 1).substring(47, 49)).isEqualTo("02");
		assertThat(registro(conteudo, 2).substring(47, 49)).isEqualTo("01"); // trocou de folha, reiniciou
	}

	// ===== SANITIZAÇÃO DE TEXTO (ISO-8859-1) =====

	@Test
	void gerarConteudoParcialNormalizaPontuacaoTipograficaMasMantemAcentuacaoLatina() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setNome("Ánderson’s Café – Órgão…");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String nomeNoRegistro = registro(conteudo, 0).substring(112, 142).stripTrailing();

		assertThat(nomeNoRegistro).isEqualTo("Ánderson's Café - Órgão...");
	}

	@Test
	void gerarConteudoParcialSubstituiCaractereForaDoLatin1PorEspaco() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.getPaciente().setNome("AB★CD");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String nomeNoRegistro = registro(conteudo, 0).substring(112, 142).stripTrailing();

		assertThat(nomeNoRegistro).isEqualTo("AB CD");
	}

	@Test
	void gerarConteudoParcialFormataCidEmMaiusculoSoAlfanumericoTruncadoAQuatro() {
		AtendimentoBPAi atendimento = criarAtendimentoCompleto();
		atendimento.setCidConsulta("i10.9-extra");

		String conteudo = service.gerarConteudoParcial(List.of(atendimento), "202412");
		String cidNoRegistro = registro(conteudo, 0).substring(81, 85).stripTrailing();

		assertThat(cidNoRegistro).isEqualTo("I109");
	}
}
