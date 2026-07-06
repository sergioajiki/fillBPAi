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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * 352 caracteres
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

			chooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo BPA-I (*.txt)", "*.txt"));

			chooser.setInitialFileName(
					especialidade + "_" + medico + "_" + competencia + ".txt"
			);

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return;

			file = garantirExtensaoTxt(file);

			try (BufferedWriter writer =
						 Files.newBufferedWriter(
								 file.toPath(),
								 StandardCharsets.ISO_8859_1)) {

				/**
				 * HEADER — linha 1
				 */
				writer.write(montarHeader(lista, competencia));
				writer.write("\r\n"); // seq 13 cbc-fim: CR+LF obrigatório conforme layout BPA

				/**
				 * REGISTROS — linhas seguintes
				 */
				int sequencial = 1;

				for (AtendimentoBPAi a : lista) {

					writer.write(montarRegistro(a, competencia, sequencial));

					writer.write("\r\n"); // seq 40 prd-fim: CR+LF obrigatório conforme layout BPA-I

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
	 * e também reinicia ao atingir 99 (regra do layout BPA-I).
	 */
	/**
	 * @param competenciaAtendimento competência no formato YYYYMM referente ao mês de atendimento
	 *                               (ex: "202503" para atendimentos de março de 2025)
	 */
	public void gerarArquivoCompletoComFileChooser(Window parentWindow, String competenciaAtendimento) {

		try {

			int atenAno  = Integer.parseInt(competenciaAtendimento.substring(0, 4));
			int atenMes  = Integer.parseInt(competenciaAtendimento.substring(4, 6));

			// 1. Calcula a competência alvo a partir do mês selecionado
			String competencia = calcularCompetencia(LocalDate.of(atenAno, atenMes, 1));

			// 2. Carrega todos os atendimentos do ano e filtra pelos que pertencem
			//    à competência alvo — inclui múltiplos meses quando a competência
			//    é a mesma para todos (ex: exceção em que todos os meses → 202505)
			List<AtendimentoBPAi> lista =
					entityManager.createQuery(
									"SELECT a FROM AtendimentoBPAi a " +
											"JOIN FETCH a.paciente p " +
											"LEFT JOIN FETCH p.endereco " +
											"JOIN FETCH a.medico " +
											"LEFT JOIN FETCH a.estabelecimento " +
											"WHERE YEAR(a.dataAgendamento) = :ano",
									AtendimentoBPAi.class)
							.setParameter("ano", atenAno)
							.getResultList()
							.stream()
							.filter(a -> competencia.equals(
									calcularCompetencia(a.getDataAgendamento())))
							.collect(Collectors.toList());

			if (lista.isEmpty())
				throw new RuntimeException(
						"Nenhum registro encontrado para a competência " + competencia);

			validarCnsProfissional(lista);

			// 3. Ordena por especialidade (alfabética) → médico (alfabético) → mês
			lista.sort(Comparator
					.comparing((AtendimentoBPAi a) ->
							a.getEspecialidadeMedico() != null ? a.getEspecialidadeMedico() : "")
					.thenComparing(a ->
							a.getMedico() != null && a.getMedico().getNome() != null
									? a.getMedico().getNome() : "")
					.thenComparingInt(a ->
							a.getDataAgendamento() != null
									? a.getDataAgendamento().getMonthValue() : 0)
			);

			// 4. Atribui folhas sequenciais em memória (sem persistir no banco)
			int totalFolhas = atribuirFolhas(lista, atenAno);

			// 5. FileChooser para o usuário escolher onde salvar
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Salvar BPA-I Completo");

			chooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo BPA-I (*.txt)", "*.txt"));

			chooser.setInitialFileName("BPA-I_COMPLETO_" + competencia + ".txt");

			File file = chooser.showSaveDialog(parentWindow);

			if (file == null)
				return;

			file = garantirExtensaoTxt(file);

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
					writer.write("\r\n"); // seq 40 prd-fim: CR+LF obrigatório conforme layout BPA-I

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
	 * Atribui folhas aos atendimentos do mês atual em memória, sem persistir no banco.
	 *
	 * Lógica:
	 * 1. Consulta todas as combinações (especialidade + médico + mês) do ano inteiro
	 * 2. Atribui folhas sequenciais em ordem: especialidade → médico → mês
	 * 3. Aplica ao mês atual apenas em memória — sem gravar no banco
	 *
	 * Sem persistência não há dados desatualizados causando conflito entre meses.
	 * A continuidade entre meses é garantida pela ordenação determinística.
	 *
	 * @param lista atendimentos do mês atual, já ordenados por especialidade → médico
	 * @param atenAno ano de atendimento, usado para limitar a consulta ao ano corrente
	 * @return total de folhas distintas no mês atual (para o header do arquivo)
	 */
	private int atribuirFolhas(List<AtendimentoBPAi> lista, int atenAno) {

		// 1. Consulta todas as combinações do ano ordenadas deterministicamente
		List<Object[]> todasCombinacoes = entityManager.createQuery(
				"SELECT DISTINCT a.especialidadeMedico, a.medico.nome, MONTH(a.dataAgendamento) " +
				"FROM AtendimentoBPAi a " +
				"WHERE YEAR(a.dataAgendamento) = :ano " +
				"ORDER BY a.especialidadeMedico, a.medico.nome, MONTH(a.dataAgendamento)",
				Object[].class)
				.setParameter("ano", atenAno)
				.getResultList();

		// 2. Atribui folha sequencial a cada combinação (esp + médico + mês)
		Map<String, String> chaveParaFolha = new LinkedHashMap<>();
		int proximaFolha = 1;

		for (Object[] row : todasCombinacoes) {
			String chave = (row[0] != null ? (String) row[0] : "") + "|"
					+ (row[1] != null ? (String) row[1] : "") + "|"
					+ row[2];
			if (!chaveParaFolha.containsKey(chave)) {
				chaveParaFolha.put(chave, String.valueOf(proximaFolha++));
			}
		}

		// 3. Aplica folhas ao mês atual em memória (sem persistir)
		for (AtendimentoBPAi a : lista) {
			a.setFolha(chaveParaFolha.get(montarChaveMedicoMes(a)));
		}

		return (int) lista.stream()
				.map(this::montarChaveMedico)
				.distinct()
				.count();
	}

	/**
	 * Garante que o arquivo escolhido no FileChooser tenha extensão .txt.
	 * O diálogo nativo de salvar do Windows nem sempre preserva a extensão
	 * do nome inicial sugerido (ex.: ao usar "Todos os arquivos"), então a
	 * extensão é reforçada aqui antes da gravação.
	 */
	private File garantirExtensaoTxt(File file) {

		if (file.getName().toLowerCase().endsWith(".txt"))
			return file;

		return new File(file.getParentFile(), file.getName() + ".txt");
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
	 * Monta a chave única para identificar uma combinação especialidade + médico + mês.
	 */
	private String montarChaveMedicoMes(AtendimentoBPAi a) {

		String esp = a.getEspecialidadeMedico() != null
				? a.getEspecialidadeMedico() : "";

		String med = a.getMedico() != null && a.getMedico().getNome() != null
				? a.getMedico().getNome() : "";

		int mes = a.getDataAgendamento() != null
				? a.getDataAgendamento().getMonthValue() : 0;

		return esp + "|" + med + "|" + mes;
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
	 * 352 caracteres
	 * seq 1 até seq 40
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
		 * posição 010-015 — AAAAMM da data de atendimento do registro
		 */
		sb.append(a.getDataAgendamento().format(FORMATO_COMPETENCIA));

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
		 * NUTRICIONISTA e PSICÓLOGO usam código fixo 0301010315
		 */
		String especialidade = a.getEspecialidadeMedico() != null ? a.getEspecialidadeMedico().trim().toUpperCase() : "";
		String prdPa = (especialidade.equals("NUTRICIONISTA") || especialidade.equals("PSICÓLOGO"))
				? "0301010315"
				: sigtap;
		sb.append(padLeftZeros(prdPa, 10));

		/**
		 * seq 10 - prd-cnspac
		 * 15 espaços brancos — CNS não utilizado
		 */
		sb.append(padRightSpaces("", 15));

		/**
		 * seq 11 - prd-sexo
		 * ALFA — F (feminino), M (masculino) ou I (indeterminado)
		 */
		String sexo = paciente != null ? paciente.getSexo() : null;
		sb.append(sexo != null ? sexo : "F");

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
		sb.append(formatarServico(prdPa));

		/** sb.append(padRightSpaces("", 3)); */  // serviço

		/**
		 * seq 25 - prd-clf
		 */
		sb.append(formatarClassificacao(prdPa));    // classificação

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
		 * seq 38 - prd_cpf_pcnte
		 * CPF do paciente, 11 chars, numérico
		 */
		String cpfPacienteNum = paciente != null ? somenteNumeros(paciente.getCpf()) : "";
		sb.append(padLeftZeros(cpfPacienteNum, 11));

		/**
		 * seq 39 - prd_situacao_rua
		 * ALFA, 1 char, valores: N ou S, padrão N
		 */
		sb.append("N");

		/**
		 * seq 40 - prd-fim
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

		// substitui caracteres não representáveis em ISO-8859-1 (encoding do arquivo de saída),
		// evitando UnmappableCharacterException ao gravar o arquivo
		valor = sanitizarLatin1(valor);

		// corta se exceder o tamanho máximo
		if (valor.length() > tamanho) {
			valor = valor.substring(0, tamanho);
		}

		return valor;
	}

	/**
	 * Substitui caracteres fora do conjunto ISO-8859-1 (ex.: aspas curvas,
	 * travessão, reticências digitadas via Word/Excel, emojis) por
	 * equivalentes ASCII ou espaço, evitando falha ao gravar o arquivo BPA-I.
	 */
	private String sanitizarLatin1(String valor) {

		if (valor == null || valor.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder(valor.length());

		for (int i = 0; i < valor.length(); i++) {

			char c = valor.charAt(i);

			if (c <= 0xFF) {
				sb.append(c);
				continue;
			}

			switch (c) {
				case '‘': case '’': sb.append('\''); break; // aspas simples curvas
				case '“': case '”': sb.append('"'); break;  // aspas duplas curvas
				case '–': case '—': sb.append('-'); break;  // en-dash / em-dash
				case '…': sb.append("..."); break;               // reticências
				default: sb.append(' ');
			}
		}

		return sb.toString();
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
		MAP_SERVICO.put("0301010315", "160");
	}

	private static final Map<String, String> MAP_CLASSIFICACAO = new HashMap<>();

	static {
		MAP_CLASSIFICACAO.put("0804010064", "009");
		MAP_CLASSIFICACAO.put("0301010315", "006");
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

		return dataAtendimento.format(FORMATO_COMPETENCIA);
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
