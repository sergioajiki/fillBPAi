# fillBPAi

Aplicação desktop JavaFX utilizada pelo **Núcleo de Telessaúde de MS** para importar dados de atendimentos de saúde a partir de planilhas Excel e gerar arquivos magnéticos **BPA-I** (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde para envio ao sistema SIA/SUS.

---

## Funcionalidades

### Importação
- **Análise prévia da planilha** — valida erros bloqueantes (CNS, CEP, CPF) e avisos antes de importar; ao não encontrar erros, oferece importação direta na mesma janela
- **Importação de planilha Excel (.xlsx)** — leitura automática com validação e normalização de todos os campos
- **Detecção de tipo de logradouro** — identifica automaticamente o prefixo do endereço (Rua → 081, Avenida/Av./Av → 008, Travessa/Trav./TV → 100) e preenche o código do logradouro
- **Resolução de CNS do profissional** — busca por nome no arquivo `dados/medicos_cns.csv` (escopo estadual)
- **Resolução de código IBGE** — via CSV por nome do município, cache do banco ou API ViaCEP
- **Log de importação persistido** — salvo em `database/log_importacao.txt` para consulta a qualquer momento

### Visualização e Edição
- **Tabela com 29 colunas** — exibição completa dos registros importados
- **Busca livre por nome do médico** — correspondência parcial, case-insensitive
- **Filtro por especialidade e médico** — combo de especialidade revela combo de médico; mutuamente exclusivo com a busca livre
- **Edição em lote** — atualiza CNS do profissional e define folha para todos os registros do médico/especialidade selecionados

### Geração BPA-I
- **Seleção de competência** — seletor de mês/ano na barra de ações; auto-detectado na primeira carga de dados
- **Geração completa** — todos os médicos do mês de competência selecionado, folhas atribuídas automaticamente (especialidade alfabética → médico alfabético → folha sequencial)
- **Geração parcial** — filtrada por especialidade e médico selecionados
- **Pré-validações bloqueantes**:
  - BPA-I completo: bloqueia se qualquer atendimento estiver sem CNS do profissional (botão `⚠ Pendências CNS`)
  - BPA-I parcial: bloqueia se houver atendimentos sem folha ou sem CNS do profissional (botão `⚠`)

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
    │   │   └── ValidacaoPlanilhaService.java       # Validação pré-importação
    │   ├── ui/
    │   │   ├── FileChooserService.java             # Diálogo de seleção de arquivo
    │   │   └── RelatorioController.java            # Tabela, filtros, ações de geração
    │   └── util/
    │       ├── CepUtils.java                       # Normalização de CEP
    │       ├── CnsProfissionalUtils.java           # Resolução de CNS por nome
    │       ├── CnsUtils.java                       # Validação/normalização CNS paciente
    │       ├── IbgeUtils.java                      # Resolução de código IBGE
    │       └── LogradouroUtils.java                # Detecção de tipo de logradouro
    └── resources/
        └── dados/
            └── medicos_cns.csv                     # Cache local CNS dos profissionais
```

---

## Layout da Interface

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Analisar Planilha] [Importar Planilha] [Ver Log Importação]           │  ← Linha 1
│                                         [Selecionar Mês] Comp: MM/AAAA│
├────────────────────────────────────────────────────────────────────────┤
│ [Selecionar Mês] [Gerar BPA-I Completo] [⚠ Pendências CNS]            │  ← Linha 2
│                  [Gerar BPA-I] [⚠]                                     │
├────────────────────────────────────────────────────────────────────────┤
│ Buscar médico: [________] [Buscar]  Especialidade[▼]  (Médico[▼])     │  ← Linha 3
│                                                         [Limpar]       │
├────────────────────────────────────────────────────────────────────────┤
│ (aparece ao selecionar médico ou usar busca)                           │  ← Linha 4
│ CNS: [___________] [Atualizar CNS] [Definir Folha]                    │
├────────────────────────────────────────────────────────────────────────┤
│                         Tabela de Atendimentos                         │  ← Linha 5
│                              Total: N                                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Fluxo da Aplicação

```
Planilha .xlsx
      │
      ▼
[Analisar Planilha] ──► sem erros ──► [Importar Planilha] (mesmo diálogo)
      │                só avisos ──► [Importar Planilha] (mesmo diálogo)
      │                com erros ──► exibe log (sem importação)
      │
[Importar Planilha]
      │
      ├─ ValidacaoPlanilhaService   (bloqueia se CNS/CEP/CPF inválidos)
      ├─ ExcelImportService         (lê linha → LinhaImportacaoDTO)
      ├─ AtendimentoProcessor       (valida e normaliza campos)
      ├─ LogradouroUtils            (detecta tipo de logradouro)
      ├─ IbgeUtils                  (resolve código IBGE)
      ├─ CnsProfissionalUtils       (resolve CNS pelo nome via CSV)
      └─ AtendimentoImportacaoService (persiste no banco H2)
              │
              ▼
        Banco H2 (database/)
              │
              ▼
[Selecionar Mês] ──► competência para geração
              │
    ┌─────────┴──────────┐
    ▼                    ▼
[Gerar BPA-I]      [Gerar BPA-I Completo]
(especialidade      (todos os médicos do
 + médico)           mês selecionado)
    │                    │
    └─────────┬──────────┘
              ▼
    Arquivo .txt BPA-I
    (ISO-8859-1, 340 chars/linha)
```

---

## Validações Pré-Importação

| Tipo | Código | Severidade | Efeito |
|------|--------|-----------|--------|
| CNS ausente ou < 15 dígitos | `CNS_INVALIDO` | ERRO | Bloqueia importação |
| CEP não informado | `CEP_AUSENTE` | ERRO | Bloqueia importação |
| CPF não informado | `CPF_AUSENTE` | ERRO | Bloqueia importação |
| CNS com > 15 dígitos | `CNS_INCOMUM` | AVISO | Importa com aviso no log |

---

## Layout do Arquivo BPA-I

O arquivo gerado segue o layout oficial de interface texto do BPA:

- **Cabeçalho** — 132 caracteres (competência, totais, checksum, órgão emissor)
- **Registros** — 340 caracteres cada, 38 campos posicionais
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
| `prd-cnspac` (seq 10) | CPF do paciente zero-padded para 15 chars |
| `prd-ibge` (seq 12) | Código IBGE de 7 dígitos truncado para 6 (sem dígito verificador) |
| `prd-cmp` (seq 3) | Competência = mês de atendimento + 1 (mês de faturamento) |

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
| 5 | Especialidade/Médico | Especialidade e nome do profissional |
| 6 | CPF Médico | CPF do profissional |
| 7 | CBO Médico | Código CBO do profissional |
| 8 | Município | Município do atendimento |
| 9 | CPF Paciente | CPF do paciente |
| 10 | Paciente | Nome do paciente |
| 11 | CNS Paciente | Cartão Nacional de Saúde do paciente |
| 12 | Raça Paciente | Raça/cor do paciente |
| 13 | Data Nascimento | Data de nascimento do paciente |
| 14 | CID Consulta | Código CID da consulta |
| 15 | Telefone | Telefone do paciente |
| 16 | Tipo Zona | Zona (urbana/rural) |
| 17 | CEP | CEP do paciente |
| 18 | Código Logradouro | Preenchido automaticamente pelo sistema |
| 19 | Endereço | Endereço do paciente (com ou sem prefixo de tipo) |
| 20 | Complemento | Complemento do endereço |
| 21 | Número | Número do endereço |
| 22 | Bairro | Bairro do paciente |

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
- A sequência dentro da folha reinicia a cada 20 registros (regra do layout BPA-I)
- Competência no arquivo = mês de atendimento + 1 mês
- Checksum do cabeçalho = (soma dos SIGTAP numéricos + contagem de registros) % 1111 + 1111
- Folhas na geração completa: especialidades em ordem alfabética → médicos em ordem alfabética → numeração sequencial por competência

---

## Licença

Uso interno — Núcleo de Telessaúde / Secretaria Estadual de Saúde de Mato Grosso do Sul.
