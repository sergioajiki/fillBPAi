package br.gov.ses.fillbpai.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pastas onde o programa grava os dados (21/10/2026):
 * <ul>
 *   <li><b>Programa instalado</b> (pacote com a propriedade
 *       {@code -Dpontebpa.instalado=true}): tudo na pasta do usuário do Windows,
 *       {@code %LOCALAPPDATA%\PonteBPA\{database,logs,dados,config}} — a pasta do
 *       programa pode ser só leitura, e trocar de versão (substituir a pasta do
 *       programa) não apaga os dados;</li>
 *   <li><b>Desenvolvimento</b> (sem a propriedade): como antes, relativo à
 *       pasta do projeto — {@code database/}, {@code logs/}, {@code config/} e
 *       os cadastros em {@code src/main/resources/dados}.</li>
 * </ul>
 * Antes, banco e logs dependiam da pasta de onde o programa era aberto, e
 * Configurações gravava em {@code src/main/resources/dados}, que não existe
 * no programa instalado.
 *
 * @param database banco H2 ({@code fillbpai.mv.db})
 * @param logs     logs de importação
 * @param dados    cadastros editados em Configurações (CNS de médicos, nomes de colunas)
 * @param config   senha do banco ({@link SenhaBanco})
 */
public record PastasApp(Path database, Path logs, Path dados, Path config, boolean instalado) {

	/** Propriedade que o pacote instalado passa ao Java ({@code -Dpontebpa.instalado=true}). */
	public static final String PROPRIEDADE_INSTALADO = "pontebpa.instalado";

	/** Nome da pasta dos dados do programa instalado, dentro de {@code %LOCALAPPDATA%}. */
	static final String PASTA_PROGRAMA = "PonteBPA";

	/** Cadastro copiado do pacote para a pasta de dados na primeira execução do programa instalado. */
	static final String CADASTRO_MEDICOS = "medicos_cns.csv";

	private static PastasApp atual;

	/** Pastas desta execução (lidas uma vez). */
	public static synchronized PastasApp atual() {
		if (atual == null) {
			atual = de(Boolean.getBoolean(PROPRIEDADE_INSTALADO),
					System.getenv("LOCALAPPDATA"), System.getProperty("user.home"));
		}
		return atual;
	}

	/**
	 * Monta as pastas para o modo informado.
	 *
	 * @param localAppData valor de {@code %LOCALAPPDATA%}; se vazio, usa {@code <home>\AppData\Local}
	 */
	static PastasApp de(boolean instalado, String localAppData, String home) {

		if (!instalado) {
			return new PastasApp(Path.of("database"), Path.of("logs"),
					Path.of("src", "main", "resources", "dados"), Path.of("config"), false);
		}

		Path local = localAppData == null || localAppData.isBlank()
				? Path.of(home, "AppData", "Local")
				: Path.of(localAppData);
		Path base = local.resolve(PASTA_PROGRAMA);

		return new PastasApp(base.resolve("database"), base.resolve("logs"),
				base.resolve("dados"), base.resolve("config"), true);
	}

	/**
	 * Programa instalado: cria as pastas e, na primeira execução, copia o
	 * cadastro de CNS de médicos do pacote para a pasta de dados — a partir
	 * daí, Configurações edita essa cópia. Nunca sobrescreve uma cópia
	 * existente. Os nomes de colunas não são copiados: o arquivo da pasta de
	 * dados guarda só os nomes acrescentados pelo usuário (os padrões vêm do
	 * pacote). No desenvolvimento não faz nada.
	 */
	public void prepararPastas() throws IOException {

		if (!instalado) {
			return;
		}

		Files.createDirectories(database);
		Files.createDirectories(logs);
		Files.createDirectories(dados);
		Files.createDirectories(config);

		copiarDoPacoteSeFaltar(CADASTRO_MEDICOS);
	}

	/** Copia {@code /dados/<nome>} do pacote para a pasta de dados, se ainda não existir lá. */
	void copiarDoPacoteSeFaltar(String nome) throws IOException {

		Path destino = dados.resolve(nome);

		if (Files.exists(destino)) {
			return;
		}

		try (InputStream in = PastasApp.class.getResourceAsStream("/dados/" + nome)) {
			if (in != null) {
				Files.createDirectories(dados);
				Files.copy(in, destino);
			}
		}
	}
}
