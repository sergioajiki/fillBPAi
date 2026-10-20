# Documentação Técnica — PonteBPA (código: fillBPAi)

> Referência de todas as classes de `src/main/java/br/gov/ses/fillbpai/`, pacote por pacote: o que cada classe faz e seus métodos (públicos e privados documentados), com o texto tirado da documentação (javadoc) do próprio código. Atualizada em 20/10/2026. Para a arquitetura, os fluxos e as regras de negócio, ver `CLAUDE.md`; para o uso, `README.md`.
>
> O nome exibido do programa é **PonteBPA**. Pacote, classes, métodos, `artifactId` e arquivo do banco continuam `fillbpai` — a troca de nome é só visual.

## Sumário

- [Pastas e arquivos em execução](#pastas-e-arquivos-em-execução)
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

## Pastas e arquivos em execução

Relativos à pasta onde o programa é executado. `database/` e `logs/` contêm dados pessoais (LGPD) e ficam fora do Git (`.gitignore`); os dois CSV de cadastro são versionados — nunca gravar CPF neles.

| Pasta / arquivo | Conteúdo | Quem grava |
|---|---|---|
| `database/fillbpai.mv.db` | Banco H2: pacientes, médicos, estabelecimentos, atendimentos (com folha, linha e data da remessa) | `DatabaseInitializer` (JPA/Hibernate, `hbm2ddl update`) |
| `logs/log_importacao_<planilha>_<aaaa-MM-dd_HHmm>.txt` | Log de cada importação: planilha, pasta, data/hora, conferência de linhas, avisos e ciência dos avisos. Pasta própria desde 20/10/2026; logs que estavam em `database/` são movidos para cá ao abrir o programa | `MainController.salvarLogEmArquivo`, `HistoricoLogsService` |
| `src/main/resources/dados/medicos_cns.csv` | CNS dos profissionais por grafia do nome (`nome;cns`) — o CPF do médico fica só no banco | Configurações → CNS de Médicos (`CnsProfissionalUtils`) |
| `src/main/resources/dados/colunas_aliases.csv` | Nomes alternativos de cabeçalho cadastrados pelo usuário | Configurações (`ColunaAliasUtils`) |

O relatório da análise (`Analisar Planilha`) só é gravado pelo botão "Salvar Log TXT", no local que o usuário escolher.

---

## Pacote `app`

### `MainApp`
`src/main/java/br/gov/ses/fillbpai/app/MainApp.java`

Classe principal da aplicação JavaFX.

Responsável por:
- Inicializar infraestrutura do banco
- Criar layout principal
- Encerrar recursos corretamente

**Métodos:**
- `start(Stage primaryStage): void`
- `stop(): void`
- `main(String[] args): void`

---

## Pacote `config`

### `DatabaseInitializer`
`src/main/java/br/gov/ses/fillbpai/config/DatabaseInitializer.java`

Responsável por inicializar e encerrar a infraestrutura de banco de dados.

Arquitetura adotada:

Aplicação (Hibernate) ↓ H2 FILE + AUTO_SERVER ↓ Arquivo físico (.mv.db)

O AUTO_SERVER permite que o console conecte simultaneamente ao banco.

**Métodos:**
- `iniciar(): void` — Inicializa:; H2 Console; JPA/Hibernate
- `finalizar(): void` — Encerra todos os recursos corretamente.

---

## Pacote `controller`

### `MainController`
`src/main/java/br/gov/ses/fillbpai/controller/MainController.java`

Controller principal da aplicação.

Monta a barra superior (Analisar Planilha, Ver Log Importação, Configurações) e delega a tabela, a árvore e a geração do BPA-I ao RelatorioController. Conduz a análise da planilha (janelas de erros, avisos com "Ciente" e estrutura) e a importação a partir dela.

O log de cada importação é salvo em `logs/` (separada do banco), um arquivo por importação (`HistoricoLogsService`); "Ver Log Importação" mostra o histórico, inclusive depois de reiniciar o programa.

**Métodos:**
- `MainController(EntityManager entityManager, BorderPane rootLayout) (construtor)`
- `processarImportacao(String caminho, Stage stage, String registroCiencia): void` *(privado)* — Executa a importação do arquivo já analisado, salva e exibe o log e recarrega a tabela. Chamado pelo botão "Importar Planilha" das janelas da análise; importação bloqueada abre a janela de erro, sem log.
- `montarConteudoLog(ImportacaoResultado resultado, String caminho, String registroCiencia, java.time.LocalDateTime quando): String` *(privado)* — Monta o conteúdo textual completo do log de importação. Começa com a identificação da importação (19/10/2026): nome da planilha, local do arquivo no computador e data/hora — o arquivo do log fica guardado e precisa dizer sozinho de que importação é. Depois: conferência de linhas, avisos ("=== AVISOS — importados, confira os pontos abaixo ===") e, por último, a ciência dos avisos marcada na análise (20/10/2026). Sem seção de erros: com erro não há importação nem log.
- `salvarLogEmArquivo(String conteudo, String nomePlanilha, java.time.LocalDateTime quando): void` *(privado)* — Salva o log da importação num arquivo próprio, com a planilha e a data/hora no nome (`HistoricoLogsService#novoArquivo`) — reimportar a mesma planilha não apaga o log anterior (20/10/2026).
- `exibirLogSalvo(): void` *(privado)* — Botão "Ver Log Importação": janela com o histórico de importações (20/10/2026). À esquerda a lista, da mais recente para a mais antiga (data/hora, planilha, linhas, avisos), com busca pelo nome da planilha; à direita o log selecionado, com "Copiar log". A mais recente já vem selecionada. Lê os arquivos da pasta, então funciona depois de reiniciar.
- `descreverEntrada(HistoricoLogsService.EntradaLog entrada): String` *(privado)* — "312 linhas · 11 avisos", "287 linhas · sem avisos" ou "log antigo · data do arquivo".
- `mostrarLog(String conteudo): void` *(privado)* — Exibe o log de importação num diálogo largo (até 1200 px), redimensionável, com a área de texto rolando por dentro e aberta no começo.
- `analisarPlanilha(Stage stage): void` *(privado)* — Analisa a planilha à procura de erros de validação sem importar. Exibe o resultado e permite download do log de erros em TXT.
- `criarCabecalhoPlanilha(): VBox` *(privado)* — Cabeçalho das janelas de validação (19/10/2026): nome do arquivo e a pasta onde ele está no computador, para o usuário saber qual planilha foi analisada e onde corrigi-la. A pasta fica num campo de texto só leitura, que dá para selecionar e copiar.
- `criarBannerFormatoLegado(ErroValidacao aviso, CienciaAvisos ciencia): VBox` *(privado)* — Monta o bloco de "formato de planilha legado" (coluna "Especialidade/Médico" combinada), no formato de `#criarBlocoInformativo`.
- `CienciaAvisos.registrar(TitledPane painel, String descricao): CheckBox` — Põe o "Ciente" no cabeçalho do painel, à direita do título, e o registra. Desabilitado até o painel ser aberto pela primeira vez.
- `CienciaAvisos.registroNoLog(): String` — Seção do final do log de importação, ou `null` quando não havia o que marcar.
- `vincularCiencia(CienciaAvisos ciencia, Button btnImportar): Label` *(privado)* — Rodapé das janelas com botão de importar: texto da situação da ciência (âmbar enquanto falta "Ciente", verde quando completa) e o botão, que só fica habilitado com a ciência completa.
- `criarBlocoInformativo(String cabecalho, List<java.util.Map.Entry<String, String>> itens, CienciaAvisos ciencia, String sufixoCiencia): VBox` *(privado)* — Bloco informativo verde no formato de `#criarBlocoSeveridade`: faixa de cabeçalho colorida e corpo com borda, com um `TitledPane` aberto por item (título → texto em itálico). Usado pelos blocos de formato legado e de colunas opcionais não encontradas. Verde para não se confundir com erro (vermelho) nem aviso (âmbar). Com `ciencia`, cada item ganha um "Ciente" (descrição no log = título + `sufixoCiencia`); `null` na janela com erros.
- `criarBannerColunasOpcionaisAusentes(List<ErroValidacao> avisos, CienciaAvisos ciencia): VBox` *(privado)* — Monta o bloco "colunas opcionais não encontradas" no mesmo estilo dos blocos de erros e avisos (`#criarBlocoSeveridade`, 19/10/2026): faixa de cabeçalho colorida e, abaixo, um corpo com borda e um `TitledPane` por coluna ausente (título = nome da coluna, conteúdo = o que o sistema faz sem ela). Cor verde, para não se confundir com erro (vermelho) nem aviso (âmbar). Todos abertos: são poucos e curtos.
- `mostrarDialogoAnaliseOk(String caminho, Stage stage, ErroValidacao avisoLegado, List<ErroValidacao> avisosColunaOpcional): void` *(privado)* — Análise sem erros nem avisos de linha, com botão para importar. Se houver blocos verdes (formato legado, colunas opcionais ausentes), eles pedem "Ciente" antes de liberar a importação; sem eles, importa direto.
- `mostrarDialogoAvisosAnalise(List<ErroValidacao> avisos, String logAvisos, String nomePlanilha, String caminho, Stage stage, ErroValidacao avisoLegado, List<ErroValidacao> avisosColunaOpcional): void` *(privado)* — Análise só com avisos: bloco âmbar (`#criarBlocoSeveridade`, chips + grupos por tipo) e blocos verdes, todos com "Ciente" (`CienciaAvisos`); o botão "Importar Planilha" só é liberado com a ciência de todos. "Salvar Log TXT" grava o relatório da análise onde o usuário escolher.
- `mostrarDialogoErroEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, String logErros, String nomePlanilha, Stage stage): void` *(privado)* — Exibe diálogo dedicado para problemas de estrutura do cabeçalho (coluna obrigatória ausente ou duplicada). Além de apontar exatamente qual(is) coluna(s) causaram o problema — em vez da lista de erros de linha, que nesse caso seria toda causada pela mesma coluna faltando — lista também as colunas do cabeçalho que não bateram com nenhum alias cadastrado: candidatas óbvias quando a planilha tem a informação, só que com um nome diferente do esperado. Oferece atalho para a tela onde um alias pode ser cadastrado sem precisar editar a planilha.
- `blocoComTitulo(String titulo, List<String> itens): VBox` *(privado)* — Monta um bloco com título em negrito seguido de uma lista de itens com marcador.
- `mostrarDialogoErrosValidacao(List<ErroValidacao> demaisErros, String logErros, String nomePlanilha, Stage stage, List<ErroValidacao> avisosColunaOpcional): void` *(privado)* — Exibe diálogo com erros de validação e botão para download do log TXT. É o único diálogo de análise onde erros bloqueantes e avisos podem aparecer juntos — por isso `demaisErros` é separado em dois blocos coloridos (vermelho para ERRO, âmbar para AVISO). Ordem: erros → colunas opcionais → avisos. Sem "Ciente" nem botão de importar: com erros não há importação. O TXT de `logErros` só é gravado pelo botão "Salvar Log TXT".
- `criarBlocoSeveridade(String titulo, List<ErroValidacao> erros, String corForte, String corSuave, CienciaAvisos ciencia): VBox` *(privado)* — Monta um bloco de severidade — faixa de cabeçalho colorida (fundo suave, texto na cor forte) e, abaixo, os itens organizados por `AgrupamentoValidacao`: ; fileira de chips de filtro ("Todos N" + um por tipo, com a contagem) — clicar num chip mostra só aquele grupo; ; um `TitledPane` recolhível por tipo, com a explicação uma única vez e as ocorrências iguais juntas (`valor — N linhas: ...`). O primeiro grupo (o mais prioritário) abre expandido. Mesmas cores usadas nos runbooks do projeto para ERRO (<code>#B3261E</code>/<code>#F6E1DF</code>) e AVISO (<code>#93630F</code>/<code>#F3E6D2</code>). Com `ciencia` (janela de avisos com botão de importar), cada grupo ganha um "Ciente" no cabeçalho e o chip do grupo ganha ✓ quando marcado; `null` nas janelas sem importação.
- `exibirComRolagem(Alert alert, javafx.scene.layout.Region conteudo): void` *(privado)* — Mostra o diálogo com o conteúdo dentro de uma área rolável e a janela limitada a 75% da altura útil (conteúdo) e 90% da largura útil do monitor (largura padrão 1100 px), centralizada na vertical. Sem isso, uma análise com muitos avisos fazia a janela passar do tamanho da tela; o sistema a encolhia e o JavaFX espremia os textos com quebra de linha até uma linha só, cortando-os com reticências.
- `exibirComRolagem(Alert alert, javafx.scene.layout.Region conteudo, double larguraMaxima): void` *(privado)* — Igual a `#exibirComRolagem(Alert, javafx.scene.layout.Region)`, com a largura máxima informada (sempre limitada a 90% do monitor). Sem largura definida, o JavaFX dimensiona a janela pelo texto de cada Label numa linha só — com textos longos a janela passava dos limites do monitor.
- `labelQuebrado(String texto): Label` *(privado)* — Label com quebra de linha que ocupa toda a largura disponível e nunca é cortado com reticências: a altura mínima passa a ser a altura necessária para o texto inteiro (por padrão o JavaFX deixa encolher até uma linha quando falta espaço). A quebra depende de a janela ter largura definida (`setPrefWidth` no `DialogPane`) — sem ela o Label cresce para caber o texto numa linha só. Cor do texto explícita (escura): a cor padrão do JavaFX é calculada por contraste com o fundo do contêiner e pode sair branca.
- `criarChipFiltro(String texto, ToggleGroup grupo, String corForte, String corSuave): ToggleButton` *(privado)* — Chip de filtro arredondado; selecionado fica com o fundo suave e o texto na cor forte da severidade (estilo trocado via listener porque o estilo inline não tem pseudo-classe `:selected`).
- `nomePlanilhaSemExtensao(String caminho): String` *(privado)* — Extrai o nome do arquivo da planilha sem extensão, sanitizado para uso em nomes de arquivo.

---

## Pacote `ui`

### `ConfiguracoesDialog`
`src/main/java/br/gov/ses/fillbpai/ui/ConfiguracoesDialog.java`

Tela de configurações da aplicação.

Duas abas: aliases de coluna da planilha (nomes de cabeçalho aceitos para cada campo canônico, ver `ColunaAliasUtils`) e cadastro de CNS de médicos (ver `CnsProfissionalUtils`), com apelidos de nome por CNS.

**Métodos:**
- `abrir(Window owner): void`
- `atualizarDetalhe(VBox detalhe, String campo, ListView<String> listaCampos): void` *(privado)* — Reconstrói o painel de detalhe do campo selecionado — chamado após qualquer alteração.
- `criarChip(ColunaAliasUtils.AliasInfo info, String campo, VBox detalhe, ListView<String> listaCampos): HBox` *(privado)* — Chip visual de um alias — padrão (não removível) ou local (com botão de remoção).
- `recarregarListaMedicos(ListView<String> listaMedicos, String filtro): void` *(privado)* — Repõe a lista de médicos (nome principal), filtrada pelo texto de busca (nome principal ou qualquer apelido).
- `atualizarAposMudancaMedico(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca, String cns): void` *(privado)* — Recarrega a lista e reabre o médico (por CNS) no painel de detalhe — chamado após qualquer alteração.
- `exibirFormularioEdicao(VBox detalhe, CnsProfissionalUtils.MedicoInfo info, ListView<String> listaMedicos, TextField campoBusca): void` *(privado)* — Reconstrói o painel de detalhe em modo edição de um médico já cadastrado.
- `criarChipApelido(String apelido, CnsProfissionalUtils.MedicoInfo info, VBox detalhe, ListView<String> listaMedicos, TextField campoBusca, Label lblErro): HBox` *(privado)* — Chip visual de um apelido, com botão de remoção (recusado pelo backend se for o único restante).
- `exibirFormularioCriacao(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca): void` *(privado)* — Reconstrói o painel de detalhe em modo criação de um médico novo.

### `FileChooserService`
`src/main/java/br/gov/ses/fillbpai/ui/FileChooserService.java`

Serviço responsável exclusivamente por abrir diálogos de seleção de arquivos.

Não contém regra de negócio.

**Métodos:**
- `selecionarPlanilha(Stage stage): String` — Abre diálogo para selecionar planilha Excel.

### `RelatorioController`
`src/main/java/br/gov/ses/fillbpai/ui/RelatorioController.java`

Tela principal de dados: barra "Exibir tabela" (Competência, Período, Completo — só o que a tabela mostra), árvore de especialidades → médicos com busca, tabela de atendimentos (com Folha, Linha e Remessa), barra de edição do CNS do médico selecionado e barra fixa inferior de geração do BPA-I (competência, pendências e o botão que gera parcial ou completo conforme a seleção da árvore).

**Métodos:**
- `NoArvore(TipoNo tipo, String especialidade, String medico, String rotulo) (construtor)`
- `RelatorioController(EntityManager entityManager) (construtor)`
- `setAcaoVerLog(Runnable acao): void` — Define a ação do botão "Ver Log Importação". Chamado pelo MainController para injetar a lógica de exibição do log.
- `getBtnVerLog(): Button` — Retorna o botão "Ver Log Importação" para ser posicionado pelo MainController.
- `setAcaoAnalisarPlanilha(Runnable acao): void` — Define a ação do botão "Analisar Planilha". Chamado pelo MainController para injetar a lógica de validação.
- `getBtnAnalisarPlanilha(): Button` — Retorna o botão "Analisar Planilha" para ser posicionado pelo MainController.
- `criarComponente(): BorderPane`
- `selecionarNaArvore(TreeItem<NoArvore> item): void` *(privado)* — Chamado quando a seleção na árvore muda (clique do usuário ou restauração automática após recarregar os dados).
- `construirArvore(): void` *(privado)* — Reconstrói o mapa especialidade -> médicos a partir dos dados carregados e repopula a árvore (aplicando o texto de busca atual, se houver).
- `filtrarArvore(String termo): void` *(privado)* — Repopula a árvore a partir de `especialidadeMedicoMap`, mostrando apenas especialidades/médicos que casam com o termo de busca (ou tudo, se o termo estiver vazio), e tenta restaurar a seleção atual.
- `restaurarSelecaoArvore(): void` *(privado)* — Reaplica a seleção de especialidade/médico atual na árvore recém-reconstruída.
- `atualizarContexto(): void` *(privado)* — Atualiza o texto do breadcrumb com o contexto atual da árvore.
- `atualizarBarraGeracao(): void` *(privado)* — Atualiza o texto do botão de geração e o chip de aviso contextual conforme a seleção atual na árvore (médico selecionado -> parcial; nada selecionado -> completo).
- `abrirDialogSelecionarPeriodo(): void` *(privado)* — Abre o diálogo de seleção de período (mês/ano inicial e final) usado pelo modo de exibição "Período" — só afeta o filtro da tabela, nunca a competência usada na geração do BPA-I.
- `verificarAvisosGeracao(): void` *(privado)* — Verifica no banco se existem atendimentos sem CNS do profissional. Usado pelo chip de aviso da barra inferior quando nenhum médico está selecionado (contexto de geração "Completo").
- `verificarAvisosParcial(): void` *(privado)* — Verifica CNS ausente para o médico/especialidade selecionados na árvore. Usado pelo chip de aviso da barra inferior quando um médico está selecionado (contexto de geração "parcial").
- `mostrarAvisosGeracao(): void` *(privado)* — Exibe o log de pendências que impedem a geração do BPA-I completo.
- `gerarBPA(): void` *(privado)* — Gera o arquivo BPA-I parcial do médico + especialidade selecionados na árvore, na competência do badge. A folha e a linha seguem a numeração da competência inteira (as mesmas da geração completa) e, com o arquivo salvo, ficam gravadas com a data/hora da remessa — a tabela é recarregada para mostrá-las. Cancelar a escolha do arquivo não grava nada.
- `gerarBPACompleto(): void` *(privado)* — Gera o arquivo BPA-I completo com todos os médicos. Folhas e linhas numeradas pelo gerador (`GeradorBPAiService.numerarFolhas`: por competência, especialidade → médico, folha nova a cada 99 linhas) e gravadas nos atendimentos com a data/hora da remessa. Depois da geração a tabela é recarregada e mostra Folha, Linha e Remessa.
- `competenciaCoincide(AtendimentoBPAiDTO dto): boolean` *(privado)* — Verifica se a data de agendamento do atendimento pertence à competência selecionada — mesma regra usada por `GeradorBPAiService` ao filtrar por YEAR/MONTH(dataAgendamento) na geração do BPA-I completo. Mantém a tabela mostrando exatamente o que seria incluído no arquivo gerado. Sem competência selecionada (banco vazio) ou no modo "Completo", não filtra.
- `atualizarBotaoSelecao(): void` *(privado)* — Mostra/oculta [Selecionar Período] conforme o modo de exibição ativo.
- `atualizarLabelModoExibicao(): void` *(privado)* — Atualiza a legenda do modo de exibição ativo, ao lado do toggle.
- `formatarCompetenciaExibicao(String competenciaYyyyMM): String` *(privado)* — Formata uma competência YYYYMM como MM/YYYY para exibição.
- `atualizarCns(): void` *(privado)* — Atualiza o CNS do profissional para todos os atendimentos do médico e especialidade selecionados na árvore. A folha não é editável: é calculada e gravada na geração do BPA-I.
- `atualizarDados(List<AtendimentoBPAi> registros): void`
- `carregarDoBanco(): void`
- `atualizarCompetencia(): void` *(privado)* — Atualiza o texto do badge de competência com base nos dados carregados. Competência = yyyyMM da data de agendamento mais recente.
- `atualizarTextoCompetencia(String texto): void` *(privado)* — Atualiza o texto do badge/botão de competência na barra inferior.

---

## Pacote `service`

### `AgrupamentoValidacao`
`src/main/java/br/gov/ses/fillbpai/service/AgrupamentoValidacao.java`

Organiza os resultados de `ValidacaoPlanilhaService` para exibição: agrupa por tipo (com título legível e explicação uma única vez) e, dentro do tipo, junta as ocorrências com o mesmo `ErroValidacao#valor()` numa linha só, com a lista de linhas da planilha.

Usado pela tela do "Analisar Planilha" (`MainController`) e pelo log TXT (`ValidacaoPlanilhaService#gerarLogTxt`), para os dois mostrarem a mesma organização.

**Métodos:**
- `InfoTipo(String titulo, String explicacao, int prioridade): record` — Título legível, explicação fixa e ordem de exibição de um tipo de erro/aviso.
- `Item(String texto, List<Integer> linhas): record` — Uma linha dentro do grupo: o valor (ou o detalhe) e as linhas da planilha onde ocorre.
- `Grupo(String tipo, Severidade severidade, String titulo, String explicacao, int ocorrencias, List<Item> itens): record` — Todas as ocorrências de um tipo.
- `info(String tipo): InfoTipo` — Título, explicação e prioridade do tipo; tipo desconhecido usa o próprio código como título.
- `agrupar(List<ErroValidacao> erros): List<Grupo>` — Agrupa por tipo, na ordem: ERRO antes de AVISO, depois pela prioridade do tipo. Dentro do grupo, ocorrências com o mesmo valor viram um item só (linhas em ordem crescente); sem valor, cada ocorrência é um item com o detalhe completo. Itens ordenados pela primeira linha.
- `descreverLinhas(List<Integer> linhas): String` — "1 linha: 5" ou "3 linhas: 2, 7, 9" (até 10 números, depois reticências).
- `formatarItem(Item item): String` — Item formatado para texto: `valor — N linhas: ...`.
- record `InfoTipo(String titulo, String explicacao, int prioridade)` — Título legível, explicação fixa e ordem de exibição de um tipo de erro/aviso.
- record `Item(String texto, List<Integer> linhas)` — Uma linha dentro do grupo: o valor (ou o detalhe) e as linhas da planilha onde ocorre.
- record `Grupo(String tipo, Severidade severidade, String titulo, String explicacao, int ocorrencias, List<Item> itens)` — Todas as ocorrências de um tipo.

### `AtendimentoImportacaoService`
`src/main/java/br/gov/ses/fillbpai/service/AtendimentoImportacaoService.java`

Importação da planilha Excel para o banco:

- lê as linhas (`ExcelImportService`) pelo mapeamento do cabeçalho (`PlanilhaColumnMapper`);

- aplica as regras de negócio (`AtendimentoProcessor`);

- cria ou encontra as entidades (Paciente, Endereco, Medico, Estabelecimento) e grava o atendimento;

- devolve o resumo para o log de importação (`ImportacaoResultado`).

Todas as linhas da planilha precisam ser importadas: qualquer erro cancela a importação inteira (ver `#importar`).

**Métodos:**
- `AtendimentoImportacaoService(EntityManager entityManager) (construtor)`
- `importar(String caminhoArquivo): ImportacaoResultado` — Importa a planilha inteira numa transação só — tudo ou nada (decisão de 09/10/2026): ; confere a estrutura do cabeçalho e o código IBGE de todas as linhas antes de gravar (`#verificarIbgeDeTodasAsLinhas`); ; para cada linha com dados: `AtendimentoProcessor` (erros e avisos), linhas repetidas (`DetectorDuplicidade`) e gravação (`#criarOuAtualizarAtendimento`, com `flush` por linha); ; qualquer linha com erro desfaz tudo; no fim, confere se cada linha virou um atendimento (`#conferirLinhasImportadas`) e resolve o CNS do profissional herdado pelo CPF.
- `criarOuAtualizarAtendimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel, List<PendenciaCns> pendentesCns): AtendimentoBPAi` *(privado)* — Cria ou atualiza um AtendimentoBPAi a partir do DTO processado. Deduplicação: busca atendimento existente pela chave paciente + médico + data + SIGTAP + horário. Se encontrado, atualiza os campos (inclusive o CNS do profissional); se não, cria um novo. Assim, reimportar a mesma planilha (ex.: depois de cadastrar o CNS de um médico) atualiza em vez de duplicar. O resultado conta novos e atualizados para o log.
- `resolverCnsProfissional(LinhaImportacaoDTO dto, Medico medico, AtendimentoBPAi atendimento, int linhaExcel, List<PendenciaCns> pendentesCns): String` *(privado)* — Resolve o CNS do profissional da linha e grava no atendimento. Fonte principal: o cadastro por nome (`medicos_cns.csv`, com apelidos) — o mesmo médico chega com grafias diferentes, por isso a busca é por nome e não por CPF. Quando o nome é encontrado, o CNS também fica guardado no `Medico` (chave CPF, só no banco local). Reserva: grafia não cadastrada, mas o mesmo CPF já tem CNS conhecido → usa esse CNS e avisa para cadastrar a grafia como apelido.
- `resolverCnsPendentes(List<PendenciaCns> pendentes, ImportacaoResultado resultado): void` *(privado)* — Fim da planilha: linhas cuja grafia não estava cadastrada e cujo CPF não tinha CNS no momento. Se outra linha do mesmo CPF trouxe o CNS depois, herda; senão, mantém o aviso "CNS do profissional não encontrado".
- `PendenciaCns(int linha, String nome, Medico medico, AtendimentoBPAi atendimento, String avisoNaoEncontrado): record` *(privado)* — Linha aguardando o fim da planilha para decidir a herança de CNS pelo CPF.
- `buscarOuCriarPaciente(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel): Paciente` *(privado)* — Busca o paciente pela chave do cadastro e cria se não existir; se existir, atualiza os dados com a linha mais recente. ; Chave: o CPF; paciente sem CPF usa a chave interna pelo CNS (`CpfUtils#chaveSemCpfPorCns`), migrando a chave antiga (`#migrarChaveSemCpfAntiga`); ; nome cadastrado diferente do da linha → aviso CPF_COM_NOMES_DIFERENTES (o cadastro fica com o nome da linha); ; o CNS só é trocado quando a linha traz um CNS melhor (`#deveTrocarCns`); ; situação de rua não é atualizada (fica no atendimento).
- `deveTrocarCns(String cnsGravado, String cnsDaLinha): boolean` — Recomendação 27 (19/10/2026): a linha só troca o CNS do cadastro quando traz um CNS e ele não é pior que o gravado — linha sem CNS (ou com CNS sem 15 dígitos, que chega vazio do processador) e CNS com dígito verificador errado no lugar de um válido mantêm o CNS já conhecido.
- `migrarChaveSemCpfAntiga(LinhaImportacaoDTO dto): void` *(privado)* — Paciente sem CPF gravado antes de 19/10/2026 com a chave antiga (nome + nascimento): passa para a chave pelo CNS, sem duplicar o paciente nem perder os atendimentos já ligados a ele. Só migra se o CNS gravado for vazio ou o mesmo da linha — CNS diferente é um homônimo (outra pessoa), que ganha cadastro próprio.
- `atualizarEndereco(Paciente paciente, LinhaImportacaoDTO dto, String codigoIbge): void` *(privado)* — Atualiza o endereço do paciente com os dados mais recentes. Se não existir, cria um novo vinculado ao paciente.
- `conferirLinhasImportadas(int linhasComDados, Map<Integer, AtendimentoBPAi> atendimentoPorLinha): void` *(privado)* — Conferência final da importação (decisão de 09/10/2026 — todas as linhas da planilha devem ser importadas): o número de linhas com dados tem que bater com o número de linhas gravadas e com o número de atendimentos distintos. Duas linhas que caíssem no mesmo atendimento (uma sobrescrevendo a outra) fariam a conta não fechar.
- `verificarIbgeDeTodasAsLinhas(Sheet sheet, Map<String, Integer> colunas): void` *(privado)* — Confere, antes de gravar qualquer linha, se todas têm município e código IBGE (tabela de MS pelo nome, CEPs já conhecidos, APIs de CEP — mesma busca da análise e da importação). Se alguma não tiver, lança exceção com a lista de linhas e a importação da planilha inteira é bloqueada: nenhuma linha é gravada. Os resultados ficam no cache do `IbgeUtils`, então a importação em seguida não consulta de novo.
- `preCarregarCacheIbge(): void` — Pré-carrega no cache do `IbgeUtils` os CEPs já resolvidos no banco, para a busca do IBGE pelo CEP não ir à internet à toa. Usado antes da importação e antes do "Analisar Planilha" (`MainController`).
- `buscarOuCriarMedico(LinhaImportacaoDTO dto): Medico` *(privado)* — Busca médico pelo CPF. Se não existir, cria novo. Se existir, atualiza o nome. O CPF já chega validado (11 dígitos) por `AtendimentoProcessor.validarCpfMedico` — linha sem CPF do médico é rejeitada antes de chegar aqui.
- `buscarOuCriarEstabelecimento(LinhaImportacaoDTO dto, ImportacaoResultado resultado, int linhaExcel): Estabelecimento` *(privado)* — Busca estabelecimento pelo código (sempre numérico — ver `StringUtils.separarCodigoENome`). Se existir, atualiza o nome quando a linha traz um; se não existir, cria — desde que a linha traga o nome. ; Sem código: vínculo por nome com um já cadastrado (aviso ESTABELECIMENTO_SEM_CODIGO já emitido pelo processador). ; Só o código (`"1234567"`, `"12345 -"`): vincula se o código já estiver cadastrado, **sem apagar o nome**; se não estiver, o atendimento fica sem estabelecimento, com aviso (não dá para cadastrar sem nome).
- `descreverProblemaEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento): String` *(privado)* — Monta uma descrição legível dos problemas de estrutura do cabeçalho (colunas obrigatórias ausentes e/ou duplicadas) para uso em mensagens de erro voltadas ao usuário.
- `carregarCepIbgeDoBanco(): Map<String, String>` *(privado)* — Carrega todos os pares CEP → codigoIbge já persistidos na tabela Endereco. Usado para pré-popular o cache do IbgeUtils antes do loop de importação, evitando consultas às APIs de CEP (ViaCEP, BrasilAPI, OpenCEP) para CEPs já conhecidos.
- record `PendenciaCns(int linha, String nome, Medico medico, AtendimentoBPAi atendimento, String avisoNaoEncontrado)` — Linha aguardando o fim da planilha para decidir a herança de CNS pelo CPF.

### `AtendimentoProcessor`
`src/main/java/br/gov/ses/fillbpai/service/AtendimentoProcessor.java`

Processa e valida uma linha da planilha já lida (`LinhaImportacaoDTO`) antes de ela ser gravada por `AtendimentoImportacaoService`.

Etapas (na ordem de `#processar`):

- Separações — estabelecimento "código
- nome", especialidade/médico no formato legado, especialidade padronizada;

- SIGTAP — a partir do tipo de serviço (procedimento fixo para nutricionista/psicólogo sem tipo);

- Normalizações — CPF, CEP, sexo, tamanho do tipo de zona;

- Validações e normalizações por coluna — nome, campos obrigatórios, CEP, CPFs, CBO, especialidade, raça/etnia, CNS, colunas S/N, CID, telefone, logradouro, número, bairro/complemento, CEP fora de MS;

- Conversões — datas e hora, com os avisos de data;

- Vigência dos campos novos do layout (`VigenciaLayoutUtils`).

Mesmas regras da análise (`ValidacaoPlanilhaService`): o que lá é ERRO aqui lança `IllegalArgumentException` (a planilha inteira deixa de ser importada — tudo ou nada); o que lá é AVISO volta como texto na lista de avisos, com o código do tipo entre parênteses, para o log de importação.

Esta classe não acessa o banco de dados.

**Métodos:**
- `processar(LinhaImportacaoDTO dto): List<String>` — Processa e valida um DTO de importação.
- `validarCns(LinhaImportacaoDTO dto): List<String>` *(privado)* — Valida e normaliza o CNS do paciente (`CnsUtils#processar`): ; vazio → sem aviso (Decisão 13: com CPF o CNS não vai para o BPA-I); ; 15 dígitos → gravado; dígito verificador errado → aviso CNS_DV_INVALIDO; ; sem 15 dígitos (a menos ou a mais) → aviso CNS_INVALIDO e o valor não é gravado (recomendação 26). Paciente sem CPF já teve o CNS exigido em `#validarCamposObrigatorios`.
- `definirSigtap(String tipoServico, String especialidade): String` *(privado)* — Mapeia o tipo de serviço para o código SIGTAP correspondente. Mapeamento:; TELECONSULTA → 03.01.01.030-7; TELEINTERCONSULTA → 08.04.01.006-4 Tipo vazio é erro, exceto para as especialidades de procedimento fixo (NUTRICIONISTA/PSICÓLOGO — `EspecialidadeUtils#usaProcedimentoFixo`), que mantêm o comportamento anterior (SIGTAP nulo) por decisão de 08/10/2026. Antes, tipo vazio passava para todas: o BPA-I saía com procedimento 0000000000 e a reimportação duplicava o atendimento.
- `codigoSigtap(String tipoServico): String` — Código SIGTAP do tipo de serviço (sem diferença de caixa e sem espaços nas pontas), ou `null` se o tipo não for reconhecido. Usado também pelo `ValidacaoPlanilhaService`, para a análise aplicar a mesma regra.
- `separarEstabelecimento(LinhaImportacaoDTO dto): List<String>` *(privado)* — Separa o campo combinado "CÓDIGO; NOME" do estabelecimento em dois campos distintos: codEstabelecimento e estabelecimento (nome). Exemplo: "12345; HOSPITAL CENTRAL" → código="12345", nome="HOSPITAL CENTRAL" Quando a célula não traz um separador reconhecido, o nome é preservado (célula inteira) mas o código fica indefinido — `AtendimentoImportacaoService` tenta então vincular por nome a um estabelecimento já cadastrado; se não encontrar, o atendimento fica sem estabelecimento. Gera aviso nesse caso.
- `separarEspecialidadeEMedico(LinhaImportacaoDTO dto): void` *(privado)* — Separa o campo combinado legado "ESPECIALIDADE; NOME DO MÉDICO" em dois campos distintos. Planilhas no formato atual trazem especialidade (coluna própria) e nome do médico em colunas diferentes, então os dois valores nunca chegam idênticos — só dispara quando `PlanilhaColumnMapper` reaproveitou o mesmo índice de coluna para os dois campos (planilha legado, uma única coluna "Especialidade/Médico" com valor combinado). Exemplo: "CARDIOLOGIA; DR. SILVA" → especialidade="CARDIOLOGIA", médico="DR. SILVA"
- `validarCep(LinhaImportacaoDTO dto): List<String>` *(privado)* — Valida o CEP após normalização: vazio ou com tamanho diferente de 8 dígitos é erro bloqueante (mesma regra da análise). Exceção: paciente em situação de rua (= Sim) sem CEP é aceito com aviso e o CEP vai em branco (Decisão 25); com CEP preenchido, segue a validação normal.
- `isSituacaoRua(LinhaImportacaoDTO dto): boolean` — Coluna "Situação de Rua" presente e marcada com Sim (S, Sim, 1).
- `validarCpf(LinhaImportacaoDTO dto): void` *(privado)* — Valida o CPF do paciente preenchido (vazio é tratado em `#validarCamposObrigatorios`). Erro bloqueante quando: vem junto com "Paciente sem CPF" = Sim (contradição), não tem 11 dígitos (sem completar zeros — a mensagem orienta formatar a coluna como texto), tem os 11 dígitos iguais ou tem os dígitos verificadores errados.
- `validarCbo(LinhaImportacaoDTO dto): void` *(privado)* — CBO do médico — vai para o BPA-I (`prd-cbo`, 6 posições). Reduzido aos dígitos (`2251-25` → `225125`): com a máscara ele tinha 7 caracteres e deslocava o registro. Vazio ou diferente de 6 dígitos é erro bloqueante da linha (decisão de 08/10/2026).
- `validarEspecialidade(LinhaImportacaoDTO dto): void` *(privado)* — Especialidade — não vai para o BPA-I, mas organiza a árvore da tela, a folha (chave especialidade + médico) e a regra de procedimento fixo de nutricionista/psicólogo. Sem ela o atendimento sumia da árvore. Vazia é erro bloqueante da linha (decisão de 08/10/2026).
- `validarCpfMedico(LinhaImportacaoDTO dto): void` *(privado)* — Valida o CPF do médico — chave do cadastro de `Medico`. Ausente, com tamanho diferente de 11 dígitos, com dígitos repetidos ou com dígitos verificadores errados é erro bloqueante da linha, lançado aqui (antes de qualquer gravação): um CPF fora do tamanho da coluna derrubava a transação da planilha inteira, e um CPF digitado errado criava um segundo cadastro do mesmo médico, com folha própria no BPA-I.
- `validarRaca(LinhaImportacaoDTO dto): void` *(privado)* — Valida a raça do paciente — campo obrigatório no layout do BPA-I (seq 21, `prd-raca`). Ausente ou não reconhecida por `RacaUtils#resolverCodigo` (grafia incorreta) é erro bloqueante.
- `verificarEtniaDoIndigena(LinhaImportacaoDTO dto): List<String>` *(privado)* — Verifica a etnia do paciente — o BPA-I só leva a etnia quando a raça é Indígena (código 05 — layout 2026, seq 22). Com raça Indígena a etnia é obrigatória (decisão de 13/10/2026 — o BPA exige a etnia para raça 05 e descartaria o registro): vazia ou fora da tabela oficial (`EtniaUtils#resolver`) é erro bloqueante da linha. Com outra raça a coluna não é olhada, sem aviso: a etnia só é relevante para raça Indígena (o gerador a envia em branco nos demais casos).
- `normalizarSituacaoRua(LinhaImportacaoDTO dto): List<String>` *(privado)* — Normaliza a situação de rua (do atendimento) para o código S/N do BPA-I. ; coluna ausente (`null`): sem aviso aqui — o atendimento é gravado com "N" (regra de coluna opcional ausente); ; célula vazia: aviso SITUACAO_RUA_VAZIA e `""` (branco no BPA-I); ; valor não reconhecido (não é S/N, Sim/Não nem 1/0): aviso, e o campo fica sem valor (`null` → "N").
- `verificarVigenciaDoLayout(LinhaImportacaoDTO dto): List<String>` *(privado)* — Avisos de campo preenchido antes da vigência no layout (ver `VigenciaLayoutUtils`).
- `normalizarPacienteSemCpf(LinhaImportacaoDTO dto): List<String>` *(privado)* — Normaliza a informação de "paciente sem CPF/registro civil" (seq 39 do BPA-I, <code>prd_sem_cpf</code>) para o código S/N. ; coluna ausente (`null`): sem aviso — o gerador deriva o valor da presença do CPF do paciente; ; célula vazia com o CPF preenchido: "N", sem aviso (20/10/2026 — o CPF mostra que o paciente tem CPF; CPF vazio com a célula vazia já é erro); ; valor não reconhecido: aviso, e o campo fica sem valor (`null`, mesma derivação pelo CPF).
- `normalizarNomePaciente(LinhaImportacaoDTO dto): List<String>` *(privado)* — Nome do paciente (recomendações 29 e 30, 19/10/2026): gravado em maiúsculas com espaços repetidos reduzidos; aviso quando passa de 30 caracteres (mostra como sai no BPA-I) ou parece não ser um nome completo.
- `validarCamposObrigatorios(LinhaImportacaoDTO dto): void` *(privado)* — Valida campos obrigatórios para a importação: ; nome do paciente; ; município (sem ele não há a primeira busca do IBGE); ; CPF do paciente — vazio só com "Paciente sem CPF" = Sim, e então o CNS é obrigatório, com 15 dígitos e dígito verificador válido; ; data de agendamento (= data do atendimento); ; data de nascimento (sem ela a idade sairia 000 no BPA-I).
- `converterDatas(LinhaImportacaoDTO dto, List<String> avisos): void` *(privado)* — Converte as datas e a hora da linha e confere a coerência entre elas. ; data do atendimento (`DateUtils#parse`, modo estrito: d/M/aaaa, aaaa-MM-dd, d-M-aaaa) — inválida é erro; mostrada sem o ano é aviso DATA_ATENDIMENTO_SEM_ANO; futura ou com mais de 2 anos é aviso DATA_FORA_DO_ESPERADO; ; hora (`TimeUtils`) — não reconhecida vira aviso, não erro (ver `#converterHora`); ; nascimento — sem o ano, inválido ou incoerente com o atendimento (depois dele, ou idade acima de 130 anos) é erro.
- `normalizarCid(LinhaImportacaoDTO dto): List<String>` *(privado)* — CID da consulta (BPA-I seq 13): grava só o código CID-10 encontrado na célula (`"CID I10"`, `"I10; Hipertensão"` → `I10`; `"I10.9"` → `I109`). Célula sem código ou com mais de um é erro da linha; vazia (decisão de 14/10/2026) ou só com traço — "-" = sem CID (19/10/2026) — é aceita com aviso CID_AUSENTE e CID em branco.
- `converterHora(LinhaImportacaoDTO dto): List<String>` *(privado)* — Converte a hora de atendimento. Diferente das datas, hora não reconhecida (ex.: "-", "--:--", "SEM HORARIO") NÃO descarta a linha: a hora não é enviada no BPA-I, então o atendimento é importado sem hora e com aviso (mesma regra de `ValidacaoPlanilhaService`, aviso HORA_INVALIDA).
- `normalizarCpf(LinhaImportacaoDTO dto): void` *(privado)* — Remove formatação dos CPFs (paciente e médico) via CpfUtils. Remove pontos, traços e demais não-numéricos: 123.456.789-00 → 12345678900
- `normalizarCep(LinhaImportacaoDTO dto): void` *(privado)* — Normaliza CEP removendo formatação. Remove hífen, espaços e caracteres inválidos. Exemplo: 79.003-020 → 79003020
- `normalizarSexo(LinhaImportacaoDTO dto): void` *(privado)* — Normaliza o sexo do paciente para F/M/I (`SexoUtils`). Vazio ou não reconhecido é erro da linha (16/10/2026 — recomendação 32): antes o valor ia como veio ("Fem" cancelava a importação no banco, "H" ia para o BPA-I) e vazio saía como F.
- `limitarCamposBanco(LinhaImportacaoDTO dto): void` *(privado)* — Corta no tamanho da coluna os campos que não têm normalização própria — hoje só o tipo de zona (50), que não vai para o BPA-I. Endereço, bairro, complemento e número são tratados em `#normalizarLogradouro`, `#normalizarBairroComplemento` e `#normalizarNumero`.
- `normalizarLogradouro(LinhaImportacaoDTO dto): List<String>` *(privado)* — Tipo de logradouro (BPA-I seq 30) e endereço (seq 31), pela tabela DNE (`LogradouroUtils`, 15/10/2026): ; tipo no começo do endereço → código, e o prefixo sai do endereço; ; senão, a coluna "Cód. Logradouro" (código ou nome do tipo); preenchida mas não reconhecida → aviso, e o gerador usa o padrão 081 (Rua); ; endereço cortado em 30 na última palavra inteira. Nunca bloqueia a linha, e o valor gravado sempre cabe nas colunas.
- `normalizarBairroComplemento(LinhaImportacaoDTO dto): List<String>` *(privado)* — Bairro (seq 34, 30 posições) e complemento (seq 32, 10 posições) — ver `EnderecoUtils` (16/10/2026): preenchimento ("-", "N/A", "0") vira vazio; bairro sem a palavra "Bairro" e cortado na palavra, sem aviso; complemento abreviado (AP, BL, CS…) e cortado na palavra, com aviso quando algo se perde (Decisão 23); aviso quando o bairro é o próprio município. Nunca bloqueia a linha.
- `normalizarNumero(LinhaImportacaoDTO dto): List<String>` *(privado)* — Número do endereço (BPA-I seq 33, 5 posições — `NumeroUtils`): vazio, "sem número" ou só traço → SN, sem aviso; outro valor fora do padrão com até 5 caracteres → como veio, com aviso; mais de 5 → número do começo ou SN, com aviso. Nunca bloqueia a linha.
- `normalizarTelefone(LinhaImportacaoDTO dto): List<String>` *(privado)* — Telefone do paciente (BPA-I seq 35, opcional): grava só DDD + número (`TelefoneUtils#normalizar`). Sem DDD ou inválido: aviso e campo vazio (decisão de 14/10/2026) — nunca bloqueia a linha.

### `DetectorDuplicidade`
`src/main/java/br/gov/ses/fillbpai/service/DetectorDuplicidade.java`

Detecta, dentro de uma mesma planilha, linhas do mesmo atendimento: mesmo paciente + médico + data + procedimento (SIGTAP) — a chave de deduplicação da importação — comparando o horário.

- Mesmo horário (ou os dois sem horário) → `Tipo#REPETIDA`: a segunda linha sobrescreveria a primeira no banco (duas linhas viram um registro). ERRO — remover a repetição ou corrigir o horário.

- Horário diferente → `Tipo#SUSPEITA`: importadas como atendimentos separados, com aviso para conferir (decisão de 09/10/2026).

Usado pela análise (`ValidacaoPlanilhaService`) e pela importação (`AtendimentoImportacaoService`), sempre com o DTO já processado pelo `AtendimentoProcessor` (CPF normalizado, SIGTAP, data e hora convertidas).

**Métodos:**
- `Ocorrencia(Tipo tipo, int linhaAnterior, LocalTime horaAnterior): record` — Linha anterior do mesmo atendimento: o tipo e o horário dela.
- `registrar(int linha, LinhaImportacaoDTO dto): Ocorrencia` — Registra a linha e diz se ela repete (ou parece repetir) uma anterior.
- `chavePaciente(LinhaImportacaoDTO dto): String` *(privado)* — Mesma chave do paciente na importação: CPF, ou a chave interna do paciente sem CPF pelo CNS (recomendação 68). CNS inválido já é erro na linha; nesse caso usa a chave antiga (nome + nascimento) só para a chave não ficar vazia.
- `descreverHora(LocalTime hora): String` — Horário para mensagens: "08:30" ou "sem horário".
- `mensagemRepetida(Ocorrencia o): String` — Mensagem do ERRO de linha repetida (mesmo horário).
- `mensagemSuspeita(Ocorrencia o, LocalTime horaAtual): String` — Mensagem do AVISO de suspeita (horário diferente).
- record `Ocorrencia(Tipo tipo, int linhaAnterior, LocalTime horaAnterior)` — Linha anterior do mesmo atendimento: o tipo e o horário dela.

### `ErroValidacao`
`src/main/java/br/gov/ses/fillbpai/service/ErroValidacao.java`

Representa um problema de validação encontrado em uma linha da planilha Excel.

Pode ser bloqueante (ERRO) ou informativo (AVISO):

- ERRO — impede a importação da planilha; deve ser corrigido antes de importar

- AVISO — não exige correção; antes de importar, o usuário marca "Ciente" em cada grupo de aviso na janela da análise (20/10/2026)

**Métodos:**
- `ErroValidacao(int linha, Severidade severidade, String tipoErro, String detalhe) (construtor)` — Sem valor separado — o item aparece pelo `detalhe` completo.
- `isBloqueante(): boolean` — Retorna true se este registro é bloqueante para a importação.
- `isEstrutural(): boolean` — Retorna true se este registro é um problema de estrutura do cabeçalho (coluna ausente ou duplicada).

### `ExcelImportService`
`src/main/java/br/gov/ses/fillbpai/service/ExcelImportService.java`

Serviço responsável por:
- Ler uma linha da planilha Excel
- Converter todas as células para String
- NÃO realizar validações ou conversões definitivas

IMPORTANTE: Esta classe NÃO valida regras de negócio. Apenas extrai dados do Excel para o DTO de importação.

A conversão para LocalDate/LocalTime será feita posteriormente pela classe AtendimentoProcessor.

**Métodos:**
- `isLinhaVazia(Row row): boolean` — Linha sem nenhum valor (todas as células vazias, só espaços ou fórmula com resultado vazio) — ex.: linha formatada ou com conteúdo apagado, que o Excel ainda grava no arquivo. Ignorada do mesmo jeito na análise, na importação e na contagem de linhas da planilha.
- `importarLinha(Row row, Map<String, Integer> colunas): LinhaImportacaoDTO` — Converte uma linha do Excel em um LinhaImportacaoDTO. Cada célula é buscada pelo campo canônico, não pelo índice fixo — o mapa vem de `PlanilhaColumnMapper#mapear`, montado uma vez por planilha a partir do cabeçalho. Isso torna a leitura indiferente à ordem das colunas e a colunas extras não mapeadas. Todos os valores são carregados inicialmente como String. Isso evita perda de informação e delega a validação para a camada de processamento.
- `getOpcional(Row row, Map<String, Integer> colunas, String campo): String` *(privado)* — Valor de uma coluna opcional: `null` quando a coluna não está no cabeçalho, `""` quando está mas a célula é vazia — para separar "a planilha não tem essa informação" de "a célula ficou em branco".
- `celula(Row row, Map<String, Integer> colunas, String campo): Cell` *(privado)* — Célula do campo canônico na linha, ou `null` se o campo não está no cabeçalho.
- `isDataSemAno(Cell cell): boolean` — Célula de data cujo formato de exibição não tem o ano — ex.: `dd/mm`, `d-mmm`. É o que o Excel faz quando se digita só dia e mês ("15/03"): grava a data com o **ano corrente** e passa a mostrar só dia/mês. O arquivo chega com uma data válida (15/03/2026) e, sem esta conferência, o paciente virava um bebê de meses (verificado em 14/10/2026). O formato é conferido sem o que está entre colchetes (cor, localidade) e entre aspas (texto literal); ano é `y` ou `e` (ano no padrão de algumas localidades). Hora pura (ano 1899) não conta.
- `lerCelula(Cell cell): String` — Valor de uma célula com as mesmas regras da leitura das linhas (fórmula pelo resultado, U+00A0).
- `getString(Row row, Map<String, Integer> colunas, String campo): String` *(privado)* — Busca o valor de um campo canônico na linha, usando o índice de coluna resolvido para esse campo. Campo ausente do mapa (não encontrado no cabeçalho) resulta em `null` em vez de exceção — a ausência de campo obrigatório já é reportada por `PlanilhaColumnMapper` antes da leitura das linhas de dados.
- `getHora(Row row, Map<String, Integer> colunas, String campo): String` *(privado)* — Leitura própria da coluna de horário. Célula de data+hora (comum em planilhas exportadas de sistemas, ex.: `25/12/2024 08:30`) devolve a **hora** — a leitura genérica (`#getString(Cell)`) devolve só a data, o que é certo para as colunas de data, mas aqui perdia o horário. Se a célula tiver só a data (hora 00:00), segue a leitura genérica (devolve a data, que vira aviso HORA_INVALIDA) para não gravar um horário 00:00 que não existe.
- `getString(Cell cell): String` *(privado)* — Método auxiliar responsável por converter qualquer tipo de célula do Excel em String, preservando o valor original. Este método trata corretamente:; Strings; Números; Datas; Horas (incluindo padrão 1899 do Excel); Booleanos; Fórmulas (pelo resultado calculado em cache); Espaço não separável (U+00A0) tratado como espaço comum
- `getString(Cell cell, CellType tipo): String` *(privado)* — Converte o valor da célula em String de acordo com o tipo informado — o tipo da própria célula, ou o tipo do resultado em cache quando a célula é uma fórmula.
- `limparTexto(String texto): String` *(privado)* — Troca o espaço não separável (U+00A0, comum em dados copiados de sistemas web) por espaço comum e remove espaços das pontas — o `trim()` do Java não remove o U+00A0, e uma célula só com ele parecia vazia na tela mas chegava preenchida ao processamento.

### `GeradorBPAiService`
`src/main/java/br/gov/ses/fillbpai/service/GeradorBPAiService.java`

Gerador do arquivo magnético BPA-I, no layout oficial do Ministério da Saúde (`data/Layout_Exportacao_BPA_2026.pdf`).

- Header: 132 caracteres + CRLF;

- registro: 351 caracteres + CRLF = 353 (`#montarRegistro`, seq 1 a 40);

- arquivo em ISO-8859-1 (caracteres fora dele são trocados — `#sanitizarLatin1`).

Campos NUM opcionais: brancos quando vazios, zeros à esquerda quando preenchidos; campos ALFA: espaços à direita e corte no tamanho do campo. Dados gravados antes das normalizações da importação passam pelas mesmas regras aqui (CBO, CID, sexo, nome, endereço, telefone…).

**Métodos:**
- `GeradorBPAiService(EntityManager entityManager) (construtor)`
- `gerarArquivoComFileChooser(Window parentWindow, String especialidade, String medico, String competenciaAtendimento): boolean` — Geração parcial: arquivo BPA-I só com os atendimentos de um médico numa especialidade, na competência informada (botão "Gerar BPA-I" com um médico selecionado na árvore). Bloqueia se algum atendimento estiver sem CNS do profissional ou sem código IBGE, como a geração completa. A folha e a linha vêm da numeração da competência inteira (`#numerarFolhas`) — as mesmas da geração completa. Depois de gravar o arquivo, folha, linha e data/hora da remessa ficam gravadas nos atendimentos do médico (`#registrarRemessa`).
- `gerarArquivoCompletoComFileChooser(Window parentWindow, String competenciaAtendimento): boolean` — Geração completa: um único arquivo BPA-I com todos os atendimentos da competência, de todos os médicos e especialidades. Antes de gravar, bloqueia se algum atendimento estiver sem CNS do profissional (`#validarCnsProfissional`) ou sem código IBGE (`#validarIbge`). Folhas e linhas por `#numerarFolhas`; depois de gravar o arquivo, ficam gravadas nos atendimentos com a data/hora da remessa (`#registrarRemessa`) — a tabela mostra o que foi enviado.
- `carregarCompetencia(int ano, int mes): List<AtendimentoBPAi>` *(privado)* — Todos os atendimentos da competência (mês da data do atendimento), com as entidades do registro.
- `PosicaoFolha(int folha, int linha): record` — Folha (`prd-flh`, 1 a 999) e linha dentro dela (`prd-seq`, 1 a 99) de um registro.
- `numerarFolhas(List<AtendimentoBPAi> competencia): Map<AtendimentoBPAi, PosicaoFolha>` — Numeração das folhas de uma competência (regra única, 20/10/2026 — antes a geração completa numerava o ano inteiro e a parcial usava a folha definida à mão): ; atendimentos em ordem especialidade → médico → data → hora; ; cada especialidade + médico começa uma folha nova (001, 002…); ; folha nova também a cada 99 linhas (a linha nunca volta para 01 dentro da mesma folha). Não altera os atendimentos — o resultado só é gravado depois que o arquivo é salvo (`#registrarRemessa`).
- `registrarRemessa(List<AtendimentoBPAi> enviados, Map<AtendimentoBPAi, PosicaoFolha> numeracao, java.time.LocalDateTime quando): void` — Grava nos atendimentos enviados a folha, a linha e a data/hora da remessa, numa transação — chamado só depois que o arquivo foi salvo.
- `gerarConteudo(List<AtendimentoBPAi> lista, String competencia): String` — Conteúdo do arquivo numerando as folhas da própria lista (testes).
- `gerarConteudo(List<AtendimentoBPAi> lista, String competencia, Map<AtendimentoBPAi, PosicaoFolha> numeracao): String` — Monta o conteúdo do arquivo BPA-I (header + registros) — geração parcial e completa. Não acessa banco, arquivo nem JavaFX. Os registros saem na ordem de folha e linha; o header leva o total de linhas e de folhas distintas do arquivo.
- `garantirExtensaoTxt(File file): File` *(privado)* — Garante que o arquivo escolhido no FileChooser tenha extensão .txt. O diálogo nativo de salvar do Windows nem sempre preserva a extensão do nome inicial sugerido (ex.: ao usar "Todos os arquivos"), então a extensão é reforçada aqui antes da gravação.
- `montarChaveMedico(AtendimentoBPAi a): String` *(privado)* — Monta a chave única para identificar uma combinação especialidade + médico.
- `validarIbge(List<AtendimentoBPAi> lista): void` — Bloqueia a geração se algum atendimento estiver sem código IBGE do município do paciente (`prd-ibge`, obrigatório no BPA-I) — mesmo padrão de `#validarCnsProfissional`. Acontece quando nem o nome do município nem o CEP resolveram o IBGE na importação (decisão de 08/10/2026 — antes o campo saía em branco sem aviso na geração).
- `validarCnsProfissional(List<AtendimentoBPAi> lista): void` *(privado)* — Verifica se todos os atendimentos da lista possuem CNS do profissional preenchido. Lança RuntimeException com relatório detalhado se houver registros sem CNS.
- `montarHeader(List<AtendimentoBPAi> lista, String competencia, int totalFolhas): String` *(privado)* — Header do arquivo (132 caracteres): competência, total de linhas, total de folhas distintas do arquivo e campo de controle. Antes a geração parcial informava o número de linhas como número de folhas.
- `montarRegistro(AtendimentoBPAi a, String competencia, int folha, int linha): String` *(privado)* — Um registro BPA-I (seq 1 a 39, 351 caracteres; o CRLF da seq 40 é acrescentado por `#gerarConteudo`).
- `calcularSomaVerificacao(List<AtendimentoBPAi> lista): int` *(privado)* — Campo de controle do header: soma dos códigos de procedimento (SIGTAP, só dígitos) e das quantidades (1 por registro), em módulo 1111, mais 1111.
- `padNumOpcional(String valor, int tamanho): String` *(privado)* — Campos NUM opcionais do BPA-I:; Quando preenchido → apenas números, zeros à esquerda; Quando vazio → espaços em branco (default "Brancos" do layout)
- `padRightSpaces(String valor, int tamanho): String` *(privado)* — Campo ALFA de tamanho fixo: completa com espaços à direita e **corta** o que passar do tamanho. Antes não cortava — um valor maior que o campo (ex.: CBO "2251-25") deslocava todos os campos seguintes do registro.
- `formatarCid(String cid): String` *(privado)* — CID-10 do atendimento, sem ponto (`I10`, `I109`). A importação já grava só o código; para dados antigos (célula gravada como veio, ex.: "I10; Hipertensão") extrai o código — antes cortava o texto em 4 caracteres e gerava CIDs inexistentes ("I10H"). Sem exatamente um código: brancos.
- `sanitizarLatin1(String valor): String` *(privado)* — Substitui caracteres fora do conjunto ISO-8859-1 (ex.: aspas curvas, travessão, reticências digitadas via Word/Excel, emojis) por equivalentes ASCII ou espaço, evitando falha ao gravar o arquivo BPA-I.
- `formatarRaca(String raca): String` *(privado)* — Formata o código de raça/cor via `RacaUtils`. Sem código reconhecido, usa branco — o próprio layout oficial declara "Brancos" como Default de `prd-raca` (campo NUM), o mesmo padrão de todo campo NUM opcional deste layout; "99 Sem informação" nunca foi esse default, é (era) só mais uma opção de código, hoje descontinuada. Esta branch só é alcançada por dados já persistidos antes da validação bloqueante de raça existir — novas importações nunca chegam aqui sem raça reconhecida, pois `ValidacaoPlanilhaService`/`AtendimentoProcessor` já bloqueiam a linha antes.
- `formatarEtnia(String etnia): String` *(privado)* — Formata a etnia do paciente (código de 4 caracteres, seq 22 do BPA-I). Etnia ausente ou não encontrada na tabela oficial (ver `EtniaUtils`) é gerada em branco — mesmo padrão do código IBGE não resolvido.
- `formatarTelefone(String telefone): String` *(privado)* — Telefone do paciente (seq 35, 11 posições): DDD + número por `TelefoneUtils`, com espaços à direita; sem DDD ou inválido → brancos.
- `padNumObrigatorio(String valor, int tamanho): String` *(privado)* — Campo NUM obrigatório: só dígitos, com zeros à esquerda. Hoje usado só pelo código de logradouro (seq 30): valor nulo vira `081` (Rua — decisão de negócio de 11/09/2026); valor sem nenhum dígito vira zeros.
- record `PosicaoFolha(int folha, int linha)` — Folha (`prd-flh`, 1 a 999) e linha dentro dela (`prd-seq`, 1 a 99) de um registro.

### `HistoricoLogsService`
`src/main/java/br/gov/ses/fillbpai/service/HistoricoLogsService.java`

Histórico dos logs de importação (20/10/2026): um arquivo por importação em `logs/` (pasta própria, separada do banco), com data e hora no nome (`log_importacao_<planilha>_<aaaa-MM-dd_HHmm>.txt`) — reimportar a mesma planilha não apaga o log anterior, e o histórico continua disponível depois de reiniciar o programa.

Lê a pasta e monta, para cada log, o que a janela "Ver Log Importação" mostra na lista: data/hora, planilha, pasta, linhas e avisos, tirados do cabeçalho do log. Logs antigos, sem cabeçalho (antes de 19/10/2026), usam a data do arquivo e o nome da planilha do nome do arquivo. Sem JavaFX — testável.

**Métodos:**
- `HistoricoLogsService(Path pasta) (construtor)`
- `EntradaLog(Path arquivo, String planilha, String local, LocalDateTime quando, Integer linhas, Integer avisos, boolean antigo): record` — Uma importação do histórico.
- `EntradaLog.quandoFormatado(): String` — Data/hora no formato das mensagens: "20/10/2026 às 13:49".
- `migrarDe(Path pastaAntiga): int` — Move para a pasta dos logs os logs de importação gravados numa pasta antiga (até 20/10/2026 eles ficavam junto do banco, em `database/`), para o histórico continuar completo. Log com o mesmo nome já na pasta nova fica onde está. Falha ao mover não interrompe o programa.
- `novoArquivo(String nomePlanilha, LocalDateTime quando): Path` — Caminho para gravar o log de uma importação nova — nunca sobrescreve um log existente.
- `listar(): List<EntradaLog>` — Todos os logs da pasta, do mais recente para o mais antigo; lista vazia se não houver.
- `conteudo(EntradaLog entrada): String` — Conteúdo do log; mensagem de erro no lugar se não puder ser lido.
- `ler(Path arquivo): EntradaLog` — Monta a entrada a partir do cabeçalho do log (ou do nome e data do arquivo, em log antigo).
- `planilhaDoNomeDoArquivo(Path arquivo): String` — "log_importacao_07; BPAi Julho 2026_2026-09-02_1105.txt" → "07; BPAi Julho 2026".
- record `EntradaLog(Path arquivo, String planilha, String local, LocalDateTime quando, Integer linhas, Integer avisos, boolean antigo)` — Uma importação do histórico.

### `ImportacaoResultado`
`src/main/java/br/gov/ses/fillbpai/service/ImportacaoResultado.java`

Representa o resumo final da importação de uma planilha Excel.

Armazena:
- Contadores: linhas da planilha, importadas (novas e atualizadas), erros, avisos
- Mensagens de erro — usadas para montar a mensagem de "Importação bloqueada"
- Avisos — vão para o log de importação
- Atendimentos importados

Diferença entre erro e aviso:
- ERRO: linha com problema — a importação da planilha inteira é cancelada (nada é gravado; todas as linhas da planilha devem ser importadas, decisão de 09/10/2026)
- AVISO: linha importada, com um ponto a conferir (ex.: CID vazio, nome longo)

**Métodos:**
- `adicionarSucesso(): void` — Incrementa contadores de sucesso. Chamado quando uma linha é importada sem erros.
- `adicionarErro(String erro): void` — Incrementa contadores de erro e armazena mensagem detalhada. Chamado quando uma linha tem erro — no fim do loop, qualquer erro cancela a importação da planilha inteira.
- `adicionarAviso(String aviso): void` — Adiciona um aviso à lista. Avisos não impedem a gravação da linha, mas merecem atenção do usuário: vão para o log de importação.
- `adicionarAvisos(List<String> avisos): void` — Adiciona múltiplos avisos de uma vez. Útil para coletar avisos do AtendimentoProcessor.
- `contarNovo(): void`
- `contarAtualizado(): void`

### `PlanilhaColumnMapper`
`src/main/java/br/gov/ses/fillbpai/service/PlanilhaColumnMapper.java`

Resolve, a partir da linha de cabeçalho de uma planilha, qual índice de coluna corresponde a cada campo canônico — independente da ordem em que as colunas aparecem, e ignorando colunas extras não reconhecidas.

A resolução de nome usa `ColunaAliasUtils`, que já combina os aliases padrão (classpath) com os aliases locais desta instalação.

**Métodos:**
- `ResultadoMapeamento(Map<String, Integer> indices, List<String> camposFaltando, List<String> camposOpcionaisFaltando, List<String> camposDuplicados, List<String> colunasNaoReconhecidas, Map<String, List<String>> colunasPorCampoDuplicado, boolean especialidadeMedicoCombinados, List<String> colunasSemCabecalho): record` — Resultado do mapeamento de um cabeçalho.
- `ResultadoMapeamento(Map<String, Integer> indices, List<String> camposFaltando, List<String> camposOpcionaisFaltando, List<String> camposDuplicados, List<String> colunasNaoReconhecidas, Map<String, List<String>> colunasPorCampoDuplicado, boolean especialidadeMedicoCombinados) (construtor)` — Sem informação de colunas sem cabeçalho (mapeamento só pelo cabeçalho).
- `ResultadoMapeamento.estruturaValida(): boolean` — true se todos os campos obrigatórios foram encontrados, sem ambiguidade.
- `mapear(Row cabecalho): ResultadoMapeamento` — Mapeia a linha de cabeçalho informada. Colunas cujo texto não bate com nenhum alias cadastrado são ignoradas — é assim que uma coluna extra não usada deixa de quebrar a leitura das demais.
- `mapear(Sheet sheet): ResultadoMapeamento` — Mapeia a planilha olhando também os dados, além do cabeçalho: ; **Colunas sem cabeçalho** com dados (ex.: a coluna da especialidade com o título apagado) — antes eram ignoradas em silêncio; agora vão em `colunasSemCabecalho`, com a letra da coluna e um exemplo do conteúdo, para a tela de estrutura e o aviso da análise. ; **Formato legado confirmado pelo conteúdo**: sem coluna "Especialidade", a coluna "Especialidade/Médico" só é tratada como combinada ("ESPECIALIDADE; NOME") se ao menos metade das células preenchidas tiver o separador. Se não tiver (só nomes de médico), a especialidade conta como coluna obrigatória ausente — a tela de estrutura aparece em vez de gravar o nome do médico como especialidade (problema encontrado em 09/10/2026).
- `conteudoConfirmaFormatoLegado(Sheet sheet, Integer coluna): boolean` *(privado)* — Ao menos metade das células preenchidas da coluna tem o separador "ESPECIALIDADE; NOME".
- `colunasSemCabecalho(Sheet sheet): List<String>` *(privado)* — Colunas com cabeçalho vazio mas com dados: "coluna E (ex.: "CARDIOLOGIA")".
- `extrairTexto(Cell celula): String` *(privado)* — Extrai o texto de uma célula de cabeçalho, qualquer que seja o tipo.
- record `ResultadoMapeamento( Map<String, Integer> indices, List<String> camposFaltando, List<String> camposOpcionaisFaltando, List<String> camposDuplicados, List<String> colunasNaoReconhecidas, Map<String, List<String>> colunasPorCampoDuplicado, boolean especialidadeMedicoCombinados, List<String> colunasSemCabecalho)` — Resultado do mapeamento de um cabeçalho.

### `ValidacaoPlanilhaService`
`src/main/java/br/gov/ses/fillbpai/service/ValidacaoPlanilhaService.java`

Serviço responsável por validar uma planilha Excel antes da importação.

Percorre todas as linhas da planilha e aplica as mesmas regras da importação (`AtendimentoProcessor` e `AtendimentoImportacaoService`), sem gravar nada. Toda linha que a importação rejeitaria aparece aqui como ERRO — a importação é tudo ou nada. Os AVISOS não exigem correção, mas pedem "Ciente" na janela da análise antes de importar.

Organização:

- estrutura do cabeçalho (`#reportarEstrutura`): coluna obrigatória ausente ou ambígua, coluna sem cabeçalho, coluna opcional ausente;

- regras por linha (`#validarLinha`), numeradas nos comentários (0a data, 0b tipo de serviço, 0c nome/nascimento, 1 CNS, 1b município, 2 CEP, 3 CPF, 4 raça, 5 etnia, 7 colunas S/N, 8 estabelecimento…);

- regras entre linhas, avaliadas depois de ler a planilha toda: linhas repetidas (`#verificarDuplicidade`), CNS do profissional por grafia (`#reportarCnsProfissional`) e mesmo documento com nomes diferentes (`#reportarCpfComNomesDiferentes`).

A lista completa de tipos, com título e explicação, está em `AgrupamentoValidacao` e no CLAUDE.md.

@see ErroValidacao @see ExcelImportService

**Métodos:**
- `ValidacaoPlanilhaService() (construtor)` — Sem acesso ao banco: a herança de CNS só considera a própria planilha.
- `ValidacaoPlanilhaService(Function<String, String> cnsConhecidoPorCpf) (construtor)`
- `ValidacaoPlanilhaService(Function<String, String> cnsConhecidoPorCpf, BiFunction<String, String, String> ibgePorCep) (construtor)`
- `comNomePacientePorCpf(Function<String, String> nomePacientePorCpf): ValidacaoPlanilhaService` — Liga a consulta do nome do paciente já cadastrado (ex.: via `PacienteRepository`).
- `NomePaciente(String nome) (construtor)`
- `buscarIbgeNoCep(String cep, String municipio): String` *(privado)* — Busca o IBGE pelo CEP; falha inesperada conta como "não encontrado" (não interrompe a análise).
- `GrafiaMedico(String nome, String cnsCadastro) (construtor)`
- `validar(String caminhoArquivo): List<ErroValidacao>` — Lê e valida todas as linhas da planilha Excel informada. Considera a primeira aba. Confere a estrutura do cabeçalho (se houver problema estrutural, para aí); depois valida cada linha com dados (linhas vazias são ignoradas, como na importação) e, no fim, as regras entre linhas. Linha que não pode ser lida vira ERRO `LINHA_ILEGIVEL` — toda linha com dados precisa ser importada.
- `validarLinha(LinhaImportacaoDTO dto, int linha, List<ErroValidacao> erros): void` *(privado)* — Aplica as regras de uma linha (numeradas nos comentários: 0a data, 0b tipo de serviço, 0c nome/nascimento, 1 CNS, 1b município, 2 CEP, 3 CPF, sexo, 4 raça, 5 etnia, CBO, especialidade, CPF do médico, hora, CID, telefone, endereço, 7 colunas S/N, 7c vigência do layout, 8 estabelecimento, nome longo/a conferir, data fora do esperado). Cada problema vira um `ErroValidacao` (ERRO ou AVISO) na lista.
- `verificarDuplicidade(Row row, Map<String, Integer> colunas, int linha, DetectorDuplicidade duplicidade, List<ErroValidacao> erros): void` *(privado)* — Linhas do mesmo atendimento na planilha (`DetectorDuplicidade`), com a mesma chave da importação: o DTO é lido de novo e passado pelo `AtendimentoProcessor` (CPF, SIGTAP, data e hora normalizados). Linha que o processador rejeita já tem o erro apontado pelas outras regras e fica fora da comparação. ; Mesmo horário → ERRO `LINHA_DUPLICADA` (uma linha seria perdida) ; Horário diferente → AVISO `SUSPEITA_DUPLICIDADE` (importadas separadas)
- `dataAtendimento(LinhaImportacaoDTO dto): java.time.LocalDate` *(privado)* — Data do atendimento da linha, ou `null` se vazia/inválida (já apontada pela Regra 0a).
- `especialidadeDaLinha(LinhaImportacaoDTO dto): String` *(privado)* — Especialidade da linha, já separada do nome do médico na planilha legado (célula única "ESPECIALIDADE; NOME" mapeada para os dois campos) — mesma regra de `AtendimentoProcessor.separarEspecialidadeEMedico`.
- `registrarMedico(LinhaImportacaoDTO dto, int linha, boolean especialidadeMedicoCombinados, Map<String, GrafiaMedico> grafias, Map<String, String> cnsPorCpfNaPlanilha): void` *(privado)* — Registra a grafia do médico da linha para a regra de CNS do profissional (avaliada no fim, em `#reportarCnsProfissional`). O CNS é buscado por nome no cadastro (`medicos_cns.csv`, com apelidos), como na importação; quando encontrado, fica associado ao CPF da linha para que outras grafias do mesmo CPF possam herdá-lo.
- `reportarCnsProfissional(Map<String, GrafiaMedico> grafias, Map<String, String> cnsPorCpfNaPlanilha, List<ErroValidacao> erros): void` *(privado)* — Regra 10: CNS do profissional — um AVISO por grafia de médico que não está em Configurações → CNS de Médicos. Não bloqueia (a importação segue e o CNS pode ser completado depois), mas a geração do BPA-I bloqueia atendimentos sem CNS. Quando o mesmo CPF tem CNS conhecido (outra grafia cadastrada nesta planilha, ou `Medico.cns` no banco), o aviso diz que o CNS será herdado e sugere cadastrar a grafia como apelido.
- `registrarNomePaciente(LinhaImportacaoDTO dto, int linha, Map<String, Map<String, NomePaciente>> nomesPorCpf): void` *(privado)* — Acumula o nome do paciente pelo documento que o identifica: CPF válido, ou o CNS válido do paciente sem CPF (chave do cadastro — recomendação 68).
- `reportarCpfComNomesDiferentes(Map<String, Map<String, NomePaciente>> nomesPorCpf, List<ErroValidacao> erros): void` *(privado)* — Regra 11: mesmo CPF de paciente (ou mesmo CNS do paciente sem CPF — recomendação 68) com nomes diferentes, na planilha ou em relação ao nome já gravado no banco. AVISO (decisão 12, 16/10/2026): a importação grava um só paciente com o nome da última linha.
- `buscarNomePacienteNoBanco(String cpf): String` *(privado)* — Nome do paciente já gravado no banco pela chave do cadastro (CPF, ou a chave interna do paciente sem CPF); falha na consulta não interrompe a análise.
- `buscarCnsNoBanco(String cpf): String` *(privado)* — Consulta o CNS já conhecido no banco para o CPF; falha na consulta não interrompe a análise.
- `obterMapeamentoEstrutura(String caminhoArquivo): PlanilhaColumnMapper.ResultadoMapeamento` — Lê apenas a linha de cabeçalho da planilha e retorna o mapeamento estrutural completo (campos encontrados, ausentes, duplicados e colunas do cabeçalho que não bateram com nenhum alias cadastrado). Usado para montar um diagnóstico detalhado quando `#validar` já indicou um problema de estrutura — evita carregar essa informação em todo fluxo de validação, já que só é necessária nesse caso.
- `reportarEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, List<ErroValidacao> erros): boolean` *(privado)* — Reporta problemas estruturais do cabeçalho a partir do mapeamento por nome: campos obrigatórios não encontrados em nenhuma coluna, e campos que casaram com mais de uma coluna (cabeçalho ambíguo). A ordem das colunas não importa — só a presença e a unicidade de cada campo.
- `mensagemColunaOpcionalAusente(String campo): String` *(privado)* — Monta a mensagem do aviso de coluna opcional ausente (`ErroValidacao#COLUNA_OPCIONAL_AUSENTE`), específica por campo — cada campo opcional tem um comportamento de fallback diferente, então o texto genérico não serve para todos.
- `construirDetalheFormatoLegado(String exemploBruto): String` *(privado)* — Monta a mensagem do aviso de formato legado (coluna "Especialidade/Médico" combinada). Quando há um exemplo real da planilha, anexa o valor bruto encontrado já separado nos dois campos — mesmo texto validado no mockup de "Analisar Planilha".
- `gerarLogTxt(List<ErroValidacao> erros, String nomeArquivo): String` — Gera um relatório TXT formatado a partir da lista de erros de validação. Cabeçalho (arquivo, data/hora, contagem de erros e avisos) e, depois, as seções de ERROS e de AVISOS, organizadas como na tela (`#anexarGrupos`). Sem nenhum problema, diz que a planilha está apta para importação. Só é gravado em arquivo pelo botão "Salvar Log TXT" da janela da análise.
- `anexarGrupos(StringBuilder sb, List<ErroValidacao> erros): void` *(privado)* — Escreve os grupos de `AgrupamentoValidacao` — mesma organização da tela: por tipo, explicação uma vez, ocorrências iguais juntas.

---

## Pacote `util`

### `CboUtils`
`src/main/java/br/gov/ses/fillbpai/util/CboUtils.java`

CBO (Classificação Brasileira de Ocupações) do profissional — campo `prd-cbo` do BPA-I (seq 5, posições 031–036, 6 caracteres).

Na planilha o CBO costuma vir com máscara (`2251-25`, `225.125`). Gravado como veio, ele tinha 7 caracteres e deslocava todos os campos seguintes do registro posicional. Por isso o valor é reduzido aos dígitos na importação e na geração, e só 6 dígitos são aceitos.

**Métodos:**
- `normalizar(String cbo): String` — Só os dígitos do CBO (`"2251-25"` → `"225125"`); nulo vira vazio.
- `isValido(String cbo): boolean` — CBO válido: exatamente 6 dígitos depois de `#normalizar`.

### `CepUtils`
`src/main/java/br/gov/ses/fillbpai/util/CepUtils.java`

Regras do CEP do paciente (BPA-I seq 29), usadas pela análise e pela importação: normalização (só dígitos), tamanho (8 dígitos, sem completar zeros), CEP de preenchimento (dígitos iguais) e CEP de outro estado para município de MS. A busca do IBGE pelo CEP fica em `IbgeUtils`.

**Métodos:**
- `normalizar(String cep): String` — Normaliza o CEP removendo hífens, espaços e qualquer caractere não numérico. Exemplos: "79003-020" -> "79003020" "79003 020" -> "79003020" "79.003-020" -> "79003020"
- `isValido(String cep): boolean` — Retorna true se o CEP, após normalização, tiver exatamente 8 dígitos.
- `isFalso(String cep): boolean` — CEP de 8 dígitos todos iguais (00000000, 99999999) — CEP de preenchimento (16/10/2026).
- `mensagemTamanhoInvalido(String cep): String` — Mensagem de CEP com tamanho errado; com 7 dígitos orienta formatar a coluna como texto (o Excel tira o zero à esquerda de CEP digitado como número). Não se completa com zero — mesma decisão do CPF.
- `isForaDeMsComMunicipioDeMs(String cep, String municipio): boolean` — CEP válido (8 dígitos) que não é de Mato Grosso do Sul (CEPs de MS começam com 79) para um município encontrado na tabela de municípios de MS. Quando o município está na tabela o CEP não é usado para achar o IBGE, então um CEP de outro estado passava sem conferência (decisão de 16/10/2026: aviso).
- `mensagemForaDeMs(String cep, String municipio): String` — Mensagem do aviso de CEP fora de MS — análise e log de importação.

### `CidUtils`
`src/main/java/br/gov/ses/fillbpai/util/CidUtils.java`

Extrai o código CID-10 da célula "CID da consulta" (BPA-I seq 13, `prd-cid`, 4 posições ALFA).

Antes o gerador só tirava o que não era letra ou número e cortava em 4 caracteres: `"CID I10"` virava `CIDI`, `"I10
- Hipertensão"` virava `I10H`, `"I10, E11"` virava `I10E` — CIDs inexistentes, que o BPA critica e descarta. E uma célula com a descrição passava do tamanho da coluna no banco e cancelava a importação inteira.

**Métodos:**
- `extrair(String celula): String` — O código CID-10 da célula, quando há exatamente um (`"CID I10"`, `"I10; Hipertensão"` → `I10`); `null` se vazia, sem código (`"Hipertensão"`) ou com mais de um (`"I10, E11"`).
- `isSemCid(String celula): boolean` — `true` quando a célula só tem traço (`-`, `--`, `–`, `—`): forma usada nas planilhas para "sem CID". Tratado como CID não informado — aviso, e o BPA-I sai com o CID em branco, como o layout permite (19/10/2026; antes era CID_INVALIDO e bloqueava).
- `motivoInvalido(String celula): String` — Motivo de a célula preenchida não ter um CID aceito — para as mensagens de erro.

### `CnsProfissionalUtils`
`src/main/java/br/gov/ses/fillbpai/util/CnsProfissionalUtils.java`

Utilitário para resolução do CNS (Cartão Nacional de Saúde) do profissional a partir do nome, utilizando cache local (medicos_cns.csv).

O CNS é a chave canônica de cada médico; os nomes são apelidos (aliases) dela — a mesma planilha pode grafar o nome de formas diferentes (com/sem sobrenome, com/sem acento) e todas devem resolver para o mesmo CNS. O arquivo continua no formato `nome;cns`: várias linhas com o mesmo CNS já significam, por definição, apelidos do mesmo médico — não há migração de formato, só de leitura.

A busca por nome é normalizada: uppercase, sem acentos, trim. Exemplo: "José da Silva" e "JOSE DA SILVA" são considerados iguais.

Profissionais não encontrados são reportados como avisos no log de importação.

**Métodos:**
- `buscar(String nome): CnsResultado` — Busca o CNS do profissional pelo nome (ou qualquer apelido cadastrado).
- `obterTodosMedicos(): List<MedicoInfo>` — Lista todos os médicos cadastrados, agrupados por CNS. O primeiro apelido de cada médico (ordem de cadastro) é o nome principal.
- `cadastrar(String cns, String primeiroNome): void` — Cadastra um médico novo.
- `adicionarApelido(String cns, String novoNome): void` — Adiciona um apelido (variação de nome) a um médico já cadastrado.
- `removerApelido(String cns, String nome): void` — Remove um apelido de um médico. Recusa remover o único apelido restante (use `#removerMedico(String)` para excluir o médico inteiro).
- `alterarCns(String cnsAntigo, String cnsNovo): void` — Altera o CNS de um médico já cadastrado (corrige erro de digitação).
- `removerMedico(String cns): void` — Remove um médico e todos os seus apelidos.
- `limparCache(): void` — Limpa o cache em memória, forçando recarga na próxima busca. Útil para testes e recarregamento manual.
- `usarCaminhoParaTeste(String caminho): void` — Só para testes: aponta o armazenamento local para outro arquivo (ex.: um arquivo temporário) e força recarga, para não gravar no CSV real do projeto durante a suíte de testes.
- `carregarCsvSeNecessario(): void` *(privado)* — Carrega o arquivo CSV de profissionais do classpath e arquivo externo (lazy loading). Formato: nome;cns (separador ponto-e-vírgula) Encoding: UTF-8
- `carregarDeClasspath(): void` *(privado)* — Carrega entradas do CSV embutido no classpath.
- `carregarDeArquivoExterno(): void` *(privado)* — Carrega entradas do arquivo externo (src/main/resources). Usado para capturar entradas adicionadas pelo cadastro que ainda não foram recompiladas no classpath.
- `carregarDeReader(BufferedReader reader): void` *(privado)* — Carrega entradas de um BufferedReader. Formato aceito: nome;cns (novo) ou cpf;cns;nome (legado — migrado automaticamente)
- `reescreverArquivoExterno(): void` *(privado)* — Reescreve o arquivo externo inteiro a partir do estado atual em memória — necessário porque atualização e remoção não são operações de append. Uma linha por apelido, ordenadas por nome para diffs de git legíveis. Nunca toca no CSV do classpath.
- `normalizar(String texto): String` — Normaliza texto para busca: remove acentos, converte para maiúsculo, trim. Garante que "José da Silva" encontra "JOSE DA SILVA".
- `CnsResultado(String cns, String aviso) (construtor)`
- `CnsResultado.getCns(): String` — CNS do profissional ou null se não encontrado.
- `CnsResultado.getAviso(): String` — Mensagem de aviso ou null se encontrado sem problemas.
- `MedicoInfo(String cns, List<String> apelidos): record` — Um médico cadastrado: CNS e seus apelidos (nomes), em ordem de cadastro — índice 0 é o nome principal.
- `MedicoInfo.nomePrincipal(): String`
- record `MedicoInfo(String cns, List<String> apelidos)` — Um médico cadastrado: CNS e seus apelidos (nomes), em ordem de cadastro — índice 0 é o nome principal.

### `CnsUtils`
`src/main/java/br/gov/ses/fillbpai/util/CnsUtils.java`

Validações do CNS (Cartão Nacional de Saúde) do paciente, usadas pela análise e pela importação.

- O CNS tem sempre 15 dígitos; o dígito verificador segue o algoritmo do Ministério da Saúde (`#isDvValido`).

- Paciente com CPF: o CNS é opcional (não vai para o BPA-I); vazio não gera aviso, inválido gera aviso e não é gravado (`#processar`).

- Paciente sem CPF: o CNS é obrigatório e válido — é o documento do paciente no BPA-I e a chave do cadastro (`#isCnsSemCpfValido`).

**Métodos:**
- `normalizar(String cns): String` — Remove caracteres não numéricos do CNS.
- `isCnsSemCpfValido(String cns): boolean` — CNS de paciente sem CPF ("Paciente sem CPF" = Sim): é o documento que identifica o paciente no BPA-I (seq 10, `prd-cnspac`), então é obrigatório com exatamente 15 dígitos (decisão de 09/10/2026).
- `isDvValido(String cns): boolean` — Confere o dígito verificador do CNS (algoritmo do Ministério da Saúde, sem consulta externa): 15 dígitos, primeiro dígito 1 ou 2 (definitivo) ou 7, 8, 9 (provisório), e soma dos dígitos multiplicados pelos pesos 15 a 1 divisível por 11. O layout do BPA-I pede "CNS do paciente com dígito verificador válido" (seq 10).
- `motivoCnsSemCpfInvalido(String cns): String` — Motivo do CNS de paciente sem CPF não ser aceito — para as mensagens de erro.
- `mensagemTamanhoInvalido(String cnsNormalizado): String` — Mensagem do CNS com tamanho diferente de 15 dígitos (análise e importação).
- `processar(String cns): CnsResultado` — Processa o CNS do paciente: normaliza (só dígitos) e valida. Nunca lança exceção — paciente sem CPF, que exige o CNS, é conferido antes (`#isCnsSemCpfValido`). ; vazio → CNS vazio, sem aviso (Decisão 13: com CPF o CNS não vai para o BPA-I); ; 15 dígitos → gravado; dígito verificador errado → aviso CNS_DV_INVALIDO; ; sem 15 dígitos (a menos ou a mais) → aviso CNS_INVALIDO e CNS vazio: o valor errado não é gravado (recomendação 26, 19/10/2026).
- `CnsResultado(String cns, List<String> avisos) (construtor)`
- `CnsResultado.getCns(): String` — CNS normalizado (somente dígitos).
- `CnsResultado.getAvisos(): List<String>` — Lista de avisos gerados durante a validação. Vazia se tudo OK.

### `ColunaAliasUtils`
`src/main/java/br/gov/ses/fillbpai/util/ColunaAliasUtils.java`

Registro de aliases (nomes de cabeçalho aceitos) para cada campo canônico lido da planilha de atendimentos.

Duas camadas, carregadas na mesma ordem de precedência já usada por `CnsProfissionalUtils`:

- **Padrão** — classpath (`/dados/colunas_aliases.csv`), versionado no git, comum a todas as instalações.

- **Local** — arquivo externo (`src/main/resources/dados/colunas_aliases.csv`), aliases adicionados por esta instalação. Complementa o padrão e pode ser removido; o padrão não pode.

Formato do CSV: `campoCanonico;alias` (separador ponto-e-vírgula).

**Métodos:**
- `AliasInfo(String nome, Origem origem): record`
- `resolverCampo(String textoCabecalho): String` — Resolve o campo canônico correspondente a um texto de cabeçalho, ou `null` se nenhum alias cadastrado bater com o texto informado.
- `obterCamposCanonicos(): List<String>` — Lista, na ordem cadastrada, os campos canônicos conhecidos (padrão + locais).
- `obterAliases(String campoCanonico): List<AliasInfo>` — Aliases cadastrados para um campo canônico, com a origem de cada um.
- `obterTodosRegistrados(): Map<String, List<AliasInfo>>` — Todos os campos e seus aliases cadastrados — usado pela tela de configuração para listar tudo de uma vez.
- `salvar(String campoCanonico, String alias): void` — Cadastra um novo alias local para um campo canônico e persiste no arquivo CSV externo desta instalação.
- `remover(String campoCanonico, String alias): void` — Remove um alias **local**. Aliases padrão (classpath) nunca são removidos por aqui — para isso existe `#restaurarPadrao`.
- `restaurarPadrao(String campoCanonico): void` — Remove todos os aliases locais de um campo, voltando só ao padrão.
- `limparCache(): void` — Limpa o cache em memória, forçando recarga na próxima chamada.
- `usarCaminhoParaTeste(String caminho): void` — Só para testes: aponta o armazenamento local para outro arquivo (ex.: um arquivo temporário) e força recarga, para não gravar no CSV real do projeto durante a suíte de testes.
- `reescreverArquivoExterno(): void` *(privado)* — Reescreve o arquivo externo inteiro a partir do estado atual em memória de `aliasesLocais` — necessário porque remoção não é uma operação de append. Nunca toca no CSV do classpath.

### `CpfUtils`
`src/main/java/br/gov/ses/fillbpai/util/CpfUtils.java`

Regras do CPF (paciente e médico): normalização, tamanho, CPF de preenchimento e dígitos verificadores. Também gera a chave interna do paciente sem CPF — o CPF é a chave do cadastro de pacientes no banco.

**Métodos:**
- `normalizar(String cpf): String` — Normaliza o CPF removendo pontos, hífens e qualquer caractere não numérico. Exemplos: "123.456.789-00" -> "12345678900" "123 456 789 00" -> "12345678900"
- `isValido(String cpf): boolean` — Retorna true se o CPF, após normalização, tiver exatamente 11 dígitos.
- `isFalso(String cpf): boolean` — CPF falso de preenchimento: 11 dígitos todos iguais (`00000000000`, `11111111111`...). Erro bloqueante (decisão de 08/10/2026) — como o CPF é a chave do paciente, todos os atendimentos com o mesmo CPF falso viravam um único paciente. Paciente sem CPF vai pela coluna "Paciente sem CPF" = Sim, com o CPF vazio.
- `isDvValido(String cpf): boolean` — Confere os dois dígitos verificadores do CPF (algoritmo oficial, módulo 11). Pega o CPF com um dígito digitado errado — que antes passava e podia coincidir com o CPF de outra pessoa. Atenção: CPF de dígitos todos iguais passa no cálculo; use `#isFalso` para esse caso.
- `digitoVerificador(String cpf, int quantidade): int` *(privado)* — Dígito verificador calculado sobre os `quantidade` primeiros dígitos.
- `chaveSemCpf(String nome, java.time.LocalDate dataNascimento): String` — **Chave antiga** (até 19/10/2026) — hoje usada só para achar pacientes já gravados e migrá-los para `#chaveSemCpfPorCns`, e como reserva na detecção de linhas repetidas quando o CNS é inválido (a linha já tem erro). Chave interna para paciente sem CPF ("Paciente sem CPF" = Sim): o CPF é a chave do paciente no banco (obrigatória, única, até 14 caracteres), então esses pacientes são identificados por `"SC"` + 12 caracteres derivados do nome (sem acento/caixa/espaços extras) e da data de nascimento. Estável — reimportar a mesma planilha encontra o mesmo paciente. Nunca vai para o BPA-I: o gerador trata a chave como CPF vazio.
- `chaveSemCpfPorCns(String cns): String` — Chave interna do paciente sem CPF pelo CNS (recomendação 68, 19/10/2026): `"SC"` + o CNS em base 36, completado com zeros até 12 caracteres (cabe nos 14 da coluna; reversível, então dois CNS nunca dão a mesma chave). Substitui a chave por nome + nascimento (`#chaveSemCpf`), que juntava homônimos nascidos no mesmo dia e separava a mesma pessoa escrita de dois jeitos. `null` quando o CNS não tem 15 dígitos.
- `isChaveSemCpf(String cpf): boolean` — `true` se o valor é uma chave interna de paciente sem CPF, e não um CPF.

### `DateUtils`
`src/main/java/br/gov/ses/fillbpai/util/DateUtils.java`

Classe utilitária responsável por conversões de datas. Centraliza regras de parsing para evitar duplicação de código.

**Métodos:**
- `parse(String dataStr): LocalDate` — Converte uma String para LocalDate aceitando múltiplos formatos.
- `formatar(LocalDate data): String` — Data no formato das mensagens (`15/03/2026`); `null` se nula.
- `formatarParaMensagem(String valor): String` — Valor da célula para as mensagens: a data em `dd/MM/aaaa` quando a célula é reconhecida como data (célula de data do Excel chega como `2026-03-15`), senão o texto como veio.
- `idadeNaData(LocalDate nascimento, LocalDate dataAtendimento): int` — Idade em anos completos na data do atendimento (prd-idade do BPA-I) — não na data de hoje: a remessa pode ser gerada meses depois.
- `incoerenciaNascimento(LocalDate nascimento, LocalDate dataAtendimento): String` — Motivo de a data de nascimento não fazer sentido para o atendimento, ou `null` se fizer: nascimento depois do atendimento, ou idade acima de `#IDADE_MAXIMA` anos (limite do layout).
- `hoje(): LocalDate` — Data de hoje. A propriedade de sistema `fillbpai.hoje` (aaaa-MM-dd) fixa a data — usada pelos testes (pom.xml), para o aviso de data fora do esperado não mudar com o passar do tempo.
- `dataAtendimentoForaDoEsperado(LocalDate dataAtendimento, LocalDate hoje): String` — Motivo de a data do atendimento estar fora do esperado, ou `null`: data futura, ou mais de `#ANOS_ATENDIMENTO_ANTIGO` anos antes de `hoje` — em geral erro de digitação no ano, que levaria o atendimento para uma competência errada (recomendação 4, 19/10/2026). Aviso, não erro: a data pode estar certa.
- `mensagemDataForaDoEsperado(LocalDate dataAtendimento, String motivo): String` — Mensagem do aviso de `#dataAtendimentoForaDoEsperado` (análise e log de importação).

### `EnderecoUtils`
`src/main/java/br/gov/ses/fillbpai/util/EnderecoUtils.java`

Bairro (BPA-I seq 34, 30 posições) e complemento (seq 32, 10 posições) do endereço do paciente — campos opcionais, só informativos (16/10/2026).

Antes eram só cortados no meio da palavra ("Apartamento 201 Bloco B" → "Apartament"), e valores de preenchimento ("-", "N/A", "0") iam para o BPA-I como se fossem o bairro ou o complemento.

**Métodos:**
- `Complemento(String valor, String semCorte, boolean cortado): record` — Complemento normalizado e se precisou ser cortado (merece aviso — decisão de 16/10/2026).
- `isVazioOuPreenchimento(String valor): boolean` — Célula com valor de preenchimento ou vazia.
- `bairro(String celula): String` — Bairro para o BPA-I: vazio/preenchimento → `null`; sem a palavra "Bairro" no começo; cortado em 30 na última palavra inteira (sem aviso).
- `complemento(String celula): Complemento` — Complemento para o BPA-I: vazio/preenchimento → `null`; palavras comuns abreviadas (Apartamento → AP, Bloco → BL, Casa → CS…); cortado em 10 na última palavra inteira — `cortado` indica que algo se perdeu.
- `mensagemComplementoCortado(String celula, Complemento complemento): String` — Mensagem do aviso de complemento cortado — análise e log de importação.
- `isBairroIgualMunicipio(String bairro, String municipio): boolean` — Bairro com o mesmo nome do município do paciente (ignorando acento, caixa e a sigla da UF) — provavelmente a cidade digitada na coluna do bairro.
- record `Complemento(String valor, String semCorte, boolean cortado)` — Complemento normalizado e se precisou ser cortado (merece aviso — decisão de 16/10/2026).

### `EspecialidadeUtils`
`src/main/java/br/gov/ses/fillbpai/util/EspecialidadeUtils.java`

Normaliza o texto da especialidade do médico vindo da planilha.

Algumas planilhas trazem um prefixo "Médico "/"Médica " antes do nome real da especialidade (ex.: "Médico Endocrinologista" em vez de apenas "Endocrinologista"). Isso não é o mesmo problema tratado por `StringUtils.separarEspecialidadeEMedico` (planilha legado com especialidade e nome do médico combinados numa única célula) — aqui a célula já é só a especialidade, só que com um prefixo redundante.

Usado tanto na importação (`AtendimentoProcessor`, para persistir o valor já limpo) quanto na geração (`GeradorBPAiService`, como proteção para atendimentos já persistidos antes desta normalização existir — mesmo padrão de defesa em duas pontas usado para raça, CBO, CID etc.).

**Métodos:**
- `normalizar(String especialidade): String` — Remove o prefixo "Médico"/"Médica" do início do texto, se presente, preservando a grafia original do restante (só o prefixo é reconhecido por acento/caixa; o nome da especialidade não é alterado). Sem o prefixo, devolve o texto apenas com `trim()`. Nulo permanece nulo.
- `padronizar(String especialidade): String` — Forma gravada na importação: `#normalizar` + maiúsculas + espaços internos repetidos reduzidos a um. Assim `"Cardiologia"`, `"CARDIOLOGIA"` e `"cardiologia "` são a mesma especialidade na árvore da tela e na chave da folha (decisão de 08/10/2026 — antes eram especialidades diferentes, e o mesmo médico podia ganhar duas folhas). Acentos são mantidos. Vazio vira `null`.
- `usaProcedimentoFixo(String especialidade): boolean` — Especialidades que usam procedimento fixo (`0301010315`) no BPA-I, qualquer que seja o tipo de serviço: NUTRICIONISTA e PSICÓLOGO (após `#normalizar`, sem diferença de caixa). Regra única usada pela geração (`prd-pa`) e pela exigência do tipo de serviço na análise e na importação — para essas especialidades o tipo vazio continua aceito (decisão de 08/10/2026).

### `EtniaUtils`
`src/main/java/br/gov/ses/fillbpai/util/EtniaUtils.java`

Resolve o código oficial (4 caracteres) de uma etnia indígena a partir do nome, usando a tabela em `dados/etnias_indigenas.csv` (carregada do classpath, somente leitura — sem cadastro via UI e sem chamada de rede).

A tabela tem dois formatos de código, ambos com 4 caracteres: numérico (`0001`–`0334`, com lacunas) e alfanumérico com prefixo "X" (`X265`–`X411`, também com lacunas) — por isso o código é tratado como texto (ALFA), nunca zero-padded.

A faixa `0265`–`0334` é uma tabela suplementar/legada que repete alguns nomes já presentes em `0001`–`0264` com código diferente (ex.: "ARARA DE RONDONIA" aparece como `0014` e como `0270`). O carregamento usa `putIfAbsent` — a primeira ocorrência no arquivo vence, e o arquivo lista `0001`–`0264` antes de `0265`–`0334`, então a faixa principal tem precedência sobre a suplementar.

Um nome pode listar nomes alternativos entre parênteses, separados por vírgula (ex.: `ACONA (WAKONAS, NACONAS, JAKONA, ACORANES)`): o nome principal e cada alternativo resolvem para o mesmo código.

**Métodos:**
- `resolver(String nomeEtnia): String` — Resolve o código da etnia pelo nome.
- `limparCache(): void` — Limpa o cache em memória, forçando recarga na próxima busca. Útil para testes.
- `variantesDoNome(String nome): List<String>` — Expande um nome do CSV no formato `"NOME (ALT1, ALT2, ...)"` em todas as formas aceitas na busca: o nome completo como está no arquivo, o nome principal (antes dos parênteses) e cada nome alternativo. Nomes sem parênteses retornam apenas a si mesmos.
- `normalizar(String texto): String` — Normaliza texto para busca: remove acentos, converte para maiúsculo, trim.

### `IbgeUtils`
`src/main/java/br/gov/ses/fillbpai/util/IbgeUtils.java`

Utilitário para resolução de código IBGE de municípios.

Estratégia de resolução (em cascata): 1. PRIMÁRIO: Busca por nome do município em arquivo CSV embutido
- Sem rede, resultado instantâneo
- Normalização Unicode para matching (remove acentos, uppercase)
- CSV contém municípios de MS (pode ser expandido)

2. SECUNDÁRIO: Cache de CEPs resolvidos (inclui dados pré-carregados do banco)
- Pré-populado antes da importação com CEP → codigoIbge dos endereços já persistidos
- Sem rede, resultado instantâneo para CEPs já conhecidos

3. FALLBACK: Consulta APIs de CEP pela internet — ViaCEP; se falhar, BrasilAPI e depois OpenCEP (reservas)
- Usado apenas quando CEP não está no cache
- Determinístico (CEP → município é 1:1)
- Resultados cacheados em memória para evitar chamadas repetidas

Códigos IBGE:
- Padrão IBGE: 7 dígitos (2 estado + 4 município + 1 verificador)
- BPA-I (prd-ibge): 6 posições (sem dígito verificador)
- Esta classe retorna o código completo (7 dígitos). A truncagem para 6 é feita no GeradorBPAiService.

**Métodos:**
- `preCarregarCacheDb(java.util.Map<String, String> cepParaIbge): void` — Pré-carrega o cache de CEPs com dados já persistidos no banco de dados. Deve ser chamado antes do loop de importação para evitar chamadas à API para CEPs que já foram resolvidos em importações anteriores.
- `resolver(String cep, String nomeMunicipio): IbgeResultado` — Resolve o código IBGE em cascata: 1. CSV (por nome do município) — sem rede, instantâneo 2. Cache/banco (por CEP) — sem rede, instantâneo para CEPs já conhecidos 3. APIs de CEP (ViaCEP → BrasilAPI → OpenCEP) — apenas se não encontrado no cache
- `avisoMunicipioPeloCep(String nomeMunicipio, String cep, String codigoIbge): String` — Aviso para quando o município não está na tabela de MS e o IBGE veio do CEP: mostra o município que o CEP indica, para o usuário conferir se é o mesmo que quis informar na planilha (o CEP pode estar errado).
- `descreverMunicipio(String codigoIbge): String` — "Cuiabá/MT (IBGE 5103403)", ou só o código se ele não estiver na tabela do IBGE.
- `nomeMunicipio(String codigoIbge): String` — Nome oficial e UF do município pelo código IBGE (7 dígitos), ex.: `"5103403"` → `"Cuiabá/MT"`. Tabela `dados/municipios_brasil.csv` (5.571 municípios, lista oficial do IBGE — servicodados.ibge.gov.br, obtida em 08/10/2026). Usada só para exibir o município encontrado pelo CEP; a busca do IBGE pelo nome continua só na tabela de MS.
- `buscarPorCep(String cep): String` — Busca o código IBGE pelo CEP: cache (CEPs já resolvidos, inclusive do banco) e, se não houver, as APIs de CEP na ordem de `#APIS_CEP` — ViaCEP; se falhar ou não tiver o CEP, BrasilAPI; depois OpenCEP. Resultados são cacheados em memória.
- `consultar(ApiCep api, String cep): Consulta` *(privado)* — Consulta uma API de CEP. Resultado: código IBGE encontrado; ou "não encontrado" (a API respondeu que o CEP não existe); ou falha (rede, fora do ar, resposta inesperada).
- `RespostaHttp(int status, String corpo): record` — Resposta HTTP simplificada (status + corpo) — permite simular as APIs nos testes.
- `ApiCep(String nome, String urlModelo, Pattern padraoIbge): record` *(privado)* — Uma API de CEP: URL (`%s` = CEP) e onde está o código IBGE na resposta.
- `usarBuscadorHttpParaTeste(Function<String, RespostaHttp> buscador): void` — Só para testes: simula as APIs de CEP sem rede; `null` volta ao HTTP real.
- `buscarPorNome(String nomeMunicipio): String` — Busca o código IBGE pelo nome do município no arquivo CSV embutido. A busca é case-insensitive e ignora acentos (normalização Unicode). O CSV é carregado na primeira chamada (lazy loading).
- `carregarCsvSeNecessario(): void` *(privado)* — Carrega o arquivo CSV de municípios do classpath (lazy loading). Formato: codigo_ibge;nome_municipio;uf Encoding: UTF-8
- `normalizar(String texto): String` — Normaliza texto para comparação: remove acentos e converte para uppercase. Utiliza java.text.Normalizer para decomposição Unicode. Exemplos:; "Campo Grande" → "CAMPO GRANDE"; "Três Lagoas" → "TRES LAGOAS"; "São Paulo" → "SAO PAULO"
- `IbgeResultado(String codigoIbge, String aviso) (construtor)`
- `IbgeResultado.getCodigoIbge(): String` — Código IBGE do município (7 dígitos) ou null se não resolvido.
- `IbgeResultado.getAviso(): String` — Mensagem de aviso ou null se resolvido sem problemas.
- record `RespostaHttp(int status, String corpo)` — Resposta HTTP simplificada (status + corpo) — permite simular as APIs nos testes.
- record `ApiCep(String nome, String urlModelo, Pattern padraoIbge)` — Uma API de CEP: URL (`%s` = CEP) e onde está o código IBGE na resposta.

### `LogradouroUtils`
`src/main/java/br/gov/ses/fillbpai/util/LogradouroUtils.java`

Tipo de logradouro do BPA-I (seq 30, `prd_lograd_pcnte`) pela tabela oficial de tipos de logradouro do DNE (`dados/tipos_logradouro.csv`, 393 tipos, código de 3 dígitos — fornecida pelo usuário em 15/10/2026; linhas com # são comentário: 023 Campo desativado em 19/10/2026).

Dois usos:

- `#resolver(String)` — o tipo no **começo do endereço** ("Rua das Flores" → 081 + "das Flores"); o prefixo sai do endereço, porque o tipo vai no campo próprio.

- `#codigoDaColuna(String)` — a coluna "Cód. Logradouro" da planilha, que pode trazer o código (`81`, `081`) ou o nome do tipo (`Rua`, `Av`).

Comparação sem acento e sem diferença de caixa. Vale o tipo mais longo que casar ("Rua Projetada 5" → 698 RUA PROJETADA), desde que sobre um nome depois dele ("Avenida Central" → 008 AVENIDA + "Central", e não 829 AVENIDA CENTRAL com o nome vazio). Abreviações comuns: R, AV, TV/TRAV/TRV, AL, PC/PCA, EST/ESTR, ROD.

Tipo não reconhecido: código `null` — o gerador usa o padrão 081 (Rua), decisão de negócio de 11/09/2026.

**Métodos:**
- `resolver(String enderecoRaw): LogradouroResultado` — Tipo no começo do endereço e o endereço sem ele.
- `codigoDaColuna(String celula): String` — Código da coluna "Cód. Logradouro": código numérico da tabela (1 a 3 dígitos, completado com zeros) ou nome/abreviação do tipo.
- `isColunaNaoReconhecida(String celula): boolean` — Coluna preenchida mas não reconhecida — para o aviso da análise e da importação.
- `restoDepoisDoPrefixo(String original, String norm, String prefixo): String` *(privado)* — Se `norm` começa com `prefixo` seguido de fim, espaço ou pontuação, o restante do texto original (sem a pontuação/espaços do começo); senão `null`.
- `normalizar(String texto): String` — Maiúsculas sem acento, caractere a caractere (o tamanho não muda, para o índice do prefixo valer no texto original).
- `LogradouroResultado(String codLogradouro, String endereco) (construtor)`
- `LogradouroResultado.getCodLogradouro(): String` — Código BPA-I do tipo de logradouro, ou null se não reconhecido.
- `LogradouroResultado.getEndereco(): String` — Endereço sem o prefixo do tipo de logradouro.

### `NomePacienteUtils`
`src/main/java/br/gov/ses/fillbpai/util/NomePacienteUtils.java`

Nome do paciente (BPA-I seq 19, `prd-nmpac`, 30 posições) — recomendações 29 e 30 (19/10/2026).

Usado pela análise, pela importação e pelo gerador (dados já gravados), para o nome sair igual nos três.

**Métodos:**
- `padronizar(String nome): String` — Nome padronizado: maiúsculas, sem espaços nas pontas e com espaços repetidos reduzidos a um. Acentos mantidos (ver Decisão 14 do runbook).
- `cortadoNoBpa(String nome): String` — Como o nome sai no BPA-I quando passa de 30 caracteres, ou `null` se couber.
- `motivoSuspeito(String nome): String` — Motivo de o nome parecer não ser um nome completo de pessoa, ou `null`: recém-nascido sem nome ("RN DE MARIA" — caso real, aceito), sem letras (-), com números, com símbolos ou com uma palavra só (TESTE, MARIA 2, -). Letras (com acento), espaço, hífen, apóstrofo e ponto são aceitos.

### `NumeroUtils`
`src/main/java/br/gov/ses/fillbpai/util/NumeroUtils.java`

Número do endereço do paciente para o BPA-I (seq 33, `prd_num_pcnte`, 5 posições ALFA; o layout indica "SN" como padrão).

Antes o número era só cortado em 5 caracteres: "SEM NUMERO" virava "SEM N", "123456" virava "12345", "100 apto 201" virava "100 a" e vazio saía em branco (decisões de 16/10/2026).

**Métodos:**
- `Resultado(String valor, boolean foraDoPadrao): record`
- `mensagemForaDoPadrao(String celula, Resultado resultado): String` — Mensagem do aviso de número fora do padrão — análise e log de importação.
- `normalizar(String celula): Resultado` — Número do endereço para o BPA-I: ; vazio, forma de "sem número" ou só traço → `SN`, sem aviso; ; número no padrão (`100`, `12A`) → como veio, sem aviso; ; outro valor com até 5 caracteres (`X`, `N/A`) → como veio, com aviso; ; mais de 5: o número do começo (também depois de Nº/KM) se tiver até 5 dígitos ("100 apto 201" → 100, "Km 12,5" → 12); senão `SN` ("123456", "Lote 10 Quadra 5") — os dois com aviso.
- record `Resultado(String valor, boolean foraDoPadrao)` — 

### `RacaUtils`
`src/main/java/br/gov/ses/fillbpai/util/RacaUtils.java`

Resolve o código de raça/cor do paciente (2 dígitos) usado no BPA-I (seq 21, `prd-raca`) a partir do texto livre da planilha.

Usado tanto por `ValidacaoPlanilhaService`/`AtendimentoProcessor` (para detectar raça ausente ou não reconhecida — grafia incorreta) quanto por `GeradorBPAiService` (para gerar o código no arquivo) — mesma régua nos dois lugares.

**Métodos:**
- `resolverCodigo(String raca): String` — Resolve o código de 2 dígitos a partir do texto da raça, ou `null` quando em branco ou não reconhecido. ; Por **palavra inteira**, sem acento e caixa: Branca/Branco, Preta/Preto, Parda/Pardo, Amarela/Amarelo, Indígena/Índio/Índia. Antes era por trecho, e "Indeterminado"/"Indefinida"/"Indisponível" viravam Indígena (05). ; O próprio código do BPA: `01`–`05` (ou `1`–`5`). ; Texto com mais de uma raça (`Preta/Parda`) ou com negação (`Não Branca`) não é reconhecido — antes ficava com a primeira raça encontrada. O código "99" (Sem informação) nunca é resolvido: está riscado no layout oficial; ausência de raça é tratada como erro, não como "99".
- `isIndigena(String raca): boolean` — true quando a raça informada corresponde ao código 05 (Indígena).
- `mensagemNaoReconhecida(String raca): String` — Mensagem para raça preenchida mas não reconhecida. "Negra/Negro" ganha explicação própria: o IBGE separa Preta e Parda (a população negra é a soma das duas), então não há conversão automática (decisão de 13/10/2026).

### `SexoUtils`
`src/main/java/br/gov/ses/fillbpai/util/SexoUtils.java`

Sexo do paciente para o BPA-I (seq 11, `prd-sexo`, 1 posição: F, M ou I — "I" desde a versão 04.11 do BPA).

Antes, valor fora de F/M/I/Feminino/Masculino/Indeterminado era gravado como veio: "Fem"/"Mulher" passavam do tamanho da coluna e cancelavam a importação, "H"/"1"/"X" iam para o BPA-I, e sexo vazio saía como F (16/10/2026).

**Métodos:**
- `normalizar(String valor): String` — Código F/M/I a partir do texto da planilha (sem acento, caixa ou ponto final): F, Feminino, Fem, Mulher → F; M, Masculino, Masc, Homem → M; I, Indeterminado → I. Vazio ou outro valor ("H", "1", "X") → `null`.

### `SimNaoUtils`
`src/main/java/br/gov/ses/fillbpai/util/SimNaoUtils.java`

Normaliza campos de resposta binária (S/N) lidos da planilha para o código de 1 caractere usado no layout BPA-I — hoje usado por `prd_situacao_rua` e `prd_sem_cpf`, ambos ALFA de 1 posição com valores "N" ou "S".

Usado tanto por `ValidacaoPlanilhaService` (análise pré-importação) quanto por `AtendimentoProcessor` (importação), para que os dois caminhos reconheçam exatamente os mesmos valores.

**Métodos:**
- `normalizar(String valor): String` — Normaliza o valor lido da planilha para `"S"`, `"N"` ou `null`. Aceita S/N, Sim/Não, 1/0 (case e acento insensível). Valor em branco ou não reconhecido retorna `null` — o chamador decide se avisa sobre um valor não reconhecido via `#isInvalido`.
- `isInvalido(String valor): boolean` — true quando o valor não está em branco mas não foi reconhecido como S/N.

### `StringUtils`
`src/main/java/br/gov/ses/fillbpai/util/StringUtils.java`

Funções de texto usadas na importação e na geração do BPA-I: corte no tamanho do campo (no caractere ou na última palavra inteira) e separação das células combinadas "código
- nome" (Estabelecimento) e "ESPECIALIDADE
- NOME" (Especialidade/Médico, formato legado).

**Métodos:**
- `limitarTamanho(String valor, int tamanhoMaximo): String` — Tira os espaços das pontas e corta no tamanho máximo, no caractere (`"Texto muito grande"` em 10 → `"Texto muit"`); `null` continua `null`. Para não partir palavra, ver `#limitarNaPalavra`.
- `limitarNaPalavra(String valor, int tamanhoMaximo): String` — Corta no tamanho máximo sem partir palavra: fica com as palavras inteiras que cabem ("Professor Doutor Fulano de Tal" em 20 → "Professor Doutor"). Uma única palavra maior que o limite é cortada no limite.
- `limitarTamanhoOuVazio(String valor, int tamanhoMaximo): String` — Como `#limitarTamanho`, mas devolve vazio no lugar de `null`.
- `isEstabelecimentoNaoInformado(String valor): boolean` — Célula de estabelecimento que, na prática, não informa nada: vazia, só espaços, só o separador (`-`, `—`) ou código zero (`0`, `000 -`). Tratada como "não informado" (aviso ESTABELECIMENTO_AUSENTE) — antes `-` virava um nome e `0` um código.
- `separarCodigoENome(String valor): String[]` — Separa a célula de estabelecimento `"CÓDIGO; NOME"` em `{codigo, nome`}. O código é sempre numérico (decisão de 08/10/2026): só vale como código o que vem antes do primeiro separador se for **só dígitos** (até 10). ; `"12345; HOSPITAL"` → `{"12345", "HOSPITAL"`} (hífens no nome são mantidos) ; `"1234567"` ou `"12345 -"` → `{"1234567", null`} (só o código) ; `"HOSPITAL SAO JOSE; UNIDADE 2"`, `"UBS; CENTRO"` → `{null, célula inteira`} — texto antes do hífen é parte do nome. Antes virava "código": estourava a coluna (importação cancelada) ou criava estabelecimento falso.
- `separarEspecialidadeEMedico(String valor): String[]` — Separa especialidade e nome do médico. Exemplo de entrada: "CARDIOLOGIA; JOÃO DA SILVA" Retorna: [0] -> especialidade [1] -> nome do médico

### `TelefoneUtils`
`src/main/java/br/gov/ses/fillbpai/util/TelefoneUtils.java`

Normaliza o telefone do paciente para o BPA-I (seq 35, `prd_ddtel_pcnte`, 11 posições NUM, opcional): DDD + número, só dígitos.

Antes o gerador tirava o que não era dígito e ficava com os 11 primeiros: `+55 67 99999-9999` virava `55679999999`, dois telefones na célula viravam um número inexistente, e uma célula com três telefones passava dos 20 caracteres da coluna e cancelava a importação.

**Métodos:**
- `Resultado(Situacao situacao, String numero): record`
- `normalizar(String celula): Resultado` — Lê o **primeiro** telefone da célula (separadores `/`, `;`, `,`, `|`, " e ", " ou "), tira o DDI `+55`/`55` e o `0` de longa distância e confere o tamanho: ; 10 ou 11 dígitos → `Situacao#VALIDO` ; 8 ou 9 dígitos → `Situacao#SEM_DDD` (o DDD não pode ser adivinhado) ; outro tamanho ou sem dígitos → `Situacao#INVALIDO` ; célula vazia → `Situacao#VAZIO`
- enum `Situacao` — Situação do telefone da célula.
- record `Resultado(Situacao situacao, String numero)` — 

### `TextoUtils`
`src/main/java/br/gov/ses/fillbpai/util/TextoUtils.java`

Normalização de texto para comparação insensível a acentuação e caixa — usado tanto para nomes de coluna de cabeçalho quanto para outros campos de texto comparados por igualdade aproximada.

**Métodos:**
- `normalizar(String texto): String` — Remove acentos, converte para maiúsculas e retira espaços nas pontas. Retorna string vazia para entrada nula ou em branco.
- `chaveNome(String nome): String` — Chave para comparar nomes de pessoa: sem acento, maiúsculas e espaços repetidos reduzidos ("Maria da Silva" = "MARIA DA SILVA").

### `TimeUtils`
`src/main/java/br/gov/ses/fillbpai/util/TimeUtils.java`

Classe utilitária responsável por conversões de horário.

Aceita múltiplos formatos comuns encontrados em planilhas:
- HH:mm
- HH:mm:ss
- H:mm
- HHmm
- HH.mm
- 8h30, 08h30, 8h30min, 14h (formato com "h")

**Métodos:**
- `parse(String timeStr): LocalTime` — Converte String para LocalTime aceitando múltiplos formatos.

### `VigenciaLayoutUtils`
`src/main/java/br/gov/ses/fillbpai/util/VigenciaLayoutUtils.java`

Vigência dos campos novos do layout BPA-I (data/Layout_Exportacao_BPA_2026.pdf): `prd_situacao_rua` vale a partir da competência 12/2024 e `prd_sem_cpf` a partir da competência 07/2026. Para atendimentos de competência anterior o campo vai em branco no arquivo (decisão 26, 16/10/2026).

A competência do registro é o mês da data do atendimento.

**Métodos:**
- `isAntesDaSituacaoRua(LocalDate dataAtendimento): boolean` — true quando a data é anterior à competência 12/2024 (data nula = vigente).
- `isAntesDoSemCpf(LocalDate dataAtendimento): boolean` — true quando a data é anterior à competência 07/2026 (data nula = vigente).
- `mensagemSituacaoRua(String competencia): String`
- `mensagemSemCpf(String competencia): String`
- `competencia(LocalDate data): String` — Competência MM/aaaa da data do atendimento.

---

## Pacote `repository`

### `AtendimentoBPAiRepository`
`src/main/java/br/gov/ses/fillbpai/repository/AtendimentoBPAiRepository.java`

Camada responsável apenas por persistência. Não contém regra de negócio.

**Métodos:**
- `AtendimentoBPAiRepository(EntityManager entityManager) (construtor)`
- `salvar(AtendimentoBPAi atendimento): void`
- `buscarTodos(): List<AtendimentoBPAi>`
- `buscarDuplicata(Paciente paciente, Medico medico, LocalDate dataAgendamento, String sigtap, java.time.LocalTime hora): Optional<AtendimentoBPAi>` — Busca atendimento existente pela chave natural de negócio: paciente + médico + data de agendamento + código SIGTAP + horário. Utilizado para evitar duplicatas na reimportação de planilhas.
- `buscarDuplicata(Paciente paciente, Medico medico, LocalDate dataAgendamento, String sigtap): List<AtendimentoBPAi>` *(privado)* — Todos os atendimentos com a chave sem o horário — ver a versão com horário.

### `EstabelecimentoRepository`
`src/main/java/br/gov/ses/fillbpai/repository/EstabelecimentoRepository.java`

Repositório para a entidade Estabelecimento. Busca por código (chave natural).

**Métodos:**
- `EstabelecimentoRepository(EntityManager entityManager) (construtor)`
- `buscarPorCodigo(String codigo): Optional<Estabelecimento>` — Busca estabelecimento pelo código.
- `salvar(Estabelecimento estabelecimento): void` — Persiste um novo estabelecimento.
- `buscarPorNome(String nome): Optional<Estabelecimento>` — Busca estabelecimento pelo nome, ignorando acentuação e caixa. Usado quando a célula "Estabelecimento" da planilha não trouxe um código reconhecido (formato "código; nome") — tenta reaproveitar um estabelecimento já cadastrado com esse nome, em vez de descartar o vínculo. A tabela é pequena (unidades de saúde do Núcleo), então a comparação em memória é suficiente.

### `MedicoRepository`
`src/main/java/br/gov/ses/fillbpai/repository/MedicoRepository.java`

Repositório para a entidade Medico. Busca por CPF (chave natural).

**Métodos:**
- `MedicoRepository(EntityManager entityManager) (construtor)`
- `buscarPorCpf(String cpf): Optional<Medico>` — Busca médico pelo CPF normalizado.
- `salvar(Medico medico): void` — Persiste um novo médico.

### `PacienteRepository`
`src/main/java/br/gov/ses/fillbpai/repository/PacienteRepository.java`

Repositório para a entidade Paciente. Busca por CPF (chave natural).

**Métodos:**
- `PacienteRepository(EntityManager entityManager) (construtor)`
- `buscarPorCpf(String cpf): Optional<Paciente>` — Busca paciente pela chave do cadastro: CPF normalizado (11 dígitos) ou a chave interna do paciente sem CPF.
- `salvar(Paciente paciente): void` — Persiste um novo paciente.

---

## Pacote `model`

### `AtendimentoBPAi`
`src/main/java/br/gov/ses/fillbpai/model/AtendimentoBPAi.java`

Entidade que representa um atendimento BPA-I.

Relacionamentos:
- Paciente (N:1) — dados do paciente e endereço
- Medico (N:1) — dados do médico
- Estabelecimento (N:1) — dados do estabelecimento

Campos próprios do atendimento:
- Tipo de serviço, SIGTAP, data, hora, CID, folha, INE
- Especialidade e CBO (podem variar por atendimento para o mesmo médico)
- CNS profissional (resolvido pelo nome do médico na importação — `medicos_cns.csv` — ou editado na tela)


Além disso, getters/setters dos campos.

### `Endereco`
`src/main/java/br/gov/ses/fillbpai/model/Endereco.java`

Entidade que representa o endereço de um paciente. Relacionamento 1:1 com Paciente. Atualizado pela última importação.


Além disso, getters/setters dos campos.

### `Estabelecimento`
`src/main/java/br/gov/ses/fillbpai/model/Estabelecimento.java`

Entidade que representa um estabelecimento de saúde. Chave natural: código (único). Dados atualizados pela última importação.


Além disso, getters/setters dos campos.

### `Medico`
`src/main/java/br/gov/ses/fillbpai/model/Medico.java`

Entidade que representa um médico. Chave natural: CPF (único). Dados atualizados pela última importação.


Além disso, getters/setters dos campos.

### `Paciente`
`src/main/java/br/gov/ses/fillbpai/model/Paciente.java`

Entidade que representa um paciente. Chave natural: CPF (único). Paciente sem CPF usa uma chave interna derivada do CNS ("SC" + 12 caracteres — `CpfUtils.chaveSemCpfPorCns`), que nunca vai para o BPA-I. Dados atualizados pela última importação.


Além disso, getters/setters dos campos.

---

## Pacote `dto`

### `AtendimentoBPAiDTO`
`src/main/java/br/gov/ses/fillbpai/dto/AtendimentoBPAiDTO.java`

DTO para exibição dos dados de atendimento na UI. Navega pelos relacionamentos (Paciente, Medico, Estabelecimento, Endereco) para montar os campos de apresentação.

**Métodos:**
- `AtendimentoBPAiDTO(AtendimentoBPAi entity) (construtor)`
- `fromEntity(AtendimentoBPAi a): AtendimentoBPAiDTO`
- `getFolha(): String` — Folha da última remessa BPA-I, com 3 dígitos ("003"); "—" se ainda não enviado. Folha gravada sem remessa (botão "Definir Folha", antes de 20/10/2026) não é mostrada: não é a folha de nenhum arquivo gerado.
- `getLinhaFolha(): String` — Linha dentro da folha na última remessa, com 2 dígitos ("47"); "—" se ainda não enviado.
- `getRemessa(): String` — Data/hora da última remessa BPA-I ("20/10/2026 14:32"), ou "não enviada".
- `getCpfPaciente(): String` — CPF do paciente; vazio para paciente sem CPF (a chave interna "SC..." não é exibida).
- `getSituacaoRuaPaciente(): String` — Situação de rua do atendimento (16/10/2026); atendimentos antigos mostram o valor legado do paciente.
- `getEnderecoEntity(): Endereco` *(privado)* — Helper para acessar o endereço do paciente.

Além disso, getters/setters dos campos.

### `LinhaImportacaoDTO`
`src/main/java/br/gov/ses/fillbpai/dto/LinhaImportacaoDTO.java`

DTO que transporta os dados brutos de uma linha da planilha Excel.

Ciclo de vida: 1. ExcelImportService preenche os campos crus (Strings) 2. AtendimentoProcessor valida, normaliza e converte 3. AtendimentoImportacaoService extrai entidades e persiste


Além disso, getters/setters dos campos.

---
