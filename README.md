# PonteBPA

> Nome exibido do programa. No código, o projeto continua como **fillBPAi** (repositório, `artifactId` do Maven, pacote `br.gov.ses.fillbpai`, arquivo do banco) — a troca de nome é só visual.

Aplicação desktop JavaFX utilizada pela **Superintendência de Saúde Digital (SES-MS)** para importar dados de atendimentos de saúde a partir de planilhas Excel e gerar arquivos magnéticos **BPA-I** (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde para envio ao SIA/SUS.

O arquivo segue o layout oficial de 2026 (`data/Layout_Exportacao_BPA_2026.pdf`).

---

## Funcionalidades

### Análise e importação da planilha
- **Analisar Planilha** — valida a planilha inteira antes de importar, com as mesmas regras da importação. O resultado aparece agrupado por tipo (chips de filtro e um painel por tipo, ocorrências iguais juntas), com o nome e a pasta da planilha no topo.
  - **Erros** bloqueiam a importação e precisam ser corrigidos na planilha.
  - **Avisos** não exigem correção, mas pedem **"Ciente"** em cada grupo antes de liberar o botão **Importar Planilha** — o "Ciente" só fica disponível depois que o grupo é aberto. A ciência fica registrada no fim do log de importação.
  - Blocos de **colunas opcionais não encontradas** e de **formato de planilha legado** aparecem à parte, também com "Ciente".
  - O relatório pode ser salvo em TXT pelo botão "Salvar Log TXT".
- **Todas as linhas são importadas ou nenhuma** — qualquer linha com erro cancela a planilha inteira; no fim, o programa confere se cada linha com dados virou um atendimento.
- **Linhas repetidas** — mesmo paciente, médico, data, procedimento e horário é erro; horário diferente é aviso de suspeita, e as linhas são importadas separadas.
- **Reimportar a mesma planilha atualiza** os atendimentos em vez de duplicar.
- **Histórico de logs de importação** (botão **Ver Log Importação**) — um arquivo por importação, com planilha, pasta, data/hora, conferência de linhas, avisos e ciência; a janela lista as importações da mais recente para a mais antiga, com busca e "Copiar log".
- **Sobre** (canto direito da barra superior) — versão e data da compilação, dados da unidade e do layout usados no BPA-I, ambiente (Java, sistema, banco) e as pastas de logs e de cadastros com "Abrir pasta"; "Copiar informações" gera um resumo para pedidos de ajuda.

### Configurações
- **Colunas da Planilha** — nomes de cabeçalho aceitos para cada campo (aliases), para planilhas com nomes de coluna diferentes. Acessível também a partir do erro de estrutura.
- **CNS de Médicos** — cadastro de profissionais (nome + CNS) e de apelidos/grafias do mesmo médico.

### Tabela e navegação
- **Árvore de especialidades e médicos** com busca; selecionar um médico filtra a tabela e mostra a barra de edição do CNS.
- **Modo de exibição** — Competência, Período ou Completo; afeta só a tabela, nunca a competência da geração.
- **Colunas Folha, Linha e Remessa** — mostram a folha e a linha com que cada atendimento foi para a última remessa BPA-I, e quando ("—" / "não enviada" antes do envio).

### Geração do BPA-I
- **Competência** no badge da barra inferior; **um botão de geração** que se adapta à seleção: completo (nada selecionado) ou parcial (médico selecionado).
- **Folha e linha** calculadas na geração, por competência: especialidade → médico → data → hora; folha nova por especialidade + médico e a cada 99 linhas. Parcial e completa usam a mesma numeração. Depois de salvo o arquivo, folha, linha e data/hora da remessa ficam gravadas nos atendimentos.
- **Pré-validação** — a geração bloqueia se algum atendimento estiver sem CNS do profissional ou sem código IBGE, com relatório de pendências.

---

## Stack

| Tecnologia | Versão | Finalidade |
|------------|--------|------------|
| Java | 21 | Linguagem principal |
| JavaFX | 21.0.x | Interface desktop |
| Hibernate / JPA | 6.4.4.Final | Persistência |
| H2 Database | 2.2.224 | Banco embarcado (`hbm2ddl.auto=update`) |
| Apache POI | 5.5.x | Leitura de `.xlsx` |
| SLF4J + Logback | — | Logging |
| JUnit 5 + AssertJ + Mockito | — | Testes |
| Maven (wrapper) | 3.9+ | Build |

---

## Estrutura do projeto

```
fillBPAi/
├── mvnw / mvnw.cmd                     # Maven Wrapper
├── data/                               # Layouts oficiais do BPA (PDF)
├── docs/                               # Runbooks de decisões e revisões (HTML)
├── database/                           # Banco H2 (criado em execução)
├── logs/                               # Logs de importação, um por importação (criados em execução)
└── src/
    ├── main/java/br/gov/ses/fillbpai/
    │   ├── app/MainApp.java                         # Ponto de entrada JavaFX
    │   ├── config/DatabaseInitializer.java          # H2 + Hibernate
    │   ├── config/DadosUnidade.java                 # CNES, órgão, sigla, CNPJ e versão do header do BPA-I
    │   ├── controller/MainController.java           # Barra superior, análise, importação, logs
    │   ├── dto/                                     # LinhaImportacaoDTO (planilha), AtendimentoBPAiDTO (tabela)
    │   ├── model/                                   # AtendimentoBPAi, Paciente, Endereco, Medico, Estabelecimento
    │   ├── repository/                              # Consultas JPA
    │   ├── service/
    │   │   ├── ValidacaoPlanilhaService.java        # Análise pré-importação (erros e avisos)
    │   │   ├── AgrupamentoValidacao.java            # Títulos, explicações e agrupamento dos avisos
    │   │   ├── ErroValidacao.java                   # Tipos de erro/aviso
    │   │   ├── PlanilhaColumnMapper.java            # Cabeçalho → campos, pelos aliases
    │   │   ├── ExcelImportService.java              # Leitura das células → DTO
    │   │   ├── AtendimentoProcessor.java            # Validação e normalização por linha
    │   │   ├── DetectorDuplicidade.java             # Linhas do mesmo atendimento
    │   │   ├── AtendimentoImportacaoService.java    # Importação (tudo ou nada)
    │   │   ├── ImportacaoResultado.java             # Resumo da importação
    │   │   ├── HistoricoLogsService.java            # Histórico dos logs de importação
    │   │   ├── SobreService.java                    # Informações da janela Sobre (versão do pom)
    │   │   └── GeradorBPAiService.java              # Arquivo BPA-I, folhas e remessa
    │   ├── ui/                                      # RelatorioController (tela), ConfiguracoesDialog, SobreDialog, FileChooserService
    │   └── util/                                    # Regras por campo: CPF, CNS, CEP, IBGE, CID, CBO, sexo, raça,
    │                                                #   etnia, nome, datas, hora, telefone, logradouro, número,
    │                                                #   bairro/complemento, S/N, vigência do layout…
    ├── main/resources/dados/
    │   ├── colunas_aliases.csv          # Nomes de coluna aceitos (padrão)
    │   ├── medicos_cns.csv              # Cadastro de CNS dos médicos (nome;cns)
    │   ├── municipios_ibge.csv          # Municípios de MS → código IBGE
    │   ├── municipios_brasil.csv        # Todos os municípios (nome/UF pelo código IBGE)
    │   ├── etnias_indigenas.csv         # Tabela oficial de etnias (não alterar)
    │   └── tipos_logradouro.csv         # Tabela DNE de tipos de logradouro (linhas com # = desativadas)
    └── test/                            # Testes unitários e de integração (H2 em memória)
```

---

## Fluxo

```
Planilha .xlsx
   │
   ▼
[Analisar Planilha]
   ├─ erro de estrutura ─► janela de estrutura (colunas ausentes/sem cabeçalho) ─► corrigir ou [Configurações]
   ├─ erros ───────────► janela de erros (sem importação) ─► corrigir a planilha
   └─ sem erros ───────► avisos + "Ciente" em cada grupo ─► [Importar Planilha]
                                                              │
   AtendimentoImportacaoService (tudo ou nada) ◄──────────────┘
     ├─ confere IBGE de todas as linhas antes de gravar
     ├─ AtendimentoProcessor por linha (mesmas regras da análise)
     ├─ DetectorDuplicidade
     ├─ grava pacientes, médicos, estabelecimentos e atendimentos
     └─ conferência: linhas da planilha = atendimentos gravados
   │
   ▼
Log de importação (logs/log_importacao_<planilha>_<data-hora>.txt)
   │
   ▼
📅 Competência + [Gerar BPA-I — completo | médico]
   ├─ bloqueia sem CNS do profissional ou sem IBGE
   ├─ numera folhas e linhas da competência
   ├─ grava o arquivo (.txt, ISO-8859-1, CRLF)
   └─ registra folha, linha e remessa nos atendimentos
```

---

## Validações

A análise e a importação aplicam as mesmas regras. A lista completa, com cada tipo de erro e aviso, está no `CLAUDE.md`; resumo:

| Grupo | Erros (bloqueiam) | Avisos (pedem "Ciente") |
|-------|-------------------|--------------------------|
| Estrutura | coluna obrigatória ausente ou ambígua; especialidade/médico sem separador no formato legado | coluna opcional ausente; coluna com dados sem cabeçalho; formato legado |
| Linha | linha ilegível; linha repetida | suspeita de duplicidade (horário diferente) |
| Paciente | nome, nascimento, sexo e raça ausentes ou inválidos; CPF ausente, com tamanho errado, falso ou com dígito verificador errado; CPF junto com "Paciente sem CPF" = Sim; paciente sem CPF sem CNS válido; indígena sem etnia ou com etnia não reconhecida | CNS sem 15 dígitos ou com dígito verificador errado; nome com mais de 30 caracteres; nome a conferir; mesmo CPF (ou CNS) com nomes diferentes |
| Datas | data do atendimento ausente/inválida; nascimento sem ano ou incoerente | data do atendimento sem ano; data futura ou com mais de 2 anos |
| Endereço | CEP ausente, com tamanho errado ou falso; município ausente ou sem IBGE | município encontrado pelo CEP; CEP de fora de MS; número fora do padrão; complemento cortado; bairro igual ao município; logradouro não reconhecido; paciente em situação de rua sem CEP |
| Atendimento | tipo de serviço ausente/inválido; CBO ausente/inválido; especialidade ausente; CPF do médico ausente/inválido; CID inválido | CID vazio ou "-"; hora não reconhecida; telefone sem DDD ou inválido; estabelecimento sem código/sem nome/ausente; médico sem CNS cadastrado; colunas S/N vazias ou não reconhecidas; campo do layout antes da vigência |

---

## Arquivo BPA-I

- **Header** — 132 caracteres + CRLF (competência, total de linhas, total de folhas, campo de controle, órgão).
- **Registro** — 351 caracteres + CRLF = 353, 40 campos posicionais.
- **Codificação** — ISO-8859-1; campos NUM vazios em branco, preenchidos com zeros à esquerda; campos ALFA com espaços à direita e cortados no tamanho do campo.

| Campo | Regra |
|-------|-------|
| `prd-cmp` (seq 3) | Mês da data do atendimento |
| `prd-flh` / `prd-seq` (seq 7 e 8) | Folha 001–999 e linha 01–99, numeradas na geração por competência |
| `prd-cnspac` (seq 10) | CNS do paciente só para paciente sem CPF; com CPF, brancos (um documento só) |
| `prd-sexo` (seq 11) | F, M ou I |
| `prd-ibge` (seq 12) | Código IBGE de 7 dígitos truncado para 6 |
| `prd-cid` (seq 13) | Código CID-10 extraído da célula, sem ponto |
| `prd-idade` (seq 14) | Idade na data do atendimento |
| `prd-raca` / `prd-etnia` (seq 21 e 22) | Raça obrigatória; etnia só para raça Indígena |
| `prd_lograd_pcnte` (seq 30) | Código da tabela DNE; não reconhecido → 081 (Rua) |
| `prd_num_pcnte` (seq 33) | Vazio, "sem número" ou "-" → SN |
| `prd_cpf_pcnte` (seq 38) | CPF com 11 dígitos; sem CPF → brancos |
| `prd_situacao_rua` (seq "38") | Do atendimento; sem a coluna → "N"; célula vazia → branco; antes de 12/2024 → branco |
| `prd_sem_cpf` (seq 39) | Da planilha; vazio com CPF → "N"; sem a coluna → derivado do CPF; antes de 07/2026 → branco |

---

## Colunas da planilha

Obrigatórias: tipo de serviço, data de agendamento (data do atendimento), hora, estabelecimento, especialidade, médico, CPF do médico, CBO, município, CPF do paciente, paciente, CNS do paciente, raça, etnia, data de nascimento, CID, telefone, endereço, CEP, número, bairro, complemento, sexo. A ordem das colunas não importa — o cabeçalho é reconhecido pelos nomes cadastrados em Configurações.

Opcionais (ausência gera só um aviso): **Cód. Logradouro**, **Tipo de zona**, **Situação de Rua**, **Paciente sem CPF**.

Planilhas antigas com especialidade e médico na mesma célula ("ESPECIALIDADE - NOME") são reconhecidas e separadas automaticamente.

---

## Como executar (desenvolvimento)

Pré-requisito: **JDK 21**.

```bash
mvnw.cmd compile        # compilar
mvnw.cmd test           # testes
mvnw.cmd javafx:run     # executar
```

O banco H2 fica em `database/` e os logs de importação em `logs/`, criados na primeira execução. Ainda não há instalador: para levar o programa a outra unidade é preciso, antes, tornar configuráveis os dados da unidade (hoje o CNES, o nome, a sigla e o CNPJ do núcleo estão fixos em `config/DadosUnidade.java`), mover banco, logs e cadastros para a pasta do usuário e gerar um pacote com o Java embutido (`jpackage`).

---

## Documentação

- `CLAUDE.md` — referência completa das regras, tipos de erro/aviso e decisões de implementação.
- `docs/DOCUMENTACAO_TECNICA.md` — referência de todas as classes e métodos, pacote por pacote, e das pastas e arquivos criados em execução. Gerada a partir dos javadocs: `node docs/gerar-documentacao.js`.
- `docs/DOCUMENTACAO_TESTES.md` — todos os testes automatizados, o que cada um confere e o resultado da última execução. Gerada com `node docs/gerar-documentacao-testes.js` depois de rodar os testes.
- `docs/runbook-verificacao-colunas.html` — revisão coluna por coluna, decisões e pendências (ponto de retomada do trabalho).
- `docs/runbook-*.html` — runbooks de temas específicos (layout 2026, médico sem CNS, etnia, logradouro…).

---

## Licença

Uso interno — Superintendência de Saúde Digital / Secretaria Estadual de Saúde de Mato Grosso do Sul.
