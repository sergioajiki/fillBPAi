# fillBPAi — Projeto CLAUDE.md

## Sobre o Projeto
Aplicação desktop JavaFX que importa dados de atendimentos de saúde de planilhas Excel e gera arquivos magnéticos BPA-I (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde.

O Núcleo de Telessaúde de MS utiliza esta aplicação para importar dados de atendimentos no sistema SIA/SUS. O formato BPA-I tem layout posicional rígido (340 chars por registro).

## Stack
- Java 21, JavaFX, JPA/Hibernate (sem Spring Boot)
- H2 Database (embarcado, `hibernate.hbm2ddl.auto=update` — persiste dados entre reinicializações)
- Apache POI para leitura de `.xlsx`
- Maven para build

## Build & Run
- Maven não está no PATH do sistema. Usar caminho completo:
  `C:\Users\sergi\.m2\wrapper\dists\apache-maven-3.9.11\d6d3cbd4012d4c1d840e93277aca316c\bin\mvn.cmd`
- `mvn test` roda automaticamente via PostToolUse hook (Edit/Write) — NÃO rodar manualmente em agents/subagents

## GitHub
- Repositório: https://github.com/sergioajiki/fillBPAi.git
- Branch principal: main
- Usuário autoriza commit e push direto para main neste projeto (sempre mostrar o que vai ser commitado antes de executar)

## Estrutura do Projeto
Arquitetura em camadas: `controller → service → repository → model`, com `util`, `dto` e `ui`.

### Modelo de Dados (normalizado)
- `Paciente` — chave natural: CPF (único). @OneToOne com Endereco.
- `Endereco` — 1:1 com Paciente. Inclui campo `codigoIbge` (7 dígitos).
- `Medico` — chave natural: CPF (único). Campos: id, cpf, nome.
- `Estabelecimento` — chave natural: codigo (único). Campos: id, codigo, nome.
- `AtendimentoBPAi` — @ManyToOne para Paciente, Medico, Estabelecimento. Campos próprios: tipoServico, sigtap, dataAgendamento, horaAtendimento, especialidadeMedico, cboMedico, cidConsulta, cnsProfissional, cnesNts, codIne, folha.

### Validação Pré-Importação
Antes de importar, a planilha é validada por `ValidacaoPlanilhaService`. Se houver erros bloqueantes, a importação é **impedida** e um relatório de erros é exibido com opção de download em TXT. Avisos não-bloqueantes são exibidos em diálogo separado mas não impedem a importação.

`ErroValidacao` tem campo `severidade` (enum `ERRO`/`AVISO`) e método `isBloqueante()`.

Tipos de ERRO (bloqueantes):
- **CNS_INVALIDO** — CNS do paciente ausente ou com menos de 15 dígitos após normalização
- **CEP_AUSENTE** — CEP do endereço não informado
- **CPF_AUSENTE** — CPF do paciente não informado

Tipos de AVISO (não bloqueantes):
- **CNS_INCOMUM** — CNS do paciente com mais de 15 dígitos (formato incomum)

O botão **"Analisar Planilha"** (topBar, à esquerda de "Importar Planilha") permite validar sem importar.
Log de erros salvo automaticamente em `database/log_erros_validacao.txt`.

### Fluxo de Importação
1. `ValidacaoPlanilhaService` — valida regras bloqueantes (CNS, CEP, CPF). Se erros → bloqueia importação
2. `ExcelImportService` — lê Excel, retorna `LinhaImportacaoDTO`
3. `AtendimentoProcessor` — valida/normaliza (CNS, datas, etc.), retorna lista de avisos
4. `AtendimentoImportacaoService` — primeira passada coleta nomes de médicos, pré-carrega CNS do DATASUS (single-pass), segunda passada processa normalmente: findOrCreate para entidades, resolve IBGE via `IbgeUtils`, resolve CNS profissional via `CnsProfissionalUtils` (por nome), persiste atendimento

### Utilitários
- `IbgeUtils` — resolve código IBGE em cascata: 1) CSV por nome do município, 2) cache pré-carregado do banco (CEP → codigoIbge de `Endereco`), 3) API ViaCEP como último recurso. `preCarregarCacheDb()` chamado em `AtendimentoImportacaoService` antes do loop de importação.
- `CnsProfissionalUtils` — resolve CNS do profissional por **nome** (normalizado: uppercase, sem acentos). Fontes: cache local (`dados/medicos_cns.csv` formato `nome;cns`) + arquivo DATASUS (`tbDadosProfissionalSus*.csv`, 7M+ registros, streaming). Auto-detecta DATASUS em Documents. Divergências (múltiplos CNS, não encontrado) vão para log de avisos.
- `CnsUtils` — processa/valida CNS de pacientes (aceita CNS incomum >15 dígitos com aviso `CNS_INCOMUM`)
- `CepUtils` — normalização de CEP

### Geração BPA-I
- Geração individual (filtrada por especialidade/médico)
- Geração completa (todos os médicos, folha auto-atribuída: especialidades em ordem alfabética → médicos em ordem alfabética → folha sequencial por competência)
- Seq 10 (prd-cnspac): usa CPF do paciente zero-padded 15 chars (não CNS)
- Seq 12 (prd-ibge): código IBGE real do endereço, truncado para 6 dígitos
- **Pré-validação obrigatória**: `GeradorBPAiService.validarCnsProfissional()` bloqueia a geração se qualquer atendimento estiver sem CNS do profissional, exibindo relatório com médico/paciente/data de cada ocorrência

### Layout da UI
A tela principal tem 4 linhas:
1. **topBar** (`MainController`): `[Analisar Planilha]` `[Importar Planilha]` `[Pré-Cadastro CNS]` ... `Competência`
2. **Barra de ações** (`RelatorioController.criarBarraAcoes()`): `[Gerar BPA-I Completo]` `[⚠]` `[Gerar BPA-I]` `[Ver Log Importação]`
   - Botão `⚠` (amarelo) aparece quando há atendimentos sem CNS do profissional; clicando exibe log de pendências. Atualizado automaticamente em cada `carregarDoBanco()`.
3. **Barra de filtros** (`RelatorioController.criarBarraFiltros()`): `[Buscar médico: ___ ]` `[Buscar]` `Especialidade [▼]` `Médico [▼]` `CNS [___]` `[Folha]` `[OK]` `[Limpar]`
   - Busca por nome do médico: correspondência parcial, case-insensitive, combinável com filtros de combo
4. **Tabela** + rodapé `Total: N`

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
- Layout BPA-I: campos posicionais com tamanho fixo (340 chars por registro + CRLF)
- Header: 132 chars
- Campos NUM opcionais: brancos quando vazio, zeros à esquerda quando preenchido
- Campos ALFA: espaços à direita até completar tamanho
- Codificação do arquivo de saída: ISO-8859-1
- Documento de referência do layout: `Layout interface texto do BPA.pdf`

## Convenções
- Conventional commits em português
- Feature branches off main
- Commit e push direto para main autorizado pelo usuário
