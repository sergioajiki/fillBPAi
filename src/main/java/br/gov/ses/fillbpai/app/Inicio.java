package br.gov.ses.fillbpai.app;

/**
 * Ponto de partida do programa instalado (pacote gerado por
 * {@code pacote/gerar-pacote.ps1}, 21/10/2026).
 * <p>
 * Fora do Maven, o JavaFX recusa iniciar quando a classe principal estende
 * {@code Application} e as bibliotecas do JavaFX estão no classpath ("JavaFX
 * runtime components are missing"). Esta classe, que não estende
 * {@code Application}, só repassa para {@link MainApp}. No desenvolvimento
 * ({@code mvnw.cmd javafx:run}) continua sendo usada a {@code MainApp}.
 */
public final class Inicio {

	private Inicio() {
	}

	public static void main(String[] args) {
		MainApp.main(args);
	}
}
