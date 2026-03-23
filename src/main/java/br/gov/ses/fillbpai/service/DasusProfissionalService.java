package br.gov.ses.fillbpai.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serviço para busca de profissionais no arquivo CSV do DATASUS
 * (tbDadosProfissionalSus).
 *
 * Realiza busca por nome via streaming para suportar arquivos grandes
 * (7+ milhões de registros) sem carregar tudo em memória.
 *
 * Formato do CSV: separador ponto-e-vírgula, campos entre aspas duplas,
 * encoding ISO-8859-1.
 * Colunas relevantes: NO_PROFISSIONAL (índice 2), CO_CNS (índice 3).
 */
public class DasusProfissionalService {

	private static final Logger log = LoggerFactory.getLogger(DasusProfissionalService.class);

	/** Limite máximo de resultados por busca para evitar sobrecarga de memória/UI */
	private static final int LIMITE_RESULTADOS = 100;

	/**
	 * Resultado da busca contendo nome e CNS do profissional.
	 */
	public record ProfissionalSusDTO(String nome, String cns) {
	}

	/**
	 * Busca profissionais por nome no arquivo CSV do DATASUS.
	 *
	 * A busca é case-insensitive, sem acentos, e usa contains (busca parcial).
	 * Limitada a {@value #LIMITE_RESULTADOS} resultados.
	 *
	 * @param caminhoArquivo caminho completo do arquivo CSV do DATASUS
	 * @param nomeBusca      termo de busca (nome ou parte do nome)
	 * @return lista de profissionais encontrados (nome + CNS)
	 */
	public List<ProfissionalSusDTO> buscarPorNome(String caminhoArquivo, String nomeBusca) {

		List<ProfissionalSusDTO> resultados = new ArrayList<>();

		if (nomeBusca == null || nomeBusca.isBlank()) {
			return resultados;
		}

		String termoBusca = normalizar(nomeBusca);

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(caminhoArquivo),
						Charset.forName("ISO-8859-1")))) {

			String linha;
			boolean primeiraLinha = true;

			while ((linha = reader.readLine()) != null) {

				// Pula cabeçalho
				if (primeiraLinha) {
					primeiraLinha = false;
					continue;
				}

				// Parse rápido: split por ; e remove aspas
				String[] campos = linha.split(";");

				if (campos.length < 4) {
					continue;
				}

				String nome = removerAspas(campos[2]);
				String cns = removerAspas(campos[3]);

				if (nome.isEmpty() || cns.isEmpty()) {
					continue;
				}

				if (normalizar(nome).contains(termoBusca)) {
					resultados.add(new ProfissionalSusDTO(nome, cns));

					if (resultados.size() >= LIMITE_RESULTADOS) {
						log.info("Busca por '{}' atingiu limite de {} resultados",
								nomeBusca, LIMITE_RESULTADOS);
						break;
					}
				}
			}

			log.info("Busca por '{}': {} resultado(s) encontrado(s)",
					nomeBusca, resultados.size());

		} catch (Exception e) {
			log.error("Erro ao buscar no arquivo DATASUS: {}", e.getMessage());
		}

		return resultados;
	}

	/**
	 * Normaliza texto para busca: remove acentos, converte para maiúsculo.
	 * Garante que "José" encontra "JOSE" e vice-versa.
	 */
	private String normalizar(String texto) {
		String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return semAcentos.toUpperCase().trim();
	}

	/**
	 * Remove aspas duplas de um campo CSV.
	 */
	private String removerAspas(String campo) {
		if (campo == null) {
			return "";
		}
		return campo.replace("\"", "").trim();
	}
}
