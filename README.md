# fillBPAi

Aplicacao desktop JavaFX para importacao de dados de atendimentos de saude a partir de planilhas Excel e geracao de arquivos magneticos **BPA-I** (Boletim de Producao Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministerio da Saude.

---

## Funcionalidades

- **Importacao de planilha Excel (.xlsx)** — leitura automatica de 22 colunas com dados de atendimento
- **Validacao e normalizacao** — CPF, CNS (15 digitos), datas, horarios, codigos SIGTAP
- **Visualizacao em tabela** — exibicao dos registros importados com filtros por especialidade e profissional
- **Edicao em lote** — atualizar folha e CNS do profissional para todos os registros filtrados
- **Geracao do arquivo BPA-I** — arquivo texto posicional (340 caracteres por registro) em codificacao ISO-8859-1, conforme layout oficial do DATASUS

---

## Stack

| Tecnologia | Versao | Finalidade |
|------------|--------|------------|
| Java | 21 | Linguagem principal |
| JavaFX | 21.0.x | Interface desktop |
| Hibernate / JPA | 6.4.4.Final | Persistencia de dados |
| H2 Database | 2.2.224 | Banco embarcado (recria schema a cada inicializacao) |
| Apache POI | 5.5.1 | Leitura de arquivos `.xlsx` |
| Maven | 3.9+ | Build e gerenciamento de dependencias |
| SLF4J + Logback | 2.0.12 / 1.5.6 | Logging |

---

## Estrutura do Projeto

```
fillBPAi/
├── pom.xml
├── database/                              # Banco H2 (runtime)
└── src/main/java/br/gov/ses/fillbpai/
    ├── app/
    │   └── MainApp.java                   # Entry point JavaFX
    ├── config/
    │   └── DatabaseInitializer.java       # Setup H2 + Hibernate
    ├── controller/
    │   └── MainController.java            # Controller principal da UI
    ├── dto/
    │   └── AtendimentoBPAiDTO.java        # DTO para apresentacao
    ├── model/
    │   └── AtendimentoBPAi.java           # Entidade JPA
    ├── repository/
    │   └── AtendimentoBPAiRepository.java # Camada de persistencia
    ├── service/
    │   ├── AtendimentoImportacaoService.java  # Orquestracao da importacao
    │   ├── AtendimentoProcessor.java          # Validacao e processamento
    │   ├── ExcelImportService.java            # Leitura do Excel
    │   ├── GeradorBPAiService.java            # Geracao do arquivo BPA-I
    │   └── ImportacaoResultado.java           # Resultado da importacao
    ├── ui/
    │   ├── FileChooserService.java        # Dialogo de selecao de arquivo
    │   └── RelatorioController.java       # UI de relatorio/tabela
    └── util/
        ├── CnsUtils.java                  # Validacao/normalizacao CNS
        ├── DateUtils.java                 # Parsing de datas
        ├── StringUtils.java               # Utilitarios de string
        └── TimeUtils.java                 # Parsing de horarios
```

---

## Fluxo da Aplicacao

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────────┐
│  Planilha Excel  │────>│  Importacao +     │────>│  Banco H2 (embarcado) │
│  (.xlsx)         │     │  Validacao        │     │                       │
└─────────────────┘     └──────────────────┘     └───────────┬───────────┘
                                                             │
                         ┌──────────────────┐                │
                         │  Arquivo BPA-I   │<───────────────┘
                         │  (.txt, 340 chars │     Filtra por especialidade
                         │   por registro)  │     + profissional e gera
                         └──────────────────┘
```

1. O usuario importa uma planilha `.xlsx` com dados de atendimentos
2. A aplicacao valida, normaliza e persiste os registros no banco H2
3. Na tela de relatorio, o usuario filtra por especialidade e profissional
4. Ao clicar em "Gerar BPA-I", o sistema gera o arquivo texto posicional conforme layout DATASUS

---

## Layout do Arquivo BPA-I

O arquivo gerado segue o layout oficial de interface texto do BPA, com:

- **Linha de cabecalho** — 132 caracteres com metadados (competencia, totais, checksum, orgao)
- **Linhas de dados** — 340 caracteres cada, com 37 campos posicionais
- **Codificacao** — ISO-8859-1
- **Quebra de linha** — CRLF

### Regras de formatacao

| Tipo de campo | Preenchido | Vazio (opcional) |
|---------------|------------|------------------|
| NUM (numerico) | Zeros a esquerda | Espacos em branco |
| ALFA (alfanumerico) | Espacos a direita | Espacos em branco |

### Mapeamento SIGTAP

| Tipo de Servico | Codigo SIGTAP |
|-----------------|---------------|
| TELECONSULTA | 03.01.01.030-7 |
| TELEINTERCONSULTA | 08.04.01.006-4 |

---

## Colunas Esperadas na Planilha

A planilha Excel deve conter as seguintes colunas (nesta ordem):

| # | Coluna | Descricao |
|---|--------|-----------|
| 1 | Tipo Servico | TELECONSULTA ou TELEINTERCONSULTA |
| 2 | Data Agendamento | Data do atendimento |
| 3 | Hora Atendimento | Horario do atendimento |
| 4 | Estabelecimento | Codigo + nome do estabelecimento |
| 5 | Especialidade/Medico | Especialidade e nome do profissional |
| 6 | CPF Medico | CPF do profissional |
| 7 | CBO Medico | Codigo CBO do profissional |
| 8 | Municipio | Municipio do atendimento |
| 9 | CPF Paciente | CPF do paciente |
| 10 | Paciente | Nome do paciente |
| 11 | CNS Paciente | Cartao Nacional de Saude do paciente |
| 12 | Raca Paciente | Raca/cor do paciente |
| 13 | Data Nascimento | Data de nascimento do paciente |
| 14 | CID Consulta | Codigo CID da consulta |
| 15 | Telefone | Telefone do paciente |
| 16 | Tipo Zona | Zona (urbana/rural) |
| 17 | CEP | CEP do paciente |
| 18 | Codigo Logradouro | Codigo do tipo de logradouro |
| 19 | Endereco | Endereco do paciente |
| 20 | Complemento | Complemento do endereco |
| 21 | Numero | Numero do endereco |
| 22 | Bairro | Bairro do paciente |

---

## Como Executar

### Pre-requisitos

- **Java 21** (JDK)
- **Maven 3.9+**

### Build e execucao

```bash
# Compilar o projeto
mvn clean compile

# Executar a aplicacao
mvn javafx:run
```

O banco H2 sera criado automaticamente em `database/fillbpai.mv.db` e o console web estara disponivel em `http://localhost:8082`.

---

## Regras de Negocio

- O schema do banco e recriado a cada inicializacao (`hibernate.hbm2ddl.auto=create`)
- O CNS deve conter exatamente 15 digitos (caracteres nao numericos sao removidos)
- O CPF e normalizado (pontos e tracos removidos)
- A idade do paciente e calculada a partir da data de nascimento
- A sequencia dentro da folha reinicia a cada 20 registros
- A competencia e derivada da data de atendimento (formato AAAAMM)
- O checksum do cabecalho e calculado como: soma dos codigos SIGTAP numericos % 1111 + 1111

---

## Licenca

Uso interno — Secretaria Estadual de Saude.
