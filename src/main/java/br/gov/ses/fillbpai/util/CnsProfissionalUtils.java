package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para resolução do CNS (Cartão Nacional de Saúde) do profissional
 * a partir do CPF, utilizando arquivo CSV embutido.
 *
 * O arquivo CSV deve estar em: /dados/medicos_cns.csv
 * Formato: cpf;cns (separador ponto-e-vírgula)
 *
 * O CPF no CSV pode estar com ou sem formatação (pontos/hífen) —
 * a busca normaliza para apenas dígitos.
 *
 * Uso típico:
 * - Durante a importação da planilha, após identificar o médico pelo CPF,
 *   o sistema consulta este utilitário para auto-preencher o campo cnsProfissional.
 * - Se o CPF não constar no CSV, o campo fica vazio e pode ser preenchido via UI.
 */
public class CnsProfissionalUtils {

	private static final Logger log = LoggerFactory.getLogger(CnsProfissionalUtils.class);

	/** Mapa: CPF (somente dígitos) → CNS do profissional. Lazy loading. */
	private static Map<String, String> mapaCns = null;

	/**
	 * Busca o CNS do profissional pelo CPF.
	 *
	 * @param cpf CPF do médico (com ou sem formatação)
	 * @return CnsResultado com o CNS encontrado (ou null) e aviso opcional
	 */
	public static CnsResultado buscar(String cpf) {

		if (cpf == null || cpf.isBlank()) {
			return new CnsResultado(null, null);
		}

		carregarCsvSeNecessario();

		String cpfNormalizado = normalizarCpf(cpf);

		String cns = mapaCns.get(cpfNormalizado);

		if (cns != null) {
			log.debug("CNS encontrado para CPF {}: {}", cpfNormalizado, cns);
			return new CnsResultado(cns, null);
		}

		// CPF não encontrado no CSV — aviso para o log de importação
		String aviso = "CNS do profissional não encontrado no CSV para CPF: " + cpf
				+ ". Preencha manualmente pela tela de relatório.";

		log.debug(aviso);

		return new CnsResultado(null, aviso);
	}

	/**
	 * Carrega o arquivo CSV de médicos do classpath (lazy loading).
	 * Formato: cpf;cns
	 * Encoding: UTF-8
	 */
	private static synchronized void carregarCsvSeNecessario() {

		if (mapaCns != null) {
			return;
		}

		mapaCns = new HashMap<>();

		try (InputStream is = CnsProfissionalUtils.class
				.getResourceAsStream("/dados/medicos_cns.csv")) {

			if (is == null) {
				log.error("Arquivo medicos_cns.csv não encontrado no classpath");
				return;
			}

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8));

			String linha;

			while ((linha = reader.readLine()) != null) {

				// Ignora linhas vazias e cabeçalho
				if (linha.isBlank() || linha.startsWith("cpf")) {
					continue;
				}

				String[] partes = linha.split(";");

				if (partes.length >= 2) {

					String cpf = normalizarCpf(partes[0].trim());
					String cns = partes[1].trim();

					if (!cpf.isEmpty() && !cns.isEmpty()) {
						mapaCns.put(cpf, cns);
					}
				}
			}

			log.info("CSV de CNS profissional carregado: {} entradas", mapaCns.size());

		} catch (Exception e) {
			log.error("Erro ao carregar CSV de CNS profissional: {}", e.getMessage());
		}
	}

	/**
	 * Remove formatação do CPF, mantendo apenas dígitos.
	 * Exemplo: "123.456.789-00" → "12345678900"
	 */
	static String normalizarCpf(String cpf) {
		return cpf.replaceAll("[^0-9]", "");
	}

	/**
	 * Resultado da busca de CNS do profissional.
	 * Contém o CNS encontrado (ou null) e um aviso opcional.
	 */
	public static class CnsResultado {

		private final String cns;
		private final String aviso;

		public CnsResultado(String cns, String aviso) {
			this.cns = cns;
			this.aviso = aviso;
		}

		/** CNS do profissional ou null se não encontrado. */
		public String getCns() {
			return cns;
		}

		/** Mensagem de aviso ou null se encontrado sem problemas. */
		public String getAviso() {
			return aviso;
		}
	}
}
