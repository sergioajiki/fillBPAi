package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import jakarta.persistence.EntityManager;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * NÃO ALTERADO (REGRA SUA)
     * ============================================================
     */
    public void gerarArquivoComFileChooser(
            Window parentWindow,
            String especialidade,
            String medico) {

        try {

            List<AtendimentoBPAi> lista =
                    entityManager.createQuery(
                                    "SELECT a FROM AtendimentoBPAi a " +
                                            "WHERE a.especialidadeMedico = :esp " +
                                            "AND a.medico = :med",
                                    AtendimentoBPAi.class)
                            .setParameter("esp", especialidade)
                            .setParameter("med", medico)
                            .getResultList();

            if (lista.isEmpty())
                throw new RuntimeException("Nenhum registro encontrado");

            String competencia =
                    lista.get(0)
                            .getDataAgendamento()
                            .format(FORMATO_COMPETENCIA);

            FileChooser chooser = new FileChooser();

            chooser.setTitle("Salvar BPA-I");

            chooser.setInitialFileName(
                    especialidade + "_" + medico + "_" + competencia + ".txt"
            );

            File file = chooser.showSaveDialog(parentWindow);

            if (file == null)
                return;

            try (BufferedWriter writer =
                         Files.newBufferedWriter(file.toPath())) {

                /**
                 * HEADER — linha 1
                 * NÃO ALTERADO
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

                    if (sequencial > 20)
                        sequencial = 1;
                }
            }

        } catch (Exception ex) {

            throw new RuntimeException(ex);
        }
    }

    /**
     * ============================================================
     * HEADER — NÃO ALTERADO
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

        String sigtap = somenteNumeros(a.getSigtap());

        String dataAtendimento =
                a.getDataAgendamento().format(FORMATO_DATA);

        String dataNascimento =
                a.getDataNascimento() != null ?
                        a.getDataNascimento().format(FORMATO_DATA) :
                        "";

        int idade = calcularIdade(a.getDataNascimento());

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
         */
        sb.append(padLeftZeros(a.getCnsProfissional(), 15));

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
         */
        sb.append(padLeftZeros(a.getCnsPaciente(), 15));

        /**
         * seq 11 - prd-sexo
         * DEFAULT: M
         */
        sb.append("M");

        /**
         * seq 12 - prd-ibge
         */
        sb.append(padLeftZeros("", 6));

        /**
         * seq 13 - prd-cid
         * sb.append(padRightSpaces(a.getCidConsulta(), 4));
         */
        sb.append(padRightSpaces(formatarCid(a.getCidConsulta()), 4));
        /**
         * seq 14 - prd-idade
         */
        sb.append(padLeftZeros(String.valueOf(idade), 3));

        /**
         * seq 15 - prd-qt
         *
         */
        sb.append("000001");

        /**
         * seq 16 - prd-caten
         *  Caracter de atendimento
         *  Formato: NUM (2 posições)
         *  Regra: deve conter apenas números, com zeros à esquerda quando necessário.
         *
         *  ATENÇÃO:
         *  Durante a fase de desenvolvimento e testes, este campo será preenchido fixamente com "01".
         *  Este valor deverá ser ajustado futuramente conforme a regra de negócio definitiva
         *  e/ou conforme orientação do layout oficial do BPA-I / DATASUS.
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
         * Regra: máximo de 30 caracteres, preenchido com espaços à direita.
         *  sb.append(padRightSpaces(a.getPaciente(), 30));
         */
        sb.append(padRightSpaces(formatarAlfa(a.getPaciente(), 30), 30));

        /**
         * seq 20 - prd-dtnasc
         */
        sb.append(padLeftZeros(dataNascimento, 8));

        /**
         * seq 21 - prd-raca
         * Raça/Cor do paciente conforme tabela oficial do BPA-I.
         * Quando não informado, preencher com "99".
         */
        sb.append(formatarRaca(a.getRacaPaciente()));

        /**
         * seq 22 até seq 36
         * seq 22 - prd-etnia
         * não disponíveis → default
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
         sb.append(padRightSpaces("160", 3)); // serviço

        /**
         * seq 25 - prd-clf
         * Código do serviço conforme SIGTAP do procedimento.
         * default → "000"
         */
        sb.append(formatarServico(a.getSigtap()));    // classificação

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
         */
        sb.append(padRightSpaces("", 8));  // cep

        /**
         * seq 30 - prd-lograd_pcnte
         */
        sb.append(padRightSpaces("", 3));  // logradouro

        /**
         * seq 31 - prd-end_pcnte
         */
        sb.append(padRightSpaces("", 30)); // endereço

        /**
         * seq 32 - prd-compl_pcnte
         */
        sb.append(padRightSpaces("", 10)); // complemento

        /**
         * seq 33 - prd-num_pcnte
         */
        sb.append(padRightSpaces("", 5));  // número

        /**
         * seq 34 - prd-bairro_pcnte
         */
        sb.append(padRightSpaces("", 30)); // bairro

        /**
         * seq 35 - prd-ddtel_pcnte
         * Telefone do paciente
         * Deve conter apenas números, preenchido com espaços à direita até 11 caracteres.
         */
        sb.append(formatarTelefone(a.getTelefone())); // telefone

        /**
         * seq 36 - prd-email_pcnte
         */
        sb.append(padRightSpaces("", 40)); // email

        /**
         * seq 37 - prd-ine
         */
        sb.append(padLeftZeros(a.getCodIne(), 10));

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
     *
     * Regra:
     * - Apenas números
     * - Máximo de 11 dígitos
     * - Preencher com espaços à direita se menor que 11
     * - Default: espaços em branco
     *
     * Exemplo:
     * "(67) 99624-2913" -> "67996242913"
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
     * Quando não houver correspondência, retornar branco (default BPA-I).
     */
    private static final Map<String, String> MAP_SERVICO = new HashMap<>();

    static {
        MAP_SERVICO.put("0301010307", "000");
        MAP_SERVICO.put("0804010064", "009");
       // MAP_SERVICO.put("NOVO_CODIGO", "162");
    }

    private String formatarServico(String sigtap) {

        if (sigtap == null)
            return padRightSpaces("", 3);

        String sigtapNumerico = sigtap.replaceAll("[^0-9]", "");

        String servico = MAP_SERVICO.get(sigtapNumerico);

        return servico != null ? servico : padRightSpaces("", 3);
    }
}