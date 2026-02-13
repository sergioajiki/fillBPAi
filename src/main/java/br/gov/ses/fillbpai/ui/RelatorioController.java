package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller responsável pela TABELA DE RESULTADO.
 *
 * ✔ NÃO abre nova janela
 * ✔ NÃO consulta banco
 * ✔ NÃO contém regra de negócio
 * ✔ Apenas exibe dados recebidos
 *
 * Ele funciona como componente reutilizável dentro
 * da tela principal.
 */
public class RelatorioController {

    /**
     * Tabela visual exibida na tela.
     */
    private TableView<AtendimentoBPAiDTO> table;

    /**
     * Lista observável vinculada à tabela.
     * Permite atualização dinâmica dos dados.
     */
    private ObservableList<AtendimentoBPAiDTO> listaUI;

    /**
     * Cria o componente visual (layout da tabela).
     *
     * Este método deve ser chamado apenas UMA vez
     * pela tela principal (MainController).
     *
     * Depois disso, apenas atualizarDados() será chamado.
     */
    public BorderPane criarComponente() {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        table = new TableView<>();
        listaUI = FXCollections.observableArrayList();

        // Cria colunas dinamicamente
        adicionarColunas(table);

        // Vincula lista à tabela
        table.setItems(listaUI);

        root.setCenter(table);

        return root;
    }

    /**
     * Atualiza os dados exibidos na tabela.
     *
     * Deve ser chamado após cada importação.
     *
     * @param registrosImportados lista da execução atual
     */
    public void atualizarDados(List<AtendimentoBPAi> registrosImportados) {

        // Limpa dados anteriores
        listaUI.clear();

        if (registrosImportados == null || registrosImportados.isEmpty()) {
            return;
        }

        DateTimeFormatter dataFormat =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        DateTimeFormatter horaFormat =
                DateTimeFormatter.ofPattern("HH:mm");

        for (AtendimentoBPAi a : registrosImportados) {

            /*
             * Reconstrói estabelecimento no formato original da planilha:
             *
             * "123456 - HOSPITAL MUNICIPAL"
             *
             * No banco:
             * codEstabelecimento → 123456
             * estabelecimento → HOSPITAL MUNICIPAL
             */
            String estabelecimentoFormatado =
                    (a.getCodEstabelecimento() != null
                            ? a.getCodEstabelecimento() + " - "
                            : "")
                            + (a.getEstabelecimento() != null
                            ? a.getEstabelecimento()
                            : "");

            /*
             * Reconstrói especialidade + médico:
             *
             * "CARDIOLOGIA - JOÃO DA SILVA"
             *
             * No banco:
             * especialidadeMedico → CARDIOLOGIA
             * medico → JOÃO DA SILVA
             */
            String especialidadeMedicoFormatado =
                    (a.getEspecialidadeMedico() != null
                            ? a.getEspecialidadeMedico()
                            : "")
                            + (a.getMedico() != null && !a.getMedico().isEmpty()
                            ? " - " + a.getMedico()
                            : "");

            listaUI.add(new AtendimentoBPAiDTO(
                    a.getTipoServico(),
                    a.getDataAgendamento() != null
                            ? a.getDataAgendamento().format(dataFormat)
                            : "",
                    a.getHoraAtendimento() != null
                            ? a.getHoraAtendimento().format(horaFormat)
                            : "",
                    estabelecimentoFormatado,
                    especialidadeMedicoFormatado,
                    a.getCpfMedico(),
                    a.getCboMedico(),
                    a.getMunicipio(),
                    a.getCpfPaciente(),
                    a.getPaciente(),
                    a.getCnsPaciente(),
                    a.getRacaPaciente(),
                    a.getDataNascimento() != null
                            ? a.getDataNascimento().format(dataFormat)
                            : "",
                    a.getCidConsulta(),
                    a.getTelefone(),
                    a.getTipoZona(),
                    a.getEnderecoCompleto()
            ));
        }
    }

    /**
     * Cria dinamicamente as colunas da tabela.
     *
     * Usa reflexão para evitar repetição de código.
     */
    private void adicionarColunas(TableView<AtendimentoBPAiDTO> table) {

        table.getColumns().addAll(
                criarColuna("Tipo Serviço", "tipoServico"),
                criarColuna("Data Agendamento", "dataAgendamento"),
                criarColuna("Hora Atendimento", "horaAtendimento"),
                criarColuna("Estabelecimento", "estabelecimento"),
                criarColuna("Especialidade Médico", "especialidadeMedico"),
                criarColuna("CPF Médico", "cpfMedico"),
                criarColuna("CBO Médico", "cboMedico"),
                criarColuna("Município", "municipio"),
                criarColuna("CPF Paciente", "cpfPaciente"),
                criarColuna("Paciente", "paciente"),
                criarColuna("CNS Paciente", "cnsPaciente"),
                criarColuna("Raça Paciente", "racaPaciente"),
                criarColuna("Data Nascimento", "dataNascimento"),
                criarColuna("CID Consulta", "cidConsulta"),
                criarColuna("Telefone", "telefone"),
                criarColuna("Tipo Zona", "tipoZona"),
                criarColuna("Endereço Completo", "enderecoCompleto")
        );
    }

    /**
     * Método genérico para criação de coluna.
     *
     * @param titulo Nome exibido na coluna
     * @param propriedade Nome da propriedade no DTO
     */
    private TableColumn<AtendimentoBPAiDTO, String> criarColuna(
            String titulo,
            String propriedade
    ) {

        TableColumn<AtendimentoBPAiDTO, String> coluna =
                new TableColumn<>(titulo);

        coluna.setCellValueFactory(data ->
                new SimpleStringProperty(
                        getValor(data.getValue(), propriedade)
                )
        );

        coluna.setPrefWidth(150);

        return coluna;
    }

    /**
     * Usa reflection para chamar dinamicamente
     * o getter correspondente no DTO.
     *
     * Exemplo:
     * propriedade = "paciente"
     * chama -> getPaciente()
     */
    private String getValor(AtendimentoBPAiDTO dto, String propriedade) {

        try {
            Object valor = dto.getClass()
                    .getMethod("get" +
                            propriedade.substring(0, 1).toUpperCase() +
                            propriedade.substring(1))
                    .invoke(dto);

            return valor != null ? valor.toString() : "";

        } catch (Exception e) {
            return "";
        }
    }
}
