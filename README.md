# fillBPAi

Aplicação desktop JavaFX utilizada pelo **Núcleo de Telessaúde de MS** para importar dados de atendimentos de saúde a partir de planilhas Excel e gerar arquivos magnéticos **BPA-I** (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde para envio ao sistema SIA/SUS.

---

## Funcionalidades

### Importação
- **Análise prévia da planilha** (`[Analisar Planilha]`) — valida erros bloqueantes (CNS, CEP, CPF, estrutura de colunas) e avisos; ao não encontrar erros bloqueantes, o próprio diálogo de resultado oferece o botão **"Importar Planilha"** para concluir a importação, com leitura, validação e normalização de todos os campos
- **Configuração de mapeamento de colunas** (`[Configurações]`) — permite cadastrar/remover aliases de nomes de cabeçalho aceitos para cada campo canônico da planilha, para acomodar variações de nomenclatura entre planilhas; acessível também diretamente a partir do erro de estrutura de planilha
- **Cadastro de CNS de médicos** (`[Configurações]` → aba "CNS de Médicos") — adicionar, editar e remover profissionais e seus apelidos/variações de nome reconhecidas na resolução automática de CNS
- **Colunas opcionais com aviso, não erro** — `Código Logradouro`, `Situação de Rua` e `Paciente sem CPF` seguem a importação normalmente quando ausentes do cabeçalho, com aviso explicando o fallback usado em cada caso
- **Detecção de tipo de logradouro** — identifica automaticamente o prefixo do endereço (Rua → 081, Avenida/Av./Av → 008, Travessa/Trav./TV → 100) e preenche o código do logradouro
- **Detecção de planilha em formato legado** — reconhece coluna combinada "Especialidade/Médico" e separa os dois valores automaticamente, com aviso dedicado
- **Resolução de etnia indígena** — código oficial de 4 caracteres via `dados/etnias_indigenas.csv`, considerado apenas quando a raça do paciente é Indígena
- **Resolução de CNS do profissional** — busca por nome no arquivo `dados/medicos_cns.csv` (escopo estadual)
- **Resolução de código IBGE** — via CSV por nome do município, cache do banco ou API ViaCEP
- **Log de importação persistido** — salvo em `database/log_importacao.txt` para consulta a qualquer momento

### Visualização e Edição
- **Tabela com 29 colunas** — exibição completa dos registros importados, incluindo Situação de Rua
- **Navegação lateral por árvore** — especialidades e médicos organizados em árvore, com busca embutida que filtra por nome de médico ou especialidade em tempo real
- **Modo de exibição da tabela** — alterna entre "Competência" (mês selecionado), "Período" (intervalo de competências) e "Completo" (todos os registros), sem afetar a competência usada na geração do BPA-I
- **Edição em lote** — atualiza CNS do profissional e define folha para todos os registros do médico/especialidade selecionados na árvore

### Geração BPA-I
- **Badge de competência único** — clicável, na barra fixa inferior; abre o seletor de mês/ano; auto-detectado na primeira carga de dados
- **Botão de geração único e adaptativo** — texto e ação mudam conforme a seleção da árvore: `Gerar BPA-I — Completo (todos os médicos)` quando nada está selecionado, ou `Gerar BPA-I — <médico> (<especialidade>)` quando um médico está selecionado
- **Geração completa** — todos os médicos do mês de competência selecionado, folhas atribuídas automaticamente (especialidade alfabética → médico alfabético → folha sequencial)
- **Geração parcial** — filtrada pelo médico selecionado na árvore
- **Pré-validação bloqueante** — bloqueia a geração (completa ou parcial) se qualquer atendimento do escopo estiver sem CNS do profissional, com chip de aviso contextual (`⚠ Pendências`) que abre o relatório de pendências

---

## Stack

| Tecnologia | Versão | Finalidade |
|------------|--------|------------|
| Java | 21 | Linguagem principal |
| JavaFX | 21.0.x | Interface desktop |
| Hibernate / JPA | 6.4.4.Final | Persistência de dados |
| H2 Database | 2.2.224 | Banco embarcado (persiste entre reinicializações) |
| Apache POI | 5.5.x | Leitura de arquivos `.xlsx` |
| Maven | 3.9+ | Build e gerenciamento de dependências |
| SLF4J + Logback | — | Logging |

---

## Estrutura do Projeto

```
fillBPAi/
├── mvnw / mvnw.cmd                        # Maven Wrapper
├── database/                              # Banco H2 e logs persistidos (runtime)
│   ├── fillbpai.mv.db
│   ├── log_importacao.txt
│   └── log_erros_validacao.txt
└── src/main/
    ├── java/br/gov/ses/fillbpai/
    │   ├── app/
    │   │   └── MainApp.java                        # Entry point JavaFX
    │   ├── config/
    │   │   └── DatabaseInitializer.java            # Setup H2 + Hibernate
    │   ├── controller/
    │   │   └── MainController.java                 # Barra superior + fluxo de importação
    │   ├── dto/
    │   │   ├── AtendimentoBPAiDTO.java             # DTO para apresentação na tabela
    │   │   └── LinhaImportacaoDTO.java             # DTO de leitura do Excel
    │   ├── model/
    │   │   ├── AtendimentoBPAi.java
    │   │   ├── Endereco.java
    │   │   ├── Estabelecimento.java
    │   │   ├── Medico.java
    │   │   └── Paciente.java
    │   ├── repository/
    │   │   ├── AtendimentoBPAiRepository.java
    │   │   ├── EstabelecimentoRepository.java
    │   │   ├── MedicoRepository.java
    │   │   └── PacienteRepository.java
    │   ├── service/
    │   │   ├── AtendimentoImportacaoService.java   # Orquestração da importação
    │   │   ├── AtendimentoProcessor.java           # Validação e normalização por linha
    │   │   ├── ErroValidacao.java                  # Modelo de erro/aviso de validação
    │   │   ├── ExcelImportService.java             # Leitura do Excel → DTO
    │   │   ├── GeradorBPAiService.java             # Geração do arquivo BPA-I
    │   │   ├── ImportacaoResultado.java            # Resultado agregado da importação
    │   │   ├── PlanilhaColumnMapper.java           # Mapeia cabeçalhos → campos canônicos via aliases
    │   │   └── ValidacaoPlanilhaService.java       # Validação pré-importação
    │   ├── ui/
    │   │   ├── ConfiguracoesDialog.java            # Aliases de colunas + cadastro de CNS de médicos
    │   │   ├── FileChooserService.java             # Diálogo de seleção de arquivo
    │   │   └── RelatorioController.java            # Sidebar de navegação, tabela, barra de geração
    │   └── util/
    │       ├── CepUtils.java                       # Normalização de CEP
    │       ├── CnsProfissionalUtils.java           # Resolução de CNS por nome
    │       ├── CnsUtils.java                       # Validação/normalização CNS paciente
    │       ├── ColunaAliasUtils.java                # Gerencia aliases de nomes de coluna de cabeçalho
    │       ├── CpfUtils.java                        # Normalização/validação de CPF (11 dígitos)
    │       ├── DateUtils.java                       # Parse de data em múltiplos formatos aceitos
    │       ├── EtniaUtils.java                      # Resolução de código de etnia indígena
    │       ├── IbgeUtils.java                       # Resolução de código IBGE
    │       ├── LogradouroUtils.java                 # Detecção de tipo de logradouro
    │       ├── RacaUtils.java                       # Resolução de código de raça/cor
    │       ├── SimNaoUtils.java                     # Normalização S/N (situação de rua, sem CPF)
    │       ├── StringUtils.java                     # Truncamento e split de "código - nome"
    │       ├── TextoUtils.java                      # Normalização de texto para comparação
    │       └── TimeUtils.java                       # Parse de hora em múltiplos formatos aceitos
    └── resources/
        └── dados/
            ├── colunas_aliases.csv                 # Aliases padrão de nomes de coluna de cabeçalho
            ├── etnias_indigenas.csv                # Tabela oficial de códigos de etnia indígena
            ├── medicos_cns.csv                     # Cache local CNS dos profissionais
            └── municipios_ibge.csv                 # Tabela de municípios → código IBGE
```

---

## Layout da Interface

Layout em `BorderPane` — sidebar de navegação à esquerda e barra de geração fixa embaixo, separando fisicamente "visualizar tabela" de "gerar BPA-I":

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [Analisar Planilha] [Ver Log Importação] [Configurações]                 │  ← topBar (MainController)
├──────────────────────────────────────────────────────────────────────────┤
│ Exibir tabela: [Competência|Período|Completo]     Competência selecionada│  ← criarBarraExibicao()
├───────────────────────────┬──────────────────────────────────────────────┤
│ ESPECIALIDADES E MÉDICOS  │ Cardiologia › Dr. João Silva — 14 atendimento│
│ [Buscar...      ] [Limpar]│ (CNS/Folha aparece só com médico selecionado)│
│ ▾ Cardiologia (12)        │ CNS: [_______] [Atualizar CNS] [Definir Folha│
│   ● Dr. João Silva        │ ┌──────────────────────────────────────────┐│
│   ○ Dra. Ana Prado        │ │           Tabela de Atendimentos          ││
│ ▸ Dermatologia (5)        │ └──────────────────────────────────────────┘│
│                           │                Total: N                     │
├───────────────────────────┴──────────────────────────────────────────────┤
│ 📅 Competência: MM/AAAA    ⚠ Pendências    [Gerar BPA-I — <contexto>]    │  ← criarBarraGeracao()
└──────────────────────────────────────────────────────────────────────────┘
```

- O botão de geração é **único e adaptativo**: mostra `Completo (todos os médicos)` quando nada está selecionado na árvore, ou `<médico> (<especialidade>)` quando um médico está selecionado — nunca há dois botões de geração ao mesmo tempo.
- O toggle "Exibir tabela" controla **apenas o que a tabela mostra**; nunca afeta a competência usada na geração.
- O botão **"Importar Planilha"** não fica fixo na barra superior — ele aparece dentro do diálogo de resultado exibido após `[Analisar Planilha]`, quando não há erros bloqueantes.

---

## Fluxo da Aplicação

```
Planilha .xlsx
      │
      ▼
[Analisar Planilha] ──► sem erros ──► botão [Importar Planilha] no diálogo
      │                só avisos ──► botão [Importar Planilha] no diálogo
      │                com erros ──► exibe log (sem importação)
      │        erro de estrutura ──► oferece abrir [Configurações]
      │
[Importar Planilha]
      │
      ├─ ValidacaoPlanilhaService   (bloqueia se CNS/CEP/CPF inválidos)
      ├─ ExcelImportService         (lê linha → LinhaImportacaoDTO)
      ├─ AtendimentoProcessor       (valida e normaliza campos)
      ├─ LogradouroUtils            (detecta tipo de logradouro)
      ├─ IbgeUtils                  (resolve código IBGE)
      ├─ RacaUtils                  (bloqueia se raça ausente/não reconhecida)
      ├─ EtniaUtils                 (resolve código de etnia indígena)
      ├─ SimNaoUtils                (normaliza situação de rua / sem CPF)
      ├─ CnsProfissionalUtils       (resolve CNS pelo nome via CSV)
      └─ AtendimentoImportacaoService (persiste no banco H2)
              │
              ▼
        Banco H2 (database/)
              │
              ▼
📅 Competência (badge da barra de geração) ──► seletor de mês/ano
              │
              ▼
   [Gerar BPA-I — <contexto>]  (botão único, adaptativo)
              │
    ┌─────────┴──────────┐
    ▼                    ▼
Médico selecionado   Nada selecionado
na árvore             na árvore
(gera parcial)        (gera completo)
    │                    │
    └─────────┬──────────┘
              ▼
    Arquivo .txt BPA-I
    (ISO-8859-1, 353 chars/linha)
```

---

## Validações Pré-Importação

| Tipo | Código | Severidade | Efeito |
|------|--------|-----------|--------|
| CNS ausente ou < 15 dígitos | `CNS_INVALIDO` | AVISO | Importa com aviso no log (não bloqueia) |
| CNS com > 15 dígitos | `CNS_INCOMUM` | AVISO | Importa com aviso no log |
| CEP não informado | `CEP_AUSENTE` | ERRO | Bloqueia importação |
| CEP com tamanho inválido (≠ 8 dígitos) | `CEP_INVALIDO` | ERRO | Bloqueia importação |
| CPF não informado | `CPF_AUSENTE` | ERRO | Bloqueia importação |
| CPF com tamanho inválido (≠ 11 dígitos) | `CPF_INVALIDO` | ERRO | Bloqueia importação |
| Raça do paciente não informada | `RACA_AUSENTE` | ERRO | Bloqueia importação (campo obrigatório no layout) |
| Raça do paciente não reconhecida (grafia incorreta) | `RACA_INVALIDA` | ERRO | Bloqueia importação; mensagem sugere conferir a grafia |
| Raça do paciente = Indígena | `RACA_INDIGENA` | AVISO | Importa com aviso no log |
| Etnia preenchida mas não encontrada na tabela oficial (só considerada com raça Indígena) | `ETNIA_NAO_ENCONTRADA` | AVISO | Importa com aviso no log |
| Coluna obrigatória ausente/ambígua no cabeçalho | `ESTRUTURA_INVALIDA` | ERRO | Bloqueia importação (oferece abrir Configurações) |
| Coluna "Especialidade/Médico" combinada (formato legado) | `FORMATO_LEGADO_ESPECIALIDADE_MEDICO` | AVISO | Separa os dois campos automaticamente; importa normalmente |
| Coluna opcional ausente (`Código Logradouro`, `Situação de Rua` ou `Paciente sem CPF`) | `COLUNA_OPCIONAL_AUSENTE` | AVISO | Importa normalmente; mensagem explica o fallback usado para o campo específico |
| Situação de Rua preenchida mas valor não reconhecido (aceita S/N, Sim/Não, 1/0) | `SITUACAO_RUA_INVALIDA` | AVISO | Importa com aviso; remessa usa "N" |
| Paciente sem CPF preenchido mas valor não reconhecido | `PACIENTE_SEM_CPF_INVALIDO` | AVISO | Importa com aviso; valor é derivado automaticamente a partir do CPF |
| Estabelecimento sem separador "código - nome" reconhecido | `ESTABELECIMENTO_SEM_CODIGO` | AVISO | Importa com aviso; tenta vincular por nome a um estabelecimento já cadastrado, senão o atendimento fica sem estabelecimento |

---

## Layout do Arquivo BPA-I

O arquivo gerado segue o layout oficial de interface texto do BPA, versão 2026 (`data/Layout_Exportacao_BPA_2026.pdf`):

- **Cabeçalho** — 132 caracteres (competência, totais, checksum, órgão emissor)
- **Registros** — 353 caracteres cada (351 de conteúdo + CRLF), 40 campos posicionais (numeração oficial repete o "38" para dois campos consecutivos)
- **Codificação** — ISO-8859-1
- **Quebra de linha** — CRLF

### Regras de formatação

| Tipo de campo | Preenchido | Vazio (opcional) |
|---------------|------------|------------------|
| NUM (numérico) | Zeros à esquerda | Espaços em branco |
| ALFA (alfanumérico) | Espaços à direita | Espaços em branco |

### Campos de destaque

| Campo | Regra |
|-------|-------|
| `prd-raca` (seq 21) | Obrigatório e validado na importação; para dados legados sem raça reconhecida, usa branco (Default oficial do layout) — nunca "99" |
| `prd-cnspac` (seq 10) | Sempre 15 espaços em branco — CNS do paciente não é utilizado neste campo |
| `prd-ibge` (seq 12) | Código IBGE de 7 dígitos truncado para 6 (sem dígito verificador) |
| `prd-cmp` (seq 3) | Competência = mesmo mês do atendimento (AAAAMM da própria `dataAgendamento`) |
| `prd-cpf-pcnte` (seq 38) | CPF do paciente, 11 dígitos zero-padded — é aqui, não na seq 10, que o CPF do paciente entra no registro |
| `prd_situacao_rua` (seq "38" duplicado) | "S"/"N" — vem da coluna "Situação de Rua" da planilha; sem essa coluna, mantém o padrão "N" |
| `prd_sem_cpf` (seq 39, novo em 2026) | "S"/"N" — vem da coluna "Paciente sem CPF" da planilha; sem essa coluna, deriva automaticamente do CPF do paciente (preenchido → "N", vazio → "S") |

### Códigos de logradouro

| Prefixo no endereço | Código |
|---------------------|--------|
| Rua | 081 |
| Avenida / Av. / Av | 008 |
| Travessa / Trav. / TV | 100 |

---

## Colunas Esperadas na Planilha

| # | Coluna | Descrição |
|---|--------|-----------|
| 1 | Tipo Serviço | TELECONSULTA ou TELEINTERCONSULTA |
| 2 | Data Agendamento | Data do atendimento |
| 3 | Hora Atendimento | Horário do atendimento |
| 4 | Estabelecimento | Código + nome do estabelecimento |
| 5 | Especialidade | Especialidade do profissional |
| 6 | Médico | Nome do profissional |
| 7 | CPF Médico | CPF do profissional |
| 8 | CBO Médico | Código CBO do profissional |
| 9 | Município | Município do atendimento |
| 10 | CPF Paciente | CPF do paciente |
| 11 | Paciente | Nome do paciente |
| 12 | CNS Paciente | Cartão Nacional de Saúde do paciente |
| 13 | Sexo Paciente | Sexo do paciente |
| 14 | Raça Paciente | Raça/cor do paciente — obrigatória; aceita Branca, Preta, Parda, Amarela ou Indígena (variações de grafia/gênero toleradas) |
| 15 | Etnia Paciente | Etnia indígena — considerada apenas quando Raça = Indígena |
| 16 | Data Nascimento | Data de nascimento do paciente |
| 17 | CID Consulta | Código CID da consulta |
| 18 | Telefone | Telefone do paciente |
| 19 | Tipo Zona | Zona (urbana/rural) |
| 20 | CEP | CEP do paciente |
| 21 | Endereço | Endereço do paciente (com ou sem prefixo de tipo) |
| 22 | Complemento | Complemento do endereço |
| 23 | Número | Número do endereço |
| 24 | Bairro | Bairro do paciente |

### Colunas opcionais

Ausência não bloqueia a importação — gera aviso `COLUNA_OPCIONAL_AUSENTE` explicando o fallback.

| Coluna | Descrição | Sem a coluna |
|--------|-----------|--------------|
| Código Logradouro | Código do tipo de logradouro | Derivado automaticamente do prefixo do endereço (Rua/Avenida/Travessa); default "081" se não reconhecido |
| Situação de Rua | Paciente em situação de rua (S/N) | Remessa BPA-I usa "N" |
| Paciente sem CPF | Exceção para paciente sem CPF/registro civil (S/N) | Remessa BPA-I deriva automaticamente do CPF: preenchido → "N", vazio → "S" |

---

## Configuração de Mapeamento de Colunas

Acessível pelo botão `[Configurações]` na barra superior, ou diretamente a partir do diálogo de erro quando `[Analisar Planilha]` detecta colunas obrigatórias ausentes/ambíguas no cabeçalho (`ESTRUTURA_INVALIDA`).

O diálogo tem duas abas:
- **Colunas da Planilha** — para cada campo canônico do sistema (ex.: `CPF_PACIENTE`, `MEDICO`), permite cadastrar aliases adicionais de nome de cabeçalho aceitos na planilha, remover aliases customizados e restaurar os aliases padrão de uma coluna. Aliases padrão vêm de `src/main/resources/dados/colunas_aliases.csv`; os customizados são persistidos separadamente.
- **CNS de Médicos** — adicionar, editar e remover profissionais (nome + CNS) e cadastrar apelidos/variações de nome que devem resolver para o mesmo CNS, persistidos em `medicos_cns.csv`.

---

## Cadastro de CNS dos Profissionais

O sistema resolve o CNS do profissional pelo nome via arquivo `src/main/resources/dados/medicos_cns.csv`.

**Formato do arquivo:**
```
nome;cns
JOAO DA SILVA;123456789012345
MARIA SOUZA;987654321098765
```

Profissionais não encontrados geram aviso no log de importação. O CNS pode ser cadastrado manualmente via `[Atualizar CNS]` na barra de edição.

---

## Como Executar

### Pré-requisitos

- **Java 21** (JDK)

### Build e execução

```bash
# Compilar
./mvnw compile          # Linux/Mac
mvnw.cmd compile        # Windows

# Executar
./mvnw javafx:run
mvnw.cmd javafx:run     # Windows
```

O banco H2 é criado automaticamente em `database/` e persiste dados entre reinicializações.

---

## Regras de Negócio

- CNS do paciente deve ter 15 dígitos; CNS com mais de 15 dígitos é aceito com aviso (`CNS_INCOMUM`)
- CPF é normalizado (remove pontos e traços)
- Idade do paciente é calculada em relação à data de geração do arquivo
- A sequência dentro da folha reinicia ao passar de 99 registros, e também a cada troca de folha (regra do layout BPA-I)
- Competência no arquivo = mesmo mês do atendimento (sem deslocamento)
- Checksum do cabeçalho = (soma dos SIGTAP numéricos + contagem de registros) % 1111 + 1111
- Folhas na geração completa: especialidades em ordem alfabética → médicos em ordem alfabética → numeração sequencial por competência
- Raça do paciente é obrigatória e validada (ausente ou grafia não reconhecida bloqueia a importação) — o valor "99 Sem informação" está descontinuado no layout oficial e nunca é aceito como entrada nem gerado como saída; para dados legados (importados antes desta validação existir) sem raça reconhecida, a geração usa branco — o próprio Default declarado pelo layout para `prd-raca`
- Etnia só é considerada quando a raça do paciente é Indígena; para as demais raças, o conteúdo da coluna é ignorado mesmo se preenchido
- Situação de Rua é um campo do **paciente** (traço estável da pessoa); reimportar uma planilha sem essa coluna nunca apaga um valor já conhecido de uma importação anterior com a coluna
- Paciente sem CPF é um campo do **atendimento**, não do paciente — a exceção está ligada ao procedimento específico (atributo SIGTAP "058 - Obrigatório CPF"), não a um traço fixo da pessoa; por isso é sempre sobrescrito pela importação mais recente daquele atendimento
- Estabelecimento tem código como chave natural; quando a célula da planilha não traz um código reconhecido (formato esperado `"código - nome"`), a importação tenta vincular por nome a um estabelecimento já cadastrado antes de deixar o atendimento sem vínculo — o campo não é escrito no arquivo BPA-I (só é usado no relatório interno da tela)

---

## Licença

Uso interno — Núcleo de Telessaúde / Secretaria Estadual de Saúde de Mato Grosso do Sul.
