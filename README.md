# fillBPAi

Aplicação desktop JavaFX utilizada pelo **Núcleo de Telessaúde de MS** para importar dados de atendimentos de saúde a partir de planilhas Excel e gerar arquivos magnéticos **BPA-I** (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde para envio ao sistema SIA/SUS.

---

## Funcionalidades

### Importação
- **Análise prévia da planilha** (`[Analisar Planilha]`) — valida erros bloqueantes (CNS, CEP, CPF, estrutura de colunas) e avisos; ao não encontrar erros bloqueantes, o próprio diálogo de resultado oferece o botão **"Importar Planilha"** para concluir a importação, com leitura, validação e normalização de todos os campos
- **Configuração de mapeamento de colunas** (`[Configurações]`) — permite cadastrar/remover aliases de nomes de cabeçalho aceitos para cada campo canônico da planilha, para acomodar variações de nomenclatura entre planilhas; acessível também diretamente a partir do erro de estrutura de planilha
- **Detecção de tipo de logradouro** — identifica automaticamente o prefixo do endereço (Rua → 081, Avenida/Av./Av → 008, Travessa/Trav./TV → 100) e preenche o código do logradouro
- **Resolução de CNS do profissional** — busca por nome no arquivo `dados/medicos_cns.csv` (escopo estadual)
- **Resolução de código IBGE** — via CSV por nome do município, cache do banco ou API ViaCEP
- **Log de importação persistido** — salvo em `database/log_importacao.txt` para consulta a qualquer momento

### Visualização e Edição
- **Tabela com 28 colunas** — exibição completa dos registros importados
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
    │   │   ├── PlanilhaColumnMapper.java           # Mapeia cabeçalhos → campos canônicos via aliases
    │   │   └── ValidacaoPlanilhaService.java       # Validação pré-importação
    │   ├── ui/
    │   │   ├── ConfiguracoesDialog.java            # Diálogo de aliases de colunas da planilha
    │   │   ├── FileChooserService.java             # Diálogo de seleção de arquivo
    │   │   └── RelatorioController.java            # Tabela, filtros, ações de geração
    │   └── util/
    │       ├── CepUtils.java                       # Normalização de CEP
    │       ├── CnsProfissionalUtils.java           # Resolução de CNS por nome
    │       ├── CnsUtils.java                       # Validação/normalização CNS paciente
    │       ├── ColunaAliasUtils.java                # Gerencia aliases de nomes de coluna de cabeçalho
    │       ├── CpfUtils.java                        # Normalização/validação de CPF (11 dígitos)
    │       ├── DateUtils.java                       # Parse de data em múltiplos formatos aceitos
    │       ├── IbgeUtils.java                       # Resolução de código IBGE
    │       ├── LogradouroUtils.java                 # Detecção de tipo de logradouro
    │       ├── StringUtils.java                     # Truncamento e split de "código - nome"
    │       ├── TextoUtils.java                      # Normalização de texto para comparação
    │       └── TimeUtils.java                       # Parse de hora em múltiplos formatos aceitos
    └── resources/
        └── dados/
            ├── colunas_aliases.csv                 # Aliases padrão de nomes de coluna de cabeçalho
            └── medicos_cns.csv                     # Cache local CNS dos profissionais
```

---

## Layout da Interface

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Analisar Planilha] [Ver Log Importação] [Configurações]   Comp:MM/AAAA│  ← Linha 1 (topBar)
├────────────────────────────────────────────────────────────────────────┤
│ [Selecionar Mês] [Gerar BPA-I Completo] [⚠ Pendências CNS]            │  ← Linha 2 (barra de ações)
├────────────────────────────────────────────────────────────────────────┤
│ Buscar médico: [________] [Buscar]  Especialidade[▼]  (Médico[▼])     │  ← Linha 3 (barra de filtros)
│                                        [Gerar BPA-I] [⚠] [Limpar]      │
├────────────────────────────────────────────────────────────────────────┤
│ (aparece ao selecionar médico ou usar busca)                           │  ← Linha 4 (barra de edição)
│ CNS: [___________] [Atualizar CNS] [Definir Folha]                    │
├────────────────────────────────────────────────────────────────────────┤
│                         Tabela de Atendimentos                         │  ← Linha 5
│                              Total: N                                  │
└────────────────────────────────────────────────────────────────────────┘
```

O botão **"Importar Planilha"** não fica fixo na barra superior — ele aparece dentro do diálogo de resultado exibido após `[Analisar Planilha]`, quando não há erros bloqueantes.

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
| CNS ausente ou < 15 dígitos | `CNS_INVALIDO` | AVISO | Importa com aviso no log (não bloqueia) |
| CNS com > 15 dígitos | `CNS_INCOMUM` | AVISO | Importa com aviso no log |
| CEP não informado | `CEP_AUSENTE` | ERRO | Bloqueia importação |
| CEP com tamanho inválido (≠ 8 dígitos) | `CEP_INVALIDO` | ERRO | Bloqueia importação |
| CPF não informado | `CPF_AUSENTE` | ERRO | Bloqueia importação |
| CPF com tamanho inválido (≠ 11 dígitos) | `CPF_INVALIDO` | ERRO | Bloqueia importação |
| Raça do paciente = Indígena | `RACA_INDIGENA` | AVISO | Importa com aviso no log |
| Coluna obrigatória ausente/ambígua no cabeçalho | `ESTRUTURA_INVALIDA` | ERRO | Bloqueia importação (oferece abrir Configurações) |

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
| `prd-cnspac` (seq 10) | Sempre 15 espaços em branco — CNS do paciente não é utilizado neste campo |
| `prd-ibge` (seq 12) | Código IBGE de 7 dígitos truncado para 6 (sem dígito verificador) |
| `prd-cmp` (seq 3) | Competência = mês de atendimento + 1 (mês de faturamento) |
| `prd-cpf-pcnte` (seq 38) | CPF do paciente, 11 dígitos zero-padded — é aqui, não na seq 10, que o CPF do paciente entra no registro |

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
| 14 | Raça Paciente | Raça/cor do paciente |
| 15 | Data Nascimento | Data de nascimento do paciente |
| 16 | CID Consulta | Código CID da consulta |
| 17 | Telefone | Telefone do paciente |
| 18 | Tipo Zona | Zona (urbana/rural) |
| 19 | CEP | CEP do paciente |
| 20 | Código Logradouro | Preenchido automaticamente pelo sistema |
| 21 | Endereço | Endereço do paciente (com ou sem prefixo de tipo) |
| 22 | Complemento | Complemento do endereço |
| 23 | Número | Número do endereço |
| 24 | Bairro | Bairro do paciente |

---

## Configuração de Mapeamento de Colunas

Acessível pelo botão `[Configurações]` na barra superior, ou diretamente a partir do diálogo de erro quando `[Analisar Planilha]` detecta colunas obrigatórias ausentes/ambíguas no cabeçalho (`ESTRUTURA_INVALIDA`).

O diálogo tem duas abas:
- **Colunas da Planilha** — para cada campo canônico do sistema (ex.: `CPF_PACIENTE`, `MEDICO`), permite cadastrar aliases adicionais de nome de cabeçalho aceitos na planilha, remover aliases customizados e restaurar os aliases padrão de uma coluna. Aliases padrão vêm de `src/main/resources/dados/colunas_aliases.csv`; os customizados são persistidos separadamente.
- **CNS de Médicos** — reservada para gerenciar `medicos_cns.csv` pela interface; ainda não implementada (placeholder "Em breve").

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
