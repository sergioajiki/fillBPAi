# Documentação dos Testes — PonteBPA (código: fillBPAi)

> Lista de todos os testes automatizados de `src/test/java/br/gov/ses/fillbpai/`, classe por classe, com o que cada um confere e o resultado da última execução. Gerada em {{DATA}} por `node docs/gerar-documentacao-testes.js`, a partir do código dos testes e dos relatórios do Maven (`target/surefire-reports`). Para o histórico de como os testes foram criados (sprints de setembro/2026), ver `docs/runbook-testes-sprint-0-2.html`.

## Sumário

- [Como rodar](#como-rodar)
- [Ambiente dos testes](#ambiente-dos-testes)
- [Como ler esta documentação](#como-ler-esta-documentação)
- [Resultado da última execução](#resultado-da-última-execução)
- [Testes por classe](#testes-por-classe)

---

## Como rodar

Na raiz do projeto:

```
mvnw.cmd test                  (Windows)
./mvnw -q test                 (Git Bash)
```

Os testes também rodam sozinhos depois de cada edição feita pelo Claude Code (hook configurado no projeto). Depois de rodar, `node docs/gerar-documentacao-testes.js` atualiza este documento com os novos resultados.

## Ambiente dos testes

| Recurso | Como os testes tratam |
|---|---|
| Ferramentas | JUnit 5 (inclusive testes parametrizados — `@ParameterizedTest`, um caso por valor), AssertJ para as conferências |
| Banco de dados | H2 **em memória** (`jdbc:h2:mem:testdb`, unidade `bpaPU-test` em `src/test/resources/META-INF/persistence.xml`) — o banco real `database/fillbpai.mv.db` nunca é tocado. Usado pelos testes de repositório, importação e registro da remessa |
| "Hoje" | Fixado em **19/10/2026** pela propriedade `fillbpai.hoje` (surefire, `pom.xml`), lida por `DateUtils.hoje()` — testes de data futura ou antiga não quebram com o passar do tempo |
| Internet (CEP → IBGE) | Simulada: `IbgeUtils.usarBuscadorHttpParaTeste` responde no lugar do ViaCEP, BrasilAPI e OpenCEP — os testes não dependem de rede |
| Arquivos de cadastro | `CnsProfissionalUtils` e `ColunaAliasUtils` gravam numa cópia temporária (`usarCaminhoParaTeste`), nunca nos CSV reais de `src/main/resources/dados` |
| Planilhas e logs | Criadas em memória (Apache POI) e gravadas em pastas temporárias (`@TempDir`), apagadas no fim de cada teste |
| Dados pessoais | Só fictícios (CPFs e CNS gerados com dígito verificador válido, nomes inventados) |

## Como ler esta documentação

- Cada teste aparece com uma frase montada a partir do nome do método (ex.: `campoIneForaDoTamanhoSaiEmBrancoParaNaoDeslocarORegistro` → "Campo INE fora do tamanho sai em branco para não deslocar o registro"); o nome original vem logo depois, para busca no código. Os acentos são recolocados por um dicionário simples — alguma palavra pode ficar sem acento.
- Quando o teste tem comentário no código, o comentário vem junto.
- **Casos**: testes parametrizados rodam uma vez por valor; aparecem com o número de casos executados e os valores usados.
- Os títulos dentro de cada classe (quando existem) são as seções marcadas no próprio código (`// ===== ... =====`).
- ✓ = passou na última execução; ✗ = falhou; "—" = sem resultado (rodar os testes e gerar de novo).

---

