# Documentação Técnica — fillBPAi

> Gerada a partir de leitura completa do código-fonte em 09/09/2026. Cobre todas as classes de `src/main/java/br/gov/ses/fillbpai/`, pacote por pacote, com todos os métodos (públicos e privados relevantes) e o que cada um faz. É um documento de referência — para o desenho geral da arquitetura, fluxos e regras de negócio, ver `CLAUDE.md` na raiz do projeto.

## Sumário

- [Achados durante a documentação](#achados-durante-a-documentação)
- [app](#pacote-app)
- [config](#pacote-config)
- [controller](#pacote-controller)
- [ui](#pacote-ui)
- [service](#pacote-service)
- [util](#pacote-util)
- [repository](#pacote-repository)
- [model](#pacote-model)
- [dto](#pacote-dto)

---

## Achados durante a documentação

Estes pontos não são o objetivo desta documentação, mas apareceram durante a leitura linha a linha do código e valem registro:

- **Divergência entre `CLAUDE.md` e o código real do layout BPA-I** (já corrigida no README/CLAUDE.md). `GeradorBPAiService.montarRegistro` grava a seq 10 (`prd-cnspac`) como **15 espaços em branco** ("CNS não utilizado") — o CPF do paciente é gravado na seq 38 (`prd_cpf_pcnte`, 11 dígitos, zero-padded), não na seq 10. Ver seção [`GeradorBPAiService`](#brgovsesfillbpaiservicegeradorbpaiservice).
- ~~Dois métodos mortos, não referenciados em nenhum lugar do projeto: `util/HibernateUtil.java` e `GeradorBPAiService.formatarCpfParaBpa(String)`~~ — **removidos** do código. Esta documentação já não os lista.

> **Nota de desatualização parcial:** esta documentação foi gerada antes das sprints de testes (ver `docs/runbook-testes-sprint-0-2.html`). `ColunaAliasUtils` e `CnsProfissionalUtils` ganharam um seam de caminho configurável (`usarCaminhoParaTeste`) para testes, e `GeradorBPAiService` foi refatorado para extrair `gerarConteudoParcial`/`gerarConteudoCompleto` — nenhum dos três reflete essas mudanças nas seções abaixo ainda.

---

## Pacote `app`

### `br.gov.ses.fillbpai.app.MainApp`
`src/main/java/br/gov/ses/fillbpai/app/MainApp.java`

Classe principal `javafx.application.Application` — ponto de entrada da aplicação desktop. Inicializa a infraestrutura de banco, monta o layout raiz (`BorderPane`), cria o `MainController` e exibe a janela principal (1500×700); ao encerrar, libera os recursos de banco.

**Métodos:**
- `start(Stage primaryStage): void` — inicializa `DatabaseInitializer`, obtém o `EntityManager`, cria o `BorderPane` raiz, instancia `MainController(entityManager, root)`, monta a `Scene` e exibe a janela com título "Importador BPAi"; se a inicialização do banco falhar, imprime o erro e encerra a JVM (`System.exit(1)`).
- `stop(): void` — chama `databaseInitializer.finalizar()` para encerrar o banco/console H2 corretamente ao fechar a aplicação.
- `main(String[] args): void` — ponto de entrada estático que chama `launch(args)` do JavaFX, disparando o ciclo de vida `start`/`stop`.

---

## Pacote `config`

### `br.gov.ses.fillbpai.config.DatabaseInitializer`
`src/main/java/br/gov/ses/fillbpai/config/DatabaseInitializer.java`

Responsável por inicializar e encerrar a infraestrutura de persistência: sobe o console web do H2 e cria o `EntityManagerFactory`/`EntityManager` via JPA (persistence unit `bpaPU`). Mantém uma única instância de `EntityManager` para toda a vida da aplicação — adequado para o desktop de um usuário só; ver o runbook `docs/runbook-servico-web.html` para as implicações disso numa migração para web.

**Métodos:**
- `iniciar(): void` — inicia o console web do H2 (`Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082")`) e, em seguida, cria o `EntityManagerFactory`/`EntityManager` via `Persistence.createEntityManagerFactory("bpaPU")`; se a inicialização do JPA falhar, encerra a JVM (`System.exit(1)`) — a falha do console H2 é apenas logada, não é fatal.
- `getEntityManager(): EntityManager` — retorna a instância única de `EntityManager` criada em `iniciar()`.
- `finalizar(): void` — fecha o `EntityManager` (se aberto), fecha o `EntityManagerFactory` (se aberto) e para o servidor web do console H2, nessa ordem, capturando qualquer exceção para não impedir o encerramento.

---

## Pacote `controller`

### `br.gov.ses.fillbpai.controller.MainController`
`src/main/java/br/gov/ses/fillbpai/controller/MainController.java`

Controller principal da aplicação: monta a barra superior (Analisar Planilha, Ver Log, Configurações, Competência), orquestra o fluxo de análise/importação de planilha e delega a exibição de dados ao `RelatorioController`. Persiste os logs de importação e de erros de validação em arquivo, para consulta posterior.

**Métodos:**
- `MainController(EntityManager entityManager, BorderPane rootLayout)` (construtor) — guarda as dependências, instancia `FileChooserService`, `RelatorioController` e `ConfiguracoesDialog`, chama `configurarLayout()` e carrega os dados já existentes no banco (`relatorioController.carregarDoBanco()`).
- `configurarLayout(): void` — monta a topBar (botões "Analisar Planilha", "Ver Log Importação", "Configurações" — este último abre `ConfiguracoesDialog` — e o label de competência empurrado para a direita por um spacer), injeta as ações de "Ver Log" e "Analisar Planilha" no `RelatorioController`, e coloca o componente central do `RelatorioController` no `BorderPane` raiz.
- `processarImportacao(String caminho, Stage stage): void` — executa `AtendimentoImportacaoService.importar()` sobre o caminho informado; em caso de `RuntimeException`, exibe um `Alert` de erro; em caso de sucesso, monta o log, salva em arquivo, exibe o log e recarrega a tabela se houve ao menos um registro importado com sucesso.
- `montarConteudoLog(ImportacaoResultado resultado): String` — monta o texto do log de importação: totais (processados/sucesso/erro/avisos), lista de erros e lista de avisos, ou a mensagem "Importação concluída sem erros ou avisos." se não houver nenhum.
- `salvarLogEmArquivo(String conteudo, String nomePlanilha): void` — grava o conteúdo do log em `database/log_importacao_{nomePlanilha}.txt`, criando o diretório se necessário.
- `exibirLogSalvo(): void` — lê o arquivo de log de importação mais recente (`caminhoLog`) e o exibe; se o arquivo não existir, mostra um alerta informativo; usado pelo botão "Ver Log Importação".
- `mostrarLog(String conteudo): void` — exibe o texto do log em um `Alert` informativo com uma `TextArea` somente-leitura.
- `analisarPlanilha(Stage stage): void` — abre o seletor de arquivo; valida a planilha via `ValidacaoPlanilhaService.validar()`; se não houver erros, mostra o diálogo de análise OK (`mostrarDialogoAnaliseOk`); caso contrário, salva o log de erros em arquivo e decide entre três diálogos: erro estrutural (`mostrarDialogoErroEstrutura`), erros bloqueantes de linha (`mostrarDialogoErrosValidacao`) ou apenas avisos (`mostrarDialogoAvisosAnalise`).
- `mostrarDialogoAnaliseOk(String caminho, Stage stage): void` — exibe um `Alert` informativo "Nenhum erro encontrado" com o botão "Importar Planilha", que fecha o diálogo e chama `processarImportacao`.
- `mostrarDialogoAvisosAnalise(String logAvisos, String nomePlanilha, String caminho, Stage stage): void` — exibe um `Alert` de aviso com o texto dos avisos, botão "Salvar Log TXT" (exporta via `FileChooser`) e botão "Importar Planilha" (fecha o diálogo e chama `processarImportacao`).
- `salvarLogErrosEmArquivo(String conteudo, String nomePlanilha): void` — grava o log de erros de validação em `database/log_erros_validacao_{nomePlanilha}.txt`.
- `mostrarDialogoErroEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, String logErros, String nomePlanilha, Stage stage): void` — exibe um `Alert` de erro dedicado a problemas estruturais do cabeçalho: lista as colunas obrigatórias ausentes, as colunas do cabeçalho não reconhecidas (candidatas), instruções de correção, colunas duplicadas (se houver) e os botões "Abrir Configurações" (fecha o alerta e, via `Platform.runLater`, abre `ConfiguracoesDialog` — deferido para evitar o bug de diálogos modais empilhados, ver commit de correção) e "Salvar Log TXT".
- `blocoComTitulo(String titulo, List<String> itens): VBox` — monta um bloco visual com um título em negrito seguido de uma lista de itens com marcador "•".
- `mostrarDialogoErrosValidacao(String logErros, String nomePlanilha, Stage stage): void` — exibe um `Alert` de aviso com os erros de validação bloqueantes (sem botão de importação) e um botão "Salvar Log TXT" para exportar o log.
- `nomePlanilhaSemExtensao(String caminho): String` — extrai o nome do arquivo sem extensão e sanitiza caracteres inválidos para nome de arquivo (`\/:*?"<>|` → `_`), usado para nomear os arquivos de log.

---

## Pacote `ui`

### `br.gov.ses.fillbpai.ui.FileChooserService`
`src/main/java/br/gov/ses/fillbpai/ui/FileChooserService.java`

Serviço puro de UI, sem regra de negócio: abre o diálogo nativo de seleção de arquivo para escolher a planilha Excel a importar.

**Métodos:**
- `selecionarPlanilha(Stage stage): String` — abre um `FileChooser` com filtro para `*.xlsx` e título "Selecionar Planilha BPAi"; retorna o caminho absoluto do arquivo escolhido, ou `null` se o usuário cancelar o diálogo.

### `br.gov.ses.fillbpai.ui.ConfiguracoesDialog`
`src/main/java/br/gov/ses/fillbpai/ui/ConfiguracoesDialog.java`

Tela de configurações da aplicação, com duas abas: "Colunas da Planilha" (gerencia aliases de nome de cabeçalho por campo canônico, via `ColunaAliasUtils`) e "CNS de Médicos" (cadastro de médicos com apelidos de nome por CNS, via `CnsProfissionalUtils`). Ambas seguem o mesmo padrão visual: lista à esquerda, painel de detalhe/edição à direita.

**Métodos:**
- `abrir(Window owner): void` — monta e exibe (`showAndWait`) o `Dialog` de Configurações, com um `TabPane` contendo as abas "Colunas da Planilha" e "CNS de Médicos" e um único botão `CLOSE`.
- `criarPainelColunas(): SplitPane` — monta a aba de colunas: `ListView` com os campos canônicos (`ColunaAliasUtils.obterCamposCanonicos()`) à esquerda e painel de detalhe à direita, atualizado (`atualizarDetalhe`) a cada seleção da lista.
- `exibirPlaceholder(VBox detalhe): void` — substitui o conteúdo do painel de detalhe por um label "Selecione um campo à esquerda." (estado sem seleção).
- `atualizarDetalhe(VBox detalhe, String campo, ListView<String> listaCampos): void` — reconstrói o painel de detalhe do campo selecionado: título, subtítulo, chips de aliases (`criarChip`), campo de texto + botão "+ Adicionar" (grava um alias novo via `ColunaAliasUtils.salvar` e reconstrói o painel) e botão "Restaurar padrão desta coluna" (chama `ColunaAliasUtils.restaurarPadrao` e reconstrói o painel).
- `criarChip(ColunaAliasUtils.AliasInfo info, String campo, VBox detalhe, ListView<String> listaCampos): HBox` — monta o chip visual de um alias; aliases de origem PADRÃO são estilizados em verde e não têm botão de remoção, aliases LOCAL ganham um botão "×" que remove o alias (`ColunaAliasUtils.remover`) e reconstrói o painel.
- `criarPainelMedicos(): SplitPane` — monta a aba de médicos: campo de busca + `ListView` de médicos (nome principal) à esquerda, botão "+ Novo Médico" (limpa a seleção e abre `exibirFormularioCriacao`), painel de detalhe à direita; a busca (`textProperty` listener) refiltra a lista a cada digitação, e a seleção da lista abre `exibirFormularioEdicao` do médico correspondente.
- `exibirPlaceholderMedico(VBox detalhe): void` — substitui o painel de detalhe por um label "Selecione um médico à esquerda ou cadastre um novo." (estado sem seleção).
- `recarregarListaMedicos(ListView<String> listaMedicos, String filtro): void` — repõe os itens da `ListView` com os nomes principais dos médicos cujo nome principal ou algum apelido contém o texto de busca normalizado (via `TextoUtils.normalizar`), ordenados alfabeticamente (case-insensitive).
- `buscarMedicoPorNomePrincipal(String nomePrincipal): Optional<MedicoInfo>` — busca em `CnsProfissionalUtils.obterTodosMedicos()` o médico cujo nome principal é exatamente igual ao informado.
- `buscarMedicoPorCns(String cns): Optional<MedicoInfo>` — busca em `CnsProfissionalUtils.obterTodosMedicos()` o médico cujo CNS é exatamente igual ao informado.
- `atualizarAposMudancaMedico(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca, String cns): void` — recarrega a lista de médicos (mantendo o filtro de busca atual) e, se o CNS informado ainda existir, reseleciona o médico e reabre o formulário de edição com os dados atualizados; se o CNS não existir mais (médico removido), limpa a seleção e volta ao placeholder.
- `mostrarErro(Label lblErro, String mensagem): void` — define o texto da label de erro inline e a torna visível/gerenciada.
- `criarLabelErro(): Label` — cria uma `Label` de erro (texto vermelho, com quebra de linha) inicialmente invisível/não-gerenciada.
- `exibirFormularioEdicao(VBox detalhe, MedicoInfo info, ListView<String> listaMedicos, TextField campoBusca): void` — reconstrói o painel de detalhe em modo edição: título "Editando: {nome principal}", campo de CNS editável, lista de chips de apelidos (`criarChipApelido`), campo + botão "+ Adicionar apelido" (chama `CnsProfissionalUtils.adicionarApelido`, mostra erro inline em caso de `IllegalArgumentException`), botão "Salvar CNS" (normaliza e valida 15 dígitos, chama `CnsProfissionalUtils.alterarCns`, mostra erro inline em caso de falha) e botão "Remover Médico" (pede confirmação via `Alert` e, se confirmado, chama `CnsProfissionalUtils.removerMedico` e volta ao placeholder).
- `criarChipApelido(String apelido, MedicoInfo info, VBox detalhe, ListView<String> listaMedicos, TextField campoBusca, Label lblErro): HBox` — monta o chip visual de um apelido com botão "×" que chama `CnsProfissionalUtils.removerApelido` e atualiza o painel; se a remoção falhar (último apelido restante), mostra a mensagem de erro inline em vez de remover.
- `exibirFormularioCriacao(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca): void` — reconstrói o painel de detalhe em modo criação: campos de CNS e Nome, botão "Cadastrar" (valida nome não vazio e CNS de 15 dígitos, chama `CnsProfissionalUtils.cadastrar`, mostra erro inline em caso de falha) e botão "Cancelar" (limpa a seleção e volta ao placeholder).

### `br.gov.ses.fillbpai.ui.RelatorioController`
`src/main/java/br/gov/ses/fillbpai/ui/RelatorioController.java`

Controla a área principal da tela: tabela de atendimentos, filtros (especialidade/médico/busca livre), barra de edição (CNS/folha) e as ações de geração do BPA-I (parcial e completo), incluindo os avisos de pendência. Mantém em memória a lista observável de `AtendimentoBPAiDTO` (com filtro e ordenação encadeados) que alimenta a `TableView`.

**Métodos:**
- `RelatorioController(EntityManager entityManager)` (construtor) — guarda o `EntityManager` recebido e liga o botão "Selecionar Mês" à abertura do diálogo de competência.
- `setAcaoVerLog(Runnable acao): void` — injeta (do `MainController`) a ação executada ao clicar em "Ver Log Importação".
- `getBtnVerLog(): Button` — expõe o botão "Ver Log Importação" para ser posicionado na topBar pelo `MainController`.
- `setAcaoAnalisarPlanilha(Runnable acao): void` — injeta (do `MainController`) a ação executada ao clicar em "Analisar Planilha".
- `getBtnAnalisarPlanilha(): Button` — expõe o botão "Analisar Planilha" para ser posicionado na topBar pelo `MainController`.
- `getLabelCompetencia(): Label` — expõe o label de competência para ser posicionado na topBar pelo `MainController`.
- `getBtnSelecionarMes(): Button` — expõe o botão "Selecionar Mês" (não utilizado atualmente pelo `MainController`, mas disponível).
- `criarComponente(): BorderPane` — monta a área central da tela: cria a `TableView` (ligada à lista ordenada/filtrada, redimensionamento de coluna `UNCONSTRAINED`), o label de total, todas as colunas (`configurarColunas`), calcula a largura mínima da tabela, envolve em `ScrollPane` com rolagem horizontal explícita, e organiza em uma `VBox` com a barra de ações, barra de filtros, barra de edição, a tabela e o total.
- `criarBarraFiltros(): HBox` — monta a barra de filtros: campo de busca livre por médico + botão "Buscar", combo de especialidade (evento reseta a busca livre e atualiza os médicos disponíveis), grupo "Médico" (inicialmente oculto, some visível ao selecionar especialidade), botão "Gerar BPA-I", botão de aviso parcial "⚠" e botão "Limpar".
- `executarBuscaLivre(): void` — ao usar a busca livre por nome, reseta a seleção por combo (especialidade/médico) e reaplica os filtros; se houver termo e resultados, exibe a barra de edição.
- `criarBarraEdicao(): HBox` — monta a barra de edição (campo de CNS, botão "Atualizar CNS" ligado a `atualizarCns()`, botão "Definir Folha" ligado a `definirFolha()`), inicialmente oculta.
- `abrirDialogSelecionarCompetencia(): void` — abre um `Dialog` com spinners de mês/ano (pré-preenchidos com a competência já selecionada ou a data atual); ao confirmar, grava `competenciaSelecionada` no formato `yyyyMM` e atualiza o label de competência.
- `criarBarraAcoes(): HBox` — monta a barra de ações: liga o botão "Gerar BPA-I Completo" a `gerarBPACompleto()`, estiliza e liga o botão de aviso de pendências (amarelo, inicialmente oculto) a `mostrarAvisosGeracao()`, liga o botão "Gerar BPA-I" a `gerarBPA()`, estiliza e liga o botão de aviso parcial (amarelo, inicialmente oculto) a `mostrarAvisosParcial()`, e retorna a `HBox` com "Selecionar Mês", "Gerar BPA-I Completo" e o botão de aviso de pendências.
- `verificarAvisosGeracao(): void` — consulta o banco por atendimentos sem CNS do profissional; se houver, monta um relatório tabular (médico/paciente/data) em `avisosGeracao` e exibe o botão de aviso; caso contrário, oculta o botão e zera o aviso. Falhas de consulta são silenciosamente ignoradas.
- `verificarAvisosParcial(): void` — para o médico/especialidade selecionados no combo, conta atendimentos sem folha e sem CNS do profissional; se houver algum, monta a mensagem em `avisosParcial` e exibe o botão de aviso parcial (com tooltip); caso contrário, oculta o botão. Sem seleção de médico/especialidade, o aviso é limpo.
- `mostrarAvisosParcial(): void` — exibe em um `Alert` de aviso o conteúdo de `avisosParcial` (se houver).
- `mostrarAvisosGeracao(): void` — exibe em um `Alert` de aviso o conteúdo de `avisosGeracao` (se houver).
- `definirFolha(): void` — exige médico e especialidade selecionados; abre um `TextInputDialog` pré-preenchido com a folha atual (se existir); valida que o valor informado é numérico e que não está em uso por outro médico/especialidade; se válido, aplica a folha a todos os atendimentos do médico/especialidade (`aplicarFolha`) e recarrega a tabela.
- `obterFolhaAtual(String medico, String especialidade): String` — consulta o banco pela folha já atribuída (se houver) a um médico/especialidade.
- `verificarFolhaEmUso(String folha, String medicoAtual, String especialidadeAtual): String` — consulta se o número de folha já está em uso por outro médico/especialidade (diferente do atual); retorna o nome do médico conflitante ou `null`.
- `aplicarFolha(String folha, String medico, String especialidade): void` — dentro de uma transação, atualiza o campo `folha` de todos os `AtendimentoBPAi` do médico/especialidade informados; em caso de erro, faz rollback e exibe um alerta.
- `gerarBPA(): void` — exige especialidade, médico e competência selecionados e nenhum aviso parcial pendente; instancia `GeradorBPAiService` e chama `gerarArquivoComFileChooser` (geração parcial, filtrada); exibe mensagem de sucesso ou erro.
- `gerarBPACompleto(): void` — exige competência selecionada; instancia `GeradorBPAiService` e chama `gerarArquivoCompletoComFileChooser`; recarrega a tabela do banco (para exibir as folhas recém-atribuídas) e exibe mensagem de sucesso ou erro.
- `limparFiltros(): void` — limpa busca livre, seleção de especialidade/médico, campo de CNS, oculta o grupo Médico, a barra de edição e o aviso parcial, e reaplica os filtros (agora vazios).
- `atualizarCombos(): void` — repovoa o combo de especialidade com os valores distintos, não vazios e ordenados presentes na lista carregada.
- `atualizarMedicosPorEspecialidade(): void` — repovoa o combo de médico com os médicos distintos da especialidade selecionada, ordenados; oculta o grupo Médico se nenhuma especialidade estiver selecionada.
- `aplicarFiltros(): void` — recalcula o `Predicate` da lista filtrada combinando especialidade, médico e termo de busca livre (contains, case-insensitive) e atualiza o label de total.
- `habilitarEdicao(): void` — exibe a barra de edição e recalcula os avisos parciais.
- `atualizarCns(): void` — dentro de uma transação, resolve os atendimentos afetados (por combo médico+especialidade, ou por busca livre de nome caso o combo não esteja preenchido), aplica o novo valor do campo de CNS a todos eles, comita e recarrega a tabela; em caso de erro, faz rollback e exibe a mensagem.
- `configurarColunas(): void` — recria as 28 colunas fixas da tabela (Tipo Serviço, SIGTAP, Data, Hora, Estabelecimento, INE, Folha, Médico, Especialidade, CPF Médico, CBO, CNS Prof, Paciente, CPF Paciente, CNS Paciente, Sexo, Raça, Nascimento, Telefone, Município, CEP, Cód. Logradouro, Endereço, Complemento, Número, Bairro, Cód. IBGE, CID), cada uma via `criarColuna`.
- `criarColuna(String nome, Function<AtendimentoBPAiDTO, String> mapper): TableColumn<AtendimentoBPAiDTO, String>` — cria uma coluna de tabela com o nome e a função de extração de valor informados, largura fixa de 150px.
- `atualizarDados(List<AtendimentoBPAi> registros): void` — limpa e repopula a lista observável a partir das entidades (convertidas via `AtendimentoBPAiDTO.fromEntity`), atualiza os combos, reaplica os filtros e recalcula a competência exibida.
- `carregarDoBanco(): void` — limpa o cache L1 do `EntityManager`, consulta todos os `AtendimentoBPAi` (com fetch join de paciente/endereço/médico/estabelecimento), chama `atualizarDados` com o resultado e recalcula os dois avisos (geração completa e parcial).
- `atualizarCompetencia(): void` — se o usuário já selecionou manualmente a competência, não faz nada; caso contrário, deriva a competência (`yyyyMM`) a partir da data de agendamento do primeiro registro carregado e atualiza o label.
- `mostrarMensagem(String msg): void` — exibe um `Alert` do tipo informação com o texto informado.
- `mostrarAlerta(String msg): void` — exibe um `Alert` do tipo aviso com o texto informado.
- `mostrarErroGeracao(String mensagem): void` — exibe um `Alert` de erro com uma `TextArea` somente-leitura contendo a mensagem (usado para erros de geração do BPA-I, que podem ser longos).

---

## Pacote `service`

### `br.gov.ses.fillbpai.service.ImportacaoResultado`
`src/main/java/br/gov/ses/fillbpai/service/ImportacaoResultado.java`

DTO acumulador do resumo de uma execução de importação: contadores de processados/sucesso/erro/aviso, listas de mensagens de erro e aviso, e a lista de `AtendimentoBPAi` efetivamente importados. Instanciado e populado por `AtendimentoImportacaoService.importar()`, que é o único chamador.

**Métodos:**
- `adicionarSucesso(): void` — incrementa `totalProcessados` e `totalSucesso`; chamado a cada linha importada sem erro.
- `adicionarErro(String erro): void` — incrementa `totalProcessados` e `totalErro`, adiciona a mensagem à lista `erros`; chamado quando uma linha é rejeitada.
- `adicionarAviso(String aviso): void` — incrementa `totalAvisos` e adiciona a mensagem à lista `avisos`; a linha correspondente continua sendo importada.
- `adicionarAvisos(List<String> avisos): void` — chama `adicionarAviso` para cada item da lista recebida (usado para propagar os avisos vindos de `AtendimentoProcessor`).
- `getTotalProcessados()/getTotalSucesso()/getTotalErro()/getTotalAvisos(): int` — getters simples dos contadores.
- `getErros()/getAvisos(): List<String>` — getters das listas de mensagens.
- `getRegistrosImportados(): List<AtendimentoBPAi>` / `setRegistrosImportados(List<AtendimentoBPAi>): void` — getter/setter da lista de entidades persistidas nesta execução.

### `br.gov.ses.fillbpai.service.ErroValidacao`
`src/main/java/br/gov/ses/fillbpai/service/ErroValidacao.java`

`record` que representa um problema (bloqueante ou informativo) encontrado ao validar uma linha ou o cabeçalho de uma planilha: número da linha, severidade, código do tipo de erro e uma mensagem descritiva. Produzido por `ValidacaoPlanilhaService` e consumido pela UI (relatório de análise) e pelo log de erros.

**Constantes de tipo (`tipoErro`), com severidade real usada no código:**
- `CNS_INVALIDO` — **AVISO**, não bloqueia. CNS do paciente ausente ou com menos de 15 dígitos após normalização.
- `CNS_INCOMUM` — AVISO. CNS do paciente com mais de 15 dígitos (formato legado).
- `CEP_AUSENTE` — ERRO bloqueante. CEP do endereço ausente ou vazio.
- `CEP_INVALIDO` — ERRO bloqueante. CEP presente mas com tamanho diferente de 8 dígitos após normalização.
- `CPF_AUSENTE` — ERRO bloqueante. CPF do paciente ausente ou vazio.
- `CPF_INVALIDO` — ERRO bloqueante. CPF presente mas com tamanho diferente de 11 dígitos após normalização.
- `RACA_INDIGENA` — AVISO. Raça do paciente informada como Indígena — sinaliza necessidade de verificação manual da etnia.
- `ESTRUTURA_INVALIDA` — ERRO bloqueante. Coluna obrigatória ausente do cabeçalho, ou nome de coluna casando com mais de um campo canônico (ambíguo).

**Métodos:**
- `isBloqueante(): boolean` — retorna `true` se `severidade == Severidade.ERRO`.
- `isEstrutural(): boolean` — retorna `true` se `tipoErro` for `ESTRUTURA_INVALIDA`.

### `br.gov.ses.fillbpai.service.ValidacaoPlanilhaService`
`src/main/java/br/gov/ses/fillbpai/service/ValidacaoPlanilhaService.java`

Serviço acionado pelo botão "Analisar Planilha": lê a planilha inteira sem persistir nada no banco, e produz a lista de `ErroValidacao` (erros e avisos) para decidir se a importação pode prosseguir. Usa `ExcelImportService` para ler cada linha e `PlanilhaColumnMapper` para resolver as colunas do cabeçalho por nome.

**Métodos:**
- `validar(String caminhoArquivo): List<ErroValidacao>` — abre o `.xlsx`, lê a primeira aba, mapeia o cabeçalho via `PlanilhaColumnMapper.mapear`; se a estrutura tiver problema (`reportarEstrutura` retorna `false`), devolve só os erros estruturais e para. Caso contrário, percorre todas as linhas de dados (pulando a linha 0) chamando `validarLinha` para cada uma; linhas que lançam exceção na leitura são logadas e puladas, sem interromper as demais. Retorna a lista completa de erros/avisos encontrados (vazia se a planilha estiver válida).
- `validarLinha(LinhaImportacaoDTO dto, int linha, List<ErroValidacao> erros): void` (privado) — aplica 4 regras sobre uma linha já lida: (1) CNS do paciente — ausente/curto gera `CNS_INVALIDO` (AVISO), mais de 15 dígitos gera `CNS_INCOMUM` (AVISO); (2) CEP — ausente gera `CEP_AUSENTE` (ERRO), presente mas inválido (≠8 dígitos via `CepUtils.isValido`) gera `CEP_INVALIDO` (ERRO); (3) CPF do paciente — ausente gera `CPF_AUSENTE` (ERRO), inválido (≠11 dígitos via `CpfUtils.isValido`) gera `CPF_INVALIDO` (ERRO); (4) Raça — se normalizada (via `TextoUtils.normalizar`) for igual a "INDIGENA", gera `RACA_INDIGENA` (AVISO).
- `obterMapeamentoEstrutura(String caminhoArquivo): PlanilhaColumnMapper.ResultadoMapeamento` — relê só a linha de cabeçalho e devolve o mapeamento completo (campos encontrados/faltando/duplicados, colunas não reconhecidas), usado para montar um diagnóstico detalhado quando `validar` já indicou problema estrutural. Em caso de `IOException`, loga o erro e devolve `columnMapper.mapear(null)`.
- `reportarEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, List<ErroValidacao> erros): boolean` (privado) — para cada campo obrigatório ausente ou duplicado no mapeamento, adiciona um `ErroValidacao` com tipo `ESTRUTURA_INVALIDA` (ERRO); retorna `true` somente se não houve nenhum problema estrutural.
- `gerarLogTxt(List<ErroValidacao> erros, String nomeArquivo): String` — monta um relatório de texto com cabeçalho (nome do arquivo, data/hora, contagem de erros bloqueantes e avisos) seguido de duas seções separadas — "ERROS (impedem a importação)" e "AVISOS (não impedem a importação)" — cada uma listando linha, tipo e detalhe. Se `erros` estiver vazio, retorna uma mensagem indicando que a planilha está apta para importação.

### `br.gov.ses.fillbpai.service.PlanilhaColumnMapper`
`src/main/java/br/gov/ses/fillbpai/service/PlanilhaColumnMapper.java`

Resolve, a partir da linha de cabeçalho de uma planilha, o índice de coluna de cada campo canônico — por nome (via `ColunaAliasUtils`), não por posição fixa. É o que permite colunas fora de ordem e colunas extras não usadas sem quebrar a leitura. Chamado por `ValidacaoPlanilhaService` e `AtendimentoImportacaoService`, uma vez por planilha.

**Tipo aninhado:** `record ResultadoMapeamento(Map<String,Integer> indices, List<String> camposFaltando, List<String> camposDuplicados, List<String> colunasNaoReconhecidas, Map<String,List<String>> colunasPorCampoDuplicado)` — com o método `estruturaValida(): boolean` (true se `camposFaltando` e `camposDuplicados` estiverem ambos vazios).

**Métodos:**
- `mapear(Row cabecalho): ResultadoMapeamento` — para cada célula do cabeçalho, extrai o texto e resolve o campo canônico via `ColunaAliasUtils.resolverCampo`; texto sem correspondência é ignorado da leitura mas registrado em `colunasNaoReconhecidas` (para diagnóstico). Se mais de uma coluna do cabeçalho casar com o mesmo campo canônico, esse campo entra em `camposDuplicados` (com as colunas conflitantes listadas em `colunasPorCampoDuplicado`). Todo campo de `ColunaAliasUtils.obterCamposCanonicos()` que não apareceu no cabeçalho entra em `camposFaltando`. Se `cabecalho` for `null`, devolve um resultado com todos os campos canônicos como faltando.
- `extrairTexto(Cell celula): String` (privado) — devolve o texto da célula: `getStringCellValue()` se for tipo STRING, senão `celula.toString()`; `null` se a célula for `null`.

### `br.gov.ses.fillbpai.service.ExcelImportService`
`src/main/java/br/gov/ses/fillbpai/service/ExcelImportService.java`

Camada de leitura pura do Excel: converte uma `Row` do POI em `LinhaImportacaoDTO`, sem nenhuma validação ou conversão de tipo (tudo fica como `String`). É reaproveitado tanto por `ValidacaoPlanilhaService` quanto por `AtendimentoImportacaoService`.

**Métodos:**
- `importarLinha(Row row, Map<String,Integer> colunas): LinhaImportacaoDTO` — usa o mapa de colunas (produzido por `PlanilhaColumnMapper`) para buscar cada célula pelo campo canônico e preencher os 24 campos do DTO (`TIPO_SERVICO`, `DATA_AGENDAMENTO`, `HORA_ATENDIMENTO`, `ESTABELECIMENTO`, `ESPECIALIDADE_MEDICO`, `MEDICO`, `CPF_MEDICO`, `CBO_MEDICO`, `MUNICIPIO`, `CPF_PACIENTE`, `PACIENTE`, `CNS_PACIENTE`, `RACA_PACIENTE`, `DATA_NASCIMENTO`, `CID_CONSULTA`, `TELEFONE`, `TIPO_ZONA`, `COD_LOGRADOURO`, `ENDERECO`, `CEP`, `NUMERO`, `BAIRRO`, `COMPLEMENTO`, `SEXO_PACIENTE`). Especialidade e nome do médico já chegam em colunas separadas (comentário no código indica que antes vinham combinadas em "ESPECIALIDADE - NOME").
- `getString(Row row, Map<String,Integer> colunas, String campo): String` (privado, sobrecarga 1) — resolve o índice do campo no mapa; se o campo não estiver mapeado (não encontrado no cabeçalho), devolve `null` em vez de lançar exceção. Delega para a sobrecarga que recebe `Cell`.
- `getString(Cell cell): String` (privado, sobrecarga 2) — converte qualquer tipo de célula POI em `String`: `STRING` → texto trimado; `NUMERIC` com formatação de data → `LocalDate`/`LocalTime` em ISO (trata o caso especial de células só de hora, que o Excel armazena como data-base 1899, retornando apenas a hora); `NUMERIC` comum → formatado via `DecimalFormat("0")` para evitar notação científica; `BOOLEAN` → `"true"/"false"`; `FORMULA` → a fórmula como texto (não avaliada); `BLANK` → `null`; qualquer outro tipo → `toString().trim()`.

### `br.gov.ses.fillbpai.service.AtendimentoProcessor`
`src/main/java/br/gov/ses/fillbpai/service/AtendimentoProcessor.java`

Pipeline de normalização/validação/conversão que roda sobre o `LinhaImportacaoDTO` já lido, antes da persistência — não acessa banco de dados. Chamado por `AtendimentoImportacaoService` para cada linha, dentro do loop de importação.

**Métodos:**
- `processar(LinhaImportacaoDTO dto): List<String>` — executa em ordem: (1) `separarEstabelecimento`; (2) define o SIGTAP via `definirSigtap`; (3) normaliza CPF, CEP, sexo e limita tamanho de campos de endereço; (4) valida campos obrigatórios, CEP, CPF, CNS (coleta avisos) e raça indígena (coleta avisos); (5) converte as strings de data/hora para `LocalDate`/`LocalTime`. Lança `IllegalArgumentException` para qualquer violação bloqueante; devolve a lista de avisos não bloqueantes (CNS incomum/ausente, raça indígena).
- `validarCns(LinhaImportacaoDTO dto): List<String>` (privado) — delega a `CnsUtils.processar`, atualiza `dto.cnsPaciente` com o valor normalizado (só dígitos) e devolve os avisos gerados por aquele utilitário.
- `definirSigtap(String tipoServico): String` (privado) — mapeia `"TELECONSULTA"` → `"03.01.01.030-7"` e `"TELEINTERCONSULTA"` → `"08.04.01.006-4"` (case-insensitive, trim); qualquer outro valor lança `IllegalArgumentException`; `null` devolve `null`.
- `separarEstabelecimento(LinhaImportacaoDTO dto): void` (privado) — se o campo `estabelecimento` estiver no formato combinado `"CÓDIGO - NOME"`, usa `StringUtils.separarCodigoENome` para preencher `codEstabelecimento` e `estabelecimento` (nome) separadamente. Não faz nada se o campo estiver vazio.
- (Há um método `separarEspecialidadeEMedico` comentado/desativado no código — a planilha atual já traz especialidade e médico em colunas separadas, então não é chamado.)
- `validarCep(LinhaImportacaoDTO dto): void` (privado) — se `cep` estiver presente mas `CepUtils.isValido` retornar `false`, lança `IllegalArgumentException`.
- `validarCpf(LinhaImportacaoDTO dto): void` (privado) — mesma lógica para `cpfPaciente` via `CpfUtils.isValido`.
- `verificarRacaIndigena(LinhaImportacaoDTO dto): List<String>` (privado) — normaliza a raça (remove acento, uppercase) e, se for `"INDIGENA"`, devolve uma lista com um aviso; senão lista vazia.
- `validarCamposObrigatorios(LinhaImportacaoDTO dto): void` (privado) — lança `IllegalArgumentException` se `paciente`, `cpfPaciente` ou `dataAgendamentoString` estiverem vazios.
- `converterDatas(LinhaImportacaoDTO dto): void` (privado) — converte `dataAgendamentoString`→`dataAgendamento`, `horaAtendimentoString`→`horaAtendimento` e `dataNascimentoString`→`dataNascimento`, usando `DateUtils.parse`/`TimeUtils.parse`; qualquer falha de parse é reembalada como `IllegalArgumentException` com mensagem específica do campo.
- `normalizarCpf(LinhaImportacaoDTO dto): void` (privado) — aplica `CpfUtils.normalizar` em `cpfPaciente` e `cpfMedico` (remove pontuação).
- `normalizarCep(LinhaImportacaoDTO dto): void` (privado) — aplica `CepUtils.normalizar` em `cep`.
- `normalizarSexo(LinhaImportacaoDTO dto): void` (privado) — converte valores abreviados ou por extenso (`F`/`FEMININO`, `M`/`MASCULINO`, `I`/`INDETERMINADO`, case-insensitive) para o código de 1 caractere usado no BPA-I; valor não reconhecido é mantido como veio.
- `limitarCamposBanco(LinhaImportacaoDTO dto): void` (privado) — trunca (via `StringUtils.limitarTamanho`) `endereco`/`bairro` a 30 caracteres, `complemento` a 10, `numero` a 5 — limites do layout BPA-I (seq 31/34/32/33).
- `isNullOrEmpty(String value): boolean` (privado) — utilitário local de nulidade/vazio usado por todos os métodos acima.

### `br.gov.ses.fillbpai.service.AtendimentoImportacaoService`
`src/main/java/br/gov/ses/fillbpai/service/AtendimentoImportacaoService.java`

Orquestrador da importação completa: lê o Excel, aplica `AtendimentoProcessor`, resolve/cria as entidades normalizadas (`Paciente`, `Medico`, `Estabelecimento`, `Endereco`) e persiste o `AtendimentoBPAi`, tudo dentro de uma única transação JPA. É a classe chamada pelo botão "Importar Planilha" da UI.

**Construtor:** `AtendimentoImportacaoService(EntityManager entityManager)` — instancia os 4 repositórios (`AtendimentoBPAiRepository`, `PacienteRepository`, `MedicoRepository`, `EstabelecimentoRepository`) a partir do `EntityManager` recebido, além de `ExcelImportService`, `PlanilhaColumnMapper` e `AtendimentoProcessor`.

**Métodos:**
- `importar(String caminhoArquivo): ImportacaoResultado` — abre o `.xlsx`, mapeia o cabeçalho (`PlanilhaColumnMapper.mapear`); se a estrutura for inválida, lança `IllegalStateException` com a descrição do problema (checagem repetida como proteção mesmo que "Analisar Planilha" já tenha validado antes). Pré-carrega o cache de IBGE do banco (`IbgeUtils.preCarregarCacheDb`) e o mapa de folhas já atribuídas (`buscarMapaFolhaPorEspecialidadeMedico`). Abre uma transação e, para cada linha (exceto cabeçalho): lê via `ExcelImportService`, processa via `AtendimentoProcessor` (coletando avisos), chama `criarOuAtualizarAtendimento`, e incrementa sucesso; qualquer exceção na linha é capturada e registrada como erro (`resultado.adicionarErro`) sem interromper as demais linhas. Ao final, comita a transação; se houver falha de IO ou outra exceção fora do loop por linha, faz rollback e relança como `RuntimeException`. Devolve o `ImportacaoResultado` populado, incluindo a lista de atendimentos importados.
- `criarOuAtualizarAtendimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel, Map<String,String> mapaFolhas): AtendimentoBPAi` (privado) — resolve paciente (`buscarOuCriarPaciente`), atualiza endereço (`atualizarEndereco`, que pode gerar aviso de IBGE), resolve médico (`buscarOuCriarMedico`) e estabelecimento (`buscarOuCriarEstabelecimento`). Busca um atendimento já existente com a mesma chave natural (paciente + médico + data + sigtap) via `atendimentoRepository.buscarDuplicata`; se encontrado, é uma atualização (gera aviso informativo) em vez de criar duplicata — permite reimportar a mesma planilha sem duplicar registros. Atualiza os campos do atendimento (tipo de serviço, sigtap, data, hora, especialidade, CBO, CID). Para atendimento novo sem folha definida, tenta herdar a folha já atribuída ao mesmo médico/especialidade no mês (via `mapaFolhas`). Por fim, resolve o CNS do profissional por nome via `CnsProfissionalUtils.buscar`, preenchendo `cnsProfissional` se encontrado e adicionando aviso ao resultado caso contrário.
- `buscarOuCriarPaciente(LinhaImportacaoDTO dto): Paciente` (privado) — busca por CPF; se existe, atualiza nome/CNS/sexo/raça/nascimento/telefone com os dados da importação atual; se não existe, cria um novo e salva.
- `atualizarEndereco(Paciente paciente, LinhaImportacaoDTO dto): String` (privado) — cria o `Endereco` do paciente se não existir; atualiza município, tipo de zona, CEP, complemento, número, bairro. Resolve o tipo/nome de logradouro via `LogradouroUtils.resolver` (usa o código derivado se houver, senão mantém o `codLogradouro` já informado na planilha). Resolve o código IBGE via `IbgeUtils.resolver(cep, municipio)` e devolve o aviso opcional desse utilitário (ou `null`).
- `buscarOuCriarMedico(LinhaImportacaoDTO dto): Medico` (privado) — se o CPF do médico estiver vazio, cria um registro "mínimo" com CPF sintético `"SEM_CPF_" + System.nanoTime()` e o nome informado (evita bloquear a importação por falta de CPF do profissional); senão, busca por CPF e atualiza o nome, ou cria novo.
- `buscarOuCriarEstabelecimento(LinhaImportacaoDTO dto): Estabelecimento` (privado) — se o código do estabelecimento estiver vazio, devolve `null`; senão busca por código e atualiza o nome, ou cria novo.
- `resolverChaveFolha(String especialidade, Long medicoId): String` (privado) — monta a chave `especialidade + "|" + medicoId`, no mesmo formato usado por `AtendimentoBPAiRepository.buscarMapaFolhaPorEspecialidadeMedico`.
- `descreverProblemaEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento): String` (privado) — monta uma frase legível listando colunas obrigatórias ausentes e/ou duplicadas, para a mensagem de `IllegalStateException`.
- `carregarCepIbgeDoBanco(): Map<String,String>` (privado) — executa uma JPQL (`SELECT e.cep, e.codigoIbge FROM Endereco e WHERE ...`) para montar o mapa CEP→IBGE de todos os endereços já persistidos, usado para pré-popular o cache de `IbgeUtils` antes do loop (evita chamadas repetidas à API ViaCEP); qualquer falha é silenciosamente ignorada (cache fica vazio, não interrompe a importação).

### `br.gov.ses.fillbpai.service.GeradorBPAiService`
`src/main/java/br/gov/ses/fillbpai/service/GeradorBPAiService.java`

Gera o arquivo magnético BPA-I: header de 132 caracteres + um registro por atendimento, codificação ISO-8859-1, quebra de linha CRLF. Tem duas variantes de geração — parcial (por especialidade+médico) e completa (todos os médicos de uma competência) — ambas acopladas hoje a `javafx.stage.Window`/`FileChooser` para escolher o destino do arquivo (ver `docs/runbook-servico-web.html` para o que isso implica numa migração para web).

**Métodos:**
- `gerarArquivoComFileChooser(Window parentWindow, String especialidade, String medico, String competenciaAtendimento): void` — busca (via JPQL com `JOIN FETCH` de paciente/endereço/médico/estabelecimento) os atendimentos daquele médico/especialidade no ano/mês informados; lança `RuntimeException` se a lista vier vazia. Chama `validarCnsProfissional` (bloqueia se faltar CNS em qualquer registro). Calcula a competência de faturamento (mês do atendimento) e abre um `FileChooser` sugerindo o nome `especialidade_medico_competencia.txt`; se o usuário cancelar, retorna sem escrever nada. Escreve o header (`montarHeader`) seguido de um registro por atendimento (`montarRegistro`), com sequencial reiniciando ao passar de 99.
- `gerarArquivoCompletoComFileChooser(Window parentWindow, String competenciaAtendimento): void` — busca todos os atendimentos do ano informado e filtra em memória os que pertencem à competência de faturamento alvo (mês de atendimento + 1, calculado por `calcularCompetencia`); lança `RuntimeException` se vazio. Valida CNS (`validarCnsProfissional`). Ordena a lista por especialidade → nome do médico → mês, todos alfabético/numérico crescente. Atribui folhas em memória (`atribuirFolhas`, sem persistir no banco). Abre `FileChooser` sugerindo `BPA-I_COMPLETO_competencia.txt`. Escreve o header completo (`montarHeaderCompleto`, com o total de folhas real) e um registro por atendimento, reiniciando o sequencial a cada troca de folha (e também ao passar de 99).
- `atribuirFolhas(List<AtendimentoBPAi> lista, int atenAno): int` (privado) — consulta (JPQL `DISTINCT`) todas as combinações (especialidade, nome do médico, mês) do ano inteiro, ordenadas deterministicamente; atribui um número de folha sequencial a cada combinação nova encontrada (mapa em memória, não persistido); aplica a folha correspondente a cada atendimento da lista do mês atual; devolve a contagem de combinações especialidade+médico distintas presentes na lista (para o header).
- `garantirExtensaoTxt(File file): File` (privado) — se o nome já terminar em `.txt` devolve como está; senão acrescenta `.txt` (o diálogo nativo do Windows nem sempre preserva a extensão sugerida).
- `montarChaveMedico(AtendimentoBPAi a): String` / `montarChaveMedicoMes(AtendimentoBPAi a): String` (privados) — montam chaves de agrupamento `especialidade|médico` e `especialidade|médico|mês`, usadas por `atribuirFolhas`.
- `validarCnsProfissional(List<AtendimentoBPAi> lista): void` (privado) — filtra os atendimentos sem `cnsProfissional` preenchido; se houver algum, monta um relatório de texto (tabela Médico/Paciente/Data, nomes truncados a 30 chars) e lança `RuntimeException` com esse relatório, bloqueando totalmente a geração.
- `montarHeaderCompleto(List<AtendimentoBPAi> lista, String competencia, int totalFolhas): String` (privado) — monta os 12 primeiros campos do header (132 chars): `"01"` (ident), `"#BPA#"`, competência (6, zero-padded), total de linhas (6), `totalFolhas` recebido como parâmetro (6), soma de verificação (`calcularSomaVerificacao`, 4), nome do órgão (`"NUCLEO DE TELESSAUDE DE MS"`, 30, espaços à direita), sigla (`"NTMS"`, 6), CNPJ fixo `"02955271000126"` (14, zeros à esquerda), nome do órgão emissor (`"SECRETARIA ESTADUAL DE SAUDE"`, 40), `"E"` (indicador fixo), versão do layout (`"ED04.10"`, 10).
- `montarHeader(List<AtendimentoBPAi> lista, String competencia): String` (privado) — mesma estrutura de campos que `montarHeaderCompleto`, mas usa `lista.size()` tanto para total de linhas quanto para total de folhas (usado só na geração parcial, onde cada atendimento da lista é considerado uma "folha" própria).
- `montarRegistro(AtendimentoBPAi a, String competencia, int sequencial): String` (privado) — monta a linha de registro campo a campo, na ordem do layout BPA-I: seq 1 `"03"` (ident); seq 2 `cnesNts` (7, NUM); seq 3 competência AAAAMM da data de atendimento (não a de faturamento); seq 4 CNS do profissional (15, NUM opcional — brancos se vazio); seq 5 CBO (6, ALFA); seq 6 data de atendimento AAAAMMDD; seq 7 folha (3, zero-padded); seq 8 sequencial dentro da folha (2, zero-padded, reinicia acima de 99); seq 9 código do procedimento SIGTAP (10) — com uma regra especial: se a especialidade for `NUTRICIONISTA` ou `PSICÓLOGO`, força o código fixo `0301010315` em vez do SIGTAP do atendimento; **seq 10 `prd-cnspac` — 15 espaços em branco** (comentário no código: "CNS não utilizado" — ver [achado no topo do documento](#achados-durante-a-documentação)); seq 11 sexo do paciente (`F`/`M`/`I`, default `F` se ausente); seq 12 código IBGE truncado para 6 dígitos (remove o dígito verificador), branco se não resolvido; seq 13 CID (4, `formatarCid` upper-case e só A-Z0-9); seq 14 idade calculada na data de geração (`calcularIdade`, 3, zero-padded); seq 15 quantidade fixa `"000001"`; seq 16 `"01"` (caráter de atendimento); seq 17 branco (13); seq 18 `"BPA"` (órgão); seq 19 nome do paciente (30, ALFA, sanitizado para ISO-8859-1); seq 20 data de nascimento (8, NUM opcional); seq 21 raça (`formatarRaca`, código de 2 dígitos); seq 22 etnia (branco, 4); seq 23 nacionalidade fixa `"010"`; seq 24 serviço (`formatarServico`, mapeado a partir do SIGTAP efetivo); seq 25 classificação (`formatarClassificacao`, idem); seq 26/27 equipe seq/área (brancos); seq 28 CNPJ (branco, 14); seq 29 CEP do paciente (8, NUM opcional); seq 30 código de logradouro (3, NUM **obrigatório** — default `"081"` se nulo, ou `"0"` se vazio após limpeza); seq 31 endereço (30, ALFA); seq 32 complemento (10, ALFA); seq 33 número (5, ALFA); seq 34 bairro (30, ALFA); seq 35 telefone (11, `formatarTelefone`); seq 36 e-mail (branco, 40); seq 37 código INE (10, NUM opcional); **seq 38 CPF do paciente (11, zero-padded — é aqui, não em seq 10, que o CPF do paciente entra no registro)**; seq 39 situação de rua fixo `"N"`. O CRLF final (seq 40) é escrito pelo chamador, não por este método.
- `calcularIdade(LocalDate nascimento): int` (privado) — `Period.between(nascimento, LocalDate.now()).getYears()`; devolve `0` se `nascimento` for `null`.
- `calcularSomaVerificacao(List<AtendimentoBPAi> lista): int` (privado) — soma o valor numérico do SIGTAP de cada atendimento mais 1 por registro, depois aplica `(soma % 1111) + 1111` — é o checksum do header (seq 6).
- `padNumOpcional(String valor, int tamanho): String` (privado) — remove não-dígitos; se ficar vazio, preenche com espaços (branco = "não informado"); senão zero-padded à esquerda.
- `padLeftZeros(String valor, int tamanho): String` (privado) — remove não-dígitos e completa com zeros à esquerda até o tamanho (não corta se já for maior).
- `padRightSpaces(String valor, int tamanho): String` (privado) — `String.format("%-Ns", valor)`, completando com espaços à direita.
- `somenteNumeros(String valor): String` (privado) — remove todo caractere não numérico.
- `formatarCid(String cid): String` (privado) — upper-case, remove caracteres fora de `[A-Z0-9]`, trunca a 4 posições.
- `formatarAlfa(String valor, int tamanho): String` (privado) — remove quebras de linha/tabs, aplica `sanitizarLatin1`, e trunca ao tamanho máximo (sem padding — o padding à direita é responsabilidade de quem chama, via `padRightSpaces`).
- `sanitizarLatin1(String valor): String` (privado) — substitui caractere a caractere qualquer código acima de `0xFF` (fora do ISO-8859-1) por um equivalente ASCII quando reconhecido (aspas curvas, en/em-dash, reticências) ou por espaço, evitando exceção ao gravar o arquivo em ISO-8859-1.
- `formatarRaca(String raca): String` (privado) — mapeia por substring (case-insensitive, upper-case): contém `"BRANC"`→`"01"`, `"PRET"`→`"02"`, `"PARD"`→`"03"`, `"AMAREL"`→`"04"`, `"IND"`→`"05"`; qualquer outro caso (incluindo `null`) →`"99"`.
- `formatarTelefone(String telefone): String` (privado) — remove não-dígitos, limita a 11 dígitos, preenche com espaços à direita até 11; telefone ausente devolve 11 espaços.
- `formatarServico(String sigtap): String` (privado) — remove não-dígitos e busca em `MAP_SERVICO` (mapa estático: `0301010307`→`"160"`, `0804010064`→`"160"`, `0301010315`→`"160"`); sem correspondência, devolve 3 espaços.
- `formatarClassificacao(String sigtap): String` (privado) — mesma lógica, usando `MAP_CLASSIFICACAO` (`0301010307`→`"006"`, `0804010064`→`"009"`, `0301010315`→`"006"`).
- `calcularCompetencia(LocalDate dataAtendimento): String` (privado) — formata a data como `yyyyMM`; lança `RuntimeException` se a data for `null`.
- `padNumObrigatorio(String valor, int tamanho): String` (privado) — como `padNumOpcional`, mas nunca produz branco: `valor` nulo vira `"081"` antes de limpar, e se ficar vazio após remover não-dígitos vira `"0"`; sempre zero-padded. Usado só no código de logradouro (seq 30).

---

## Pacote `util`

### `br.gov.ses.fillbpai.util.DateUtils`
`src/main/java/br/gov/ses/fillbpai/util/DateUtils.java`

Utilitário de conversão de texto para `LocalDate`, tentando múltiplos formatos aceitos em planilhas, para centralizar o parsing de datas num único lugar.

**Métodos:**
- `parse(String dataStr): LocalDate` — converte a string para `LocalDate` tentando, em ordem, os formatos `dd/MM/yyyy`, `yyyy-MM-dd` e `dd-MM-yyyy` (aplica `trim()` antes de tentar). Lança `IllegalArgumentException` se `dataStr` for `null`/vazio, ou se nenhum dos três formatos conseguir interpretar o valor.

### `br.gov.ses.fillbpai.util.TimeUtils`
`src/main/java/br/gov/ses/fillbpai/util/TimeUtils.java`

Utilitário de conversão de texto para `LocalTime`, tolerante a variações de formato comuns em planilhas de atendimento.

**Métodos:**
- `parse(String timeStr): LocalTime` — converte a string para `LocalTime`. Primeiro faz `trim()` e substitui todo `.` por `:` (ex.: `"08.30"` → `"08:30"`), depois tenta, em ordem, os formatos `HH:mm`, `H:mm`, `HH:mm:ss` e `HHmm`. Lança `IllegalArgumentException` se `timeStr` for `null`/vazio, ou se nenhum formato bater após a substituição.

### `br.gov.ses.fillbpai.util.IbgeUtils`
`src/main/java/br/gov/ses/fillbpai/util/IbgeUtils.java`

Resolve o código IBGE (7 dígitos) de um município em cascata: primeiro por nome via CSV embutido (sem rede), depois por CEP via cache pré-carregado do banco, e por último via chamada à API pública ViaCEP como fallback. Mantém cache em memória dos resultados de CEP (inclusive negativos) para nunca repetir uma chamada de rede para o mesmo CEP.

**Métodos:**
- `preCarregarCacheDb(Map<String,String> cepParaIbge): void` — popula o cache de CEP→IBGE com dados já persistidos no banco (chamado antes do loop de importação); só insere entradas que ainda não estão no cache (`putIfAbsent`) e ignora valores de IBGE nulos/vazios.
- `resolver(String cep, String nomeMunicipio): IbgeResultado` — orquestra a cascata: (1) se `nomeMunicipio` não vazio, tenta `buscarPorNome`; se achar, retorna sem aviso; (2) senão, se `cep` tiver pelo menos 5 caracteres, tenta `buscarPorCep` (cache/API); se achar, retorna com aviso informando que veio por CEP, não pelo nome; (3) se nada resolver, retorna `null` com aviso de que `prd-ibge` ficará em branco.
- `buscarPorCep(String cep): String` *(pacote-privado, estático)* — verifica o cache em memória primeiro (`cacheViaCep`); se ausente, faz `GET https://viacep.com.br/ws/{cep}/json/` com timeout de 5s; se status ≠ 200, retorna `null`; se a resposta contiver `"erro":true`, grava `null` no cache (cache negativo) e retorna `null`; senão extrai o campo `"ibge"` via regex, cacheia e retorna. Qualquer exceção (timeout, sem internet, parse) é logada como aviso e retorna `null`.
- `buscarPorNome(String nomeMunicipio): String` *(pacote-privado, estático)* — carrega o CSV se necessário e busca no mapa pelo nome normalizado (sem acento, uppercase).
- `carregarCsvSeNecessario(): void` *(privado, estático, `synchronized`)* — lazy-load do CSV `/dados/municipios_ibge.csv` do classpath (formato `codigo_ibge;nome_municipio;uf`, UTF-8, ignora linhas vazias/cabeçalho iniciado em "codigo"), indexando por nome normalizado.
- `normalizar(String texto): String` *(pacote-privado, estático)* — remove acentos via `Normalizer.Form.NFD` + regex de marcas combinantes, `trim()` e `toUpperCase()`. Retorna `""` para `null`.
- **Classe interna `IbgeResultado`** — record simples com `getCodigoIbge()` (código de 7 dígitos ou `null`) e `getAviso()` (mensagem ou `null`).

### `br.gov.ses.fillbpai.util.LogradouroUtils`
`src/main/java/br/gov/ses/fillbpai/util/LogradouroUtils.java`

Detecta o tipo de logradouro (Rua/Avenida/Travessa e variantes) a partir do início do texto do endereço, retornando o código BPA-I correspondente e o endereço sem o prefixo.

**Métodos:**
- `resolver(String enderecoRaw): LogradouroResultado` — se a entrada for `null`/vazia, retorna `codLogradouro=null` e o endereço original. Senão, testa prefixos em cascata (nesta ordem, para evitar match parcial): `"TRAVESSA "`/`"TRAVESSA"` → código `100`; `"TRAV. "`/`"TRAV."` → `100`; `"TV "`/`"TV"` → `100`; `"AVENIDA "`/`"AVENIDA"` → `008`; `"AV. "`/`"AV."` → `008`; `"AV "`/`"AV"` → `008`; `"RUA "`/`"RUA"` → `081`. Em cada caso retorna o endereço com o prefixo removido e `trim()`. Se nenhum prefixo bater, retorna `codLogradouro=null` com o endereço original (só `trim()`, sem remoção de prefixo).
- **Classe interna `LogradouroResultado`** — record simples com `getCodLogradouro()` e `getEndereco()`.

### `br.gov.ses.fillbpai.util.CnsUtils`
`src/main/java/br/gov/ses/fillbpai/util/CnsUtils.java`

Centraliza validação/normalização do CNS (Cartão Nacional de Saúde) do **paciente**. Regra: 15 dígitos é o padrão atual; menos que isso ou ausente gera aviso (não bloqueia); mais que 15 é aceito como "formato legado" com aviso.

**Métodos:**
- `normalizar(String cns): String` — remove todo caractere não numérico (`replaceAll("[^0-9]", "")`); retorna `null` se a entrada for `null`.
- `processar(String cns): CnsResultado` — normaliza e classifica: se vazio/nulo após normalização → aviso "CNS do paciente não informado... (CNS_INVALIDO)", retorna CNS `""`; se < 15 dígitos → aviso "CNS inválido (N dígitos, mínimo 15)... (CNS_INVALIDO)", retorna o CNS parcial; se > 15 dígitos → aviso "CNS com formato incomum", mas retorna o CNS completo como válido; se exatamente 15 → sem avisos. Nunca lança exceção.
- **Classe interna `CnsResultado`** — record simples com `getCns()` (valor normalizado) e `getAvisos()` (lista, vazia se nada a reportar).

### `br.gov.ses.fillbpai.util.CepUtils`
`src/main/java/br/gov/ses/fillbpai/util/CepUtils.java`

Normalização e validação de CEP.

**Métodos:**
- `normalizar(String cep): String` — remove todo caractere não numérico; retorna `null` se a entrada for `null` ou em branco (após `trim()`).
- `isValido(String cep): boolean` — normaliza e verifica se o resultado tem exatamente 8 dígitos.

### `br.gov.ses.fillbpai.util.CpfUtils`
`src/main/java/br/gov/ses/fillbpai/util/CpfUtils.java`

Normalização e validação de CPF (aplica-se tanto ao CPF do paciente quanto ao do médico).

**Métodos:**
- `normalizar(String cpf): String` — remove todo caractere não numérico; retorna `null` se a entrada for `null` ou em branco.
- `isValido(String cpf): boolean` — normaliza e verifica se o resultado tem exatamente 11 dígitos.

### `br.gov.ses.fillbpai.util.StringUtils`
`src/main/java/br/gov/ses/fillbpai/util/StringUtils.java`

Utilitários genéricos de manipulação de string usados para respeitar limites de tamanho de campo (layout BPA-I/banco) e para separar textos combinados do tipo "código - nome" vindos da planilha.

**Métodos:**
- `limitarTamanho(String valor, int tamanhoMaximo): String` — `trim()` e corta a string no tamanho máximo se exceder; retorna `null` se `valor` for `null`.
- `limitarTamanhoOuVazio(String valor, int tamanhoMaximo): String` — igual ao anterior, mas retorna `""` (não `null`) quando `valor` é `null` — variante segura para campos de banco que não aceitam `null`.
- `separarCodigoENome(String valor): String[]` — separa a string em `[codigo, nome]` usando como separador hífen comum ou en dash/em dash (regex `[-–—]`, primeira ocorrência). Se `valor` for `null` ou não contiver nenhum desses separadores, retorna `[null, valor]` (nome recebe o valor original inteiro).
- `separarEspecialidadeEMedico(String valor): String[]` — mesma lógica de separador, mas para o par `[especialidade, medico]` (ex.: `"CARDIOLOGIA - JOÃO DA SILVA"` → `["CARDIOLOGIA", "JOÃO DA SILVA"]`). Se não houver separador, retorna `[valor, null]` (especialidade recebe o valor original, médico fica `null`).

### `br.gov.ses.fillbpai.util.TextoUtils`
`src/main/java/br/gov/ses/fillbpai/util/TextoUtils.java`

Normalização de texto genérica para comparação aproximada (usada por `ColunaAliasUtils` e outros pontos que comparam nomes de campo/coluna). Construtor privado — classe só de métodos estáticos.

**Métodos:**
- `normalizar(String texto): String` — retorna `""` para `null`/em branco; senão aplica `trim()`, decompõe acentos via `Normalizer.Form.NFD`, remove as marcas diacríticas combinantes via regex `\p{InCombiningDiacriticalMarks}` e converte para maiúsculas.

### `br.gov.ses.fillbpai.util.ColunaAliasUtils`
`src/main/java/br/gov/ses/fillbpai/util/ColunaAliasUtils.java`

Registro de aliases de nome de cabeçalho de planilha por campo canônico, em duas camadas de precedência: **Padrão** (CSV embutido no classpath, versionado no git, comum a todas as instalações) e **Local** (CSV externo desta instalação, complementa o padrão e é o único removível pela tela de Configurações). Formato de linha: `campoCanonico;alias`.

**Métodos:**
- `resolverCampo(String textoCabecalho): String` — normaliza o texto do cabeçalho (via `TextoUtils.normalizar`) e busca o campo canônico correspondente no índice combinado; retorna `null` se vazio ou sem correspondência.
- `obterCamposCanonicos(): List<String>` — retorna, na ordem de descoberta, todos os campos canônicos conhecidos (união de padrão + locais).
- `obterAliases(String campoCanonico): List<AliasInfo>` — retorna todos os aliases de um campo, cada um marcado com sua origem (`PADRAO` ou `LOCAL`), padrão primeiro.
- `obterTodosRegistrados(): Map<String, List<AliasInfo>>` — mapa completo campo→aliases, usado pela tela de Configurações para listar tudo de uma vez.
- `salvar(String campoCanonico, String alias): void` *(`synchronized`)* — cadastra um alias **local** novo: se o alias normalizado já apontar para o mesmo campo, não faz nada (idempotente); senão adiciona em memória e persiste (append) no CSV externo.
- `remover(String campoCanonico, String alias): void` *(`synchronized`)* — remove um alias local pelo nome normalizado (comparação case/acento-insensível), reindexa o índice combinado e reescreve o arquivo externo inteiro. Nunca remove aliases padrão.
- `restaurarPadrao(String campoCanonico): void` *(`synchronized`)* — remove **todos** os aliases locais de um campo (volta a usar só o padrão), reindexa e reescreve o arquivo externo.
- `limparCache(): void` *(`synchronized`)* — zera os três mapas em memória, forçando recarga do CSV na próxima chamada.
- `carregarCsvSeNecessario(): void` *(privado, `synchronized`)* — lazy-load: carrega classpath, depois externo, depois reindexa; não recarrega se já carregado.
- `carregarDeClasspath(): void` *(privado)* — lê `/dados/colunas_aliases.csv` do classpath (UTF-8) para o mapa `aliasesPadrao`.
- `carregarDeArquivoExterno(): void` *(privado)* — lê `src/main/resources/dados/colunas_aliases.csv` (se existir) para o mapa `aliasesLocais`.
- `carregarDeReader(BufferedReader reader, Map<String,List<String>> destino): void` *(privado)* — parser comum de linha `campo;alias` (`split(";", 2)`), ignora linhas vazias/cabeçalho (`"campo"`), popula o mapa de destino.
- `reindexarCampoPorAlias(): void` *(privado)* — reconstrói o índice combinado `alias normalizado → campo` a partir de `aliasesPadrao` + `aliasesLocais` (padrão primeiro, depois local, então local pode sobrescrever padrão no índice se houver colisão).
- `salvarNoCsv(String campoCanonico, String alias): void` *(privado)* — grava o cabeçalho `campo;alias` se o arquivo não existir, depois faz **append** da linha nova (UTF-8).
- `reescreverArquivoExterno(): void` *(privado)* — reescreve o arquivo externo do zero (`CREATE`+`TRUNCATE_EXISTING`) a partir do estado atual de `aliasesLocais` — necessário porque remoção/restauração não são operações de append. Nunca toca no CSV do classpath.

### `br.gov.ses.fillbpai.util.CnsProfissionalUtils`
`src/main/java/br/gov/ses/fillbpai/util/CnsProfissionalUtils.java`

Resolve o CNS do **profissional** (médico) a partir do nome, com cache local em `medicos_cns.csv`. Modelo de dados: o **CNS é a chave canônica**; os nomes cadastrados são **apelidos** dele — a mesma pessoa pode ter múltiplas grafias de nome (com/sem sobrenome, com/sem acento) todas resolvendo para o mesmo CNS. O primeiro apelido cadastrado de cada CNS é o "nome principal". Todas as operações de escrita reescrevem o arquivo externo inteiro (não fazem append), evitando linhas duplicadas obsoletas.

**Métodos:**
- `buscar(String nome): CnsResultado` — busca o CNS pelo nome (qualquer apelido cadastrado, normalizado); se `nome` for `null`/vazio retorna resultado vazio sem aviso; se não encontrado, retorna aviso orientando a conferir o nome ou cadastrar em Configurações → CNS de Médicos.
- `obterTodosMedicos(): List<MedicoInfo>` — lista todos os médicos cadastrados, um `MedicoInfo(cns, apelidos)` por CNS único.
- `cadastrar(String cns, String primeiroNome): void` *(`synchronized`)* — cria um médico novo; lança `IllegalArgumentException` se o CNS já existir (citando o nome principal do cadastro existente) ou se o nome já pertencer a outro CNS. Persiste com reescrita completa do CSV.
- `adicionarApelido(String cns, String novoNome): void` *(`synchronized`)* — adiciona um apelido a um médico existente; lança `IllegalArgumentException` se o CNS não existir ou se o nome já pertencer a **outro** CNS; se o nome já for apelido do mesmo CNS, não faz nada (idempotente). Persiste com reescrita completa.
- `removerApelido(String cns, String nome): void` *(`synchronized`)* — remove um apelido pelo nome normalizado; lança `IllegalArgumentException` se for o único apelido restante do médico (nesse caso orienta a usar `removerMedico`). Persiste com reescrita completa.
- `alterarCns(String cnsAntigo, String cnsNovo): void` *(`synchronized`)* — corrige/altera o CNS de um médico já cadastrado, reindexando todos os seus apelidos para a nova chave; lança `IllegalArgumentException` se `cnsAntigo` não existir ou se `cnsNovo` já pertencer a outro médico (mensagem orienta a adicionar como apelido em vez de editar). Não faz merge automático em caso de colisão. Persiste com reescrita completa.
- `removerMedico(String cns): void` *(`synchronized`)* — remove o médico e todos os seus apelidos do cache e do arquivo (reescrita completa).
- `limparCache(): void` *(`synchronized`)* — zera os dois mapas em memória, forçando recarga do CSV na próxima chamada.
- `nomePrincipal(String cns): String` *(privado)* — retorna o primeiro apelido cadastrado de um CNS (ou o próprio CNS se não houver apelidos, caso defensivo).
- `carregarCsvSeNecessario(): void` *(privado, `synchronized`)* — lazy-load: carrega classpath, depois externo (mesma fonte física em desenvolvimento; complementar em produção).
- `carregarDeClasspath(): void` *(privado)* — lê `/dados/medicos_cns.csv` do classpath (UTF-8).
- `carregarDeArquivoExterno(): void` *(privado)* — lê `src/main/resources/dados/medicos_cns.csv` (se existir).
- `carregarDeReader(BufferedReader reader): void` *(privado)* — parser de linha que aceita dois formatos: novo (`nome;cns`) e legado (`cpf;cns;nome`, detectado quando o primeiro campo é só dígitos com até 14 caracteres — nesse caso o CPF é descartado). Ignora linhas vazias/cabeçalho. Deduplica apelidos já vistos (evita duplicar entradas presentes tanto no classpath quanto no externo).
- `reescreverArquivoExterno(): void` *(privado)* — reescreve o arquivo inteiro (`CREATE`+`TRUNCATE_EXISTING`) a partir do estado atual em memória, uma linha por apelido, **ordenadas por nome** (case-insensitive) para manter diffs de git legíveis. Nunca toca no CSV do classpath.
- `normalizar(String texto): String` *(pacote-privado, estático)* — remove acentos (`Normalizer.Form.NFD` + regex `\p{M}`), `toUpperCase()` e `trim()`. Retorna `""` para `null`.
- **Classe interna `CnsResultado`** — record com `getCns()` e `getAviso()`.
- **Record `MedicoInfo(String cns, List<String> apelidos)`** — com método `nomePrincipal()` que retorna o primeiro apelido da lista, ou `""` se vazia.

---

## Pacote `repository`

### `br.gov.ses.fillbpai.repository.EstabelecimentoRepository`
`src/main/java/br/gov/ses/fillbpai/repository/EstabelecimentoRepository.java`

Repositório para a entidade `Estabelecimento`, busca por código (chave natural). Não usa Spring Data — é uma classe simples que recebe um `EntityManager` no construtor e monta as queries JPQL manualmente.

**Métodos:**
- `EstabelecimentoRepository(EntityManager entityManager)` — construtor; guarda o `EntityManager` injetado.
- `buscarPorCodigo(String codigo): Optional<Estabelecimento>` — executa `SELECT e FROM Estabelecimento e WHERE e.codigo = :codigo` e retorna o primeiro resultado (ou vazio).
- `salvar(Estabelecimento estabelecimento): void` — chama `entityManager.persist(estabelecimento)` para inserir um novo registro.

### `br.gov.ses.fillbpai.repository.MedicoRepository`
`src/main/java/br/gov/ses/fillbpai/repository/MedicoRepository.java`

Repositório para a entidade `Medico`, busca por CPF (chave natural). Mesmo padrão de `EstabelecimentoRepository`: `EntityManager` injetado, sem Spring Data.

**Métodos:**
- `MedicoRepository(EntityManager entityManager)` — construtor; guarda o `EntityManager` injetado.
- `buscarPorCpf(String cpf): Optional<Medico>` — executa `SELECT m FROM Medico m WHERE m.cpf = :cpf` (espera CPF já normalizado) e retorna o primeiro resultado (ou vazio).
- `salvar(Medico medico): void` — chama `entityManager.persist(medico)` para inserir um novo registro.

### `br.gov.ses.fillbpai.repository.PacienteRepository`
`src/main/java/br/gov/ses/fillbpai/repository/PacienteRepository.java`

Repositório para a entidade `Paciente`, busca por CPF (chave natural). Mesmo padrão dos demais repositórios: `EntityManager` injetado, sem Spring Data.

**Métodos:**
- `PacienteRepository(EntityManager entityManager)` — construtor; guarda o `EntityManager` injetado.
- `buscarPorCpf(String cpf): Optional<Paciente>` — executa `SELECT p FROM Paciente p LEFT JOIN FETCH p.endereco WHERE p.cpf = :cpf` (espera CPF já normalizado), com `JOIN FETCH` para carregar o endereço na mesma consulta (evita lazy-loading posterior); retorna o primeiro resultado (ou vazio).
- `salvar(Paciente paciente): void` — chama `entityManager.persist(paciente)` para inserir um novo registro.

### `br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository`
`src/main/java/br/gov/ses/fillbpai/repository/AtendimentoBPAiRepository.java`

Repositório para a entidade `AtendimentoBPAi`. Javadoc da classe declara explicitamente: "camada responsável apenas por persistência, não contém regra de negócio". Mesmo padrão: `EntityManager` injetado, queries JPQL manuais.

**Métodos:**
- `AtendimentoBPAiRepository(EntityManager entityManager)` — construtor; guarda o `EntityManager` injetado.
- `salvar(AtendimentoBPAi atendimento): void` — chama `entityManager.persist(atendimento)` para inserir um novo registro.
- `buscarTodos(): List<AtendimentoBPAi>` — executa `FROM AtendimentoBPAi` sem filtro e retorna todos os registros.
- `buscarMapaFolhaPorEspecialidadeMedico(): Map<String, String>` — busca todos os atendimentos com `folha` não nula (via `JOIN FETCH a.medico`), e constrói um mapa `"especialidade|medicoId" → folha`, usando `putIfAbsent` para manter a primeira folha encontrada por combinação; usado para reaproveitar a mesma folha já atribuída a um par especialidade+médico em importações futuras, independente do mês.
- `buscarDuplicata(Paciente paciente, Medico medico, LocalDate dataAgendamento, String sigtap): Optional<AtendimentoBPAi>` — busca um atendimento existente que combine exatamente paciente, médico, data de agendamento e código SIGTAP (chave natural de negócio), usada para evitar duplicar registros ao reimportar a mesma planilha; retorna o primeiro resultado encontrado, ou vazio.

---

## Pacote `model`

### `br.gov.ses.fillbpai.model.AtendimentoBPAi`
`src/main/java/br/gov/ses/fillbpai/model/AtendimentoBPAi.java`

Entidade JPA (`@Entity`, tabela `atendimento_bpai`) que representa um atendimento BPA-I. Relacionamentos `@ManyToOne` (fetch `EAGER`) para `Paciente`, `Medico` e `Estabelecimento`. Campos próprios: `cnesNts` (valor padrão fixo `"6970451"`), `codIne`, `folha`, `cnsProfissional` (preenchido manualmente na UI), `tipoServico`, `sigtap`, `dataAgendamento`, `horaAtendimento`, `especialidadeMedico`, `cboMedico`, `cidConsulta`.

**Métodos:**
- Getters/setters padrão para os campos: `id`, `paciente`, `medico`, `estabelecimento`, `cnesNts`, `codIne`, `folha`, `cnsProfissional`, `tipoServico`, `sigtap`, `dataAgendamento`, `horaAtendimento`, `especialidadeMedico`, `cboMedico`, `cidConsulta`.

### `br.gov.ses.fillbpai.model.Estabelecimento`
`src/main/java/br/gov/ses/fillbpai/model/Estabelecimento.java`

Entidade JPA (tabela `estabelecimento`) representando um estabelecimento de saúde. Chave natural: `codigo` (`unique = true, nullable = false`). Campos: `id`, `codigo`, `nome`. Dados atualizados pela última importação.

**Métodos:**
- Getters/setters padrão para os campos: `id`, `codigo`, `nome`.

### `br.gov.ses.fillbpai.model.Medico`
`src/main/java/br/gov/ses/fillbpai/model/Medico.java`

Entidade JPA (tabela `medico`) representando um médico. Chave natural: `cpf` (`unique = true, nullable = false`). Campos: `id`, `cpf`, `nome`. Dados atualizados pela última importação.

**Métodos:**
- Getters/setters padrão para os campos: `id`, `cpf`, `nome`.

### `br.gov.ses.fillbpai.model.Paciente`
`src/main/java/br/gov/ses/fillbpai/model/Paciente.java`

Entidade JPA (tabela `paciente`) representando um paciente. Chave natural: `cpf` (`unique = true, nullable = false`). Campos: `id`, `cpf`, `nome`, `cns`, `sexo`, `raca`, `dataNascimento`, `telefone`. Relacionamento `@OneToOne(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)` com `Endereco` — o paciente é o lado "dono inverso"; remover/desassociar o endereço em cascata remove o registro de `Endereco`.

**Métodos:**
- Getters/setters padrão para os campos: `id`, `cpf`, `nome`, `cns`, `sexo`, `raca`, `dataNascimento`, `telefone`, `endereco`.

### `br.gov.ses.fillbpai.model.Endereco`
`src/main/java/br/gov/ses/fillbpai/model/Endereco.java`

Entidade JPA (tabela `endereco`) representando o endereço de um paciente. Relacionamento `@OneToOne` com `Paciente` (`paciente_id`, `unique = true, nullable = false` — este é o lado proprietário da relação 1:1). Campos: `id`, `paciente`, `municipio`, `tipoZona`, `cep`, `codLogradouro`, `endereco`, `complemento`, `numero`, `bairro`, `codigoIbge` (7 dígitos, resolvido na importação via CSV → cache do banco por CEP → API ViaCEP, nessa ordem; truncado para 6 dígitos só no `GeradorBPAiService`, não aqui).

**Métodos:**
- Getters/setters padrão para os campos: `id`, `paciente`, `municipio`, `tipoZona`, `cep`, `codLogradouro`, `endereco`, `complemento`, `numero`, `bairro`, `codigoIbge`.

---

## Pacote `dto`

### `br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO`
`src/main/java/br/gov/ses/fillbpai/dto/AtendimentoBPAiDTO.java`

DTO somente-leitura (wrapper) para exibição de um `AtendimentoBPAi` na tabela da UI. Envolve a entidade e navega pelos relacionamentos (`Paciente`, `Medico`, `Estabelecimento`, `Endereco`) para expor campos "planos" prontos para exibição, tratando nulos com fallback para string vazia `""`.

**Métodos:**
- `AtendimentoBPAiDTO(AtendimentoBPAi entity)` — construtor; guarda a referência à entidade envolvida.
- `static fromEntity(AtendimentoBPAi a): AtendimentoBPAiDTO` — factory estático, equivalente a chamar o construtor.
- `getId(): Long` — delega para `entity.getId()`.
- `getTipoServico(): String` — delega para `entity.getTipoServico()`.
- `getSigtap(): String` — delega para `entity.getSigtap()`.
- `getDataAgendamento(): String` — formata `entity.getDataAgendamento()` como `dd/MM/yyyy`, ou `""` se nulo.
- `getHoraAtendimento(): String` — formata `entity.getHoraAtendimento()` como `HH:mm`, ou `""` se nulo.
- `getCodEstabelecimento(): String` — retorna o código do estabelecimento associado, ou `""` se não houver.
- `getEstabelecimento(): String` — retorna o nome do estabelecimento associado, ou `""` se não houver.
- `getCnesNts(): String` — delega para `entity.getCnesNts()`.
- `getCodIne(): String` — delega para `entity.getCodIne()`.
- `getFolha(): String` — delega para `entity.getFolha()`.
- `getMedico(): String` — retorna o nome do médico associado, ou `""` se não houver.
- `getEspecialidadeMedico(): String` — delega para `entity.getEspecialidadeMedico()`.
- `getCpfMedico(): String` — retorna o CPF do médico associado, ou `""` se não houver.
- `getCboMedico(): String` — delega para `entity.getCboMedico()`.
- `getCnsProfissional(): String` — delega para `entity.getCnsProfissional()`.
- `getPaciente(): String` — retorna o nome do paciente associado, ou `""` se não houver.
- `getCpfPaciente(): String` — retorna o CPF do paciente associado, ou `""` se não houver.
- `getSexoPaciente(): String` — retorna o sexo do paciente associado, ou `""` se não houver.
- `getCnsPaciente(): String` — retorna o CNS do paciente associado, ou `""` se não houver.
- `getRacaPaciente(): String` — retorna a raça do paciente associado, ou `""` se não houver.
- `getDataNascimento(): String` — formata a data de nascimento do paciente como `dd/MM/yyyy`, ou `""` se o paciente ou a data forem nulos.
- `getTelefone(): String` — retorna o telefone do paciente associado, ou `""` se não houver.
- `getMunicipio(): String` — retorna o município via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getTipoZona(): String` — retorna a zona via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getCep(): String` — retorna o CEP via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getCodLogradouro(): String` — retorna o código de logradouro via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getEndereco(): String` — retorna o logradouro via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getComplemento(): String` — retorna o complemento via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getNumero(): String` — retorna o número via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getBairro(): String` — retorna o bairro via `getEnderecoEntity()`, ou `""` se não houver endereço.
- `getCodigoIbge(): String` — retorna o código IBGE via `getEnderecoEntity()`, ou `""` se não houver endereço ou código.
- `getCidConsulta(): String` — delega para `entity.getCidConsulta()`.
- `getEnderecoEntity(): Endereco` (privado) — helper que retorna `entity.getPaciente().getEndereco()`, ou `null` se o paciente for nulo; centraliza o acesso usado por todos os getters de endereço acima.

### `br.gov.ses.fillbpai.dto.LinhaImportacaoDTO`
`src/main/java/br/gov/ses/fillbpai/dto/LinhaImportacaoDTO.java`

DTO mutável que transporta os dados brutos de uma linha da planilha Excel antes de virarem entidades. Ciclo de vida documentado no Javadoc da classe: (1) `ExcelImportService` preenche os campos crus (Strings); (2) `AtendimentoProcessor` valida, normaliza e converte; (3) `AtendimentoImportacaoService` extrai entidades e persiste. Campos agrupados por domínio: atendimento (`tipoServico`, `sigtap`, `dataAgendamento`, `horaAtendimento`, `cidConsulta`, `codIne`), estabelecimento (`codEstabelecimento`, `estabelecimento`), médico (`especialidadeMedico`, `medico`, `cpfMedico`, `cboMedico`), paciente (`cpfPaciente`, `paciente`, `cnsPaciente`, `sexoPaciente`, `racaPaciente`, `dataNascimento`, `telefone`), endereço (`municipio`, `tipoZona`, `cep`, `codLogradouro`, `endereco`, `complemento`, `numero`, `bairro`) e campos auxiliares com o texto bruto do Excel antes da conversão (`dataAgendamentoString`, `horaAtendimentoString`, `dataNascimentoString`).

**Métodos:**
- Getters/setters padrão para os campos: `tipoServico`, `sigtap`, `dataAgendamento`, `horaAtendimento`, `cidConsulta`, `codIne`, `codEstabelecimento`, `estabelecimento`, `especialidadeMedico`, `medico`, `cpfMedico`, `cboMedico`, `cpfPaciente`, `paciente`, `cnsPaciente`, `sexoPaciente`, `racaPaciente`, `dataNascimento`, `telefone`, `municipio`, `tipoZona`, `cep`, `codLogradouro`, `endereco`, `complemento`, `numero`, `bairro`, `dataAgendamentoString`, `horaAtendimentoString`, `dataNascimentoString`.
