# fillBPAi — Projeto CLAUDE.md

## Sobre o Projeto
Aplicação desktop JavaFX que importa dados de atendimentos de saúde de planilhas Excel e gera arquivos magnéticos BPA-I (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde.

O Núcleo de Telessaúde de MS utiliza esta aplicação para importar dados de atendimentos no sistema SIA/SUS. O formato BPA-I tem layout posicional rígido (353 chars por registro, incluindo CRLF — layout 2026, ver `data/Layout_Exportacao_BPA_2026.pdf`).

## Stack
- Java 21, JavaFX, JPA/Hibernate (sem Spring Boot)
- H2 Database (embarcado, `hibernate.hbm2ddl.auto=update` — persiste dados entre reinicializações)
- Apache POI para leitura de `.xlsx`
- Maven para build

## Build & Run
- Usar o wrapper do projeto: `mvnw.cmd compile` (na raiz do projeto)
- `mvn test` roda automaticamente via PostToolUse hook (Edit/Write) — NÃO rodar manualmente em agents/subagents

## GitHub
- Repositório: https://github.com/sergioajiki/fillBPAi.git
- Branch principal: main
- Usuário autoriza commit e push direto para main neste projeto (sempre mostrar o que vai ser commitado antes de executar)

## Estrutura do Projeto
Arquitetura em camadas: `controller → service → repository → model`, com `util`, `dto` e `ui`.

### Modelo de Dados (normalizado)
- `Paciente` — chave natural: CPF (único). @OneToOne com Endereco. Campo `situacaoRua` ("S"/"N"/nulo, opcional) — só preenchido quando a planilha traz a coluna "Situação de Rua"; nulo = não informado (a geração do BPA-I usa "N" como padrão nesse caso).
- `Endereco` — 1:1 com Paciente. Inclui campo `codigoIbge` (7 dígitos).
- `Medico` — chave natural: CPF (único). Campos: id, cpf, nome.
- `Estabelecimento` — chave natural: codigo (único). Campos: id, codigo, nome.
- `AtendimentoBPAi` — @ManyToOne para Paciente, Medico, Estabelecimento. Campos próprios: tipoServico, sigtap, dataAgendamento, horaAtendimento, especialidadeMedico, cboMedico, cidConsulta, cnsProfissional, cnesNts, codIne, folha, pacienteSemCpf ("S"/"N"/nulo — ver Geração BPA-I).

### Validação Pré-Importação
Antes de importar, a planilha é validada por `ValidacaoPlanilhaService`. Se houver erros bloqueantes, a importação é **impedida** e um relatório de erros é exibido com opção de download em TXT. Avisos não-bloqueantes são exibidos em diálogo separado mas não impedem a importação.

`ErroValidacao` tem campo `severidade` (enum `ERRO`/`AVISO`) e método `isBloqueante()`.

Tipos de ERRO (bloqueantes):
- **CEP_AUSENTE** — CEP do endereço não informado
- **CEP_INVALIDO** — CEP presente mas com tamanho incorreto (diferente de 8 dígitos após normalização)
- **CPF_AUSENTE** — CPF do paciente não informado
- **CPF_INVALIDO** — CPF presente mas com tamanho incorreto (diferente de 11 dígitos após normalização)
- **ESTRUTURA_INVALIDA** — coluna obrigatória ausente do cabeçalho ou nome de coluna ambíguo (casa com mais de um campo canônico)

Tipos de AVISO (não bloqueantes):
- **CNS_INVALIDO** — CNS do paciente ausente ou com menos de 15 dígitos após normalização (não bloqueia a importação)
- **CNS_INCOMUM** — CNS do paciente com mais de 15 dígitos (formato incomum)
- **RACA_INDIGENA** — raça do paciente informada como Indígena
- **COLUNA_OPCIONAL_AUSENTE** — coluna opcional (`COD_LOGRADOURO`, `SITUACAO_RUA` ou `PACIENTE_SEM_CPF`) não encontrada no cabeçalho; mensagem específica por campo (`ValidacaoPlanilhaService.mensagemColunaOpcionalAusente`) explica o fallback usado
- **SITUACAO_RUA_INVALIDA** — coluna "Situação de Rua" presente mas valor da célula não reconhecido (aceita S/N, Sim/Não, 1/0 via `SimNaoUtils`) — será enviado "N" na remessa
- **PACIENTE_SEM_CPF_INVALIDO** — coluna "Paciente sem CPF" presente mas valor da célula não reconhecido (mesmas regras de `SimNaoUtils`) — valor será derivado automaticamente a partir do CPF do paciente

O botão **"Analisar Planilha"** (topBar, à esquerda de "Importar Planilha") permite validar sem importar.
Log de erros salvo automaticamente em `database/log_erros_validacao.txt`.

### Fluxo de Importação
1. `ValidacaoPlanilhaService` — valida regras bloqueantes (CNS, CEP, CPF). Se erros → bloqueia importação
2. `ExcelImportService` — lê Excel, retorna `LinhaImportacaoDTO`
3. `AtendimentoProcessor` — valida/normaliza (CNS, datas, etc.), retorna lista de avisos
4. `AtendimentoImportacaoService` — processa normalmente: findOrCreate para entidades, resolve IBGE via `IbgeUtils`, resolve CNS profissional via `CnsProfissionalUtils` (por nome), persiste atendimento

### Utilitários
- `IbgeUtils` — resolve código IBGE em cascata: 1) CSV por nome do município, 2) cache pré-carregado do banco (CEP → codigoIbge de `Endereco`), 3) API ViaCEP como último recurso. `preCarregarCacheDb()` chamado em `AtendimentoImportacaoService` antes do loop de importação.
- `CnsProfissionalUtils` — resolve CNS do profissional por **nome** (normalizado: uppercase, sem acentos). Fonte única: `dados/medicos_cns.csv` (classpath + arquivo externo `src/main/resources/dados/medicos_cns.csv`, formato `nome;cns`). Sem consulta ao DATASUS. Profissional não encontrado gera aviso no log de importação.
- `CnsUtils` — processa/valida CNS de pacientes (aceita CNS incomum >15 dígitos com aviso `CNS_INCOMUM`)
- `CepUtils` — normalização de CEP

### Geração BPA-I
- Geração individual (filtrada por especialidade/médico selecionados)
- Geração completa (atendimentos do mês de competência selecionado, folha auto-atribuída: especialidades em ordem alfabética → médicos em ordem alfabética → folha sequencial)
- Competência para geração: selecionada clicando no badge `📅 Competência: MM/YYYY` da barra fixa inferior; auto-detectada da primeira carga de dados se não definida manualmente
- `GeradorBPAiService.gerarArquivoCompletoComFileChooser(window, competenciaAtendimento)` recebe competência no formato `YYYYMM` do mês de atendimento e filtra por `YEAR/MONTH(dataAgendamento)`
- Seq 10 (prd-cnspac): sempre 15 espaços em branco — CNS do paciente não é utilizado neste campo
- Seq 12 (prd-ibge): código IBGE real do endereço, truncado para 6 dígitos
- Seq 38 (prd-cpf-pcnte): CPF do paciente, 11 dígitos zero-padded — é aqui, não na seq 10, que o CPF do paciente entra no registro
- Seq "38" duplicado no layout oficial (prd_situacao_rua): usa `paciente.situacaoRua` quando a planilha trouxe a coluna "Situação de Rua"; sem essa informação, mantém o padrão "N" (comportamento anterior à existência da coluna)
- Seq 39 (prd_sem_cpf, novo no layout 2026): usa `atendimento.pacienteSemCpf` quando a planilha trouxe a coluna "Paciente sem CPF"; sem essa informação, deriva automaticamente da presença do CPF do paciente (CPF preenchido → "N", CPF vazio → "S") — hoje sempre resulta em "N" na prática, já que CPF vazio já bloqueia a importação (`CPF_AUSENTE`)
- **Pré-validação obrigatória**: `GeradorBPAiService.validarCnsProfissional()` bloqueia a geração se qualquer atendimento estiver sem CNS do profissional, exibindo relatório com médico/paciente/data de cada ocorrência

### Layout da UI
Layout em `BorderPane` (sidebar de navegação + barra fixa de geração), não mais em linhas horizontais empilhadas. Reescrito para separar fisicamente "visualizar tabela" de "gerar BPA-I", que antes ficavam misturados na mesma barra e causavam confusão (toggle de exibição colado ao botão de geração, dois botões "Gerar BPA-I"/"Gerar BPA-I Parcial" parecidos, busca livre e combo de especialidade/médico mutuamente exclusivos sem indicação visual).

1. **topBar** (`MainController`): `[Analisar Planilha]` `[Ver Log Importação]` `[Configurações]` — não exibe mais competência (movida para a barra inferior, fonte única).
2. **Topo do conteúdo** (`RelatorioController.criarBarraExibicao()`): `Exibir tabela:` + toggle `Competência | Período | Completo` + `[Selecionar Período]` (só no modo Período) + legenda do modo ativo.
   - Controla **apenas o que a tabela mostra**; nunca afeta a competência de geração.
3. **Sidebar esquerda** (`RelatorioController.criarSidebarNavegacao()`): campo de busca + `TreeView` de especialidades → médicos, substituindo busca livre + combos de especialidade/médico.
   - Busca filtra a árvore em tempo real (nome do médico ou da especialidade); selecionar um nó de especialidade filtra por ela, selecionar um médico filtra por médico+especialidade.
   - Campos `especialidadeSelecionada`/`medicoSelecionado` (não mais `ComboBox`) guardam o estado da seleção.
4. **Área central**: breadcrumb do contexto atual (`crumbLabel`) + **barra de edição** (`CNS: [___]` `[Atualizar CNS]` `[Definir Folha]`), visível apenas quando um médico está selecionado na árvore, + tabela + rodapé `Total: N`.
5. **Barra fixa inferior** (`RelatorioController.criarBarraGeracao()`, fundo escuro): badge clicável `📅 Competência: MM/YYYY` (abre o seletor de mês/ano) + chip `⚠ Pendências` contextual + botão único `[Gerar BPA-I — ...]` cujo texto/ação se adapta à seleção da árvore:
   - Médico selecionado → `Gerar BPA-I — <médico> (<especialidade>)`, gera parcial (`GeradorBPAiService.gerarArquivoComFileChooser`)
   - Nada selecionado → `Gerar BPA-I — Completo (todos os médicos)`, gera completo (`GeradorBPAiService.gerarArquivoCompletoComFileChooser`)
   - O chip de aviso mostra pendências de CNS/folha do contexto atual (parcial ou completo) e abre o respectivo relatório ao clicar

Nota: a atualização de CNS por busca livre em nome parcial (aplicar a todos os registros cujo médico contém um termo, independente de especialidade) existia na versão anterior e foi removida nesta reestruturação — a árvore sempre identifica um médico exato antes de habilitar a edição.

## Team Profile: Standard

| Role | Model | Responsabilidade |
|------|-------|-----------------|
| **Orchestrator** | Sonnet | Gerencia issues, delega tarefas, revisa output, controla git workflow |
| **Implementer** | Sonnet | Escreve testes e implementação (TDD em agente único) |
| **Explorer** | Haiku | Buscas rápidas no codebase, lookups, exploração de arquivos |

### Regras do Time
- Orchestrator é o ponto de entrada — recebe a tarefa e decide quem executa
- Implementer segue TDD: escreve teste falhando primeiro, depois implementa
- Explorer é usado para buscas rápidas antes de implementar (entender contexto)
- Tarefas independentes devem rodar em paralelo quando possível

## Regras de Negócio Principais
- Layout BPA-I: campos posicionais com tamanho fixo (351 chars de conteúdo por registro + CRLF = 353)
- Header: 132 chars
- Campos NUM opcionais: brancos quando vazio, zeros à esquerda quando preenchido
- Campos ALFA: espaços à direita até completar tamanho
- Codificação do arquivo de saída: ISO-8859-1
- Documento de referência do layout: `data/Layout_Exportacao_BPA_2026.pdf` (substitui `Layout interface texto do BPA.pdf`, que não existe mais no repositório)

## Convenções
- Conventional commits em português
- Feature branches off main
- Commit e push direto para main autorizado pelo usuário
