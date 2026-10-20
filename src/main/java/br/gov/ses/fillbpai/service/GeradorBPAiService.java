package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.config.DadosUnidade;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Endereco;
import br.gov.ses.fillbpai.model.Paciente;
import br.gov.ses.fillbpai.util.CboUtils;
import br.gov.ses.fillbpai.util.CidUtils;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import br.gov.ses.fillbpai.util.EnderecoUtils;
import br.gov.ses.fillbpai.util.EspecialidadeUtils;
import br.gov.ses.fillbpai.util.EtniaUtils;
import br.gov.ses.fillbpai.util.NumeroUtils;
import br.gov.ses.fillbpai.util.NomePacienteUtils;
import br.gov.ses.fillbpai.util.RacaUtils;
import br.gov.ses.fillbpai.util.SexoUtils;
import br.gov.ses.fillbpai.util.TelefoneUtils;
import br.gov.ses.fillbpai.util.VigenciaLayoutUtils;
import jakarta.persistence.EntityManager;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * Gerador do arquivo magnético BPA-I, no layout oficial do Ministério da
 * Saúde ({@code data/Layout_Exportacao_BPA_2026.pdf}).
 * <ul>
 *   <li>Header: 132 caracteres + CRLF;</li>
 *   <li>registro: 351 caracteres + CRLF = 353 ({@link #montarRegistro}, seq 1 a 40);</li>
 *   <li>arquivo em ISO-8859-1 (caracteres fora dele são trocados — {@link #sanitizarLatin1}).</li>
 * </ul>
 * Campos NUM opcionais: brancos quando vazios, zeros à esquerda quando
 * preenchidos; campos ALFA: espaços à direita e corte no tamanho do campo.
 * Dados gravados antes das normalizações da importação passam pelas mesmas
 * regras aqui (CBO, CID, sexo, nome, endereço, telefone…).
 */
public class GeradorBPAiService {

	private final EntityManager entityManager;

	public GeradorBPAiService(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	private static final DateTimeFormatter FORMATO_COMPETENCIA =
			DateTimeFormatter.ofPattern("yyyyMM");

	private static final DateTimeFormatter FORMATO_DATA =
			DateTimeFormatter.ofPattern("yyyyMMdd");

	/**
	 * Geração parcial: arquivo BPA-I só com os atendimentos de um médico numa
	 * especialidade, na competência informada (botão "Gerar BPA-I" com um
	 * médico selecionado na árvore). Bloqueia se algum atendimento estiver sem
	 * CNS do profissional ou sem código IBGE, como a geração completa.
	 * <p>
	 * A folha e a linha vêm da numeração da competência inteira
	 * ({@link #numerarFolhas}) — as mesmas da geração completa. Depois de
	 * gravar o arquivo, folha, linha e data/hora da remessa ficam gravadas nos
	 * atendimentos do médico ({@link #registrarRemessa}).
	 *
	 * @param parentWindow           janela dona do diálogo de salvar
	 * @param especialidade          especialidade selecionada
	 * @param medico                 nome do médico selecionado
	 * @param competenciaAtendimento competência no formato AAAAMM do mês de atendimento
	 * @return {@code false} se o usuário cancelou a escolha do arquivo
	 */
	public boolean gerarArquivoComFileChooser(
			Window parentWindow,
			String especialidade,
			String medico,
			String competenciaAtendimento) {

		try {

			int atenAno = Integer.parseInt(competenciaAtendimento.substring(0, 4));
			int atenMes = Integer.parseInt(competenciaAtendimento.substring(4, 6));

			List<AtendimentoBPAi> competenciaInteira = carregarCompetencia(atenAno, atenMes);

			List<AtendimentoBPAi> lista = competenciaInteira.stream()
					.filter(a -> especialidade.equals(a.getEspecialidadeMedico())
							&& a.getMedico() != null && medico.equals(a.getMedico().getNome()))
					.collect(Collectors.toList());

			if (lista.isEmpty())
				throw new RuntimeException(
						"Nenhum registro encontrado para "
						+ medico + " em " + String.format("%02d/%04d", atenMes, atenAno));

			validarCnsProfissional(lista);
			validarIbge(lista);

			String competencia = calcularCompetencia(LocalDate.of(atenAno, atenMes, 1));

			// Mesma numeração da geração completa: calculada sobre a competência inteira
			Map<AtendimentoBPAi, PosicaoFolha> numeracao = numerarFolhas(competenciaInteira);

			FileChooser chooser = new FileChooser();

			chooser.setTitle("Salvar BPA-I");

			chooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo BPA-I (*.txt)", "*.txt"));

			chooser.setInitialFileName(
					especialidade + "_" + medico + "_" + competencia + ".txt"
			);

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return false;

			file = garantirExtensaoTxt(file);

			String conteudo = gerarConteudo(lista, competencia, numeracao);

			Files.writeString(file.toPath(), conteudo, StandardCharsets.ISO_8859_1);

			registrarRemessa(lista, numeracao, java.time.LocalDateTime.now());

			return true;

		} catch (Exception ex) {

			throw new RuntimeException(ex);
		}
	}

	/**
	 * Geração completa: um único arquivo BPA-I com todos os atendimentos da
	 * competência, de todos os médicos e especialidades. Antes de gravar,
	 * bloqueia se algum atendimento estiver sem CNS do profissional
	 * ({@link #validarCnsProfissional}) ou sem código IBGE ({@link #validarIbge}).
	 * <p>
	 * Folhas e linhas por {@link #numerarFolhas}; depois de gravar o arquivo,
	 * ficam gravadas nos atendimentos com a data/hora da remessa
	 * ({@link #registrarRemessa}) — a tabela mostra o que foi enviado.
	 *
	 * @param parentWindow           janela dona do diálogo de salvar
	 * @param competenciaAtendimento competência no formato AAAAMM do mês de atendimento
	 *                               (ex.: "202503" para atendimentos de março de 2025)
	 * @return {@code false} se o usuário cancelou a escolha do arquivo
	 */
	public boolean gerarArquivoCompletoComFileChooser(Window parentWindow, String competenciaAtendimento) {

		try {

			int atenAno = Integer.parseInt(competenciaAtendimento.substring(0, 4));
			int atenMes = Integer.parseInt(competenciaAtendimento.substring(4, 6));

			String competencia = calcularCompetencia(LocalDate.of(atenAno, atenMes, 1));

			List<AtendimentoBPAi> lista = carregarCompetencia(atenAno, atenMes);

			if (lista.isEmpty())
				throw new RuntimeException(
						"Nenhum registro encontrado para a competência " + competencia);

			validarCnsProfissional(lista);
			validarIbge(lista);

			Map<AtendimentoBPAi, PosicaoFolha> numeracao = numerarFolhas(lista);

			FileChooser chooser = new FileChooser();
			chooser.setTitle("Salvar BPA-I Completo");

			chooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo BPA-I (*.txt)", "*.txt"));

			chooser.setInitialFileName("BPA-I_COMPLETO_" + competencia + ".txt");

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return false;

			file = garantirExtensaoTxt(file);

			String conteudo = gerarConteudo(lista, competencia, numeracao);

			Files.writeString(file.toPath(), conteudo, StandardCharsets.ISO_8859_1);

			registrarRemessa(lista, numeracao, java.time.LocalDateTime.now());

			return true;

		} catch (Exception ex) {

			throw new RuntimeException(ex);
		}
	}

	/** Todos os atendimentos da competência (mês da data do atendimento), com as entidades do registro. */
	private List<AtendimentoBPAi> carregarCompetencia(int ano, int mes) {

		return entityManager.createQuery(
						"SELECT a FROM AtendimentoBPAi a " +
								"JOIN FETCH a.paciente p " +
								"LEFT JOIN FETCH p.endereco " +
								"JOIN FETCH a.medico " +
								"LEFT JOIN FETCH a.estabelecimento " +
								"WHERE YEAR(a.dataAgendamento) = :ano " +
								"AND MONTH(a.dataAgendamento) = :mes",
						AtendimentoBPAi.class)
				.setParameter("ano", ano)
				.setParameter("mes", mes)
				.getResultList();
	}

	/** Folha ({@code prd-flh}, 1 a 999) e linha dentro dela ({@code prd-seq}, 1 a 99) de um registro. */
	public record PosicaoFolha(int folha, int linha) {
	}

	/** Linhas por folha no BPA-I ({@code prd-seq}: domínio 01..99). */
	static final int LINHAS_POR_FOLHA = 99;

	/** Maior número de folha do layout ({@code prd-flh}: domínio 001..999). */
	static final int MAXIMO_FOLHAS = 999;

	/**
	 * Numeração das folhas de uma competência (regra única, 20/10/2026 — antes
	 * a geração completa numerava o ano inteiro e a parcial usava a folha
	 * definida à mão):
	 * <ol>
	 *   <li>atendimentos em ordem especialidade → médico → data → hora;</li>
	 *   <li>cada especialidade + médico começa uma folha nova (001, 002…);</li>
	 *   <li>folha nova também a cada 99 linhas (a linha nunca volta para 01
	 *       dentro da mesma folha).</li>
	 * </ol>
	 * Não altera os atendimentos — o resultado só é gravado depois que o
	 * arquivo é salvo ({@link #registrarRemessa}).
	 *
	 * @param competencia todos os atendimentos da competência
	 * @return posição de cada atendimento, na ordem do arquivo
	 * @throws IllegalStateException se a competência passar de 999 folhas
	 */
	static Map<AtendimentoBPAi, PosicaoFolha> numerarFolhas(List<AtendimentoBPAi> competencia) {

		List<AtendimentoBPAi> ordenados = new java.util.ArrayList<>(competencia);
		ordenados.sort(Comparator
				.comparing((AtendimentoBPAi a) -> a.getEspecialidadeMedico() != null ? a.getEspecialidadeMedico() : "")
				.thenComparing(a -> a.getMedico() != null && a.getMedico().getNome() != null ? a.getMedico().getNome() : "")
				.thenComparing(AtendimentoBPAi::getDataAgendamento, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AtendimentoBPAi::getHoraAtendimento, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(AtendimentoBPAi::getId, Comparator.nullsLast(Comparator.naturalOrder())));

		Map<AtendimentoBPAi, PosicaoFolha> numeracao = new LinkedHashMap<>();
		int folha = 0;
		int linha = 0;
		String chaveAnterior = null;

		for (AtendimentoBPAi a : ordenados) {

			String chave = montarChaveMedico(a);

			if (!chave.equals(chaveAnterior) || linha == LINHAS_POR_FOLHA) {
				folha++;
				linha = 0;
				chaveAnterior = chave;
			}

			linha++;
			numeracao.put(a, new PosicaoFolha(folha, linha));
		}

		if (folha > MAXIMO_FOLHAS) {
			throw new IllegalStateException("A competência precisaria de " + folha
					+ " folhas, e o BPA-I aceita no máximo " + MAXIMO_FOLHAS + ".");
		}

		return numeracao;
	}

	/**
	 * Grava nos atendimentos enviados a folha, a linha e a data/hora da
	 * remessa, numa transação — chamado só depois que o arquivo foi salvo.
	 */
	void registrarRemessa(List<AtendimentoBPAi> enviados, Map<AtendimentoBPAi, PosicaoFolha> numeracao,
			java.time.LocalDateTime quando) {

		jakarta.persistence.EntityTransaction tx = entityManager.getTransaction();

		try {
			tx.begin();
			for (AtendimentoBPAi a : enviados) {
				PosicaoFolha posicao = numeracao.get(a);
				a.setFolha(String.valueOf(posicao.folha()));
				a.setLinhaFolha(posicao.linha());
				a.setRemessaEm(quando);
			}
			tx.commit();
		} catch (RuntimeException e) {
			if (tx.isActive()) {
				tx.rollback();
			}
			throw new IllegalStateException("O arquivo foi gerado, mas não foi possível registrar a remessa no banco: "
					+ e.getMessage(), e);
		}
	}

	/** Conteúdo do arquivo numerando as folhas da própria lista (testes). */
	String gerarConteudo(List<AtendimentoBPAi> lista, String competencia) {
		return gerarConteudo(lista, competencia, numerarFolhas(lista));
	}

	/**
	 * Monta o conteúdo do arquivo BPA-I (header + registros) — geração parcial
	 * e completa. Não acessa banco, arquivo nem JavaFX. Os registros saem na
	 * ordem de folha e linha; o header leva o total de linhas e de folhas
	 * distintas do arquivo.
	 *
	 * @param lista      atendimentos que vão para o arquivo
	 * @param competencia competência AAAAMM do header
	 * @param numeracao  folha e linha de cada atendimento ({@link #numerarFolhas})
	 */
	String gerarConteudo(List<AtendimentoBPAi> lista, String competencia,
			Map<AtendimentoBPAi, PosicaoFolha> numeracao) {

		List<AtendimentoBPAi> ordenados = new java.util.ArrayList<>(lista);
		ordenados.sort(Comparator
				.comparingInt((AtendimentoBPAi a) -> numeracao.get(a).folha())
				.thenComparingInt(a -> numeracao.get(a).linha()));

		int totalFolhas = (int) ordenados.stream().map(a -> numeracao.get(a).folha()).distinct().count();

		StringBuilder sb = new StringBuilder();

		sb.append(montarHeader(ordenados, competencia, totalFolhas));
		sb.append("\r\n"); // seq 13 cbc-fim: CR+LF obrigatório conforme layout BPA

		for (AtendimentoBPAi a : ordenados) {
			PosicaoFolha posicao = numeracao.get(a);
			sb.append(montarRegistro(a, competencia, posicao.folha(), posicao.linha()));
			sb.append("\r\n"); // seq 40 prd-fim: CR+LF obrigatório conforme layout BPA-I
		}

		return sb.toString();
	}

	/**
	 * Garante que o arquivo escolhido no FileChooser tenha extensão .txt.
	 * O diálogo nativo de salvar do Windows nem sempre preserva a extensão
	 * do nome inicial sugerido (ex.: ao usar "Todos os arquivos"), então a
	 * extensão é reforçada aqui antes da gravação.
	 */
	private File garantirExtensaoTxt(File file) {

		if (file.getName().toLowerCase().endsWith(".txt"))
			return file;

		return new File(file.getParentFile(), file.getName() + ".txt");
	}

	/**
	 * Monta a chave única para identificar uma combinação especialidade + médico.
	 */
	private static String montarChaveMedico(AtendimentoBPAi a) {

		String esp = a.getEspecialidadeMedico() != null
				? a.getEspecialidadeMedico() : "";

		String med = a.getMedico() != null && a.getMedico().getNome() != null
				? a.getMedico().getNome() : "";

		return esp + "|" + med;
	}

	/**
	 * Bloqueia a geração se algum atendimento estiver sem código IBGE do
	 * município do paciente ({@code prd-ibge}, obrigatório no BPA-I) — mesmo
	 * padrão de {@link #validarCnsProfissional}. Acontece quando nem o nome do
	 * município nem o CEP resolveram o IBGE na importação (decisão de
	 * 08/10/2026 — antes o campo saía em branco sem aviso na geração).
	 *
	 * @throws RuntimeException com a lista de médico/paciente/data dos atendimentos sem IBGE
	 */
	void validarIbge(List<AtendimentoBPAi> lista) {

		List<AtendimentoBPAi> semIbge = lista.stream()
				.filter(a -> {
					Endereco e = a.getPaciente() != null ? a.getPaciente().getEndereco() : null;
					return e == null || e.getCodigoIbge() == null || e.getCodigoIbge().isBlank();
				})
				.collect(java.util.stream.Collectors.toList());

		if (semIbge.isEmpty()) {
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("=== ERRO DE GERAÇÃO BPA-I ===\n");
		sb.append("Geração bloqueada: ").append(semIbge.size())
				.append(" atendimento(s) sem código IBGE do município do paciente.\n");
		sb.append("Corrija o município ou o CEP na planilha e reimporte.\n\n");
		sb.append(String.format("%-30s| %-30s| %s%n", "Médico", "Paciente", "Data"));
		sb.append("------------------------------|------------------------------|----------\n");

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

		for (AtendimentoBPAi a : semIbge) {
			String medNome = a.getMedico() != null ? a.getMedico().getNome() : "(sem médico)";
			String pacNome = a.getPaciente() != null ? a.getPaciente().getNome() : "(sem paciente)";
			String data = a.getDataAgendamento() != null ? a.getDataAgendamento().format(fmt) : "";

			if (medNome.length() > 30) medNome = medNome.substring(0, 27) + "...";
			if (pacNome.length() > 30) pacNome = pacNome.substring(0, 27) + "...";

			sb.append(String.format("%-30s| %-30s| %s%n", medNome, pacNome, data));
		}

		throw new RuntimeException(sb.toString());
	}

	/**
	 * Verifica se todos os atendimentos da lista possuem CNS do profissional preenchido.
	 * Lança RuntimeException com relatório detalhado se houver registros sem CNS.
	 *
	 * @param lista atendimentos a verificar
	 * @throws RuntimeException com log formatado listando os registros sem CNS
	 */
	private void validarCnsProfissional(List<AtendimentoBPAi> lista) {

		List<AtendimentoBPAi> semCns = lista.stream()
				.filter(a -> a.getCnsProfissional() == null || a.getCnsProfissional().isBlank())
				.collect(java.util.stream.Collectors.toList());

		if (semCns.isEmpty()) {
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("=== ERRO DE GERAÇÃO BPA-I ===\n");
		sb.append("Geração bloqueada: ").append(semCns.size())
				.append(" atendimento(s) sem CNS do profissional.\n");
		sb.append("Preencha o CNS antes de gerar o arquivo.\n\n");
		sb.append(String.format("%-30s| %-30s| %s%n", "Médico", "Paciente", "Data"));
		sb.append("------------------------------|------------------------------|----------\n");

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

		for (AtendimentoBPAi a : semCns) {
			String medNome = a.getMedico() != null ? a.getMedico().getNome() : "(sem médico)";
			String pacNome = a.getPaciente() != null ? a.getPaciente().getNome() : "(sem paciente)";
			String data = a.getDataAgendamento() != null ? a.getDataAgendamento().format(fmt) : "";

			// trunca nomes longos para caber na coluna
			if (medNome.length() > 30) medNome = medNome.substring(0, 27) + "...";
			if (pacNome.length() > 30) pacNome = pacNome.substring(0, 27) + "...";

			sb.append(String.format("%-30s| %-30s| %s%n", medNome, pacNome, data));
		}

		throw new RuntimeException(sb.toString());
	}

	/**
	 * Header do arquivo (132 caracteres): competência, total de linhas, total
	 * de folhas distintas do arquivo e campo de controle. Antes a geração
	 * parcial informava o número de linhas como número de folhas.
	 */
	private String montarHeader(List<AtendimentoBPAi> lista, String competencia, int totalFolhas) {

		int totalLinhas = lista.size();
		int somaControle = calcularSomaVerificacao(lista);

		StringBuilder sb = new StringBuilder();

		sb.append("01"); // seq 1 cbc-hdr
		sb.append("#BPA#"); // seq 2 cbc-hdr
		sb.append(padLeftZeros(competencia, 6)); // seq 3 cbc-mvm
		sb.append(padLeftZeros(String.valueOf(totalLinhas), 6)); // seq 4 cbc-lin
		sb.append(padLeftZeros(String.valueOf(totalFolhas), 6)); // seq 5 cbc-flh
		sb.append(padLeftZeros(String.valueOf(somaControle), 4)); // seq 6 cbc-smt-vrf
		sb.append(padRightSpaces(DadosUnidade.ORGAO, 30)); // seq 7
		sb.append(padRightSpaces(DadosUnidade.SIGLA, 6)); // seq 8
		sb.append(padLeftZeros(DadosUnidade.CNPJ, 14)); // seq 9
		sb.append(padRightSpaces(DadosUnidade.DESTINO, 40)); // seq 10
		sb.append(DadosUnidade.INDICADOR_DESTINO); // seq 11
		sb.append(padRightSpaces(DadosUnidade.VERSAO_HEADER, 10)); // seq 12

		return sb.toString();
	}

	/**
	 * Um registro BPA-I (seq 1 a 39, 351 caracteres; o CRLF da seq 40 é
	 * acrescentado por {@link #gerarConteudo}).
	 *
	 * @param a           atendimento do registro
	 * @param competencia competência AAAAMM do arquivo
	 * @param folha       folha do registro ({@code prd-flh}), de {@link #numerarFolhas}
	 * @param linha       linha dentro da folha ({@code prd-seq}), de {@link #numerarFolhas}
	 */
	private String montarRegistro(
			AtendimentoBPAi a,
			String competencia,
			int folha,
			int linha) {

		StringBuilder sb = new StringBuilder();

		Paciente paciente = a.getPaciente();
		Endereco endereco = paciente != null ? paciente.getEndereco() : null;

		String sigtap = somenteNumeros(a.getSigtap());

		String dataAtendimento =
				a.getDataAgendamento().format(FORMATO_DATA);

		LocalDate dataNascimento =
				paciente != null ? paciente.getDataNascimento() : null;

		String dataNascimentoStr =
				dataNascimento != null ?
						dataNascimento.format(FORMATO_DATA) :
						"";

		// Idade na data do atendimento — antes era na data de hoje, e a remessa
		// gerada meses depois saía com um ano a mais para quem fez aniversário
		int idade = DateUtils.idadeNaData(dataNascimento, a.getDataAgendamento());

		/**
		 * seq 1 - prd-ident
		 * posição 001-002
		 */
		sb.append("03");

		/**
		 * seq 2 - prd-cnes
		 * posição 003-009
		 */
		sb.append(padLeftZeros(a.getCnesNts(), 7));

		/**
		 * seq 3 - prd-cmp
		 * posição 010-015 — AAAAMM da data de atendimento do registro
		 */
		sb.append(a.getDataAgendamento().format(FORMATO_COMPETENCIA));

		/**
		 * seq 4 - prd-cnsmed
		 * posição 016-030
		 * NUM, default Brancos
		 */
		sb.append(padNumOpcional(a.getCnsProfissional(), 15));

		/**
		 * seq 5 - prd-cbo
		 */
		// Só dígitos: atendimentos importados antes da normalização podem estar
		// gravados com máscara ("2251-25"), que deslocava o registro
		sb.append(padRightSpaces(CboUtils.normalizar(a.getCboMedico()), 6));

		/**
		 * seq 6 - prd-dtaten
		 */
		sb.append(dataAtendimento);

		/**
		 * seq 7 - prd-flh
		 */
		sb.append(padLeftZeros(String.valueOf(folha), 3));

		/**
		 * seq 8 - prd-seq
		 */
		sb.append(padLeftZeros(String.valueOf(linha), 2));

		/**
		 * seq 9 - prd-pa
		 * NUTRICIONISTA e PSICÓLOGO usam código fixo 0301010315.
		 * Normaliza via EspecialidadeUtils antes de comparar — proteção para
		 * atendimentos já persistidos com prefixo "Médico"/"Médica" (ex.:
		 * "Médico Psicólogo") de antes desta normalização existir na
		 * importação; novas importações já chegam limpas, então esta chamada
		 * é um no-op na prática para dados novos.
		 */
		String prdPa = EspecialidadeUtils.usaProcedimentoFixo(a.getEspecialidadeMedico())
				? "0301010315"
				: sigtap;
		sb.append(padLeftZeros(prdPa, 10));

		/**
		 * seq 10 - prd-cnspac
		 * O paciente é identificado por um documento só: CPF (seq 38) ou CNS
		 * (layout 2026, observação 2; BPA 04.06/04.07 critica os dois juntos).
		 * Com CPF → 15 brancos. Sem CPF ("Paciente sem CPF" = Sim) → o CNS,
		 * obrigatório com 15 dígitos desde a importação (decisão de 09/10/2026).
		 */
		boolean semCpfNoCadastro = paciente == null || paciente.getCpf() == null
				|| paciente.getCpf().isBlank() || CpfUtils.isChaveSemCpf(paciente.getCpf());
		String cnsPaciente = semCpfNoCadastro && paciente != null ? CnsUtils.normalizar(paciente.getCns()) : null;
		sb.append(cnsPaciente != null && cnsPaciente.length() == 15 ? cnsPaciente : padRightSpaces("", 15));

		/**
		 * seq 11 - prd-sexo
		 * ALFA — F (feminino), M (masculino) ou I (indeterminado)
		 */
		// A importação só grava F/M/I (16/10/2026). O "F" fica só como reserva para
		// dados antigos sem sexo ou com valor inválido — nunca vai outro caractere
		String sexo = SexoUtils.normalizar(paciente != null ? paciente.getSexo() : null);
		sb.append(sexo != null ? sexo : "F");

		/**
		 * seq 12 - prd-ibge
		 * Código IBGE do município do paciente (6 posições, numérico).
		 *
		 * O código IBGE padrão tem 7 dígitos. O BPA-I usa 6 posições
		 * (sem o dígito verificador), então truncamos o último dígito.
		 *
		 * Resolvido na importação ({@link br.gov.ses.fillbpai.util.IbgeUtils}:
		 * tabela de MS pelo nome do município, CEPs já conhecidos, APIs de CEP).
		 * A importação não grava linha sem IBGE e {@link #validarIbge} bloqueia
		 * a geração de dados antigos sem ele; os brancos abaixo ficam só como
		 * proteção.
		 */
		String codigoIbge = endereco != null ? endereco.getCodigoIbge() : null;
		// Trunca para 6 dígitos (remove dígito verificador do IBGE)
		if (codigoIbge != null && codigoIbge.length() > 6) {
			codigoIbge = codigoIbge.substring(0, 6);
		}
		sb.append(padNumOpcional(codigoIbge, 6));

		/**
		 * seq 13 - prd-cid
		 */
		sb.append(padRightSpaces(formatarCid(a.getCidConsulta()), 4));

		/**
		 * seq 14 - prd-idade
		 */
		sb.append(padLeftZeros(String.valueOf(idade), 3));

		/**
		 * seq 15 - prd-qt
		 */
		sb.append("000001");

		/**
		 * seq 16 - prd-caten
		 */
		sb.append("01");

		/**
		 * seq 17 - prd-naut
		 */
		sb.append(padRightSpaces("", 13));

		/**
		 * seq 18 - prd-org
		 */
		sb.append("BPA");

		/**
		 * seq 19 - prd-nmpac
		 * Nome completo do paciente
		 */
		// Maiúsculas e espaços reduzidos também para dados antigos (recomendação 29)
		String nomePaciente = paciente != null && paciente.getNome() != null
				? NomePacienteUtils.padronizar(paciente.getNome()) : "";
		sb.append(padRightSpaces(formatarAlfa(nomePaciente, 30), 30));

		/**
		 * seq 20 - prd-dtnasc
		 * NUM, default Brancos
		 */
		sb.append(padNumOpcional(dataNascimentoStr, 8));

		/**
		 * seq 21 - prd-raca
		 */
		String raca = paciente != null ? paciente.getRaca() : null;
		sb.append(formatarRaca(raca));

		/**
		 * seq 22 - prd-etnia
		 */
		// Só para raça Indígena (05): o layout manda brancos nas demais raças,
		// mesmo com a etnia preenchida (vale também para dados já gravados)
		sb.append(RacaUtils.isIndigena(raca)
				? formatarEtnia(paciente != null ? paciente.getEtnia() : null)
				: padRightSpaces("", 4));

		/**
		 * seq 23 - prd-nac
		 * default → "010"
		 */
		sb.append(padRightSpaces("010", 3));  // nacionalidade

		/**
		 * seq 24 - prd-srv
		 */
		sb.append(formatarServico(prdPa));

		/**
		 * seq 25 - prd-clf
		 */
		sb.append(formatarClassificacao(prdPa));    // classificação

		/**
		 * seq 26 - prd-equipe_Seq
		 */
		sb.append(padRightSpaces("", 8));  // equipe seq

		/**
		 * seq 27 - prd-equipe_Area
		 */
		sb.append(padRightSpaces("", 4));  // equipe área

		/**
		 * seq 28 - prd-cnpj
		 */
		sb.append(padRightSpaces("", 14)); // cnpj

		/**
		 * seq 29 - prd-cep_pcnte
		 * NUM, default Brancos
		 */
		String cep = endereco != null ? endereco.getCep() : null;
		sb.append(padNumOpcional(cep, 8));

		/**
		 * seq 30 - prd-lograd_pcnte
		 * NUM, default Brancos
		 */
		String codLogradouro = endereco != null ? endereco.getCodLogradouro() : null;
		sb.append(padNumObrigatorio(codLogradouro, 3));

		/**
		 * seq 31 - prd-end_pcnte
		 */
		String enderecoStr = endereco != null ? endereco.getEndereco() : "";
		sb.append(padRightSpaces(formatarAlfa(enderecoStr, 30), 30));

		/**
		 * seq 32 - prd-compl_pcnte
		 */
		// Mesma normalização da importação (preenchimento → vazio, abreviações),
		// para dados já gravados (16/10/2026)
		String complemento = EnderecoUtils.complemento(endereco != null ? endereco.getComplemento() : null).valor();
		sb.append(padRightSpaces(formatarAlfa(complemento, 10), 10));

		/**
		 * seq 33 - prd-num_pcnte
		 */
		// Vazio ou "sem número" → SN (padrão do layout, decisão de 16/10/2026);
		// a mesma normalização da importação vale para dados antigos
		String numero = NumeroUtils.normalizar(endereco != null ? endereco.getNumero() : null).valor();
		sb.append(padRightSpaces(formatarAlfa(numero, 5), 5));

		/**
		 * seq 34 - prd-bairro_pcnte
		 */
		String bairro = EnderecoUtils.bairro(endereco != null ? endereco.getBairro() : null);
		sb.append(padRightSpaces(formatarAlfa(bairro, 30), 30));

		/**
		 * seq 35 - prd-ddtel_pcnte
		 */
		String telefone = paciente != null ? paciente.getTelefone() : null;
		sb.append(formatarTelefone(telefone));

		/**
		 * seq 36 - prd-email_pcnte
		 */
		sb.append(padRightSpaces("", 40)); // email

		/**
		 * seq 37 - prd-ine
		 * NUM, default Brancos
		 */
		sb.append(padNumOpcional(a.getCodIne(), 10));

		/**
		 * seq 38 - prd_cpf_pcnte
		 * CPF do paciente, 11 chars, numérico
		 */
		// Paciente sem CPF fica no banco com uma chave interna ("SC..."), que
		// não é CPF: sai como CPF vazio (e deriva prd_sem_cpf = S)
		String cpfPacienteNum = paciente != null && !CpfUtils.isChaveSemCpf(paciente.getCpf())
				? somenteNumeros(paciente.getCpf()) : "";
		// Sem CPF: brancos (default do layout). Antes saía "00000000000",
		// CPF que o programa BPA critica (versão 04.03) e descarta o registro.
		sb.append(padNumOpcional(cpfPacienteNum, 11));

		/**
		 * seq "38" duplicado no PDF oficial - prd_situacao_rua
		 * (ver docs/runbook-layout-bpa-2026.html)
		 * ALFA, 1 char, valores: N ou S.
		 * Usa a situação de rua do atendimento (reserva: o valor legado do
		 * paciente, para atendimentos gravados antes de 16/10/2026). Sem
		 * informação → "N"; célula vazia → branco; atendimento de competência
		 * anterior a 12/2024 → branco (o campo não existia — Decisão 26).
		 */
		// Situação de rua do próprio atendimento (16/10/2026); nulo só em
		// atendimentos gravados antes da mudança → valor legado do paciente.
		// null → "N" (coluna ausente); "" → branco (célula vazia)
		String situacaoRua = a.getSituacaoRua() != null ? a.getSituacaoRua()
				: paciente != null ? paciente.getSituacaoRua() : null;
		// Antes da competência 12/2024 o campo não existe → branco (decisão 26)
		if (VigenciaLayoutUtils.isAntesDaSituacaoRua(a.getDataAgendamento())) {
			sb.append(" ");
		} else {
			sb.append(situacaoRua == null ? "N" : situacaoRua.isBlank() ? " " : situacaoRua);
		}

		/**
		 * seq 39 - prd_sem_cpf (novo no layout 2026 — ver
		 * docs/runbook-layout-bpa-2026.html)
		 * ALFA, 1 char, valores: N ou S.
		 * Usa o valor informado na planilha (atendimento.pacienteSemCpf);
		 * planilha sem a coluna → deriva do CPF do paciente (CPF preenchido →
		 * "N"; vazio ou chave interna de paciente sem CPF → "S"); célula
		 * vazia → branco; atendimento de competência anterior a 07/2026 →
		 * branco (o campo não existia — Decisão 26).
		 */
		// null = planilha sem a coluna → deriva do CPF (regra de coluna opcional
		// ausente); "" = coluna presente com a célula vazia → branco (16/10/2026)
		String pacienteSemCpf = a.getPacienteSemCpf();
		if (pacienteSemCpf == null) {
			pacienteSemCpf = cpfPacienteNum.isEmpty() ? "S" : "N";
		} else if (pacienteSemCpf.isBlank()) {
			pacienteSemCpf = " ";
		}
		// Antes da competência 07/2026 o campo não existe → branco (decisão 26)
		if (VigenciaLayoutUtils.isAntesDoSemCpf(a.getDataAgendamento())) {
			pacienteSemCpf = " ";
		}
		sb.append(pacienteSemCpf);

		/**
		 * seq 40 - prd-fim
		 * CRLF acrescentado por quem monta o arquivo (gerarConteudo), depois
		 * de cada registro
		 */

		return sb.toString();
	}

	/**
	 * Campo de controle do header: soma dos códigos de procedimento (SIGTAP,
	 * só dígitos) e das quantidades (1 por registro), em módulo 1111, mais 1111.
	 */
	private int calcularSomaVerificacao(List<AtendimentoBPAi> lista) {

		int soma = 0;

		for (AtendimentoBPAi a : lista) {

			String sigtapNumerico = somenteNumeros(a.getSigtap());

			if (!sigtapNumerico.isEmpty())
				soma += Integer.parseInt(sigtapNumerico);

			soma += 1;
		}

		return (soma % 1111) + 1111;
	}

	/**
	 * Campos NUM opcionais do BPA-I:
	 * - Quando preenchido → apenas números, zeros à esquerda
	 * - Quando vazio → espaços em branco (default "Brancos" do layout)
	 */
	private String padNumOpcional(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		valor = valor.replaceAll("[^0-9]", "");

		if (valor.isEmpty())
			return padRightSpaces("", tamanho);

		return padLeftZeros(valor, tamanho);
	}

	private String padLeftZeros(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		// remove tudo que não for número
		valor = valor.replaceAll("[^0-9]", "");

		if (valor.length() >= tamanho)
			return valor;

		StringBuilder sb = new StringBuilder();

		while (sb.length() + valor.length() < tamanho) {
			sb.append('0');
		}

		sb.append(valor);

		return sb.toString();
	}

	/**
	 * Campo ALFA de tamanho fixo: completa com espaços à direita e <b>corta</b>
	 * o que passar do tamanho. Antes não cortava — um valor maior que o campo
	 * (ex.: CBO "2251-25") deslocava todos os campos seguintes do registro.
	 */
	private String padRightSpaces(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		if (valor.length() > tamanho)
			valor = valor.substring(0, tamanho);

		return String.format("%-" + tamanho + "s", valor);
	}

	private String somenteNumeros(String valor) {

		if (valor == null)
			return "";

		return valor.replaceAll("[^0-9]", "");
	}

	/**
	 * CID-10 do atendimento, sem ponto ({@code I10}, {@code I109}). A
	 * importação já grava só o código; para dados antigos (célula gravada como
	 * veio, ex.: "I10 - Hipertensão") extrai o código — antes cortava o texto
	 * em 4 caracteres e gerava CIDs inexistentes ("I10H"). Sem exatamente um
	 * código: brancos.
	 */
	private String formatarCid(String cid) {

		String codigo = CidUtils.extrair(cid);

		return codigo != null ? codigo : "";
	}

	private String formatarAlfa(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		// remove quebras de linha e caracteres de controle
		valor = valor.replaceAll("[\\r\\n\\t]", " ").trim();

		// substitui caracteres não representáveis em ISO-8859-1 (encoding do arquivo de saída),
		// evitando UnmappableCharacterException ao gravar o arquivo
		valor = sanitizarLatin1(valor);

		// corta se exceder o tamanho máximo
		if (valor.length() > tamanho) {
			valor = valor.substring(0, tamanho);
		}

		return valor;
	}

	/**
	 * Substitui caracteres fora do conjunto ISO-8859-1 (ex.: aspas curvas,
	 * travessão, reticências digitadas via Word/Excel, emojis) por
	 * equivalentes ASCII ou espaço, evitando falha ao gravar o arquivo BPA-I.
	 */
	private String sanitizarLatin1(String valor) {

		if (valor == null || valor.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder(valor.length());

		for (int i = 0; i < valor.length(); i++) {

			char c = valor.charAt(i);

			if (c <= 0xFF) {
				sb.append(c);
				continue;
			}

			switch (c) {
				case '‘': case '’': sb.append('\''); break; // aspas simples curvas
				case '“': case '”': sb.append('"'); break;  // aspas duplas curvas
				case '–': case '—': sb.append('-'); break;  // en-dash / em-dash
				case '…': sb.append("..."); break;               // reticências
				default: sb.append(' ');
			}
		}

		return sb.toString();
	}

	/**
	 * Formata o código de raça/cor via {@link RacaUtils}. Sem código
	 * reconhecido, usa branco — o próprio layout oficial declara "Brancos"
	 * como Default de {@code prd-raca} (campo NUM), o mesmo padrão de todo
	 * campo NUM opcional deste layout; "99 Sem informação" nunca foi esse
	 * default, é (era) só mais uma opção de código, hoje descontinuada.
	 * Esta branch só é alcançada por dados já persistidos antes da
	 * validação bloqueante de raça existir — novas importações nunca
	 * chegam aqui sem raça reconhecida, pois
	 * {@code ValidacaoPlanilhaService}/{@code AtendimentoProcessor} já
	 * bloqueiam a linha antes.
	 */
	private String formatarRaca(String raca) {

		String codigo = RacaUtils.resolverCodigo(raca);

		return codigo != null ? codigo : padRightSpaces("", 2);
	}

	/**
	 * Formata a etnia do paciente (código de 4 caracteres, seq 22 do BPA-I).
	 * Etnia ausente ou não encontrada na tabela oficial (ver {@link EtniaUtils})
	 * é gerada em branco — mesmo padrão do código IBGE não resolvido.
	 */
	private String formatarEtnia(String etnia) {

		if (etnia == null || etnia.isBlank()) {
			return padRightSpaces("", 4);
		}

		String codigo = EtniaUtils.resolver(etnia);

		return codigo != null ? codigo : padRightSpaces("", 4);
	}

	/**
	 * Telefone do paciente (seq 35, 11 posições): DDD + número por
	 * {@link TelefoneUtils}, com espaços à direita; sem DDD ou inválido → brancos.
	 */
	private String formatarTelefone(String telefone) {

		// A importação já grava só DDD + número; para dados antigos (célula
		// gravada como veio) aplica a mesma normalização — antes ficava com os
		// 11 primeiros dígitos ("+55 67 ..." → "5567..."). Sem DDD ou inválido:
		// brancos (campo opcional)
		String numero = TelefoneUtils.normalizar(telefone).numero();

		return padRightSpaces(numero != null ? numero : "", 11);
	}

	/**
	 * Regra de negócio:
	 * SIGTAP 03.01.01.030-7 -> 006
	 * SIGTAP 08.04.01.006-4 -> 009
	 * SIGTAP 03.01.01.031-5 -> 006
	 */
	private static final Map<String, String> MAP_SERVICO = new HashMap<>();

		static {
		MAP_SERVICO.put("0301010307", "160");
		MAP_SERVICO.put("0804010064", "160");
		MAP_SERVICO.put("0301010315", "160");
	}

	private static final Map<String, String> MAP_CLASSIFICACAO = new HashMap<>();

	static {
		MAP_CLASSIFICACAO.put("0301010307", "006");
		MAP_CLASSIFICACAO.put("0804010064", "009");
		MAP_CLASSIFICACAO.put("0301010315", "006");
	}


	private String formatarServico(String sigtap) {

		if (sigtap == null)
			return padRightSpaces("", 3);

		String sigtapNumerico = sigtap.replaceAll("[^0-9]", "");

		String servico = MAP_SERVICO.get(sigtapNumerico);

		if (servico == null || servico.isBlank())
			return padRightSpaces("", 3);

		return padLeftZeros(servico, 3);
	}

	private String formatarClassificacao(String sigtap) {

		if (sigtap == null)
			return padRightSpaces("", 3);

		String sigtapNumerico = sigtap.replaceAll("[^0-9]", "");

		String cls = MAP_CLASSIFICACAO.get(sigtapNumerico);

		if (cls == null || cls.isBlank())
			return padRightSpaces("", 3);

		return padLeftZeros(cls, 3);
	}

	private String calcularCompetencia(LocalDate dataAtendimento) {

		if (dataAtendimento == null)
			throw new RuntimeException("Data do atendimento não pode ser nula");

		return dataAtendimento.format(FORMATO_COMPETENCIA);
	}

	/**
	 * Campo NUM obrigatório: só dígitos, com zeros à esquerda. Hoje usado só
	 * pelo código de logradouro (seq 30): valor nulo vira {@code 081} (Rua —
	 * decisão de negócio de 11/09/2026); valor sem nenhum dígito vira zeros.
	 */
	private String padNumObrigatorio(String valor, int tamanho) {

		if (valor == null)
			valor = "081";

		valor = valor.replaceAll("[^0-9]", "");

		if (valor.isEmpty())
			valor = "0";

		return padLeftZeros(valor, tamanho);
	}

}
