# fillBPAi — Projeto CLAUDE.md

## Sobre o Projeto
Aplicação desktop JavaFX que importa dados de atendimentos de saúde de planilhas Excel e gera arquivos magnéticos BPA-I (Boletim de Produção Ambulatorial Individualizada) no formato exigido pelo DATASUS/Ministério da Saúde.

## Stack
- Java 21, JavaFX, JPA/Hibernate (sem Spring Boot)
- H2 Database (embarcado, recria schema a cada inicialização)
- Apache POI para leitura de `.xlsx`
- Maven para build

## Build & Run
- Maven não está no PATH do sistema. Usar caminho completo:
  `C:\Users\sergi\.m2\wrapper\dists\apache-maven-3.9.11\d6d3cbd4012d4c1d840e93277aca316c\bin\mvn.cmd`
- `mvn test` roda automaticamente via PostToolUse hook (Edit/Write) — NÃO rodar manualmente em agents/subagents

## Estrutura do Projeto
Arquitetura em camadas: `controller → service → repository → model`, com `util`, `dto` e `ui`.

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
- Campos NUM opcionais: brancos quando vazio, zeros à esquerda quando preenchido
- Campos ALFA: espaços à direita até completar tamanho
- Codificação do arquivo de saída: ISO-8859-1
- Documento de referência do layout: `Layout interface texto do BPA.pdf`

## Convenções
- Conventional commits em português
- Feature branches off main
- Não commitar diretamente — sugerir mensagem, usuário commita
