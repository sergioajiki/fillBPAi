package br.gov.ses.fillbpai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import br.gov.ses.fillbpai.service.ErroValidacao.Severidade;

/**
 * Organiza os resultados de {@link ValidacaoPlanilhaService} para exibição:
 * agrupa por tipo (com título legível e explicação uma única vez) e, dentro do
 * tipo, junta as ocorrências com o mesmo {@link ErroValidacao#valor()} numa
 * linha só, com a lista de linhas da planilha.
 * <p>
 * Usado pela tela do "Analisar Planilha" ({@code MainController}) e pelo log
 * TXT ({@link ValidacaoPlanilhaService#gerarLogTxt}), para os dois mostrarem a
 * mesma organização.
 */
public final class AgrupamentoValidacao {

	private AgrupamentoValidacao() {
	}

	/** Título legível, explicação fixa e ordem de exibição de um tipo de erro/aviso. */
	public record InfoTipo(String titulo, String explicacao, int prioridade) {
	}

	/** Uma linha dentro do grupo: o valor (ou o detalhe) e as linhas da planilha onde ocorre. */
	public record Item(String texto, List<Integer> linhas) {
	}

	/** Todas as ocorrências de um tipo. */
	public record Grupo(String tipo, Severidade severidade, String titulo, String explicacao,
			int ocorrencias, List<Item> itens) {
	}

	/**
	 * Prioridade: ERRO antes de AVISO (ver {@link #agrupar}); dentro da mesma
	 * severidade, o que trava a geração do BPA-I vem primeiro, depois o que
	 * muda dado gravado, por último o informativo.
	 */
	private static final Map<String, InfoTipo> TIPOS = Map.ofEntries(

			// ERROS (bloqueiam a importação)
			Map.entry(ErroValidacao.ESTRUTURA_INVALIDA, new InfoTipo(
					"Estrutura da planilha",
					"Coluna obrigatória ausente ou nome de coluna ambíguo no cabeçalho.", 0)),
			Map.entry(ErroValidacao.DATA_AUSENTE, new InfoTipo(
					"Data de agendamento não informada",
					"É a data do atendimento: vai para o BPA-I e define a competência. Campo obrigatório.", 1)),
			Map.entry(ErroValidacao.DATA_INVALIDA, new InfoTipo(
					"Data de agendamento em formato não reconhecido",
					"Use dd/MM/aaaa (ex.: 25/12/2024), ou formate a coluna como data no Excel.", 2)),
			Map.entry(ErroValidacao.TIPO_SERVICO_AUSENTE, new InfoTipo(
					"Tipo de serviço não informado",
					"Define o procedimento (SIGTAP) do BPA-I. Aceitos: Teleconsulta, Teleinterconsulta."
							+ " Nutricionista e psicólogo podem ficar sem tipo (procedimento fixo).", 3)),
			Map.entry(ErroValidacao.TIPO_SERVICO_INVALIDO, new InfoTipo(
					"Tipo de serviço não reconhecido",
					"Aceitos: Teleconsulta, Teleinterconsulta (sem diferença de maiúsculas).", 4)),
			Map.entry(ErroValidacao.CPF_AUSENTE, new InfoTipo(
					"CPF do paciente não informado",
					"Campo obrigatório. Corrija a planilha antes de importar.", 5)),
			Map.entry(ErroValidacao.CPF_INVALIDO, new InfoTipo(
					"CPF do paciente com tamanho inválido",
					"O CPF deve ter 11 dígitos.", 6)),
			Map.entry(ErroValidacao.CPF_MEDICO_AUSENTE, new InfoTipo(
					"CPF do médico não informado",
					"O CPF identifica o médico na importação. Campo obrigatório.", 7)),
			Map.entry(ErroValidacao.CPF_MEDICO_INVALIDO, new InfoTipo(
					"CPF do médico com tamanho inválido",
					"O CPF deve ter 11 dígitos.", 8)),
			Map.entry(ErroValidacao.CBO_AUSENTE, new InfoTipo(
					"CBO do médico não informado",
					"O CBO vai para o BPA-I (6 dígitos, ex.: 225125). Campo obrigatório.", 8)),
			Map.entry(ErroValidacao.CBO_INVALIDO, new InfoTipo(
					"CBO do médico inválido",
					"O CBO deve ter 6 dígitos (ex.: 225125). Máscara como 2251-25 é aceita.", 8)),
			Map.entry(ErroValidacao.ESPECIALIDADE_AUSENTE, new InfoTipo(
					"Especialidade não informada",
					"Organiza a árvore da tela e a folha do BPA-I; sem ela o atendimento não aparece na"
							+ " árvore. Campo obrigatório.", 8)),
			Map.entry(ErroValidacao.CEP_AUSENTE, new InfoTipo(
					"CEP não informado",
					"Campo obrigatório no endereço do paciente.", 9)),
			Map.entry(ErroValidacao.CEP_INVALIDO, new InfoTipo(
					"CEP com tamanho inválido",
					"O CEP deve ter 8 dígitos.", 10)),
			Map.entry(ErroValidacao.RACA_AUSENTE, new InfoTipo(
					"Raça do paciente não informada",
					"Campo obrigatório no BPA-I.", 11)),
			Map.entry(ErroValidacao.RACA_INVALIDA, new InfoTipo(
					"Raça do paciente não reconhecida",
					"Verifique a grafia. Aceito: Branca, Preta, Parda, Amarela, Indígena.", 12)),

			// AVISOS (não bloqueiam)
			Map.entry(ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO, new InfoTipo(
					"CNS do profissional não cadastrado",
					"Sem CNS do profissional, a geração do BPA-I fica bloqueada. Cadastre o médico (ou a grafia"
							+ " como apelido) em Configurações → CNS de Médicos. Quando o mesmo CPF já tem CNS,"
							+ " ele é herdado (indicado no item).", 10)),
			Map.entry(ErroValidacao.HORA_INVALIDA, new InfoTipo(
					"Horário de atendimento não reconhecido",
					"Como a remessa BPA-I não utiliza o horário, o atendimento será importado com o horário"
							+ " vazio. Para gravar o horário, use HH:mm (ex.: 08:30).", 20)),
			Map.entry(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO, new InfoTipo(
					"Estabelecimento sem código",
					"Formato esperado: \"código - nome\". Será vinculado por nome a um estabelecimento já"
							+ " cadastrado; se não houver, o atendimento fica sem estabelecimento.", 21)),
			Map.entry(ErroValidacao.ESTABELECIMENTO_AUSENTE, new InfoTipo(
					"Estabelecimento não informado",
					"Célula vazia, só com \"-\" ou com código 0. O atendimento será importado sem"
							+ " estabelecimento (não afeta o BPA-I, só a tabela e os relatórios).", 21)),
			Map.entry(ErroValidacao.ESTABELECIMENTO_SEM_NOME, new InfoTipo(
					"Estabelecimento só com o código",
					"Será vinculado se o código já estiver cadastrado, mantendo o nome cadastrado; caso"
							+ " contrário, o atendimento fica sem estabelecimento. Use \"código - nome\" para"
							+ " cadastrar um estabelecimento novo.", 21)),
			Map.entry(ErroValidacao.TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO, new InfoTipo(
					"Tipo de serviço vazio (procedimento fixo)",
					"Nutricionista e psicólogo usam o procedimento fixo 0301010315, então a linha será"
							+ " importada. A coluna Tipo de Serviço veio vazia; preencha se quiser o registro"
							+ " completo na planilha.", 22)),
			Map.entry(ErroValidacao.SITUACAO_RUA_INVALIDA, new InfoTipo(
					"Situação de rua não reconhecida",
					"Use Sim/Não ou S/N. Será enviado 'N' na remessa.", 22)),
			Map.entry(ErroValidacao.PACIENTE_SEM_CPF_INVALIDO, new InfoTipo(
					"\"Paciente sem CPF\" não reconhecido",
					"Use Sim/Não ou S/N. O valor será derivado do CPF do paciente.", 23)),
			Map.entry(ErroValidacao.RACA_INDIGENA, new InfoTipo(
					"Raça indígena sem etnia",
					"Para raça Indígena, preencha a etnia do paciente.", 24)),
			Map.entry(ErroValidacao.ETNIA_NAO_ENCONTRADA, new InfoTipo(
					"Etnia não encontrada",
					"Etnia não encontrada na tabela oficial. Verifique o texto informado.", 25)),
			Map.entry(ErroValidacao.CNS_INVALIDO, new InfoTipo(
					"CNS do paciente ausente ou incompleto",
					"Menos de 15 dígitos ou não informado. Não impede a importação.", 30)),
			Map.entry(ErroValidacao.CNS_INCOMUM, new InfoTipo(
					"CNS do paciente com formato incomum",
					"Mais de 15 dígitos. Não impede a importação.", 31)),
			Map.entry(ErroValidacao.COLUNA_OPCIONAL_AUSENTE, new InfoTipo(
					"Coluna opcional ausente", "", 40)),
			Map.entry(ErroValidacao.FORMATO_LEGADO_ESPECIALIDADE_MEDICO, new InfoTipo(
					"Formato legado (Especialidade/Médico)", "", 41)));

	/** Título, explicação e prioridade do tipo; tipo desconhecido usa o próprio código como título. */
	public static InfoTipo info(String tipo) {
		return TIPOS.getOrDefault(tipo, new InfoTipo(tipo, "", 99));
	}

	/**
	 * Agrupa por tipo, na ordem: ERRO antes de AVISO, depois pela prioridade
	 * do tipo. Dentro do grupo, ocorrências com o mesmo valor viram um item
	 * só (linhas em ordem crescente); sem valor, cada ocorrência é um item
	 * com o detalhe completo. Itens ordenados pela primeira linha.
	 */
	public static List<Grupo> agrupar(List<ErroValidacao> erros) {

		Map<String, List<ErroValidacao>> porTipo = erros.stream()
				.collect(Collectors.groupingBy(ErroValidacao::tipoErro, LinkedHashMap::new, Collectors.toList()));

		List<Grupo> grupos = new ArrayList<>();

		for (Map.Entry<String, List<ErroValidacao>> entry : porTipo.entrySet()) {

			List<ErroValidacao> ocorrencias = entry.getValue();
			InfoTipo info = info(entry.getKey());

			// ERRO prevalece se o mesmo tipo vier com severidades diferentes
			Severidade severidade = ocorrencias.stream().anyMatch(ErroValidacao::isBloqueante)
					? Severidade.ERRO : Severidade.AVISO;

			grupos.add(new Grupo(entry.getKey(), severidade, info.titulo(), info.explicacao(),
					ocorrencias.size(), montarItens(ocorrencias)));
		}

		grupos.sort(Comparator
				.comparing((Grupo g) -> g.severidade() == Severidade.ERRO ? 0 : 1)
				.thenComparingInt(g -> info(g.tipo()).prioridade())
				.thenComparing(Grupo::titulo));

		return grupos;
	}

	private static List<Item> montarItens(List<ErroValidacao> ocorrencias) {

		Map<String, List<Integer>> linhasPorValor = new LinkedHashMap<>();
		List<Item> itens = new ArrayList<>();

		for (ErroValidacao e : ocorrencias) {
			if (e.valor() != null) {
				linhasPorValor.computeIfAbsent(e.valor(), k -> new ArrayList<>()).add(e.linha());
			} else {
				itens.add(new Item(e.detalhe(), List.of(e.linha())));
			}
		}

		linhasPorValor.forEach((valor, linhas) ->
				itens.add(new Item(valor, linhas.stream().sorted().distinct().toList())));

		itens.sort(Comparator.comparingInt(i -> i.linhas().get(0)));

		return itens;
	}

	/** "1 linha: 5" ou "3 linhas: 2, 7, 9" (até 10 números, depois reticências). */
	public static String descreverLinhas(List<Integer> linhas) {

		String lista = linhas.stream().limit(10).map(String::valueOf).collect(Collectors.joining(", "));

		if (linhas.size() > 10) {
			lista += ", ...";
		}

		return linhas.size() + (linhas.size() == 1 ? " linha: " : " linhas: ") + lista;
	}

	/** Item formatado para texto: {@code valor — N linhas: ...}. */
	public static String formatarItem(Item item) {
		return item.texto() + " — " + descreverLinhas(item.linhas());
	}
}
