# Documentação Técnica — PonteBPA (código: fillBPAi)

> Referência de todas as classes de `src/main/java/br/gov/ses/fillbpai/`, pacote por pacote: o que cada classe faz e seus métodos (públicos e privados documentados), com o texto tirado da documentação (javadoc) do próprio código. Atualizada em {{DATA}}. Para a arquitetura, os fluxos e as regras de negócio, ver `CLAUDE.md`; para o uso, `README.md`.
>
> O nome exibido do programa é **PonteBPA**. Pacote, classes, métodos, `artifactId` e arquivo do banco continuam `fillbpai` — a troca de nome é só visual.

## Sumário

- [Pastas e arquivos em execução](#pastas-e-arquivos-em-execução)
- [app](#pacote-app)
- [config](#pacote-config)
- [controller](#pacote-controller)
- [ui](#pacote-ui)
- [service](#pacote-service)
- [util](#pacote-util)
- [repository](#pacote-repository)
- [model](#pacote-model)
- [dto](#pacote-dto)

---

## Pastas e arquivos em execução

Definidas por `PastasApp` (21/10/2026). No **desenvolvimento**, relativas à pasta do projeto (caminhos abaixo). No **programa instalado** (`-Dpontebpa.instalado=true`), tudo em `%LOCALAPPDATA%\PonteBPA\` — `database\`, `logs\`, `dados\` (cadastros) e `config\` (senha). `database/`, `logs/` e `config/` contêm dados pessoais ou a senha (LGPD) e ficam fora do Git (`.gitignore`); os dois CSV de cadastro do projeto são versionados — nunca gravar CPF neles.

| Pasta / arquivo | Conteúdo | Quem grava |
|---|---|---|
| `config/banco.properties` | Senha do banco (gerada na primeira execução). A cópia de segurança do banco só serve junto com ele | `SenhaBanco` |
| `database/fillbpai.mv.db` | Banco H2: pacientes, médicos, estabelecimentos, atendimentos (com folha, linha e data da remessa) | `DatabaseInitializer` (JPA/Hibernate, `hbm2ddl update`) |
| `logs/log_importacao_<planilha>_<aaaa-MM-dd_HHmm>.txt` | Log de cada importação: planilha, pasta, data/hora, conferência de linhas, avisos e ciência dos avisos. Pasta própria desde 20/10/2026; logs que estavam em `database/` são movidos para cá ao abrir o programa | `MainController.salvarLogEmArquivo`, `HistoricoLogsService` |
| `src/main/resources/dados/medicos_cns.csv` | CNS dos profissionais por grafia do nome (`nome;cns`) — o CPF do médico fica só no banco | Configurações → CNS de Médicos (`CnsProfissionalUtils`) |
| `src/main/resources/dados/colunas_aliases.csv` | Nomes alternativos de cabeçalho cadastrados pelo usuário | Configurações (`ColunaAliasUtils`) |

O relatório da análise (`Analisar Planilha`) só é gravado pelo botão "Salvar Log TXT", no local que o usuário escolher.

---

