package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.util.ColunaAliasUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import br.gov.ses.fillbpai.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolve, a partir da linha de cabeçalho de uma planilha, qual índice de
 * coluna corresponde a cada campo canônico — independente da ordem em que
 * as colunas aparecem, e ignorando colunas extras não reconhecidas.
 * <p>
 * A resolução de nome usa {@link ColunaAliasUtils}, que já combina os
 * aliases padrão (classpath) com os aliases locais desta instalação.
 */
public class PlanilhaColumnMapper {

	/**
	 * Campos canônicos cuja ausência no cabeçalho não bloqueia a importação —
	 * ao contrário dos demais campos, tratados como obrigatórios por padrão.
	 * <p>
	 * {@code COD_LOGRADOURO}: o código do tipo de logradouro é, na prática,
	 * derivado automaticamente a partir do prefixo do texto do endereço
	 * (ver {@link br.gov.ses.fillbpai.util.LogradouroUtils}) — a coluna da
	 * planilha é só um fallback secundário, raramente preenchida. Na geração
	 * do BPA-I, {@code GeradorBPAiService.padNumObrigatorio} já garante um
	 * default válido ("081" — Rua) quando nenhum código é resolvido, então a
	 * ausência desta coluna nunca produz um registro inválido.
	 */
	private static final Set<String> CAMPOS_OPCIONAIS =
			Set.of("COD_LOGRADOURO", "SITUACAO_RUA", "PACIENTE_SEM_CPF");

	/**
	 * Resultado do mapeamento de um cabeçalho.
	 *
	 * @param indices                  campo canônico → índice da coluna (0-based)
	 * @param camposFaltando           campos obrigatórios não encontrados no cabeçalho
	 * @param camposOpcionaisFaltando  campos opcionais ({@link #CAMPOS_OPCIONAIS}) não
	 *                                 encontrados no cabeçalho — não impedem a importação
	 * @param camposDuplicados         campos que casaram com mais de uma coluna do cabeçalho
	 * @param colunasNaoReconhecidas   texto original das colunas do cabeçalho que não
	 *                                 bateram com nenhum alias cadastrado — candidatas a
	 *                                 corresponder a um campo obrigatório ausente, se o
	 *                                 nome da coluna na planilha for diferente do esperado
	 * @param colunasPorCampoDuplicado para cada campo em {@code camposDuplicados}, o texto
	 *                                 original de todas as colunas do cabeçalho que casaram
	 *                                 com ele — útil para apontar exatamente qual par de
	 *                                 colunas está em conflito
	 * @param especialidadeMedicoCombinados true quando a planilha não tem coluna própria
	 *                                 para ESPECIALIDADE_MEDICO mas tem uma para MEDICO —
	 *                                 sinal de planilha legado, onde uma única coluna
	 *                                 "Especialidade/Médico" traz os dois valores
	 *                                 combinados ("ESPECIALIDADE - NOME"). Nesse caso os
	 *                                 dois campos apontam para o mesmo índice de coluna.
	 */
	public record ResultadoMapeamento(
			Map<String, Integer> indices,
			List<String> camposFaltando,
			List<String> camposOpcionaisFaltando,
			List<String> camposDuplicados,
			List<String> colunasNaoReconhecidas,
			Map<String, List<String>> colunasPorCampoDuplicado,
			boolean especialidadeMedicoCombinados,
			List<String> colunasSemCabecalho) {

		/** Sem informação de colunas sem cabeçalho (mapeamento só pelo cabeçalho). */
		public ResultadoMapeamento(Map<String, Integer> indices, List<String> camposFaltando,
				List<String> camposOpcionaisFaltando, List<String> camposDuplicados,
				List<String> colunasNaoReconhecidas, Map<String, List<String>> colunasPorCampoDuplicado,
				boolean especialidadeMedicoCombinados) {
			this(indices, camposFaltando, camposOpcionaisFaltando, camposDuplicados, colunasNaoReconhecidas,
					colunasPorCampoDuplicado, especialidadeMedicoCombinados, List.of());
		}

		/** true se todos os campos obrigatórios foram encontrados, sem ambiguidade. */
		public boolean estruturaValida() {
			return camposFaltando.isEmpty() && camposDuplicados.isEmpty();
		}
	}

	/**
	 * Mapeia a linha de cabeçalho informada. Colunas cujo texto não bate com
	 * nenhum alias cadastrado são ignoradas — é assim que uma coluna extra
	 * não usada deixa de quebrar a leitura das demais.
	 */
	public ResultadoMapeamento mapear(Row cabecalho) {

		Map<String, Integer> indices = new LinkedHashMap<>();
		Map<String, List<String>> colunasPorCampo = new LinkedHashMap<>();
		List<String> naoReconhecidas = new ArrayList<>();

		if (cabecalho != null) {

			for (Cell celula : cabecalho) {

				String texto = extrairTexto(celula);
				String campo = ColunaAliasUtils.resolverCampo(texto);

				if (campo == null) {
					// Coluna não reconhecida por nenhum alias — ignorada da leitura,
					// mas registrada como candidata para exibição em diagnósticos.
					if (texto != null && !texto.isBlank()) {
						naoReconhecidas.add(texto.trim());
					}
					continue;
				}

				String textoOriginal = texto != null ? texto.trim() : "";
				colunasPorCampo.computeIfAbsent(campo, k -> new ArrayList<>()).add(textoOriginal);

				if (!indices.containsKey(campo)) {
					indices.put(campo, celula.getColumnIndex());
				}
			}
		}

		// Um campo é duplicado quando mais de uma coluna do cabeçalho casou com ele —
		// guardamos também os nomes originais dessas colunas para apontar o conflito.
		List<String> duplicados = new ArrayList<>();
		Map<String, List<String>> colunasPorCampoDuplicado = new LinkedHashMap<>();

		for (Map.Entry<String, List<String>> entry : colunasPorCampo.entrySet()) {
			if (entry.getValue().size() > 1) {
				duplicados.add(entry.getKey());
				colunasPorCampoDuplicado.put(entry.getKey(), entry.getValue());
			}
		}

		// Planilha legado: uma única coluna cobre especialidade + nome do médico
		// (valor "ESPECIALIDADE - NOME" combinado numa célula só). Se não há
		// coluna própria para ESPECIALIDADE_MEDICO mas MEDICO foi resolvido,
		// reaproveita o mesmo índice para os dois — o split do valor combinado
		// acontece em AtendimentoProcessor.
		boolean especialidadeMedicoCombinados = false;

		if (!indices.containsKey("ESPECIALIDADE_MEDICO") && indices.containsKey("MEDICO")) {
			indices.put("ESPECIALIDADE_MEDICO", indices.get("MEDICO"));
			especialidadeMedicoCombinados = true;
		}

		List<String> faltando = new ArrayList<>();
		List<String> opcionaisFaltando = new ArrayList<>();

		for (String campo : ColunaAliasUtils.obterCamposCanonicos()) {
			if (!indices.containsKey(campo)) {
				if (CAMPOS_OPCIONAIS.contains(campo)) {
					opcionaisFaltando.add(campo);
				} else {
					faltando.add(campo);
				}
			}
		}

		return new ResultadoMapeamento(indices, faltando, opcionaisFaltando, duplicados, naoReconhecidas,
				colunasPorCampoDuplicado, especialidadeMedicoCombinados);
	}

	/**
	 * Mapeia a planilha olhando também os dados, além do cabeçalho:
	 * <ul>
	 *   <li><b>Colunas sem cabeçalho</b> com dados (ex.: a coluna da
	 *       especialidade com o título apagado) — antes eram ignoradas em
	 *       silêncio; agora vão em {@code colunasSemCabecalho}, com a letra da
	 *       coluna e um exemplo do conteúdo, para a tela de estrutura e o aviso
	 *       da análise.</li>
	 *   <li><b>Formato legado confirmado pelo conteúdo</b>: sem coluna
	 *       "Especialidade", a coluna "Especialidade/Médico" só é tratada como
	 *       combinada ("ESPECIALIDADE - NOME") se ao menos metade das células
	 *       preenchidas tiver o separador. Se não tiver (só nomes de médico),
	 *       a especialidade conta como coluna obrigatória ausente — a tela de
	 *       estrutura aparece em vez de gravar o nome do médico como
	 *       especialidade (problema encontrado em 09/10/2026).</li>
	 * </ul>
	 */
	public ResultadoMapeamento mapear(Sheet sheet) {

		ResultadoMapeamento base = mapear(sheet != null ? sheet.getRow(0) : null);

		if (sheet == null) {
			return base;
		}

		Map<String, Integer> indices = new LinkedHashMap<>(base.indices());
		List<String> faltando = new ArrayList<>(base.camposFaltando());
		boolean combinados = base.especialidadeMedicoCombinados();

		if (combinados && !conteudoConfirmaFormatoLegado(sheet, indices.get("MEDICO"))) {
			indices.remove("ESPECIALIDADE_MEDICO");
			faltando.add("ESPECIALIDADE_MEDICO");
			combinados = false;
		}

		return new ResultadoMapeamento(indices, faltando, base.camposOpcionaisFaltando(), base.camposDuplicados(),
				base.colunasNaoReconhecidas(), base.colunasPorCampoDuplicado(), combinados,
				colunasSemCabecalho(sheet));
	}

	/** Ao menos metade das células preenchidas da coluna tem o separador "ESPECIALIDADE - NOME". */
	private boolean conteudoConfirmaFormatoLegado(Sheet sheet, Integer coluna) {

		if (coluna == null) {
			return true;
		}

		int preenchidas = 0;
		int comSeparador = 0;

		for (Row row : sheet) {
			if (row.getRowNum() == 0) {
				continue;
			}
			String valor = leitor.lerCelula(row.getCell(coluna));
			if (valor == null || valor.isBlank()) {
				continue;
			}
			preenchidas++;
			if (StringUtils.separarEspecialidadeEMedico(valor)[1] != null) {
				comSeparador++;
			}
		}

		// Sem dados não há como saber — mantém o comportamento pelo cabeçalho
		return preenchidas == 0 || comSeparador * 2 >= preenchidas;
	}

	/** Colunas com cabeçalho vazio mas com dados: "coluna E (ex.: "CARDIOLOGIA")". */
	private List<String> colunasSemCabecalho(Sheet sheet) {

		Row cabecalho = sheet.getRow(0);
		Map<Integer, String> exemplos = new java.util.TreeMap<>();

		for (Row row : sheet) {
			if (row.getRowNum() == 0) {
				continue;
			}
			for (Cell celula : row) {
				int coluna = celula.getColumnIndex();
				if (exemplos.containsKey(coluna)) {
					continue;
				}
				String titulo = cabecalho != null ? extrairTexto(cabecalho.getCell(coluna)) : null;
				if (titulo != null && !titulo.isBlank()) {
					continue;
				}
				String valor = leitor.lerCelula(celula);
				if (valor != null && !valor.isBlank()) {
					exemplos.put(coluna, valor.trim());
				}
			}
		}

		List<String> colunas = new ArrayList<>();
		exemplos.forEach((coluna, exemplo) -> colunas.add("coluna "
				+ org.apache.poi.ss.util.CellReference.convertNumToColString(coluna)
				+ " (ex.: \"" + exemplo + "\")"));
		return colunas;
	}

	/** Leitura do valor das células de dados — mesma regra da importação (fórmula, U+00A0). */
	private final ExcelImportService leitor = new ExcelImportService();

	/** Extrai o texto de uma célula de cabeçalho, qualquer que seja o tipo. */
	private String extrairTexto(Cell celula) {

		if (celula == null) {
			return null;
		}

		return switch (celula.getCellType()) {
			case STRING -> celula.getStringCellValue();
			default -> celula.toString();
		};
	}
}
