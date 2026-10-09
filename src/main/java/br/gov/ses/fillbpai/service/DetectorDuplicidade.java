package br.gov.ses.fillbpai.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.CpfUtils;

/**
 * Detecta, dentro de uma mesma planilha, linhas do mesmo atendimento:
 * mesmo paciente + médico + data + procedimento (SIGTAP) — a chave de
 * deduplicação da importação — comparando o horário.
 * <ul>
 *   <li>Mesmo horário (ou os dois sem horário) → {@link Tipo#REPETIDA}: a
 *       segunda linha sobrescreveria a primeira no banco (duas linhas viram
 *       um registro). ERRO — remover a repetição ou corrigir o horário.</li>
 *   <li>Horário diferente → {@link Tipo#SUSPEITA}: importadas como
 *       atendimentos separados, com aviso para conferir (decisão de
 *       09/10/2026).</li>
 * </ul>
 * Usado pela análise ({@link ValidacaoPlanilhaService}) e pela importação
 * ({@link AtendimentoImportacaoService}), sempre com o DTO já processado
 * pelo {@link AtendimentoProcessor} (CPF normalizado, SIGTAP, data e hora
 * convertidas).
 */
public class DetectorDuplicidade {

	public enum Tipo { REPETIDA, SUSPEITA }

	/** Linha anterior do mesmo atendimento: o tipo e o horário dela. */
	public record Ocorrencia(Tipo tipo, int linhaAnterior, LocalTime horaAnterior) {
	}

	private record Chave(String paciente, String medico, LocalDate data, String sigtap) {
	}

	private record Linha(int numero, LocalTime hora) {
	}

	private final Map<Chave, List<Linha>> vistas = new HashMap<>();

	/**
	 * Registra a linha e diz se ela repete (ou parece repetir) uma anterior.
	 *
	 * @param linha número da linha na planilha (1-based)
	 * @param dto   DTO já processado pelo {@link AtendimentoProcessor}
	 * @return {@code null} se é o primeiro atendimento com essa chave
	 */
	public Ocorrencia registrar(int linha, LinhaImportacaoDTO dto) {

		Chave chave = new Chave(chavePaciente(dto), dto.getCpfMedico(), dto.getDataAgendamento(), dto.getSigtap());
		LocalTime hora = dto.getHoraAtendimento();

		List<Linha> anteriores = vistas.computeIfAbsent(chave, c -> new ArrayList<>());

		Ocorrencia ocorrencia = anteriores.stream()
				.filter(l -> Objects.equals(l.hora(), hora))
				.findFirst()
				.map(l -> new Ocorrencia(Tipo.REPETIDA, l.numero(), l.hora()))
				.orElseGet(() -> anteriores.isEmpty() ? null
						: new Ocorrencia(Tipo.SUSPEITA, anteriores.get(0).numero(), anteriores.get(0).hora()));

		anteriores.add(new Linha(linha, hora));

		return ocorrencia;
	}

	/** Mesma chave do paciente na importação: CPF, ou a chave interna de paciente sem CPF. */
	private static String chavePaciente(LinhaImportacaoDTO dto) {

		String cpf = dto.getCpfPaciente();

		return cpf != null && !cpf.isBlank() ? cpf : CpfUtils.chaveSemCpf(dto.getPaciente(), dto.getDataNascimento());
	}

	/** Horário para mensagens: "08:30" ou "sem horário". */
	public static String descreverHora(LocalTime hora) {
		return hora == null ? "sem horário" : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
	}

	/** Mensagem do ERRO de linha repetida (mesmo horário). */
	public static String mensagemRepetida(Ocorrencia o) {
		return "Linha repetida — mesmo paciente, médico, data, procedimento e horário ("
				+ descreverHora(o.horaAnterior()) + ") da linha " + o.linhaAnterior()
				+ "; as duas virariam um único atendimento. Remova a repetição ou corrija o horário.";
	}

	/** Mensagem do AVISO de suspeita (horário diferente). */
	public static String mensagemSuspeita(Ocorrencia o, LocalTime horaAtual) {
		return "Suspeita de atendimento duplicado — mesmo paciente, médico, data e procedimento da linha "
				+ o.linhaAnterior() + ", com horário diferente (" + descreverHora(o.horaAnterior()) + " × "
				+ descreverHora(horaAtual) + "); importado como atendimento separado — confira se não é repetição.";
	}
}
