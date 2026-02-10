# fillBPAi

Aplicação desktop desenvolvida em **Java 21**, com interface **JavaFX**, persistência de dados utilizando **Hibernate** e banco de dados **H2**, destinada à importação e gerenciamento de dados a partir de arquivos **Excel (.xlsx)**.

---

## 🎯 Objetivo do Projeto

O **fillBPAi** tem como objetivo facilitar o preenchimento, validação e armazenamento de informações oriundas de planilhas Excel, oferecendo uma interface desktop simples e eficiente, com banco de dados local e processamento automatizado.

---

## 🧰 Tecnologias Utilizadas

- **Java JDK 21**
- **JavaFX** (Aplicação Desktop)
- **Maven** (Gerenciamento de dependências)
- **Hibernate (JPA)** (Persistência)
- **H2 Database** (Banco embarcado)
- **Apache POI** (Leitura de arquivos Excel `.xlsx`)
- **IntelliJ IDEA**
- **Git / GitHub**

---

## 🏗️ Estrutura inicial do Projeto

```text
fillBPAi
├── pom.xml
└── src
    └── main
        ├── java
        │   └── br.gov.ses.fillbpai
        │       ├── br.gov.ses.fillbpai.app
        │       ├── controller
        │       ├── model
        │       ├── dao
        │       ├── service
        │       └── util
        └── resources
            ├── fxml
            ├── application.properties
            └── hibernate.cfg.xml
