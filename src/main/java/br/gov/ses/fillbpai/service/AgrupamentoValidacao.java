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
			Map.entry(ErroValidacao.LINHA_ILEGIVEL, new InfoTipo(
					"Linha não pôde ser lida",
					"Falha inesperada ao ler as células da linha. Confira se há células com conteúdo"
							+ " estranho (imagem, erro de fórmula, texto muito longo) e salve a planilha de novo.", 0)),
			Map.entry(ErroValidacao.LINHA_DUPLICADA, new InfoTipo(
					"Linha repetida",
					"Mesmo paciente, médico, data, procedimento e horário de outra linha: as duas virariam"
							+ " um único atendimento e uma linha seria perdida. Remova a repetição ou corrija o"
							+ " horário.", 1)),
			Map.entry(ErroValidacao.ESPECIALIDADE_MEDICO_SEM_SEPARADOR, new InfoTipo(
					"Especialidade/Médico sem \"ESPECIALIDADE - NOME\"",
					"A planilha não tem coluna \"Especialidade\", então a coluna \"Especialidade/Médico\" foi lida"
							+ " no formato antigo, com os dois valores na mesma célula. Estas células não trazem o"
							+ " separador: o nome do médico viraria a especialidade. Se a especialidade está em outra"
							+ " coluna, coloque o cabeçalho \"Especialidade\" nela.", 8)),
			Map.entry(ErroValidacao.COLUNA_SEM_CABECALHO, new InfoTipo(
					"Coluna sem cabeçalho",
					"Coluna com dados, mas com o cabeçalho vazio — não é lida na importação. A correção é na"
							+ " planilha: escreva o nome da coluna no cabeçalho, salve e analise de novo. Não se"
							+ " resolve em Configurações, porque a coluna não tem nome para cadastrar como alias.", 15)),
			Map.entry(ErroValidacao.CNS_OBRIGATORIO_SEM_CPF, new InfoTipo(
					"Paciente sem CPF sem CNS válido",
					"Com \"Paciente sem CPF\" = Sim, o CNS é o documento que identifica o paciente no BPA-I e"
							+ " precisa ter 15 dígitos com dígito verificador válido. Preencha ou confira o CNS do"
							+ " paciente.", 6)),
			Map.entry(ErroValidacao.PACIENTE_AUSENTE, new InfoTipo(
					"Nome do paciente não informado",
					"Campo obrigatório no BPA-I. Preencha o nome do paciente.", 5)),
			Map.entry(ErroValidacao.DATA_NASCIMENTO_INVALIDA, new InfoTipo(
					"Data de nascimento em formato não reconhecido",
					"Use dd/MM/aaaa (ex.: 25/12/1980), ou formate a coluna como data no Excel. Data inexistente"
							+ " (ex.: 31/02) também é recusada.", 5)),
			Map.entry(ErroValidacao.SEXO_AUSENTE, new InfoTipo(
					"Sexo do paciente não informado",
					"Campo obrigatório no BPA-I. Use F (Feminino), M (Masculino) ou I (Indeterminado).", 11)),
			Map.entry(ErroValidacao.SEXO_INVALIDO, new InfoTipo(
					"Sexo do paciente não reconhecido",
					"Aceitos: F, M, I, Feminino, Masculino, Indeterminado, Fem, Masc, Mulher, Homem.", 11)),
			Map.entry(ErroValidacao.DATA_NASCIMENTO_SEM_ANO, new InfoTipo(
					"Data de nascimento fora do formato correto",
					"A data não está no formato correto (dd/MM/aaaa).", 5)),
			Map.entry(ErroValidacao.DATA_NASCIMENTO_AUSENTE, new InfoTipo(
					"Data de nascimento não informada",
					"Sem a data de nascimento, a idade sairia 000 no BPA-I. Preencha a data de nascimento.", 5)),
			Map.entry(ErroValidacao.DATA_NASCIMENTO_INCOERENTE, new InfoTipo(
					"Data de nascimento não confere com o atendimento",
					"Nascimento depois da data do atendimento, ou idade acima de 130 anos (limite do BPA-I)."
							+ " Confira a data de nascimento e a do atendimento.", 5)),
			Map.entry(ErroValidacao.CID_INVALIDO, new InfoTipo(
					"CID da consulta inválido",
					"A célula precisa ter um código CID-10 — letra + 2 dígitos, com subcategoria opcional"
							+ " (ex.: I10, I10.9). Pode vir com a descrição (\"I10 - Hipertensão\"), mas só um CID"
							+ " por célula.", 8)),
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
					"Campo obrigatório. Para paciente sem CPF, marque a coluna \"Paciente sem CPF\" = Sim.", 5)),
			Map.entry(ErroValidacao.CPF_INVALIDO, new InfoTipo(
					"CPF do paciente com tamanho inválido",
					"O CPF deve ter 11 dígitos.", 6)),
			Map.entry(ErroValidacao.CPF_FALSO, new InfoTipo(
					"CPF do paciente falso (dígitos repetidos)",
					"CPF como 00000000000 ou 11111111111 juntaria pacientes diferentes num só. Para paciente"
							+ " sem CPF, deixe o CPF vazio e marque a coluna \"Paciente sem CPF\" = Sim.", 6)),
			Map.entry(ErroValidacao.CPF_CONFLITO_SEM_CPF, new InfoTipo(
					"CPF preenchido com \"Paciente sem CPF\" = Sim",
					"As duas informações se contradizem. Se o paciente tem CPF, marque \"Paciente sem CPF\" = Não;"
							+ " se não tem, deixe o CPF vazio.", 6)),
			Map.entry(ErroValidacao.CPF_DV_INVALIDO, new InfoTipo(
					"CPF do paciente inválido (dígito verificador)",
					"Os dígitos verificadores não conferem: algum dígito foi digitado errado. Um CPF errado pode"
							+ " coincidir com o de outro paciente e juntar os dois.", 6)),
			Map.entry(ErroValidacao.CPF_MEDICO_DV_INVALIDO, new InfoTipo(
					"CPF do médico inválido",
					"Dígitos repetidos ou verificadores que não conferem. Um CPF errado cria um segundo"
							+ " cadastro do mesmo médico, com folha própria no BPA-I.", 8)),
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
			Map.entry(ErroValidacao.MUNICIPIO_AUSENTE, new InfoTipo(
					"Município não informado na planilha",
					"A coluna do município veio vazia. O município é usado para achar o código IBGE"
							+ " (obrigatório no BPA-I). Preencha o município na planilha.", 9)),
			Map.entry(ErroValidacao.MUNICIPIO_NAO_ENCONTRADO, new InfoTipo(
					"Município e CEP não encontrados",
					"O município não foi encontrado na tabela de MS e o CEP também não foi encontrado (CEPs já"
							+ " importados e consulta na internet), então não há como obter o código IBGE"
							+ " (obrigatório no BPA-I). Verifique se a grafia do município e o CEP estão"
							+ " corretos.", 9)),
			Map.entry(ErroValidacao.MUNICIPIO_PELO_CEP, new InfoTipo(
					"Município identificado pelo CEP",
					"O município da planilha não está na tabela de MS; o código IBGE foi obtido pelo CEP. Cada"
							+ " item mostra o município da planilha → o município que o CEP indica. Confira se é"
							+ " o mesmo: se não for, o CEP ou o município estão errados na planilha.", 11)),
			Map.entry(ErroValidacao.SUSPEITA_DUPLICIDADE, new InfoTipo(
					"Suspeita de atendimento duplicado",
					"Mesmo paciente, médico, data e procedimento de outra linha, mas com horário diferente."
							+ " As duas linhas serão importadas como atendimentos separados — confira se não é"
							+ " uma repetição na planilha.", 12)),
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
					"Para raça Indígena a etnia é obrigatória no BPA-I — sem ela o registro seria descartado."
							+ " Preencha a etnia do paciente.", 12)),
			Map.entry(ErroValidacao.ETNIA_NAO_ENCONTRADA, new InfoTipo(
					"Grafia da etnia não reconhecida",
					"A grafia da etnia não foi reconhecida.", 12)),
			Map.entry(ErroValidacao.DATA_ATENDIMENTO_SEM_ANO, new InfoTipo(
					"Data do atendimento fora do formato correto",
					"A data não está no formato correto (dd/MM/aaaa).", 19)),
			Map.entry(ErroValidacao.CEP_FALSO, new InfoTipo(
					"CEP de preenchimento",
					"CEP com todos os dígitos iguais (ex.: 00000000) não é um CEP de verdade. Informe o CEP do"
							+ " endereço do paciente.", 10)),
			Map.entry(ErroValidacao.NUMERO_FORA_DO_PADRAO, new InfoTipo(
					"Número do endereço fora do padrão",
					"O número deve ser o número do imóvel (ex.: 100 ou 12A, até 5 caracteres) ou uma forma de \"sem"
							+ " número\" (S/N, SN, SEM NUMERO, 0). Outros valores vão como estão; com mais de 5"
							+ " caracteres vai o número do começo (ex.: \"100 apto 201\" → 100) ou SN.", 27)),
			Map.entry(ErroValidacao.CEP_AUSENTE_SITUACAO_RUA, new InfoTipo(
					"Paciente em situação de rua sem CEP",
					"O paciente está marcado em situação de rua e não tem CEP: o CEP será registrado em branco. O"
							+ " município continua obrigatório (é dele que vem o código IBGE).", 22)),
			Map.entry(ErroValidacao.NOME_PACIENTE_LONGO, new InfoTipo(
					"Nome do paciente maior que 30 caracteres",
					"O BPA-I aceita até 30 caracteres no nome: o resto é cortado. O item mostra como o nome vai"
							+ " sair; se preferir, abrevie na planilha (ex.: sobrenomes do meio).", 28)),
			Map.entry(ErroValidacao.NOME_PACIENTE_SUSPEITO, new InfoTipo(
					"Nome do paciente suspeito",
					"Nome sem letras, com números, símbolos, uma palavra só ou de recém-nascido sem nome (\"RN DE …\")."
							+ " Pode ser preenchimento de teste ou erro de digitação — confira na planilha. Não impede"
							+ " a importação.", 27)),
			Map.entry(ErroValidacao.DATA_FORA_DO_ESPERADO, new InfoTipo(
					"Data do atendimento futura ou muito antiga",
					"A data do atendimento é depois de hoje ou tem mais de 2 anos. Em geral é o ano digitado"
							+ " errado (ex.: 2062 em vez de 2026), e o atendimento cairia numa competência errada."
							+ " Se a data estiver certa, pode importar.", 9)),
			Map.entry(ErroValidacao.CPF_COM_NOMES_DIFERENTES, new InfoTipo(
					"Mesmo CPF (ou CNS) com nomes diferentes",
					"O CPF identifica o paciente (sem CPF, o CNS): linhas com o mesmo documento e nomes diferentes"
							+ " viram um só cadastro, com o nome da última linha importada. Confira na planilha se o"
							+ " documento ou o nome foi"
							+ " digitado errado. Diferenças só de acento, maiúsculas ou espaços não contam.", 12)),
			Map.entry(ErroValidacao.CAMPO_ANTES_DA_VIGENCIA, new InfoTipo(
					"Campo ainda não existia na competência",
					"O layout do BPA-I só tem o campo \"Situação de Rua\" a partir da competência 12/2024 e o"
							+ " campo \"Paciente sem CPF\" a partir de 07/2026. Em atendimentos de competência"
							+ " anterior o \"Sim\" da planilha é guardado, mas o campo vai em branco no arquivo.", 29)),
			Map.entry(ErroValidacao.SITUACAO_RUA_VAZIA, new InfoTipo(
					"Situação de rua vazia",
					"A coluna \"Situação de Rua\" existe, mas a célula está vazia. Será importada em branco (sem"
							+ " informação). Preencha com Sim ou Não se souber.", 23)),
			Map.entry(ErroValidacao.PACIENTE_SEM_CPF_VAZIO, new InfoTipo(
					"\"Paciente sem CPF\" vazio",
					"A coluna \"Paciente sem CPF\" existe, mas a célula está vazia. Será importada em branco (sem"
							+ " informação). Preencha com Não quando o paciente tem CPF.", 23)),
			Map.entry(ErroValidacao.COMPLEMENTO_CORTADO, new InfoTipo(
					"Complemento maior que 10 caracteres",
					"O BPA-I aceita até 10 caracteres no complemento. As palavras comuns são abreviadas (Apartamento"
							+ " → AP, Bloco → BL, Casa → CS, Quadra → QD, Lote → LT) e o resto é cortado na última"
							+ " palavra inteira. Se perder algo importante, abrevie na planilha.", 28)),
			Map.entry(ErroValidacao.BAIRRO_IGUAL_MUNICIPIO, new InfoTipo(
					"Bairro igual ao município",
					"O bairro tem o mesmo nome do município do paciente — provavelmente a cidade foi digitada na"
							+ " coluna do bairro. Confira a planilha.", 28)),
			Map.entry(ErroValidacao.CEP_FORA_DE_MS, new InfoTipo(
					"CEP não confere com o município",
					"O município é de Mato Grosso do Sul, mas o CEP não é (os CEPs de MS começam com 79). Confira"
							+ " o CEP e o município na planilha.", 18)),
			Map.entry(ErroValidacao.LOGRADOURO_NAO_RECONHECIDO, new InfoTipo(
					"Tipo de logradouro não reconhecido",
					"A coluna do tipo de logradouro deve ter o código ou o nome do tipo (ex.: 081 ou Rua, 008 ou"
							+ " Avenida). Se não for corrigido, será usado 081 (Rua).", 27)),
			Map.entry(ErroValidacao.TELEFONE_SEM_DDD, new InfoTipo(
					"Telefone fora do padrão",
					"O telefone deve ter DDD + número (ex.: 67 99999-9999 ou 67 3333-3333). Se não for corrigido,"
							+ " o telefone será gerado em branco no BPA-I.", 27)),
			Map.entry(ErroValidacao.TELEFONE_INVALIDO, new InfoTipo(
					"Telefone não reconhecido",
					"Esperado DDD + número (10 ou 11 dígitos). O BPA-I será gerado com o telefone em branco."
							+ " Com mais de um telefone na célula, vale o primeiro.", 27)),
			Map.entry(ErroValidacao.CID_AUSENTE, new InfoTipo(
					"CID da consulta não informado",
					"O BPA-I será gerado com o CID em branco. Preencha o CID se o procedimento exigir.", 26)),
			Map.entry(ErroValidacao.CNS_INVALIDO, new InfoTipo(
					"CNS do paciente sem 15 dígitos",
					"O CNS tem 15 dígitos: com menos ou mais, o valor não é gravado (o CNS que o paciente já tem"
							+ " no cadastro é mantido). Não impede a importação.", 30)),
			Map.entry(ErroValidacao.CNS_DV_INVALIDO, new InfoTipo(
					"CNS do paciente com dígito verificador inválido",
					"O CNS tem 15 dígitos, mas o dígito verificador não confere — provavelmente um dígito foi"
							+ " digitado errado.", 30)),
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
