package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.util.ColunaAliasUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * Resultado do mapeamento de um cabeçalho.
	 *
	 * @param indices          campo canônico → índice da coluna (0-based)
	 * @param camposFaltando   campos obrigatórios não encontrados no cabeçalho
	 * @param camposDuplicados campos que casaram com mais de uma coluna do cabeçalho
	 */
	public record ResultadoMapeamento(
			Map<String, Integer> indices,
			List<String> camposFaltando,
			List<String> camposDuplicados) {

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
		List<String> duplicados = new ArrayList<>();

		if (cabecalho != null) {

			for (Cell celula : cabecalho) {

				String texto = extrairTexto(celula);
				String campo = ColunaAliasUtils.resolverCampo(texto);

				if (campo == null) {
					// Coluna não reconhecida por nenhum alias — ignorada de propósito.
					continue;
				}

				if (indices.containsKey(campo)) {
					if (!duplicados.contains(campo)) {
						duplicados.add(campo);
					}
				} else {
					indices.put(campo, celula.getColumnIndex());
				}
			}
		}

		List<String> faltando = new ArrayList<>();

		for (String campo : ColunaAliasUtils.obterCamposCanonicos()) {
			if (!indices.containsKey(campo)) {
				faltando.add(campo);
			}
		}

		return new ResultadoMapeamento(indices, faltando, duplicados);
	}

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
