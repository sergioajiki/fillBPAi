package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Endereco;
import br.gov.ses.fillbpai.model.Paciente;
import jakarta.persistence.EntityManager;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

/**
 * ============================================================
 * GERADOR DE ARQUIVO BPA-I MAGNÉTICO
 * ============================================================
 * <p>
 * HEADER:
 * 132 caracteres (MANTIDO INALTERADO)
 * <p>
 * REGISTRO:
 * 340 caracteres
 * <p>
 * Layout oficial do Ministério da Saúde
 * <p>
 * ============================================================
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
	 * ============================================================
	 * MÉTODO PRINCIPAL
	 * ============================================================
	 */
	public void gerarArquivoComFileChooser(
			Window parentWindow,
			String especialidade,
			String medico,
			String competenciaAtendimento) {

		try {

			int atenAno = Integer.parseInt(competenciaAtendimento.substring(0, 4));
			int atenMes = Integer.parseInt(competenciaAtendimento.substring(4, 6));

			List<AtendimentoBPAi> lista =
					entityManager.createQuery(
									"SELECT a FROM AtendimentoBPAi a " +
											"JOIN FETCH a.paciente p " +
											"LEFT JOIN FETCH p.endereco " +
											"JOIN FETCH a.medico " +
											"LEFT JOIN FETCH a.estabelecimento " +
											"WHERE a.especialidadeMedico = :esp " +
											"AND a.medico.nome = :med " +
											"AND YEAR(a.dataAgendamento) = :ano " +
											"AND MONTH(a.dataAgendamento) = :mes",
									AtendimentoBPAi.class)
							.setParameter("esp", especialidade)
							.setParameter("med", medico)
							.setParameter("ano", atenAno)
							.setParameter("mes", atenMes)
							.getResultList();

			if (lista.isEmpty())
				throw new RuntimeException(
						"Nenhum registro encontrado para "
						+ medico + " em " + String.format("%02d/%04d", atenMes, atenAno));

			validarCnsProfissional(lista);

			String competencia = calcularCompetencia(LocalDate.of(atenAno, atenMes, 1));

			FileChooser chooser = new FileChooser();

			chooser.setTitle("Salvar BPA-I");

			chooser.setInitialFileName(
					especialidade + "_" + medico + "_" + competencia + ".txt"
			);

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return;

			try (BufferedWriter writer =
						 Files.newBufferedWriter(
								 file.toPath(),
								 StandardCharsets.ISO_8859_1)) {

				/**
				 * HEADER — linha 1
				 */
				writer.write(montarHeader(lista, competencia));
				writer.newLine();

				/**
				 * REGISTROS — linhas seguintes
				 */
				int sequencial = 1;

				for (AtendimentoBPAi a : lista) {

					writer.write(montarRegistro(a, competencia, sequencial));

					writer.newLine();

					sequencial++;

					if (sequencial > 99)
						sequencial = 1;
				}
			}

		} catch (Exception ex) {

			throw new RuntimeException(ex);
		}
	}

	/**
	 * ============================================================
	 * GERAÇÃO COMPLETA — TODOS OS MÉDICOS
	 * ============================================================
	 *
	 * Gera um único arquivo BPA-I contendo todos os atendimentos
	 * de todos os médicos e especialidades.
	 *
	 * Regra de atribuição de folha:
	 * 1. Especialidades são ordenadas alfabeticamente
	 * 2. Dentro de cada especialidade, médicos são ordenados alfabeticamente
	 * 3. Cada combinação (especialidade + médico) recebe uma folha sequencial:
	 *    folha 1 para o primeiro médico, folha 2 para o segundo, etc.
	 * 4. A sequência de folha é válida apenas para a competência do mês.
	 *    Na próxima competência, a numeração recomeça.
	 *
	 * O sequencial (prd-seq) reinicia a cada troca de folha,
	 * e também reinicia ao atingir 20 (regra do layout BPA-I).
	 */
	/**
	 * @param competenciaAtendimento competência no formato YYYYMM referente ao mês de atendimento
	 *                               (ex: "202503" para atendimentos de março de 2025)
	 */
	public void gerarArquivoCompletoComFileChooser(Window parentWindow, String competenciaAtendimento) {

		try {

			// Deriva o mês de atendimento a partir da competência selecionada
			int atenAno  = Integer.parseInt(competenciaAtendimento.substring(0, 4));
			int atenMes  = Integer.parseInt(competenciaAtendimento.substring(4, 6));

			// 1. Carrega apenas os atendimentos do mês de competência selecionado
			List<AtendimentoBPAi> lista =
					entityManager.createQuery(
									"SELECT a FROM AtendimentoBPAi a " +
											"JOIN FETCH a.paciente p " +
											"LEFT JOIN FETCH p.endereco " +
											"JOIN FETCH a.medico " +
											"LEFT JOIN FETCH a.estabelecimento " +
											"WHERE YEAR(a.dataAgendamento) = :ano " +
											"AND MONTH(a.dataAgendamento) = :mes",
									AtendimentoBPAi.class)
							.setParameter("ano", atenAno)
							.setParameter("mes", atenMes)
							.getResultList();

			if (lista.isEmpty())
				throw new RuntimeException(
						"Nenhum registro encontrado para a competência "
						+ String.format("%02d/%04d", atenMes, atenAno));

			validarCnsProfissional(lista);

			// 2. Ordena por especialidade (alfabética) → médico (alfabético)
			lista.sort(Comparator
					.comparing((AtendimentoBPAi a) ->
							a.getEspecialidadeMedico() != null ? a.getEspecialidadeMedico() : "")
					.thenComparing(a ->
							a.getMedico() != null && a.getMedico().getNome() != null
									? a.getMedico().getNome() : "")
			);

			// 3. Atribui folhas sequenciais por combinação (especialidade + médico)
			//    e persiste os valores no banco
			int totalFolhas = atribuirFolhas(lista);

			// 4. Determina a competência BPA-I (mês seguinte ao atendimento)
			String competencia =
					calcularCompetencia(
							LocalDate.of(atenAno, atenMes, 1)
					);

			// 5. FileChooser para o usuário escolher onde salvar
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Salvar BPA-I Completo");
			chooser.setInitialFileName("BPA-I_COMPLETO_" + competencia + ".txt");

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return;

			// 6. Escreve o arquivo no formato BPA-I (ISO-8859-1)
			try (BufferedWriter writer =
						 Files.newBufferedWriter(
								 file.toPath(),
								 StandardCharsets.ISO_8859_1)) {

				// HEADER com totalFolhas correto (quantidade de médicos distintos)
				writer.write(montarHeaderCompleto(lista, competencia, totalFolhas));
				writer.newLine();

				// REGISTROS — sequencial reinicia a cada troca de folha
				int sequencial = 1;
				String folhaAtual = null;

				for (AtendimentoBPAi a : lista) {

					// Detecta troca de folha → reinicia sequencial
					if (!a.getFolha().equals(folhaAtual)) {
						folhaAtual = a.getFolha();
						sequencial = 1;
					}

					writer.write(montarRegistro(a, competencia, sequencial));
					writer.newLine();

					sequencial++;

					if (sequencial > 20)
						sequencial = 1;
				}
			}

		} catch (Exception ex) {

			throw new RuntimeException(ex);
		}
	}

	/**
	 * Atribui folhas aos atendimentos respeitando folhas já definidas manualmente.
	 *
	 * Lógica:
	 * 1. Identifica combinações (especialidade + médico) que JÁ possuem folha
	 * 2. Coleta os números de folha já em uso (reservados)
	 * 3. Para combinações SEM folha, atribui números sequenciais
	 *    pulando os números já reservados
	 * 4. Persiste no banco via dirty checking
	 *
	 * @param lista atendimentos já ordenados por especialidade → médico
	 * @return total de folhas distintas (manuais + auto-atribuídas)
	 */
	private int atribuirFolhas(List<AtendimentoBPAi> lista) {

		// 1. Mapeia cada combinação (esp+med) → folha existente (ou null)
		//    LinkedHashMap preserva a ordem de inserção (alfabética, já que lista está ordenada)
		Map<String, String> chaveParaFolha = new LinkedHashMap<>();
		Set<Integer> folhasReservadas = new HashSet<>();

		for (AtendimentoBPAi a : lista) {

			String chave = montarChaveMedico(a);
			String folhaExistente = a.getFolha();

			if (!chaveParaFolha.containsKey(chave)) {

				chaveParaFolha.put(chave, folhaExistente);

				if (folhaExistente != null && !folhaExistente.isBlank()) {
					try {
						folhasReservadas.add(Integer.parseInt(folhaExistente));
					} catch (NumberFormatException ignored) {
					}
				}

			} else if ((chaveParaFolha.get(chave) == null || chaveParaFolha.get(chave).isBlank())
					&& folhaExistente != null && !folhaExistente.isBlank()) {

				// Chave já registrada como null, mas este atendimento tem folha definida
				// (atendimento antigo aparece depois do novo na lista ordenada)
				chaveParaFolha.put(chave, folhaExistente);
				try {
					folhasReservadas.add(Integer.parseInt(folhaExistente));
				} catch (NumberFormatException ignored) {
				}
			}
		}

		// 2. Atribui folhas sequenciais para quem não tem, pulando as reservadas
		int proximaFolha = 1;

		for (Map.Entry<String, String> entry : chaveParaFolha.entrySet()) {

			if (entry.getValue() == null || entry.getValue().isBlank()) {

				// Avança até encontrar um número livre
				while (folhasReservadas.contains(proximaFolha)) {
					proximaFolha++;
				}

				entry.setValue(String.valueOf(proximaFolha));
				folhasReservadas.add(proximaFolha);
				proximaFolha++;
			}
		}

		// 3. Aplica as folhas a todos os atendimentos
		entityManager.getTransaction().begin();

		try {

			for (AtendimentoBPAi a : lista) {

				String chave = montarChaveMedico(a);
				String folha = chaveParaFolha.get(chave);

				a.setFolha(folha);
			}

			entityManager.getTransaction().commit();

		} catch (Exception e) {

			if (entityManager.getTransaction().isActive()) {
				entityManager.getTransaction().rollback();
			}

			throw new RuntimeException("Erro ao atribuir folhas: " + e.getMessage());
		}

		return chaveParaFolha.size();
	}

	/**
	 * Monta a chave única para identificar uma combinação especialidade + médico.
	 */
	private String montarChaveMedico(AtendimentoBPAi a) {

		String esp = a.getEspecialidadeMedico() != null
				? a.getEspecialidadeMedico() : "";

		String med = a.getMedico() != null && a.getMedico().getNome() != null
				? a.getMedico().getNome() : "";

		return esp + "|" + med;
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
	 * Header para o arquivo completo.
	 * Usa o totalFolhas calculado (quantidade de médicos distintos)
	 * ao invés de lista.size().
	 */
	private String montarHeaderCompleto(
			List<AtendimentoBPAi> lista,
			String competencia,
			int totalFolhas) {

		int totalLinhas = lista.size();
		int somaControle = calcularSomaVerificacao(lista);

		StringBuilder sb = new StringBuilder();

		sb.append("01"); // seq 1 cbc-hdr
		sb.append("#BPA#"); // seq 2 cbc-hdr
		sb.append(padLeftZeros(competencia, 6)); // seq 3 cbc-mvm
		sb.append(padLeftZeros(String.valueOf(totalLinhas), 6)); // seq 4 cbc-lin
		sb.append(padLeftZeros(String.valueOf(totalFolhas), 6)); // seq 5 cbc-flh
		sb.append(padLeftZeros(String.valueOf(somaControle), 4)); // seq 6 cbc-smt-vrf
		sb.append(padRightSpaces("NUCLEO DE TELESSAUDE DE MS", 30)); // seq 7
		sb.append(padRightSpaces("NTMS", 6)); // seq 8
		sb.append(padLeftZeros("02955271000126", 14)); // seq 9
		sb.append(padRightSpaces("SECRETARIA ESTADUAL DE SAUDE", 40)); // seq 10
		sb.append("E"); // seq 11
		sb.append(padRightSpaces("ED04.10", 10)); // seq 12

		return sb.toString();
	}

	/**
	 * ============================================================
	 * HEADER
	 * ============================================================
	 */
	private String montarHeader(List<AtendimentoBPAi> lista, String competencia) {

		int totalLinhas = lista.size();
		int totalFolhas = lista.size();
		int somaControle = calcularSomaVerificacao(lista);

		StringBuilder sb = new StringBuilder();

		sb.append("01"); // seq 1 cbc-hdr
		sb.append("#BPA#"); // seq 2 cbc-hdr
		sb.append(padLeftZeros(competencia, 6)); // seq 3 cbc-mvm
		sb.append(padLeftZeros(String.valueOf(totalLinhas), 6)); // seq 4 cbc-lin
		sb.append(padLeftZeros(String.valueOf(totalFolhas), 6)); // seq 5 cbc-flh
		sb.append(padLeftZeros(String.valueOf(somaControle), 4)); // seq 6 cbc-smt-vrf
		sb.append(padRightSpaces("NUCLEO DE TELESSAUDE DE MS", 30)); // seq 7
		sb.append(padRightSpaces("NTMS", 6)); // seq 8
		sb.append(padLeftZeros("02955271000126", 14)); // seq 9
		sb.append(padRightSpaces("SECRETARIA ESTADUAL DE SAUDE", 40)); // seq 10
		sb.append("E"); // seq 11
		sb.append(padRightSpaces("ED04.10", 10)); // seq 12

		return sb.toString();
	}

	/**
	 * ============================================================
	 * REGISTRO BPA-I COMPLETO
	 * 340 caracteres
	 * seq 1 até seq 38
	 * ============================================================
	 */
	private String montarRegistro(
			AtendimentoBPAi a,
			String competencia,
			int sequencial) {

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

		int idade = calcularIdade(dataNascimento);

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
		 * posição 010-015
		 */
		sb.append(competencia);

		/**
		 * seq 4 - prd-cnsmed
		 * posição 016-030
		 * NUM, default Brancos
		 */
		sb.append(padNumOpcional(a.getCnsProfissional(), 15));

		/**
		 * seq 5 - prd-cbo
		 */
		sb.append(padRightSpaces(a.getCboMedico(), 6));

		/**
		 * seq 6 - prd-dtaten
		 */
		sb.append(dataAtendimento);

		/**
		 * seq 7 - prd-flh
		 */
		sb.append(padLeftZeros(a.getFolha(), 3));

		/**
		 * seq 8 - prd-seq
		 */
		sb.append(padLeftZeros(String.valueOf(sequencial), 2));

		/**
		 * seq 9 - prd-pa
		 */
		sb.append(padLeftZeros(sigtap, 10));

		/**
		 * seq 10 - prd-cnspac
		 * Campo de identificação do paciente (15 caracteres, numérico).
		 *
		 * REGRA DE NEGÓCIO:
		 * Utiliza o CPF do paciente (11 dígitos) completado com zeros
		 * à esquerda até 15 caracteres, em substituição ao CNS.
		 *
		 * Motivo: o CNS pode estar em formato legado (>15 dígitos)
		 * ou ausente. O CPF é mais confiável como identificador.
		 *
		 * Exemplo: CPF "12345678901" → "000012345678901"
		 *
		 */

		String cpfPaciente = paciente != null ? paciente.getCpf() : null;
		sb.append(padNumOpcional(cpfPaciente, 15));

		/**
		 * seq 11 - prd-sexo
		 * ALFA, M ou F
		 */
		String sexo = paciente != null ? paciente.getSexo() : null;
		sb.append(sexo != null ? sexo : "M");

		/**
		 * seq 12 - prd-ibge
		 * Código IBGE do município do paciente (6 posições, numérico).
		 *
		 * O código IBGE padrão tem 7 dígitos. O BPA-I usa 6 posições
		 * (sem o dígito verificador), então truncamos o último dígito.
		 *
		 * Resolvido durante a importação via ViaCEP (CEP) ou CSV (nome).
		 * Se não resolvido, preenchido com brancos (default do layout).
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
		String nomePaciente = paciente != null ? paciente.getNome() : "";
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
		sb.append(padRightSpaces("", 4));  // etnia

		/**
		 * seq 23 - prd-nac
		 * default → "010"
		 */
		sb.append(padRightSpaces("010", 3));  // nacionalidade

		/**
		 * seq 24 - prd-srv
		 */
		sb.append(formatarServico(a.getSigtap()));

		/** sb.append(padRightSpaces("", 3)); */  // serviço

		/**
		 * seq 25 - prd-clf
		 */
		sb.append(formatarClassificacao(a.getSigtap()));    // classificação

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
		String complemento = endereco != null ? endereco.getComplemento() : "";
		sb.append(padRightSpaces(formatarAlfa(complemento, 10), 10));

		/**
		 * seq 33 - prd-num_pcnte
		 */
		String numero = endereco != null ? endereco.getNumero() : "";
		sb.append(padRightSpaces(formatarAlfa(numero, 5), 5));

		/**
		 * seq 34 - prd-bairro_pcnte
		 */
		String bairro = endereco != null ? endereco.getBairro() : "";
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
		 * seq 38 - prd-fim
		 * CRLF controlado pelo writer.newLine()
		 */

		return sb.toString();
	}

	/**
	 * cálculo idade
	 */
	private int calcularIdade(LocalDate nascimento) {

		if (nascimento == null)
			return 0;

		return Period.between(nascimento, LocalDate.now()).getYears();
	}

	/**
	 * cálculo campo controle header
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

	private String padRightSpaces(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		return String.format("%-" + tamanho + "s", valor);
	}

	private String somenteNumeros(String valor) {

		if (valor == null)
			return "";

		return valor.replaceAll("[^0-9]", "");
	}

	private String formatarCid(String cid) {

		if (cid == null || cid.isBlank())
			return "";

		cid = cid.trim().toUpperCase();

		// remove caracteres inválidos
		cid = cid.replaceAll("[^A-Z0-9]", "");

		return cid.length() > 4 ? cid.substring(0, 4) : cid;
	}

	private String formatarAlfa(String valor, int tamanho) {

		if (valor == null)
			valor = "";

		// remove quebras de linha e caracteres de controle
		valor = valor.replaceAll("[\\r\\n\\t]", " ").trim();

		// corta se exceder o tamanho máximo
		if (valor.length() > tamanho) {
			valor = valor.substring(0, tamanho);
		}

		return valor;
	}

	private String formatarRaca(String raca) {

		if (raca == null)
			return "99";

		raca = raca.trim().toUpperCase();

		if (raca.contains("BRANC")) return "01";
		if (raca.contains("PRET")) return "02";
		if (raca.contains("PARD")) return "03";
		if (raca.contains("AMAREL")) return "04";
		if (raca.contains("IND")) return "05";

		return "99";
	}

	/**
	 * Formata o telefone do paciente conforme layout BPA-I.
	 */
	private String formatarTelefone(String telefone) {

		if (telefone == null || telefone.isBlank()) {
			return padRightSpaces("", 11);
		}

		// remove tudo que não for número
		telefone = telefone.replaceAll("[^0-9]", "");

		// limita a 11 dígitos
		if (telefone.length() > 11) {
			telefone = telefone.substring(0, 11);
		}

		return padRightSpaces(telefone, 11);
	}

	/**
	 * Regra de negócio:
	 * SIGTAP 03.01.01.030-7 -> 000
	 * SIGTAP 08.04.01.006-4 -> 009
	 */
	private static final Map<String, String> MAP_SERVICO = new HashMap<>();

		static {
		MAP_SERVICO.put("0301010307", "");
		MAP_SERVICO.put("0804010064", "160");
	}

	private static final Map<String, String> MAP_CLASSIFICACAO = new HashMap<>();

	static {
		MAP_CLASSIFICACAO.put("0804010064", "009");
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

		// competência é o mês seguinte
		LocalDate competencia = dataAtendimento.plusMonths(1);

		return competencia.format(FORMATO_COMPETENCIA);
	}

	/**
	 * Campo NUM obrigatório
	 * - Apenas números
	 * - Preenchimento com zeros à esquerda
	 * - Default = 000
	 */
	private String padNumObrigatorio(String valor, int tamanho) {

		if (valor == null)
			valor = "081";

		valor = valor.replaceAll("[^0-9]", "");

		if (valor.isEmpty())
			valor = "0";

		return padLeftZeros(valor, tamanho);
	}

	/**
	 * Formata CPF para campo prd-cnspac
	 * - Remove caracteres não numéricos
	 * - Mantém 11 dígitos
	 * - Acrescenta 4 espaços à esquerda
	 * - Total = 15 caracteres
	 */
	private String formatarCpfParaBpa(String cpf) {

		if (cpf == null)
			cpf = "";

		// remove tudo que não for número
		cpf = cpf.replaceAll("[^0-9]", "");

		// limita a 11 dígitos
		if (cpf.length() > 11) {
			cpf = cpf.substring(0, 11);
		}

		// adiciona 4 espaços à esquerda
		//return String.format("%15s", cpf);

		// completa com espaços à direita
		return padRightSpaces(cpf, 15);
	}

}
