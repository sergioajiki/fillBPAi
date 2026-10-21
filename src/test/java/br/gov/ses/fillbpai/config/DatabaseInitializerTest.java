package br.gov.ses.fillbpai.config;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Aviso "O PonteBPA já está aberto" (21/10/2026). */
class DatabaseInitializerTest {

	// Um segundo programa só pode ser simulado com outro processo (dentro do mesmo Java
	// o H2 compartilha o banco aberto); a mensagem "The file is locked" abaixo é a do
	// teste real do pacote, com o PonteBPA aberto duas vezes (21/10/2026)
	@Test
	void reconheceAsDuasFormasDoAvisoDoH2() {
		assertThat(DatabaseInitializer.isBancoEmUso(new SQLException("Database may be already in use", "90020", 90020))).isTrue();
		assertThat(DatabaseInitializer.isBancoEmUso(new RuntimeException("falha ao abrir",
				new SQLException("General error: \"org.h2.mvstore.MVStoreException: The file is locked: C:/x/fillbpai.mv.db\"",
						"HY000", 50000)))).isTrue();
	}

	@Test
	void outrosErrosNaoSaoConfundidosComProgramaAberto() {
		assertThat(DatabaseInitializer.isBancoEmUso(new SQLException("Wrong user name or password", "28000", 28000))).isFalse();
		assertThat(DatabaseInitializer.isBancoEmUso(new IllegalStateException("senha diferente da guardada"))).isFalse();
		assertThat(DatabaseInitializer.isBancoEmUso(null)).isFalse();
	}
}
