# Documentação dos Testes — PonteBPA (código: fillBPAi)

> Lista de todos os testes automatizados de `src/test/java/br/gov/ses/fillbpai/`, classe por classe, com o que cada um confere e o resultado da última execução. Gerada em 21/10/2026 por `node docs/gerar-documentacao-testes.js`, a partir do código dos testes e dos relatórios do Maven (`target/surefire-reports`). Para o histórico de como os testes foram criados (sprints de setembro/2026), ver `docs/runbook-testes-sprint-0-2.html`.

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

## Resultado da última execução

Execução de 21/10/2026 às 15:14: **710 execuções** de 558 testes em 40 classes — **todas passaram** ✓ (16.9 s somando as classes).

| Classe | Pacote | Testes | Execuções | Resultado | Tempo |
|---|---|---:|---:|:---:|---:|
| [CboUtilsTest](#cboutilstest) | util | 3 | 3 | ✓ | 0.00 s |
| [CepUtilsTest](#ceputilstest) | util | 5 | 10 | ✓ | 0.07 s |
| [CidUtilsTest](#cidutilstest) | util | 6 | 6 | ✓ | 0.00 s |
| [CnsProfissionalUtilsTest](#cnsprofissionalutilstest) | util | 17 | 17 | ✓ | 0.07 s |
| [CnsUtilsTest](#cnsutilstest) | util | 11 | 11 | ✓ | 0.00 s |
| [ColunaAliasUtilsTest](#colunaaliasutilstest) | util | 10 | 10 | ✓ | 0.02 s |
| [CpfUtilsTest](#cpfutilstest) | util | 13 | 27 | ✓ | 0.01 s |
| [DateUtilsTest](#dateutilstest) | util | 11 | 20 | ✓ | 0.01 s |
| [EnderecoUtilsTest](#enderecoutilstest) | util | 7 | 36 | ✓ | 0.02 s |
| [EspecialidadeUtilsTest](#especialidadeutilstest) | util | 10 | 10 | ✓ | 0.00 s |
| [EtniaUtilsTest](#etniautilstest) | util | 12 | 12 | ✓ | 0.01 s |
| [IbgeUtilsTest](#ibgeutilstest) | util | 19 | 19 | ✓ | 0.01 s |
| [LogradouroUtilsTest](#logradouroutilstest) | util | 10 | 39 | ✓ | 0.02 s |
| [NomePacienteUtilsTest](#nomepacienteutilstest) | util | 4 | 14 | ✓ | 0.01 s |
| [NumeroUtilsTest](#numeroutilstest) | util | 4 | 38 | ✓ | 0.03 s |
| [RacaUtilsTest](#racautilstest) | util | 11 | 11 | ✓ | 0.00 s |
| [StringUtilsTest](#stringutilstest) | util | 18 | 18 | ✓ | 0.00 s |
| [TelefoneUtilsTest](#telefoneutilstest) | util | 4 | 19 | ✓ | 0.01 s |
| [TextoUtilsTest](#textoutilstest) | util | 3 | 5 | ✓ | 0.00 s |
| [TimeUtilsTest](#timeutilstest) | util | 6 | 11 | ✓ | 0.01 s |
| [AgrupamentoValidacaoTest](#agrupamentovalidacaotest) | service | 6 | 6 | ✓ | 0.04 s |
| [AtendimentoImportacaoServiceTest](#atendimentoimportacaoservicetest) | service | 56 | 56 | ✓ | 6.38 s |
| [AtendimentoProcessorTest](#atendimentoprocessortest) | service | 88 | 88 | ✓ | 0.09 s |
| [DataSemAnoTest](#datasemanotest) | service | 3 | 3 | ✓ | 0.25 s |
| [ExcelImportServiceTest](#excelimportservicetest) | service | 14 | 14 | ✓ | 0.05 s |
| [GeradorBPAiServiceTest](#geradorbpaiservicetest) | service | 41 | 41 | ✓ | 0.04 s |
| [HistoricoLogsServiceTest](#historicologsservicetest) | service | 7 | 7 | ✓ | 0.03 s |
| [PlanilhaColumnMapperTest](#planilhacolumnmappertest) | service | 14 | 14 | ✓ | 0.02 s |
| [RegistroRemessaTest](#registroremessatest) | service | 1 | 1 | ✓ | 0.05 s |
| [SobreServiceTest](#sobreservicetest) | service | 5 | 5 | ✓ | 0.01 s |
| [ValidacaoPlanilhaServiceTest](#validacaoplanilhaservicetest) | service | 103 | 103 | ✓ | 1.45 s |
| [AtendimentoBPAiRepositoryTest](#atendimentobpairepositorytest) | repository | 4 | 4 | ✓ | 2.39 s |
| [EstabelecimentoRepositoryTest](#estabelecimentorepositorytest) | repository | 5 | 5 | ✓ | 0.54 s |
| [MedicoRepositoryTest](#medicorepositorytest) | repository | 2 | 2 | ✓ | 0.18 s |
| [PacienteRepositoryTest](#pacienterepositorytest) | repository | 2 | 2 | ✓ | 0.19 s |
| [DatabaseInitializerTest](#databaseinitializertest) | config | 2 | 2 | ✓ | 0.01 s |
| [PastaRemessasTest](#pastaremessastest) | config | 5 | 5 | ✓ | 0.13 s |
| [PastasAppTest](#pastasapptest) | config | 6 | 6 | ✓ | 0.04 s |
| [SenhaBancoTest](#senhabancotest) | config | 7 | 7 | ✓ | 4.50 s |
| [RegistroErrosTest](#registroerrostest) | app | 3 | 3 | ✓ | 0.17 s |
| **Total** | | **558** | **710** | ✓ | 16.9 s |

"Testes" = métodos de teste; "Execuções" conta cada caso dos testes parametrizados.

---

## Testes por classe

### Pacote `util`

Regras por campo (CPF, CNS, CEP, datas, endereço, raça, etnia…) — funções puras, sem banco.

#### CboUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CboUtilsTest.java` · 3 testes · 3 execuções · ✓ todas passaram

- ✓ Normalizar mantem só os dígitos (`normalizarMantemSoOsDigitos`)
- ✓ Normalizar com nulo devolve vazio (`normalizarComNuloDevolveVazio`)
- ✓ Válido exige seis dígitos depois de normalizar (`isValidoExigeSeisDigitosDepoisDeNormalizar`)

#### CepUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CepUtilsTest.java` · 5 testes · 10 execuções · ✓ todas passaram

- ✓ Normalizar remove máscara (`normalizarRemoveMascara`)
- ✓ Normalizar retorna null para null (`normalizarRetornaNullParaNull`)
- ✓ Normalizar retorna null para vazio ou branco (`normalizarRetornaNullParaVazioOuBranco`) · *2 casos*: "", " "
- ✓ Válido exige 8 dígitos (`isValidoExige8Digitos`) · *4 casos*: "01234567, true", "01234-567, true", "1234567, false", "012345678, false"
- ✓ Válido falso para nulo ou vazio (`isValidoFalsoParaNuloOuVazio`) · *2 casos*

#### CidUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CidUtilsTest.java` · 6 testes · 6 execuções · ✓ todas passaram

- ✓ Extrair aceita o código com ou sem subcategoria e ponto (`extrairAceitaOCodigoComOuSemSubcategoriaEPonto`)
- ✓ Extrair tira o descritivo eo prefixo (`extrairTiraODescritivoEOPrefixo`)
- ✓ Extrair recusa celula sem código ou com mais de um (`extrairRecusaCelulaSemCodigoOuComMaisDeUm`)
- ✓ Extrair mesmo código repetido conta como um (`extrairMesmoCodigoRepetidoContaComoUm`)
- ✓ Motivo inválido diferencia nenhum de mais de um (`motivoInvalidoDiferenciaNenhumDeMaisDeUm`)
- ✓ Sem CID reconhece só traco (`isSemCidReconheceSoTraco`)

#### CnsProfissionalUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CnsProfissionalUtilsTest.java` · 17 testes · 17 execuções · ✓ todas passaram

Usa `CnsProfissionalUtils#usarCaminhoParaTeste` para redirecionar a camada "arquivo externo" (gravável) para um arquivo temporário — nunca escreve no CSV real do projeto. A camada classpath (os 47 médicos reais) continua carregando normalmente; por isso os testes usam CNS/nomes obviamente falsos, e reaproveitam o médico real conhecido (RODRIGO SILVA GRILO → CNS 700207960618529) só para os casos de leitura.

- ✓ Buscar com nome real conhecido encontra CNS (`buscarComNomeRealConhecidoEncontraCns`)
- ✓ Buscar com nome desconhecido devolve aviso com o nome e nulo no CNS (`buscarComNomeDesconhecidoDevolveAvisoComONomeENuloNoCns`)
- ✓ Cadastrar novo médico aparece na lista e encontravel pelo nome (`cadastrarNovoMedicoApareceNaListaEEncontravelPeloNome`)
- ✓ Cadastrar com CNS já existente lanca exceção (`cadastrarComCnsJaExistenteLancaExcecao`)
- ✓ Cadastrar com nome já associado a outro CNS lanca exceção (`cadastrarComNomeJaAssociadoAOutroCnsLancaExcecao`)
- ✓ Adicionar apelido a médico existente resolve pelo novo nome (`adicionarApelidoAMedicoExistenteResolvePeloNovoNome`)
- ✓ Adicionar apelido já cadastrado do mesmo médico não lanca exceção (`adicionarApelidoJaCadastradoDoMesmoMedicoNaoLancaExcecao`)
- ✓ Adicionar apelido com nome de outro médico lanca exceção (`adicionarApelidoComNomeDeOutroMedicoLancaExcecao`)
- ✓ Remover apelido quando ha mais de um funciona (`removerApelidoQuandoHaMaisDeUmFunciona`)
- ✓ Remover apelido único lanca exceção (`removerApelidoUnicoLancaExcecao`)
- ✓ Alterar CNS de médico existente atualiza resolução dos apelidos antigos (`alterarCnsDeMedicoExistenteAtualizaResolucaoDosApelidosAntigos`)
- ✓ Alterar CNS para valor já usado pelo médico real lanca exceção (`alterarCnsParaValorJaUsadoPeloMedicoRealLancaExcecao`)
- ✓ Remover médico remove todos os apelidos (`removerMedicoRemoveTodosOsApelidos`)
- ✓ Médico do pacote removido não volta depois de reabrir (`medicoDoPacoteRemovidoNaoVoltaDepoisDeReabrir`) — Programa instalado (21/10/2026): o arquivo da pasta de dados é o cadastro completo; antes, o CSV do pacote era somado a ele e o médico removido voltava na próxima abertura
- ✓ CNS alterado não volta ao valor do pacote depois de reabrir (`cnsAlteradoNaoVoltaAoValorDoPacoteDepoisDeReabrir`)
- ✓ Limpar cache forca releitura do arquivo temporario (`limparCacheForcaReleituraDoArquivoTemporario`)
- ✓ Obter todos médicos devolve apelidos em ordem de cadastro (`obterTodosMedicosDevolveApelidosEmOrdemDeCadastro`)

#### CnsUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CnsUtilsTest.java` · 11 testes · 11 execuções · ✓ todas passaram

- ✓ Normalizar remove não dígitos (`normalizarRemoveNaoDigitos`)
- ✓ Normalizar retorna null para null (`normalizarRetornaNullParaNull`)
- ✓ Processar com quinze dígitos não gera avisos (`processarComQuinzeDigitosNaoGeraAvisos`)
- ✓ Processar com menos de quinze dígitos gera aviso CNS inválido (`processarComMenosDeQuinzeDigitosGeraAvisoCnsInvalido`)
- ✓ Processar com valor nulo não gera aviso e devolve CNS vazio (`processarComValorNuloNaoGeraAvisoEDevolveCnsVazio`)
- ✓ Processar com valor vazio não gera aviso e devolve CNS vazio (`processarComValorVazioNaoGeraAvisoEDevolveCnsVazio`)

**Dígito verificador (recomendação 25)**

- ✓ DV válido aceita CNS definitivo e provisorio (`isDvValidoAceitaCnsDefinitivoEProvisorio`)
- ✓ DV válido recusa dígito errado primeiro dígito inválido e tamanho errado (`isDvValidoRecusaDigitoErradoPrimeiroDigitoInvalidoETamanhoErrado`)
- ✓ Processar com quinze dígitos e DV errado gera aviso (`processarComQuinzeDigitosEDvErradoGeraAviso`)
- ✓ CNS sem CPF válido exige quinze dígitos e DV válido (`isCnsSemCpfValidoExigeQuinzeDigitosEDvValido`)
- ✓ Processar com mais de quinze dígitos gera aviso e não grava o valor (`processarComMaisDeQuinzeDigitosGeraAvisoENaoGravaOValor`)

#### ColunaAliasUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/ColunaAliasUtilsTest.java` · 10 testes · 10 execuções · ✓ todas passaram

Usa `ColunaAliasUtils#usarCaminhoParaTeste` para redirecionar a camada "local" (gravável) para um arquivo temporário — nunca escreve no CSV real do projeto. A camada "padrão" continua vindo do classpath real (somente leitura aqui, portanto seguro).

- ✓ Resolver campo com alias padrão conhecido (`resolverCampoComAliasPadraoConhecido`)
- ✓ Resolver campo ignora acento e caixa (`resolverCampoIgnoraAcentoECaixa`)
- ✓ Resolver campo sem correspondencia devolve null (`resolverCampoSemCorrespondenciaDevolveNull`)
- ✓ Obter campos canonicos contem campos conhecidos (`obterCamposCanonicosContemCamposConhecidos`)
- ✓ Obter aliases de campo padrão traz origem padrão (`obterAliasesDeCampoPadraoTrazOrigemPadrao`)
- ✓ Salvar cadastra alias local que passa a aparecer (`salvarCadastraAliasLocalQuePassaAAparecer`)
- ✓ Remover tira apenas o alias local (`removerTiraApenasOAliasLocal`)
- ✓ Remover não afeta alias padrão (`removerNaoAfetaAliasPadrao`)
- ✓ Restaurar padrão limpa só os aliases locais do campo (`restaurarPadraoLimpaSoOsAliasesLocaisDoCampo`)
- ✓ Limpar cache forca releitura do arquivo (`limparCacheForcaReleituraDoArquivo`)

#### CpfUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/CpfUtilsTest.java` · 13 testes · 27 execuções · ✓ todas passaram

- ✓ Normalizar remove máscara (`normalizarRemoveMascara`)
- ✓ Normalizar retorna null para nulo ou vazio (`normalizarRetornaNullParaNuloOuVazio`) · *2 casos*
- ✓ Válido exige 11 dígitos (`isValidoExige11Digitos`) · *4 casos*: "12345678909, true", "123.456.789-09, true", "1234567890, false", "123456789090, false"
- ✓ Válido falso para vazio ou branco (`isValidoFalsoParaVazioOuBranco`) · *2 casos*: "", " "
- ✓ Válido falso para nulo (`isValidoFalsoParaNulo`)
- ✓ Falso para dígitos todos iguais (`isFalsoParaDigitosTodosIguais`) · *4 casos*: "00000000000", "11111111111", "999.999.999-99", "22222222222"
- ✓ Falso falso para CPF com dígitos variados (`isFalsoFalsoParaCpfComDigitosVariados`) · *2 casos*: "12345678909", "01234567890"
- ✓ Falso falso para nulo ou tamanho errado (`isFalsoFalsoParaNuloOuTamanhoErrado`)
- ✓ DV válido para CPF com dígitos verificadores corretos (`isDvValidoParaCpfComDigitosVerificadoresCorretos`) · *4 casos*: "12345678909", "123.456.789-09", "98765432100", "01234567890"
- ✓ DV válido falso com um dígito errado (`isDvValidoFalsoComUmDigitoErrado`) · *3 casos*: "12345678900", "12345678919", "98765432101"
- ✓ DV válido falso para nulo ou tamanho errado (`isDvValidoFalsoParaNuloOuTamanhoErrado`)
- ✓ Chave sem CPF e estável e cabe na coluna de CPF (`chaveSemCpfEEstavelECabeNaColunaDeCpf`)
- ✓ Chave sem CPF por CNS cabe na coluna e distingue cada CNS (`chaveSemCpfPorCnsCabeNaColunaEDistingueCadaCns`)

#### DateUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/DateUtilsTest.java` · 11 testes · 20 execuções · ✓ todas passaram

- ✓ Parse aceita os três formatos suportados (`parseAceitaOsTresFormatosSuportados`) · *3 casos*: "25/12/2024, 2024-12-25", "2024-12-25, 2024-12-25", "25-12-2024, 2024-12-25"
- ✓ Parse aplica trim antes de interpretar (`parseAplicaTrimAntesDeInterpretar`)
- ✓ Parse com formato não reconhecido lanca exceção (`parseComFormatoNaoReconhecidoLancaExcecao`) · *3 casos*: "25122024", "2024/12/25", "não é uma data"
- ✓ Parse com valor nulo ou vazio lanca exceção (`parseComValorNuloOuVazioLancaExcecao`) · *2 casos*

**Modo estrito, dia/mês sem zero, idade e coerência (14/10/2026)**

- ✓ Parse recusa data inexistente em vez de ajustar o dia (`parseRecusaDataInexistenteEmVezDeAjustarODia`) · *5 casos*: "31/02/1990", "31/04/2024", "29/02/2023", "1990-02-30", "00/01/1990"
- ✓ Parse aceita dia e mês sem zero e ano bissexto (`parseAceitaDiaEMesSemZeroEAnoBissexto`)
- ✓ Parse recusa ano com dois dígitos (`parseRecusaAnoComDoisDigitos`)
- ✓ Idade na data usa a data do atendimento e não ade hoje (`idadeNaDataUsaADataDoAtendimentoENaoAdeHoje`)
- ✓ Incoerencia nascimento aponta nascimento depois do atendimento e idade acima de 130 (`incoerenciaNascimentoApontaNascimentoDepoisDoAtendimentoEIdadeAcimaDe130`)

**Data do atendimento fora do esperado (recomendação 4, 19/10/2026)**

- ✓ Data atendimento fora do esperado avisa futura e com mais de dois anos (`dataAtendimentoForaDoEsperadoAvisaFuturaEComMaisDeDoisAnos`)
- ✓ Mensagem data fora do esperado mostra a data ea competência (`mensagemDataForaDoEsperadoMostraADataEACompetencia`)

#### EnderecoUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/EnderecoUtilsTest.java` · 7 testes · 36 execuções · ✓ todas passaram


**Bairro**

- ✓ Bairro tira o prefixo e corta na palavra (`bairroTiraOPrefixoECortaNaPalavra`) · *5 casos*: "'CENTRO', 'CENTRO'", "'Bairro Centro', 'Centro'", "'BAIRRO: Vila Nova', 'Vila Nova'", "'Conjunto Habitacional Professor Fulano de Tal', 'Conjunto Habitacional'", // antes: "...Professo" "'Jardim São Bento', 'Jardim S…
- ✓ Bairro de preenchimento vira vazio (`bairroDePreenchimentoViraVazio`) · *13 casos*: "-", "--", ".", "X", "0", "N/A", "NA", "Não informado", "NAO SE APLICA", "Sem", "Bairro"
- ✓ Bairro igual ao município ignora acento caixa e UF (`bairroIgualAoMunicipioIgnoraAcentoCaixaEUf`)

**Complemento**

- ✓ Complemento que cabe fica como foi digitado sem aviso (`complementoQueCabeFicaComoFoiDigitadoSemAviso`) · *4 casos*: "'APTO 1', 'APTO 1'", "'Casa 2', 'Casa 2'", "'Fundos', 'Fundos'", "'Ap. 101 B', 'Ap. 101 B'"
- ✓ Complemento que não cabe e abreviado e cortado na palavra (`complementoQueNaoCabeEAbreviadoECortadoNaPalavra`) · *5 casos*: "'Apartamento 201', 'AP 201', false", // antes: "Apartament" "'Casa 3 Fundos', 'CS 3 FDS', false", "'Quadra 10 Lote 5', 'QD 10 LT 5', false", "'Apartamento 201 Bloco B', 'AP 201 BL', true", // abreviado "AP 201 BL B" …
- ✓ Complemento de preenchimento vira vazio (`complementoDePreenchimentoViraVazio`) · *7 casos*: "-", "X", "0", "N/A", "não informado"
- ✓ Mensagem complemento cortado mostra o que vai no BPA (`mensagemComplementoCortadoMostraOQueVaiNoBpa`)

#### EspecialidadeUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/EspecialidadeUtilsTest.java` · 10 testes · 10 execuções · ✓ todas passaram

Cobre `EspecialidadeUtils` — remoção do prefixo "Médico"/"Médica" que algumas planilhas trazem antes do nome real da especialidade.

- ✓ Normalizar remove prefixo médico (`normalizarRemovePrefixoMedico`)
- ✓ Normalizar remove prefixo medica feminino (`normalizarRemovePrefixoMedicaFeminino`)
- ✓ Normalizar ignora acento caixa no prefixo (`normalizarIgnoraAcentoCaixaNoPrefixo`)
- ✓ Normalizar preserva grafia original do restante (`normalizarPreservaGrafiaOriginalDoRestante`)
- ✓ Normalizar sem prefixo devolve texto com trim (`normalizarSemPrefixoDevolveTextoComTrim`)
- ✓ Normalizar com nulo devolve nulo (`normalizarComNuloDevolveNulo`)
- ✓ Padronizar remove prefixo coloca em maiusculas e junta espacos (`padronizarRemovePrefixoColocaEmMaiusculasEJuntaEspacos`)
- ✓ Padronizar com nulo ou vazio devolve nulo (`padronizarComNuloOuVazioDevolveNulo`)
- ✓ Usa procedimento fixo para nutricionista e psicologo com ou sem prefixo e caixa (`usaProcedimentoFixoParaNutricionistaEPsicologoComOuSemPrefixoECaixa`)
- ✓ Normalizar não remove prefixo no meio do texto (`normalizarNaoRemovePrefixoNoMeioDoTexto`)

#### EtniaUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/EtniaUtilsTest.java` · 12 testes · 12 execuções · ✓ todas passaram

Cobre `EtniaUtils` — resolução por nome via o CSV do classpath, sem qualquer chamada de rede (tabela é estática, sem fallback externo).

- ✓ Resolver com nome conhecido devolve código de quatro caracteres (`resolverComNomeConhecidoDevolveCodigoDeQuatroCaracteres`)
- ✓ Resolver ignora acento e caixa (`resolverIgnoraAcentoECaixa`)
- ✓ Resolver aceita o código digitado conferido contra a tabela (`resolverAceitaOCodigoDigitadoConferidoContraATabela`)
- ✓ Resolver tolera parênteses na celula (`resolverToleraParentesesNaCelula`)
- ✓ Resolver não inventa grafia fora da tabela (`resolverNaoInventaGrafiaForaDaTabela`)
- ✓ Resolver com nome desconhecido devolve null (`resolverComNomeDesconhecidoDevolveNull`)
- ✓ Resolver com nulo ou vazio devolve null (`resolverComNuloOuVazioDevolveNull`)
- ✓ Resolver com nome duplicado entre faixas usa código da faixa principal (`resolverComNomeDuplicadoEntreFaixasUsaCodigoDaFaixaPrincipal`)
- ✓ Resolver aceita nome principal e nomes alternativos entre parênteses (`resolverAceitaNomePrincipalENomesAlternativosEntreParenteses`)
- ✓ Resolver aceita nome completo com parênteses (`resolverAceitaNomeCompletoComParenteses`)
- ✓ Normalizar remove acentos e maiusculiza (`normalizarRemoveAcentosEMaiusculiza`)
- ✓ Normalizar com nulo devolve vazio (`normalizarComNuloDevolveVazio`)

#### IbgeUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/IbgeUtilsTest.java` · 19 testes · 19 execuções · ✓ todas passaram

Cobre só os caminhos sem rede de `IbgeUtils` — nenhum teste aqui deve disparar uma chamada real à API ViaCEP. Não há `limparCache()` nesta classe (o cache de CEP é estático e compartilhado entre todos os testes da suíte), então cada teste que grava no cache usa um CEP fake exclusivo.

- ✓ Normalizar remove acentos e maiusculiza (`normalizarRemoveAcentosEMaiusculiza`)
- ✓ Normalizar retorna vazio para nulo (`normalizarRetornaVazioParaNulo`)
- ✓ Buscar por nome encontra município conhecido (`buscarPorNomeEncontraMunicipioConhecido`)
- ✓ Buscar por nome ignora acento e caixa (`buscarPorNomeIgnoraAcentoECaixa`)
- ✓ Buscar por nome tolera sigla da UF e espacos repetidos (`buscarPorNomeToleraSiglaDaUfEEspacosRepetidos`)
- ✓ Nome município devolve nome oficial e UF de qualquer município do brasil (`nomeMunicipioDevolveNomeOficialEUfDeQualquerMunicipioDoBrasil`)
- ✓ Resolver pelo CEP informa no aviso o município encontrado (`resolverPeloCepInformaNoAvisoOMunicipioEncontrado`)
- ✓ Buscar por nome com município de outra UF continua não encontrado (`buscarPorNomeComMunicipioDeOutraUfContinuaNaoEncontrado`)
- ✓ Buscar por nome com município desconhecido devolve null (`buscarPorNomeComMunicipioDesconhecidoDevolveNull`)
- ✓ Resolver com nome resolvivel nunca tenta o caminho por CEP (`resolverComNomeResolvivelNuncaTentaOCaminhoPorCep`)
- ✓ Resolver sem nome usa cache já carregado sem rede (`resolverSemNomeUsaCacheJaCarregadoSemRede`)
- ✓ Resolver sem nome e sem CEP conhecido devolve aviso sem código (`resolverSemNomeESemCepConhecidoDevolveAvisoSemCodigo`)
- ✓ Pre carregar cache db com mapa nulo ou vazio não lanca exceção (`preCarregarCacheDbComMapaNuloOuVazioNaoLancaExcecao`)

**APIs de CEP: ViaCEP com reservas (BrasilAPI, OpenCEP) — HTTP simulado, sem rede**

- ✓ Buscar por CEP usa via CEP quando responde (`buscarPorCepUsaViaCepQuandoResponde`)
- ✓ Buscar por CEP com via CEP fora do ar usa brasil API (`buscarPorCepComViaCepForaDoArUsaBrasilApi`)
- ✓ Buscar por CEP com via CEP e brasil API fora do ar usa open CEP (`buscarPorCepComViaCepEBrasilApiForaDoArUsaOpenCep`)
- ✓ Buscar por CEP não encontrado no via CEP tenta as reservas (`buscarPorCepNaoEncontradoNoViaCepTentaAsReservas`)
- ✓ Buscar por CEP não encontrado em todas guarda resultado negativo e não consulta de novo (`buscarPorCepNaoEncontradoEmTodasGuardaResultadoNegativoENaoConsultaDeNovo`)
- ✓ Buscar por CEP com todas fora do ar não guarda resultado negativo (`buscarPorCepComTodasForaDoArNaoGuardaResultadoNegativo`)

#### LogradouroUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/LogradouroUtilsTest.java` · 10 testes · 39 execuções · ✓ todas passaram

- ✓ Resolver detecta prefixo e remove do endereço (`resolverDetectaPrefixoERemoveDoEndereco`) · *8 casos*: "'RUA DAS FLORES', '081', 'DAS FLORES'", "'Rua das Flores', '081', 'das Flores'", "'AVENIDA BRASIL', '008', 'BRASIL'", "'AV. BRASIL', '008', 'BRASIL'", "'AV BRASIL', '008', 'BRASIL'", "'TRAVESSA DA PAZ', '100', 'DA PA…
- ✓ Resolver reconhece os tipos da tabela DNE e abreviações (`resolverReconheceOsTiposDaTabelaDneEAbreviacoes`) · *12 casos*: // Tipos da tabela DNE (15/10/2026) — antes iam como Rua (081) com o tipo dentro do endereço "'Rodovia BR-163', '090', 'BR-163'", "'Alameda dos Ipês', '004', 'dos Ipês'", "'Praça Ary Coelho', '065', 'Ary Coelho'", "'E…
- ✓ Resolver prefere o tipo mais longo só quando sobra um nome (`resolverPrefereOTipoMaisLongoSoQuandoSobraUmNome`)
- ✓ Resolver não confunde palavra que comeca com o tipo (`resolverNaoConfundePalavraQueComecaComOTipo`)
- ✓ Resolver sem prefixo conhecido mantem endereço original trimado (`resolverSemPrefixoConhecidoMantemEnderecoOriginalTrimado`)
- ✓ Resolver com endereço nulo devolve código nulo e endereço nulo (`resolverComEnderecoNuloDevolveCodigoNuloEEnderecoNulo`)
- ✓ Resolver com endereço vazio devolve código nulo e endereço vazio (`resolverComEnderecoVazioDevolveCodigoNuloEEnderecoVazio`)

**Coluna "Cód. Logradouro"**

- ✓ Código da coluna aceita código da tabela ou nome do tipo (`codigoDaColunaAceitaCodigoDaTabelaOuNomeDoTipo`) · *12 casos*: "'081', '081'", "'81', '081'", "'8', '008'", "'090', '090'", "'Rua', '081'", "'R', '081'", "'R.', '081'", "'Avenida', '008'", "'Av', '008'", "'travessa', '100'", "'Rodovia', '090'", "'Praça', '065'"
- ✓ Código da coluna recusa código fora da tabela e texto desconhecido (`codigoDaColunaRecusaCodigoForaDaTabelaETextoDesconhecido`)
- ✓ Campo desativado não e reconhecido como tipo de logradouro (`campoDesativadoNaoEReconhecidoComoTipoDeLogradouro`)

#### NomePacienteUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/NomePacienteUtilsTest.java` · 4 testes · 14 execuções · ✓ todas passaram

Recomendações 29 e 30 (19/10/2026).

- ✓ Padronizar deixa em maiusculas com espacos reduzidos e mantem acentos (`padronizarDeixaEmMaiusculasComEspacosReduzidosEMantemAcentos`)
- ✓ Cortado no BPA mostra os trinta primeiros caracteres (`cortadoNoBpaMostraOsTrintaPrimeirosCaracteres`)
- ✓ Motivo suspeito reconhece nomes que não parecem completos (`motivoSuspeitoReconheceNomesQueNaoParecemCompletos`) · *7 casos*: "TESTE, com uma palavra só", "MARIA 2, com números", "-, sem letras", "'..', sem letras", "MARIA @ SILVA, com símbolos", "RN DE MARIA SILVA, recém-nascido sem nome", "rn, recém-nascido sem nome"
- ✓ Motivo suspeito aceita nomes comuns (`motivoSuspeitoAceitaNomesComuns`) · *5 casos*: "MARIA DA SILVA", "José D'Ávila", "ANA-LUÍSA SOUZA", "JOÃO S. PEREIRA", "RONALDO LIMA"

#### NumeroUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/NumeroUtilsTest.java` · 4 testes · 38 execuções · ✓ todas passaram

- ✓ Normalizar vazio ou sem número vira SN sem aviso (`normalizarVazioOuSemNumeroViraSnSemAviso`) · *21 casos*: " ", "S/N", "s/n", "S N", "SN", "S.N.", "S/Nº", "SEM NUMERO", "Sem número", "SEM Nº", "SEM N", "sem", "0", "00", "000", "-", " -- ", "–", "—"
- ✓ Normalizar número no padrão fica como veio sem aviso (`normalizarNumeroNoPadraoFicaComoVeioSemAviso`) · *6 casos*: "'100', '100'", "' 1500 ', '1500'", "'12A', '12A'", "'12-B', '12-B'", "'12 b', '12 b'", "'99999', '99999'"
- ✓ Normalizar outro valor curto fica como veio com aviso (`normalizarOutroValorCurtoFicaComoVeioComAviso`) · *5 casos*: "X", "N/A", "NT", "LT 5", "A12"
- ✓ Normalizar mais de cinco caracteres fica com o número inicial ou SN com aviso (`normalizarMaisDeCincoCaracteresFicaComONumeroInicialOuSnComAviso`) · *6 casos*: "'100 apto 201', '100'", // antes: "100 a" "'Km 12,5', '12'", // antes: "Km 12" "'Nº 1500 fundos', '1500'", "'123456', 'SN'", // antes: "12345" (outro número) "'Lote 10 Quadra 5', 'SN'", // antes: "Lote " "'SEM NUMERO…

#### RacaUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/RacaUtilsTest.java` · 11 testes · 11 execuções · ✓ todas passaram

Cobre `RacaUtils` — a régua de reconhecimento compartilhada entre validação (ValidacaoPlanilhaService/AtendimentoProcessor) e geração do BPA-I (GeradorBPAiService).

- ✓ Resolver código reconhece as cinco categorias oficiais (`resolverCodigoReconheceAsCincoCategoriasOficiais`)
- ✓ Resolver código ignora acento caixa e espacos (`resolverCodigoIgnoraAcentoCaixaEEspacos`)
- ✓ Resolver código tolera variações de genero por substring (`resolverCodigoToleraVariacoesDeGeneroPorSubstring`)
- ✓ Resolver código com nulo ou vazio devolve null (`resolverCodigoComNuloOuVazioDevolveNull`)
- ✓ Resolver código com texto não reconhecido devolve null (`resolverCodigoComTextoNaoReconhecidoDevolveNull`)
- ✓ Resolver código não resolve o código descontinuado 99 (`resolverCodigoNaoResolveOCodigoDescontinuado99`)
- ✓ Resolver código reconhece por palavra inteira e não por trecho (`resolverCodigoReconhecePorPalavraInteiraENaoPorTrecho`)
- ✓ Resolver código recusa texto ambíguo ou com negação (`resolverCodigoRecusaTextoAmbiguoOuComNegacao`)
- ✓ Resolver código aceita o código do BPA (`resolverCodigoAceitaOCodigoDoBpa`)
- ✓ Mensagem não reconhecida explica negra e pede grafia nos demais (`mensagemNaoReconhecidaExplicaNegraEPedeGrafiaNosDemais`)
- ✓ Indígena true só para raça indígena (`isIndigenaTrueSoParaRacaIndigena`)

#### StringUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/StringUtilsTest.java` · 18 testes · 18 execuções · ✓ todas passaram

- ✓ Limitar tamanho corta quando excede (`limitarTamanhoCortaQuandoExcede`)
- ✓ Limitar tamanho mantem quando menor ou igual (`limitarTamanhoMantemQuandoMenorOuIgual`)
- ✓ Limitar tamanho trima antes de cortar (`limitarTamanhoTrimaAntesDeCortar`)
- ✓ Limitar tamanho retorna null para null (`limitarTamanhoRetornaNullParaNull`)
- ✓ Limitar tamanho ou vazio retorna vazio para null (`limitarTamanhoOuVazioRetornaVazioParaNull`)
- ✓ Limitar tamanho ou vazio corta quando excede (`limitarTamanhoOuVazioCortaQuandoExcede`)
- ✓ Separar código e nome com hífen comum (`separarCodigoENomeComHifenComum`)
- ✓ Separar código e nome com en dash (`separarCodigoENomeComEnDash`)
- ✓ Separar código e nome com em dash (`separarCodigoENomeComEmDash`)
- ✓ Separar código e nome sem separador devolve nome completo e nulo no código (`separarCodigoENomeSemSeparadorDevolveNomeCompletoENuloNoCodigo`)
- ✓ Separar código e nome com valor nulo devolve nulo e nulo (`separarCodigoENomeComValorNuloDevolveNuloENulo`)
- ✓ Separar código e nome sem espacos e mantem hífen do nome (`separarCodigoENomeSemEspacosEMantemHifenDoNome`)
- ✓ Separar código e nome com texto antes do hífen trata a celula inteira como nome (`separarCodigoENomeComTextoAntesDoHifenTrataACelulaInteiraComoNome`)
- ✓ Separar código e nome com código maior que dez dígitos trata como nome (`separarCodigoENomeComCodigoMaiorQueDezDigitosTrataComoNome`)
- ✓ Estabelecimento não informado para vazio separador ou zero (`estabelecimentoNaoInformadoParaVazioSeparadorOuZero`)
- ✓ Separar código e nome só com código devolve código e nome nulo (`separarCodigoENomeSoComCodigoDevolveCodigoENomeNulo`)
- ✓ Separar especialidade e médico com separador (`separarEspecialidadeEMedicoComSeparador`)
- ✓ Separar especialidade e médico sem separador devolve especialidade completa e nulo no médico (`separarEspecialidadeEMedicoSemSeparadorDevolveEspecialidadeCompletaENuloNoMedico`)

#### TelefoneUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/TelefoneUtilsTest.java` · 4 testes · 19 execuções · ✓ todas passaram

- ✓ Normalizar fica com o primeiro número com DDD (`normalizarFicaComOPrimeiroNumeroComDdd`) · *11 casos*: "(67) 99999-9999\|67999999999", "67999999999\|67999999999", "(67) 3333-3333\|6733333333", "+55 67 99999-9999\|67999999999", // antes: 55679999999 "5567999999999\|67999999999", "0 67 99999-9999\|67999999999", // antes: 06799…
- ✓ Normalizar sem DDD não adivinha o DDD (`normalizarSemDddNaoAdivinhaODdd`) · *2 casos*: "99999-9999", "3333-3333"
- ✓ Normalizar com texto ou tamanho errado e inválido (`normalizarComTextoOuTamanhoErradoEInvalido`) · *3 casos*: "NÃO TEM", "123", "679999999999999"
- ✓ Normalizar vazio não e problema (`normalizarVazioNaoEProblema`) · *3 casos*: " "

#### TextoUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/TextoUtilsTest.java` · 3 testes · 5 execuções · ✓ todas passaram

- ✓ Normalizar remove acentos maiusculiza e trima (`normalizarRemoveAcentosMaiusculizaETrima`)
- ✓ Normalizar mantem texto já normalizado igual (`normalizarMantemTextoJaNormalizadoIgual`)
- ✓ Normalizar retorna vazio para nulo ou branco (`normalizarRetornaVazioParaNuloOuBranco`) · *3 casos*: " "

#### TimeUtilsTest

`src/test/java/br/gov/ses/fillbpai/util/TimeUtilsTest.java` · 6 testes · 11 execuções · ✓ todas passaram

- ✓ Parse aceita os formatos suportados (`parseAceitaOsFormatosSuportados`) · *4 casos*: "08:30, 08:30", "8:30, 08:30", "08:30:00, 08:30", "0830, 08:30"
- ✓ Parse substitui ponto por dois pontos (`parseSubstituiPontoPorDoisPontos`)
- ✓ Parse aceita formato com h (`parseAceitaFormatoComH`)
- ✓ Parse com formato com h fora do intervalo continua inválido (`parseComFormatoComHForaDoIntervaloContinuaInvalido`)
- ✓ Parse com formato não reconhecido lanca exceção (`parseComFormatoNaoReconhecidoLancaExcecao`) · *2 casos*: "25h30", "não é uma hora"
- ✓ Parse com valor nulo ou vazio lanca exceção (`parseComValorNuloOuVazioLancaExcecao`) · *2 casos*

### Pacote `service`

Análise da planilha, importação, geração do BPA-I, logs e janela Sobre.

#### AgrupamentoValidacaoTest

`src/test/java/br/gov/ses/fillbpai/service/AgrupamentoValidacaoTest.java` · 6 testes · 6 execuções · ✓ todas passaram

- ✓ Agrupar separa por tipo com título legivel e explicação (`agruparSeparaPorTipoComTituloLegivelEExplicacao`)
- ✓ Agrupar junta itens com o mesmo valor e lista as linhas (`agruparJuntaItensComOMesmoValorEListaAsLinhas`)
- ✓ Agrupar sem valor usa o detalhe como item (`agruparSemValorUsaODetalheComoItem`)
- ✓ Agrupar ordena erros antes de avisos e pela prioridade do tipo (`agruparOrdenaErrosAntesDeAvisosEPelaPrioridadeDoTipo`)
- ✓ Agrupar com tipo desconhecido usa o proprio código como título (`agruparComTipoDesconhecidoUsaOProprioCodigoComoTitulo`)
- ✓ Descrever linhas no singular no plural e limita em dez (`descreverLinhasNoSingularNoPluralELimitaEmDez`)

#### AtendimentoImportacaoServiceTest

`src/test/java/br/gov/ses/fillbpai/service/AtendimentoImportacaoServiceTest.java` · 56 testes · 56 execuções · ✓ todas passaram

Teste fim a fim de `AtendimentoImportacaoService` contra a persistence unit de teste `bpaPU-test` (H2 em memória). Usa "CAMPO GRANDE" como município (resolve por nome via CSV, sem rede) e "RODRIGO SILVA GRILO" como médico real cadastrado em `dados/medicos_cns.csv` (CNS 700207960618529) para o teste de resolução de CNS — leitura real, sem mutar o arquivo.

- ✓ Importar cria paciente médico estabelecimento e atendimento (`importarCriaPacienteMedicoEstabelecimentoEAtendimento`)
- ✓ Importar com hora não reconhecida importa a linha sem hora e com aviso (`importarComHoraNaoReconhecidaImportaALinhaSemHoraEComAviso`)
- ✓ Importar mesmo CPF com nome diferente importa tudo com aviso e fica com o nome mais recente (`importarMesmoCpfComNomeDiferenteImportaTudoComAvisoEFicaComONomeMaisRecente`)

**Estabelecimento: código sempre numérico**

- ✓ Importar com nome com hífen e sem código não cancela a importação (`importarComNomeComHifenESemCodigoNaoCancelaAImportacao`)
- ✓ Importar com estabelecimento não informado avisa no log e não cria nada (`importarComEstabelecimentoNaoInformadoAvisaNoLogENaoCriaNada`)
- ✓ Importar com texto curto antes do hífen não cria estabelecimento falso (`importarComTextoCurtoAntesDoHifenNaoCriaEstabelecimentoFalso`)
- ✓ Importar com celula só com código cadastrado vincula e mantem o nome (`importarComCelulaSoComCodigoCadastradoVinculaEMantemONome`)
- ✓ Importar com código sem nome não apaga o nome cadastrado (`importarComCodigoSemNomeNaoApagaONomeCadastrado`)
- ✓ Importar com celula só com código não cadastrado fica sem estabelecimento com aviso (`importarComCelulaSoComCodigoNaoCadastradoFicaSemEstabelecimentoComAviso`)
- ✓ Reimportar sem CNS ou com CNS inválido mantem o CNS já cadastrado (`reimportarSemCnsOuComCnsInvalidoMantemOCnsJaCadastrado`)
- ✓ Deve trocar CNS só quando a linha traz um CNS não pior que o gravado (`deveTrocarCnsSoQuandoALinhaTrazUmCnsNaoPiorQueOGravado`)

**Paciente sem CPF**

- ✓ Importar paciente sem CPF com coluna sim grava com chave interna e reimportar não duplica (`importarPacienteSemCpfComColunaSimGravaComChaveInternaEReimportarNaoDuplica`)
- ✓ Importar paciente sem CPF usa o CNS como chave (`importarPacienteSemCpfUsaOCnsComoChave`)
- ✓ Importar paciente sem CPF migra a chave antiga sem duplicar (`importarPacienteSemCpfMigraAChaveAntigaSemDuplicar`)
- ✓ Importar homônimo com CNS diferente não herda o paciente da chave antiga (`importarHomonimoComCnsDiferenteNaoHerdaOPacienteDaChaveAntiga`)

**Município / IBGE**

- ✓ Importar com município sem IBGE bloqueia a planilha inteira sem gravar nada (`importarComMunicipioSemIbgeBloqueiaAPlanilhaInteiraSemGravarNada`)
- ✓ Importar com município fora da tabela encontrado pelo CEP grava e avisa o município encontrado (`importarComMunicipioForaDaTabelaEncontradoPeloCepGravaEAvisaOMunicipioEncontrado`)

**Especialidade padronizada**

- ✓ Importar grava especialidade em maiusculas para grafias diferentes virarem a mesma (`importarGravaEspecialidadeEmMaiusculasParaGrafiasDiferentesViraremAMesma`)

**Tipo de serviço vazio**

- ✓ Importar com tipo de serviço vazio bloqueia a planilha (`importarComTipoDeServicoVazioBloqueiaAPlanilha`)

**Médico sem CPF / erro de gravação**

- ✓ Importar com médico sem CPF bloqueia a planilha inteira (`importarComMedicoSemCpfBloqueiaAPlanilhaInteira`)
- ✓ Importar com erro de gravação no banco cancela a importação em vez de reportar sucesso (`importarComErroDeGravacaoNoBancoCancelaAImportacaoEmVezDeReportarSucesso`)

**CNS do profissional: nome + reserva pelo CPF**

- ✓ Importar guarda no médico o CNS encontrado pelo nome (`importarGuardaNoMedicoOCnsEncontradoPeloNome`)
- ✓ Importar com grafia não cadastrada herda CNS do mesmo CPF com aviso (`importarComGrafiaNaoCadastradaHerdaCnsDoMesmoCpfComAviso`)
- ✓ Importar herda CNS do mesmo CPF mesmo quando a grafia cadastrada vem depois na planilha (`importarHerdaCnsDoMesmoCpfMesmoQuandoAGrafiaCadastradaVemDepoisNaPlanilha`)
- ✓ Importar com grafia não cadastrada e CPF sem CNS conhecido mantem aviso de não encontrado (`importarComGrafiaNaoCadastradaECpfSemCnsConhecidoMantemAvisoDeNaoEncontrado`)
- ✓ Importar com CNS do nome diferente do CNS já conhecido para o CPF usa o nome e avisa (`importarComCnsDoNomeDiferenteDoCnsJaConhecidoParaOCpfUsaONomeEAvisa`)
- ✓ Importar com estabelecimento sem código reaproveita estabelecimento já cadastrado pelo nome (`importarComEstabelecimentoSemCodigoReaproveitaEstabelecimentoJaCadastradoPeloNome`)
- ✓ Importar com estabelecimento sem código e nome desconhecido fica sem estabelecimento (`importarComEstabelecimentoSemCodigoENomeDesconhecidoFicaSemEstabelecimento`)
- ✓ Importar persiste texto da etnia no paciente (`importarPersisteTextoDaEtniaNoPaciente`)
- ✓ Importar sem coluna situação rua deixa campo nulo no paciente (`importarSemColunaSituacaoRuaDeixaCampoNuloNoPaciente`)
- ✓ Importar com coluna situação rua persiste valor normalizado no atendimento (`importarComColunaSituacaoRuaPersisteValorNormalizadoNoAtendimento`)
- ✓ Situação de rua fica no atendimento e não muda os atendimentos anteriores (`situacaoDeRuaFicaNoAtendimentoENaoMudaOsAtendimentosAnteriores`)
- ✓ Importar sem coluna paciente sem CPF deixa campo nulo no atendimento (`importarSemColunaPacienteSemCpfDeixaCampoNuloNoAtendimento`)
- ✓ Importar com coluna paciente sem CPF persiste valor normalizado no atendimento (`importarComColunaPacienteSemCpfPersisteValorNormalizadoNoAtendimento`)
- ✓ Importar paciente sem CPF sem CNS bloqueia a planilha (`importarPacienteSemCpfSemCnsBloqueiaAPlanilha`)
- ✓ Importar com CPF preenchido e paciente sem CPF sim rejeita a linha (`importarComCpfPreenchidoEPacienteSemCpfSimRejeitaALinha`)
- ✓ Importar com especialidade sem cabeçalho bloqueia pela estrutura sem gravar (`importarComEspecialidadeSemCabecalhoBloqueiaPelaEstruturaSemGravar`)
- ✓ Importar CID com descrição longa grava só o código (`importarCidComDescricaoLongaGravaSoOCodigo`)
- ✓ Importar telefone com varios números grava só o primeiro e avisa sem DDD (`importarTelefoneComVariosNumerosGravaSoOPrimeiroEAvisaSemDdd`)
- ✓ Importar logradouro pela tabela DNE e endereço cortado na palavra (`importarLogradouroPelaTabelaDneEEnderecoCortadoNaPalavra`)
- ✓ Importar colunas opcionais com celula vazia grava em branco com aviso (`importarColunasOpcionaisComCelulaVaziaGravaEmBrancoComAviso`)
- ✓ Importar sem CEP bloqueia a planilha (`importarSemCepBloqueiaAPlanilha`)
- ✓ Importar sem coluna tipo zona não bloqueia (`importarSemColunaTipoZonaNaoBloqueia`)
- ✓ Importar sem nascimento ou com CID inválido bloqueia (`importarSemNascimentoOuComCidInvalidoBloqueia`)
- ✓ Importar planilha legado com celula sem separador bloqueia (`importarPlanilhaLegadoComCelulaSemSeparadorBloqueia`)
- ✓ Importar planilha legado separa especialidade e médico ao persistir (`importarPlanilhaLegadoSeparaEspecialidadeEMedicoAoPersistir`)
- ✓ Importar duas vezes a mesma linha não duplica atendimento (`importarDuasVezesAMesmaLinhaNaoDuplicaAtendimento`)
- ✓ Importar não herda folha de atendimento anterior (`importarNaoHerdaFolhaDeAtendimentoAnterior`)
- ✓ Importar resolve CNS do profissional pelo nome quando conhecido (`importarResolveCnsDoProfissionalPeloNomeQuandoConhecido`)
- ✓ Importar com médico desconhecido gera aviso de CNS não encontrado (`importarComMedicoDesconhecidoGeraAvisoDeCnsNaoEncontrado`)
- ✓ Importar com coluna obrigatória ausente lanca exceção com mensagem de estrutura inválida (`importarComColunaObrigatoriaAusenteLancaExcecaoComMensagemDeEstruturaInvalida`)
- ✓ Importar com linha inválida bloqueia a planilha inteira sem gravar nada (`importarComLinhaInvalidaBloqueiaAPlanilhaInteiraSemGravarNada`)

**Conferência de linhas / duplicidade dentro da planilha**

- ✓ Importar informa a conferência de linhas novas e atualizadas (`importarInformaAConferenciaDeLinhasNovasEAtualizadas`)
- ✓ Importar ignora linhas vazias na contagem (`importarIgnoraLinhasVaziasNaContagem`)
- ✓ Importar com linha repetida no mesmo horário bloqueia a planilha (`importarComLinhaRepetidaNoMesmoHorarioBloqueiaAPlanilha`)
- ✓ Importar mesmo atendimento com horário diferente grava os dois com aviso de suspeita (`importarMesmoAtendimentoComHorarioDiferenteGravaOsDoisComAvisoDeSuspeita`)

#### AtendimentoProcessorTest

`src/test/java/br/gov/ses/fillbpai/service/AtendimentoProcessorTest.java` · 88 testes · 88 execuções · ✓ todas passaram

`AtendimentoProcessor` só expõe `processar(dto)` publicamente — os métodos internos (definirSigtap, validarCep, converterDatas, etc.) são privados e testados indiretamente através dele, verificando o efeito no DTO e/ou a exceção lançada.


**SIGTAP**

- ✓ Processar define SIGTAP para teleconsulta (`processarDefineSigtapParaTeleconsulta`)
- ✓ Processar define SIGTAP para teleinterconsulta (`processarDefineSigtapParaTeleinterconsulta`)
- ✓ Processar com tipo serviço desconhecido lanca exceção (`processarComTipoServicoDesconhecidoLancaExcecao`)
- ✓ Processar com tipo serviço vazio lanca exceção (`processarComTipoServicoVazioLancaExcecao`)
- ✓ Processar com tipo serviço em branco lanca exceção (`processarComTipoServicoEmBrancoLancaExcecao`)
- ✓ Processar com tipo serviço vazio para nutricionista ou psicologo mantem comportamento atual (`processarComTipoServicoVazioParaNutricionistaOuPsicologoMantemComportamentoAtual`)
- ✓ Processar com tipo serviço preenchido para nutricionista não gera aviso de tipo vazio (`processarComTipoServicoPreenchidoParaNutricionistaNaoGeraAvisoDeTipoVazio`)

**ESTABELECIMENTO**

- ✓ Processar separa código e nome do estabelecimento (`processarSeparaCodigoENomeDoEstabelecimento`)
- ✓ Processar sem separador mantem nome do estabelecimento e não define código (`processarSemSeparadorMantemNomeDoEstabelecimentoENaoDefineCodigo`)
- ✓ Processar sem separador no estabelecimento gera aviso (`processarSemSeparadorNoEstabelecimentoGeraAviso`)
- ✓ Processar com estabelecimento não informado gera aviso e deixa sem estabelecimento (`processarComEstabelecimentoNaoInformadoGeraAvisoEDeixaSemEstabelecimento`)
- ✓ Processar com separador no estabelecimento não gera aviso (`processarComSeparadorNoEstabelecimentoNaoGeraAviso`)

**ESPECIALIDADE/MÉDICO (formato legado)**

- ✓ Processar com especialidade e médico combinados legado separa os dois campos (`processarComEspecialidadeEMedicoCombinadosLegadoSeparaOsDoisCampos`)
- ✓ Processar com especialidade e médico diferentes não altera nada mesmo com hífen no nome (`processarComEspecialidadeEMedicoDiferentesNaoAlteraNadaMesmoComHifenNoNome`)

**ESPECIALIDADE — prefixo "Médico"/"Médica"**

- ✓ Processar remove prefixo médico da especialidade (`processarRemovePrefixoMedicoDaEspecialidade`)
- ✓ Processar sem prefixo médico só padroniza a especialidade em maiusculas (`processarSemPrefixoMedicoSoPadronizaAEspecialidadeEmMaiusculas`)

**SEXO**

- ✓ Processar normaliza sexo por extenso para código de uma letra (`processarNormalizaSexoPorExtensoParaCodigoDeUmaLetra`)
- ✓ Processar normaliza sexo abreviado independente de caixa (`processarNormalizaSexoAbreviadoIndependenteDeCaixa`)
- ✓ Processar com sexo não reconhecido lanca exceção (`processarComSexoNaoReconhecidoLancaExcecao`)
- ✓ Processar sem sexo lanca exceção (`processarSemSexoLancaExcecao`)
- ✓ Processar sexo aceita as formas usuais (`processarSexoAceitaAsFormasUsuais`)

**CEP / CPF**

- ✓ Processar com CEP de tamanho inválido lanca exceção (`processarComCepDeTamanhoInvalidoLancaExcecao`)
- ✓ Processar paciente em situação de rua sem CEP aceita com aviso e CEP em branco (`processarPacienteEmSituacaoDeRuaSemCepAceitaComAvisoECepEmBranco`)
- ✓ Processar paciente em situação de rua com CEP segue normal (`processarPacienteEmSituacaoDeRuaComCepSegueNormal`)
- ✓ Processar sem CEP lanca exceção (`processarSemCepLancaExcecao`)
- ✓ Processar com município de MS e CEP de outro estado gera aviso (`processarComMunicipioDeMsECepDeOutroEstadoGeraAviso`)
- ✓ Processar com município de MS e CEP de MS não gera aviso de CEP (`processarComMunicipioDeMsECepDeMsNaoGeraAvisoDeCep`)
- ✓ Processar com CPF de tamanho inválido lanca exceção (`processarComCpfDeTamanhoInvalidoLancaExcecao`)
- ✓ Processar sem CPF do médico lanca exceção (`processarSemCpfDoMedicoLancaExcecao`)
- ✓ Processar com CPF do médico de tamanho inválido lanca exceção (`processarComCpfDoMedicoDeTamanhoInvalidoLancaExcecao`)

**Município**

- ✓ Processar sem município lanca exceção (`processarSemMunicipioLancaExcecao`)

**CBO e especialidade**

- ✓ Processar normaliza máscara do CBO (`processarNormalizaMascaraDoCbo`)
- ✓ Processar sem CBO lanca exceção (`processarSemCboLancaExcecao`)
- ✓ Processar com CBO sem seis dígitos lanca exceção (`processarComCboSemSeisDigitosLancaExcecao`)
- ✓ Processar sem especialidade lanca exceção (`processarSemEspecialidadeLancaExcecao`)
- ✓ Processar normaliza máscara do CPF do médico (`processarNormalizaMascaraDoCpfDoMedico`)
- ✓ Processar sem CPF com paciente sem CPF sim não lanca exceção (`processarSemCpfComPacienteSemCpfSimNaoLancaExcecao`)
- ✓ Processar com CPF preenchido e paciente sem CPF sim lanca exceção (`processarComCpfPreenchidoEPacienteSemCpfSimLancaExcecao`)
- ✓ Processar sem CPF sem coluna paciente sem CPF lanca exceção (`processarSemCpfSemColunaPacienteSemCpfLancaExcecao`)
- ✓ Processar com CPF do paciente com dígito verificador errado lanca exceção (`processarComCpfDoPacienteComDigitoVerificadorErradoLancaExcecao`)
- ✓ Processar com CPF do médico com dígito verificador errado ou repetido lanca exceção (`processarComCpfDoMedicoComDigitoVerificadorErradoOuRepetidoLancaExcecao`)
- ✓ Processar com CPF falso lanca exceção (`processarComCpfFalsoLancaExcecao`)
- ✓ Processar normaliza máscara de CPF e CEP (`processarNormalizaMascaraDeCpfECep`)

**RAÇA (obrigatória, ERRO bloqueante)**

- ✓ Processar sem raça lanca exceção (`processarSemRacaLancaExcecao`)
- ✓ Processar com raça em branco lanca exceção (`processarComRacaEmBrancoLancaExcecao`)
- ✓ Processar com raça não reconhecida lanca exceção (`processarComRacaNaoReconhecidaLancaExcecao`)
- ✓ Processar com variação de grafia da raça não lanca exceção (`processarComVariacaoDeGrafiaDaRacaNaoLancaExcecao`)

**CAMPOS OBRIGATÓRIOS**

- ✓ Processar sem paciente lanca exceção (`processarSemPacienteLancaExcecao`)
- ✓ Processar sem CPF do paciente lanca exceção (`processarSemCpfDoPacienteLancaExcecao`)
- ✓ Processar sem data de agendamento lanca exceção (`processarSemDataDeAgendamentoLancaExcecao`)

**CNS (aviso, não bloqueia)**

- ✓ Processar com CNS de quinze dígitos não gera aviso (`processarComCnsDeQuinzeDigitosNaoGeraAviso`)
- ✓ Processar com CNS curto gera aviso mas não bloqueia (`processarComCnsCurtoGeraAvisoMasNaoBloqueia`)
- ✓ Processar com CNS longo gera aviso de CNS inválido e não grava o valor (`processarComCnsLongoGeraAvisoDeCnsInvalidoENaoGravaOValor`)

**RAÇA INDÍGENA: etnia obrigatória (erro, decisão de 13/10/2026)**

- ✓ Processar com raça indígena sem etnia rejeita a linha (`processarComRacaIndigenaSemEtniaRejeitaALinha`)
- ✓ Processar com raça não indígena não gera aviso de raça (`processarComRacaNaoIndigenaNaoGeraAvisoDeRaca`)
- ✓ Processar com raça indígena e etnia reconhecida não gera aviso de raça (`processarComRacaIndigenaEEtniaReconhecidaNaoGeraAvisoDeRaca`)

**ETNIA NÃO ENCONTRADA (erro com raça Indígena; ignorada com aviso nas demais)**

- ✓ Processar com etnia preenchida não reconhecida e raça indígena rejeita a linha (`processarComEtniaPreenchidaNaoReconhecidaERacaIndigenaRejeitaALinha`)
- ✓ Processar com etnia preenchida e raça não indígena não gera aviso de etnia (`processarComEtniaPreenchidaERacaNaoIndigenaNaoGeraAvisoDeEtnia`)
- ✓ Processar com etnia preenchida não reconhecida mas raça não indígena não gera aviso (`processarComEtniaPreenchidaNaoReconhecidaMasRacaNaoIndigenaNaoGeraAviso`)
- ✓ Processar com etnia preenchida reconhecida não gera aviso de etnia (`processarComEtniaPreenchidaReconhecidaNaoGeraAvisoDeEtnia`)

**SITUAÇÃO DE RUA (aviso, não bloqueia)**

- ✓ Processar sem coluna de situação de rua não gera aviso e deixa campo nulo (`processarSemColunaDeSituacaoDeRuaNaoGeraAvisoEDeixaCampoNulo`)
- ✓ Processar normaliza situação de rua por extenso para código de uma letra (`processarNormalizaSituacaoDeRuaPorExtensoParaCodigoDeUmaLetra`)
- ✓ Processar normaliza situação de rua abreviada independente de caixa (`processarNormalizaSituacaoDeRuaAbreviadaIndependenteDeCaixa`)
- ✓ Processar com situação de rua não reconhecida gera aviso mas não bloqueia e deixa campo nulo (`processarComSituacaoDeRuaNaoReconhecidaGeraAvisoMasNaoBloqueiaEDeixaCampoNulo`)

**PACIENTE SEM CPF (aviso, não bloqueia)**

- ✓ Processar sem coluna de paciente sem CPF não gera aviso e deixa campo nulo (`processarSemColunaDePacienteSemCpfNaoGeraAvisoEDeixaCampoNulo`)
- ✓ Processar normaliza paciente sem CPF por extenso para código de uma letra (`processarNormalizaPacienteSemCpfPorExtensoParaCodigoDeUmaLetra`)
- ✓ Processar com paciente sem CPF não reconhecido gera aviso mas não bloqueia e deixa campo nulo (`processarComPacienteSemCpfNaoReconhecidoGeraAvisoMasNaoBloqueiaEDeixaCampoNulo`)

**CONVERSÃO DE DATA/HORA**

- ✓ Processar converte data de agendamento válida (`processarConverteDataDeAgendamentoValida`)
- ✓ Processar com data de agendamento inválida lanca exceção com mensagem específica (`processarComDataDeAgendamentoInvalidaLancaExcecaoComMensagemEspecifica`)
- ✓ Processar converte hora de atendimento válida (`processarConverteHoraDeAtendimentoValida`)
- ✓ Processar com hora de atendimento inválida importa sem hora com aviso (`processarComHoraDeAtendimentoInvalidaImportaSemHoraComAviso`)
- ✓ Processar com hora de atendimento hífen importa sem hora com aviso (`processarComHoraDeAtendimentoHifenImportaSemHoraComAviso`)
- ✓ Processar com hora de atendimento vazia não gera aviso (`processarComHoraDeAtendimentoVaziaNaoGeraAviso`)
- ✓ Processar converte data de nascimento válida (`processarConverteDataDeNascimentoValida`)
- ✓ Processar com data de nascimento inválida lanca exceção com mensagem específica (`processarComDataDeNascimentoInvalidaLancaExcecaoComMensagemEspecifica`)

**LIMITE DE TAMANHO DE CAMPOS**

- ✓ Processar trunca campos de endereço conforme limites do layout bpai (`processarTruncaCamposDeEnderecoConformeLimitesDoLayoutBpai`)
- ✓ Processar bairro e complemento normaliza e avisa só o complemento cortado (`processarBairroEComplementoNormalizaEAvisaSoOComplementoCortado`)
- ✓ Processar bairro e complemento de preenchimento viram vazio (`processarBairroEComplementoDePreenchimentoViramVazio`)
- ✓ Processar número do endereço fora do padrão gera aviso e fica com o número inicial (`processarNumeroDoEnderecoForaDoPadraoGeraAvisoEFicaComONumeroInicial`)
- ✓ Processar número vazio ou sem número vira SN sem aviso (`processarNumeroVazioOuSemNumeroViraSnSemAviso`)
- ✓ Processar CEP de preenchimento lanca exceção (`processarCepDePreenchimentoLancaExcecao`)
- ✓ Processar CEP com sete dígitos orienta formatar como texto (`processarCepComSeteDigitosOrientaFormatarComoTexto`)

**Vigência dos campos novos do layout (decisão 26, 16/10/2026)**

- ✓ Processar sim antes da vigência do campo gera aviso (`processarSimAntesDaVigenciaDoCampoGeraAviso`)
- ✓ Processar não antes da vigência ou sim já vigente não gera aviso (`processarNaoAntesDaVigenciaOuSimJaVigenteNaoGeraAviso`)
- ✓ Processar com CNS vazio e CPF preenchido não gera aviso de CNS (`processarComCnsVazioECpfPreenchidoNaoGeraAvisoDeCns`)
- ✓ Processar data de atendimento futura gera aviso mas importa (`processarDataDeAtendimentoFuturaGeraAvisoMasImporta`)
- ✓ Processar padroniza o nome e avisa nome longo ou suspeito (`processarPadronizaONomeEAvisaNomeLongoOuSuspeito`)
- ✓ Processar CID com traco gera aviso e deixa o CID em branco (`processarCidComTracoGeraAvisoEDeixaOCidEmBranco`)

#### DataSemAnoTest

`src/test/java/br/gov/ses/fillbpai/service/DataSemAnoTest.java` · 3 testes · 3 execuções · ✓ todas passaram

Data digitada só com dia e mês: o Excel grava a data com o ano corrente e mostra só dia/mês (formato `dd/mm`, `d-mmm`). Verificado em 14/10/2026: o paciente virava um bebê de meses, sem alerta. Nascimento → ERRO; atendimento → AVISO.

- ✓ Nascimento digitado só com dia e mês gera erro na análise e bloqueia a importação (`nascimentoDigitadoSoComDiaEMesGeraErroNaAnaliseEBloqueiaAImportacao`)
- ✓ Atendimento mostrado sem ano gera só aviso e importa (`atendimentoMostradoSemAnoGeraSoAvisoEImporta`)
- ✓ Datas com ano no formato não geram alerta (`datasComAnoNoFormatoNaoGeramAlerta`)

#### ExcelImportServiceTest

`src/test/java/br/gov/ses/fillbpai/service/ExcelImportServiceTest.java` · 14 testes · 14 execuções · ✓ todas passaram

- ✓ Importar linha com celula texto trima o valor (`importarLinhaComCelulaTextoTrimaOValor`)
- ✓ Importar linha com celula numérica formata sem notação cientifica (`importarLinhaComCelulaNumericaFormataSemNotacaoCientifica`)
- ✓ Importar linha com celula de data formata como ISO (`importarLinhaComCelulaDeDataFormataComoIso`)
- ✓ Importar linha com celula só de hora base 1899 formata como hora (`importarLinhaComCelulaSoDeHoraBase1899FormataComoHora`)
- ✓ Importar linha com celula data e hora na coluna de horário le a hora (`importarLinhaComCelulaDataEHoraNaColunaDeHorarioLeAHora`)
- ✓ Importar linha com celula só de data na coluna de horário não inventa meia noite (`importarLinhaComCelulaSoDeDataNaColunaDeHorarioNaoInventaMeiaNoite`)
- ✓ Importar linha com celula data e hora na coluna de data continua lendo só a data (`importarLinhaComCelulaDataEHoraNaColunaDeDataContinuaLendoSoAData`)
- ✓ Importar linha com celula booleana converte para texto (`importarLinhaComCelulaBooleanaConverteParaTexto`)
- ✓ Importar linha com celula de formula usa resultado calculado não o texto da formula (`importarLinhaComCelulaDeFormulaUsaResultadoCalculadoNaoOTextoDaFormula`)
- ✓ Importar linha com formula que resulta em vazio retorna vazio (`importarLinhaComFormulaQueResultaEmVazioRetornaVazio`)
- ✓ Importar linha com formula de hora usa hora calculada (`importarLinhaComFormulaDeHoraUsaHoraCalculada`)
- ✓ Importar linha com espaco não separavel trata como espaco comum (`importarLinhaComEspacoNaoSeparavelTrataComoEspacoComum`)
- ✓ Importar linha com celula em branco retorna nulo (`importarLinhaComCelulaEmBrancoRetornaNulo`)
- ✓ Importar linha com campo não mapeado retorna nulo sem lancar exceção (`importarLinhaComCampoNaoMapeadoRetornaNuloSemLancarExcecao`)

#### GeradorBPAiServiceTest

`src/test/java/br/gov/ses/fillbpai/service/GeradorBPAiServiceTest.java` · 41 testes · 41 execuções · ✓ todas passaram

Testa `gerarConteudo` e `numerarFolhas` — métodos extraídos de `GeradorBPAiService` para permitir verificar o layout posicional do BPA-I sem depender de banco de dados nem de `javafx.stage.FileChooser`. Nenhum destes testes usa `EntityManager`: `montarRegistro` nunca acessa `a.getMedico()`, só campos do próprio atendimento e de paciente/endereço, então a lista é construída inteiramente em memória. Posições (0-based, fim exclusivo) de cada campo dentro da linha de registro, calculadas somando os tamanhos de `montarRegistro` campo a campo — usadas para extrair e conferir cada seq individualmente: seq1[0,2) seq2[2,9) seq3[9,15) seq4[15,30) seq5[30,36) seq6[36,44) seq7[44,47) seq8[47,49) seq9[49,59) seq10[59,74) seq11[74,75) seq12[75,81) seq13[81,85) seq14[85,88) seq15[88,94) seq16[94,96) seq17[96,109) seq18[109,112) seq19[112,142) seq20[142,150) seq21[150,152) seq22[152,156) seq23[156,159) seq24[159,162) seq25[162,165) seq26[165,173) seq27[173,177) seq28[177,191) seq29[191,199) seq30[199,202) seq31[202,232) seq32[232,242) seq33[242,247) seq34[247,277) seq35[277,288) seq36[288,328) seq37[328,338) seq38[338,349) seq"38"dup[349,350) seq39[350,351)


**TAMANHO E QUEBRA DE LINHA**

- ✓ Gerar conteúdo parcial tem header com 130 caracteres e registro com 351 (`gerarConteudoParcialTemHeaderCom130CaracteresERegistroCom351`)
- ✓ Seq 5 prd CBO já gravado com máscara sai só com dígitos sem deslocar o registro (`seq5PrdCboJaGravadoComMascaraSaiSoComDigitosSemDeslocarORegistro`)
- ✓ Registro nunca passa de 351 posições mesmo com valores grandes demais (`registroNuncaPassaDe351PosicoesMesmoComValoresGrandesDemais`)
- ✓ Validar IBGE bloqueia a geração quando algum atendimento está sem código IBGE (`validarIbgeBloqueiaAGeracaoQuandoAlgumAtendimentoEstaSemCodigoIbge`)
- ✓ Validar IBGE não bloqueia quando todos tem código IBGE (`validarIbgeNaoBloqueiaQuandoTodosTemCodigoIbge`)
- ✓ Gerar conteúdo parcial usa sempre CRLF sem quebra de linha solta (`gerarConteudoParcialUsaSempreCrlfSemQuebraDeLinhaSolta`)
- ✓ Gerar conteúdo completo usa sempre CRLF sem quebra de linha solta (`gerarConteudoCompletoUsaSempreCrlfSemQuebraDeLinhaSolta`)

**HEADER**

- ✓ Gerar conteúdo parcial calcula checksum do header corretamente (`gerarConteudoParcialCalculaChecksumDoHeaderCorretamente`)
- ✓ Gerar conteúdo informa no header o total de folhas não o total de linhas (`gerarConteudoInformaNoHeaderOTotalDeFolhasNaoOTotalDeLinhas`)

**REGISTRO — CAMPO A CAMPO**

- ✓ Gerar conteúdo parcial monta todos os campos do registro nas posições corretas (`gerarConteudoParcialMontaTodosOsCamposDoRegistroNasPosicoesCorretas`)

**SITUAÇÃO DE RUA (seq "38" duplicado)**

- ✓ Seq situação rua sem informação na planilha usa padrão N (`seqSituacaoRuaSemInformacaoNaPlanilhaUsaPadraoN`)
- ✓ Seq situação rua com valor S na planilha usa o valor informado (`seqSituacaoRuaComValorSNaPlanilhaUsaOValorInformado`)

**PACIENTE SEM CPF (seq 39)**

- ✓ Seq 39 prd sem CPF sem informação na planilha deriva N quando CPF preenchido (`seq39PrdSemCpfSemInformacaoNaPlanilhaDerivaNQuandoCpfPreenchido`)
- ✓ Seq 39 prd sem CPF sem informação na planilha deriva S quando CPF vazio (`seq39PrdSemCpfSemInformacaoNaPlanilhaDerivaSQuandoCpfVazio`)
- ✓ Paciente sem CPF com chave interna não envia a chave no campo de CPF (`pacienteSemCpfComChaveInternaNaoEnviaAChaveNoCampoDeCpf`)
- ✓ Paciente sem CPF envia o CNS no seq 10 e CPF em branco (`pacienteSemCpfEnviaOCnsNoSeq10ECpfEmBranco`)
- ✓ Situação de rua vem do atendimento eo paciente só para dados antigos (`situacaoDeRuaVemDoAtendimentoEOPacienteSoParaDadosAntigos`)
- ✓ Sexo inválido gravado antes nunca vai para o BPA (`sexoInvalidoGravadoAntesNuncaVaiParaOBpa`)
- ✓ Situação de rua e paciente sem CPF com celula vazia saem em branco (`situacaoDeRuaEPacienteSemCpfComCelulaVaziaSaemEmBranco`)
- ✓ Situação de rua e paciente sem CPF sem a coluna mantem o padrão (`situacaoDeRuaEPacienteSemCpfSemAColunaMantemOPadrao`)
- ✓ Seq 33 número vazio ou sem número sai como SN (`seq33NumeroVazioOuSemNumeroSaiComoSn`)
- ✓ Paciente com CPF nunca envia o CNS junto (`pacienteComCpfNuncaEnviaOCnsJunto`)
- ✓ Seq 39 prd sem CPF com valor informado na planilha prevalece sobre a derivação (`seq39PrdSemCpfComValorInformadoNaPlanilhaPrevaleceSobreADerivacao`)
- ✓ Campos novos do layout saem em branco antes da vigência (`camposNovosDoLayoutSaemEmBrancoAntesDaVigencia`)
- ✓ Campos novos do layout sao enviados a partir da vigência (`camposNovosDoLayoutSaoEnviadosAPartirDaVigencia`)
- ✓ Especialidade nutricionista forca SIGTAP fixo e deriva serviço e classificação dele (`especialidadeNutricionistaForcaSigtapFixoEDerivaServicoEClassificacaoDele`)
- ✓ Especialidade com prefixo médico ainda forca SIGTAP fixo (`especialidadeComPrefixoMedicoAindaForcaSigtapFixo`)

**RAÇA (seq 21) — fallback "99" só para dados legados**

- ✓ Seq 21 prd raça com raça nula usa branco conforme default oficial do layout (`seq21PrdRacaComRacaNulaUsaBrancoConformeDefaultOficialDoLayout`)
- ✓ Seq 21 prd raça com variação de genero reconhece mesmo código (`seq21PrdRacaComVariacaoDeGeneroReconheceMesmoCodigo`)

**ETNIA (seq 22)**

- ✓ Seq 22 prd etnia com etnia reconhecida gera código de quatro caracteres (`seq22PrdEtniaComEtniaReconhecidaGeraCodigoDeQuatroCaracteres`)
- ✓ Seq 22 prd etnia em branco quando a raça não e indígena mesmo com etnia preenchida (`seq22PrdEtniaEmBrancoQuandoARacaNaoEIndigenaMesmoComEtniaPreenchida`)
- ✓ Seq 22 prd etnia com etnia não reconhecida gera quatro espacos (`seq22PrdEtniaComEtniaNaoReconhecidaGeraQuatroEspacos`)

**SEQUENCIAL E FOLHA**

- ✓ Numerar folhas uma folha por especialidade e médico na ordem alfabetica (`numerarFolhasUmaFolhaPorEspecialidadeEMedicoNaOrdemAlfabetica`)
- ✓ Numerar folhas abre folha nova a cada noventa e nove linhas (`numerarFolhasAbreFolhaNovaACadaNoventaENoveLinhas`)
- ✓ Gerar conteúdo escreve folha e linha da numeração na ordem do arquivo com total de folhas no header (`gerarConteudoEscreveFolhaELinhaDaNumeracaoNaOrdemDoArquivoComTotalDeFolhasNoHeader`)
- ✓ Parcial usa a folha da numeração da competência inteira (`parcialUsaAFolhaDaNumeracaoDaCompetenciaInteira`)

**SANITIZAÇÃO DE TEXTO (ISO-8859-1)**

- ✓ Gerar conteúdo parcial normaliza pontuação tipografica mas mantem acentuação latina (`gerarConteudoParcialNormalizaPontuacaoTipograficaMasMantemAcentuacaoLatina`)
- ✓ Gerar conteúdo parcial substitui caractere fora do latin 1 por espaco (`gerarConteudoParcialSubstituiCaractereForaDoLatin1PorEspaco`)
- ✓ Gerar conteúdo parcial formata CID em maiusculo só alfanumérico truncado a quatro (`gerarConteudoParcialFormataCidEmMaiusculoSoAlfanumericoTruncadoAQuatro`)

**INE (seq 37) — Decisão 30**

- ✓ Campo INE vazio sai com dez brancos e preenchido com os dez dígitos (`campoIneVazioSaiComDezBrancosEPreenchidoComOsDezDigitos`)
- ✓ Campo INE fora do tamanho sai em branco para não deslocar o registro (`campoIneForaDoTamanhoSaiEmBrancoParaNaoDeslocarORegistro`)

#### HistoricoLogsServiceTest

`src/test/java/br/gov/ses/fillbpai/service/HistoricoLogsServiceTest.java` · 7 testes · 7 execuções · ✓ todas passaram

Histórico dos logs de importação (20/10/2026).

- ✓ Listar le o cabeçalho e ordena do mais recente para o mais antigo (`listarLeOCabecalhoEOrdenaDoMaisRecenteParaOMaisAntigo`)
- ✓ Log antigo sem cabeçalho usa o nome ea data do arquivo (`logAntigoSemCabecalhoUsaONomeEADataDoArquivo`)
- ✓ Novo arquivo tem data e hora no nome e nunca sobrescreve (`novoArquivoTemDataEHoraNoNomeENuncaSobrescreve`)
- ✓ Pasta inexistente ou sem logs devolve lista vazia (`pastaInexistenteOuSemLogsDevolveListaVazia`)
- ✓ Conteúdo devolve o texto do log (`conteudoDevolveOTextoDoLog`)
- ✓ Migrar de move os logs da pasta antiga sem sobrescrever nem mexer no banco (`migrarDeMoveOsLogsDaPastaAntigaSemSobrescreverNemMexerNoBanco`)
- ✓ Migrar de pasta inexistente não faz nada (`migrarDePastaInexistenteNaoFazNada`)

#### PlanilhaColumnMapperTest

`src/test/java/br/gov/ses/fillbpai/service/PlanilhaColumnMapperTest.java` · 14 testes · 14 execuções · ✓ todas passaram

- ✓ Mapear com cabeçalho completo e em ordem mapeia todos os campos (`mapearComCabecalhoCompletoEEmOrdemMapeiaTodosOsCampos`)
- ✓ Mapear sem coluna cod logradouro não bloqueia estrutura mas reporta opcional faltando (`mapearSemColunaCodLogradouroNaoBloqueiaEstruturaMasReportaOpcionalFaltando`)
- ✓ Mapear sem coluna paciente sem CPF não bloqueia estrutura mas reporta opcional faltando (`mapearSemColunaPacienteSemCpfNaoBloqueiaEstruturaMasReportaOpcionalFaltando`)
- ✓ Mapear com apenas coluna combinada legada backfilla especialidade médico (`mapearComApenasColunaCombinadaLegadaBackfillaEspecialidadeMedico`)
- ✓ Mapear com colunas fora de ordem mapeia por nome não por posição (`mapearComColunasForaDeOrdemMapeiaPorNomeNaoPorPosicao`)
- ✓ Mapear com coluna extra não reconhecida ignora sem quebrar o resto (`mapearComColunaExtraNaoReconhecidaIgnoraSemQuebrarOResto`)
- ✓ Mapear com coluna obrigatória ausente reporta campo faltando (`mapearComColunaObrigatoriaAusenteReportaCampoFaltando`)
- ✓ Mapear com coluna duplicada reporta campo duplicado com as colunas conflitantes (`mapearComColunaDuplicadaReportaCampoDuplicadoComAsColunasConflitantes`)
- ✓ Mapear com cabeçalho nulo reporta todos os campos como faltando (`mapearComCabecalhoNuloReportaTodosOsCamposComoFaltando`)
- ✓ Mapear sem coluna tipo zona não bloqueia estrutura mas reporta opcional faltando (`mapearSemColunaTipoZonaNaoBloqueiaEstruturaMasReportaOpcionalFaltando`)

**Mapeamento pela planilha (cabeçalho + dados)**

- ✓ Mapear planilha sem título da especialidade e só nomes de médico reporta especialidade faltando (`mapearPlanilhaSemTituloDaEspecialidadeESoNomesDeMedicoReportaEspecialidadeFaltando`)
- ✓ Mapear planilha legado com separador continua combinada (`mapearPlanilhaLegadoComSeparadorContinuaCombinada`)
- ✓ Mapear planilha completa não aponta coluna sem cabeçalho (`mapearPlanilhaCompletaNaoApontaColunaSemCabecalho`)
- ✓ Mapear aponta coluna extra sem cabeçalho com dados (`mapearApontaColunaExtraSemCabecalhoComDados`)

#### RegistroRemessaTest

`src/test/java/br/gov/ses/fillbpai/service/RegistroRemessaTest.java` · 1 testes · 1 execuções · ✓ todas passaram

Folha, linha e data da remessa gravadas no atendimento depois da geração (20/10/2026).

- ✓ Registrar remessa grava folha linha e data só nos atendimentos enviados (`registrarRemessaGravaFolhaLinhaEDataSoNosAtendimentosEnviados`)

#### SobreServiceTest

`src/test/java/br/gov/ses/fillbpai/service/SobreServiceTest.java` · 5 testes · 5 execuções · ✓ todas passaram

Janela "Sobre" (20/10/2026).

- ✓ Carregar traz versão do pom dados da unidade e pastas (`carregarTrazVersaoDoPomDadosDaUnidadeEPastas`)
- ✓ Texto para copiar tem uma informação por linha (`textoParaCopiarTemUmaInformacaoPorLinha`)
- ✓ Sem data de compilação o texto não mostra parênteses (`semDataDeCompilacaoOTextoNaoMostraParenteses`)
- ✓ Valor não filtrado pelo maven eh tratado como ausente (`valorNaoFiltradoPeloMavenEhTratadoComoAusente`)
- ✓ Formatar CNPJ (`formatarCnpj`)

#### ValidacaoPlanilhaServiceTest

`src/test/java/br/gov/ses/fillbpai/service/ValidacaoPlanilhaServiceTest.java` · 103 testes · 103 execuções · ✓ todas passaram

- ✓ Validar com planilha totalmente válida não retorna erros (`validarComPlanilhaTotalmenteValidaNaoRetornaErros`)
- ✓ Validar com CNS ausente e CPF preenchido não gera aviso (`validarComCnsAusenteECpfPreenchidoNaoGeraAviso`)
- ✓ Validar com CNS longo gera aviso CNS inválido (`validarComCnsLongoGeraAvisoCnsInvalido`)
- ✓ Validar com CEP ausente gera erro CEP ausente (`validarComCepAusenteGeraErroCepAusente`)
- ✓ Validar com CEP inválido gera erro CEP inválido (`validarComCepInvalidoGeraErroCepInvalido`)
- ✓ Validar com CPF ausente gera erro CPF ausente (`validarComCpfAusenteGeraErroCpfAusente`)
- ✓ Validar com CPF preenchido e paciente sem CPF sim gera erro de conflito (`validarComCpfPreenchidoEPacienteSemCpfSimGeraErroDeConflito`)

**Município**

- ✓ Validar com município fora da tabela e IBGE não encontrado pelo CEP gera erro (`validarComMunicipioForaDaTabelaEIbgeNaoEncontradoPeloCepGeraErro`)
- ✓ Validar com município fora da tabela mas IBGE encontrado pelo CEP avisa qual município foi encontrado (`validarComMunicipioForaDaTabelaMasIbgeEncontradoPeloCepAvisaQualMunicipioFoiEncontrado`)
- ✓ Validar com município da tabela não consulta o CEP (`validarComMunicipioDaTabelaNaoConsultaOCep`)
- ✓ Validar sem município gera erro de município ausente com o paciente (`validarSemMunicipioGeraErroDeMunicipioAusenteComOPaciente`)
- ✓ Validar com município com sigla da UF não gera aviso (`validarComMunicipioComSiglaDaUfNaoGeraAviso`)
- ✓ Validar sem CPF com paciente sem CPF sim não gera erro (`validarSemCpfComPacienteSemCpfSimNaoGeraErro`)
- ✓ Validar sem CPF com paciente sem CPF sim e sem CNS gera erro (`validarSemCpfComPacienteSemCpfSimESemCnsGeraErro`)
- ✓ Validar sem CPF com paciente sem CPF sim e CNS sem os 15 dígitos gera erro (`validarSemCpfComPacienteSemCpfSimECnsSemOs15DigitosGeraErro`)
- ✓ Validar sem CPF com paciente sem CPF sim e CNS com DV errado gera erro (`validarSemCpfComPacienteSemCpfSimECnsComDvErradoGeraErro`)
- ✓ Validar com CPF e CNS com DV errado gera só aviso (`validarComCpfECnsComDvErradoGeraSoAviso`)
- ✓ Validar sem CPF sem a coluna paciente sem CPF gera erro CPF ausente (`validarSemCpfSemAColunaPacienteSemCpfGeraErroCpfAusente`)
- ✓ Validar sem CPF com paciente sem CPF não continua erro (`validarSemCpfComPacienteSemCpfNaoContinuaErro`)
- ✓ Validar com CPF falso de dígitos repetidos gera erro CPF falso (`validarComCpfFalsoDeDigitosRepetidosGeraErroCpfFalso`)
- ✓ Validar com CPF do paciente com dígito verificador errado gera erro CPF DV inválido (`validarComCpfDoPacienteComDigitoVerificadorErradoGeraErroCpfDvInvalido`)
- ✓ Validar com CPF do médico com dígito verificador errado gera erro agrupado pelo médico (`validarComCpfDoMedicoComDigitoVerificadorErradoGeraErroAgrupadoPeloMedico`)
- ✓ Validar com CPF de dez dígitos orienta a formatar como texto (`validarComCpfDeDezDigitosOrientaAFormatarComoTexto`)
- ✓ Validar sem CPF do médico gera erro CPF médico ausente (`validarSemCpfDoMedicoGeraErroCpfMedicoAusente`)
- ✓ Validar com CPF do médico inválido gera erro CPF médico inválido (`validarComCpfDoMedicoInvalidoGeraErroCpfMedicoInvalido`)
- ✓ Validar com CPF inválido gera erro CPF inválido (`validarComCpfInvalidoGeraErroCpfInvalido`)
- ✓ Validar com raça indígena sem etnia gera erro raça indígena (`validarComRacaIndigenaSemEtniaGeraErroRacaIndigena`)
- ✓ Validar com raça não indígena não olha a etnia (`validarComRacaNaoIndigenaNaoOlhaAEtnia`)
- ✓ Validar com raça indeterminada ou negra gera erro com mensagem propria (`validarComRacaIndeterminadaOuNegraGeraErroComMensagemPropria`)
- ✓ Validar com raça indígena e etnia pelo código não gera erro (`validarComRacaIndigenaEEtniaPeloCodigoNaoGeraErro`)
- ✓ Validar com raça indígena e etnia reconhecida não gera aviso de raça (`validarComRacaIndigenaEEtniaReconhecidaNaoGeraAvisoDeRaca`)
- ✓ Validar com etnia preenchida não reconhecida e raça indígena gera erro etnia não encontrada (`validarComEtniaPreenchidaNaoReconhecidaERacaIndigenaGeraErroEtniaNaoEncontrada`)
- ✓ Validar com etnia preenchida não reconhecida mas raça não indígena não gera aviso (`validarComEtniaPreenchidaNaoReconhecidaMasRacaNaoIndigenaNaoGeraAviso`)
- ✓ Validar com raça ausente gera erro raça ausente (`validarComRacaAusenteGeraErroRacaAusente`)
- ✓ Validar com raça não reconhecida gera erro raça inválida (`validarComRacaNaoReconhecidaGeraErroRacaInvalida`)
- ✓ Validar com variação de grafia da raça não gera erro de raça (`validarComVariacaoDeGrafiaDaRacaNaoGeraErroDeRaca`)
- ✓ Validar com coluna obrigatória ausente no cabeçalho para antes de validar linhas (`validarComColunaObrigatoriaAusenteNoCabecalhoParaAntesDeValidarLinhas`)
- ✓ Validar com coluna cod logradouro ausente não bloqueia e gera aviso coluna opcional ausente (`validarComColunaCodLogradouroAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente`)
- ✓ Validar com coluna situação rua ausente não bloqueia e gera aviso coluna opcional ausente (`validarComColunaSituacaoRuaAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente`)
- ✓ Validar com situação rua não reconhecida gera aviso situação rua inválida (`validarComSituacaoRuaNaoReconhecidaGeraAvisoSituacaoRuaInvalida`)
- ✓ Validar com situação rua reconhecida não gera aviso (`validarComSituacaoRuaReconhecidaNaoGeraAviso`)
- ✓ Validar com coluna paciente sem CPF ausente não bloqueia e gera aviso coluna opcional ausente (`validarComColunaPacienteSemCpfAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente`)
- ✓ Validar com paciente sem CPF não reconhecido gera aviso paciente sem CPF inválido (`validarComPacienteSemCpfNaoReconhecidoGeraAvisoPacienteSemCpfInvalido`)
- ✓ Validar com paciente sem CPF reconhecido não gera aviso (`validarComPacienteSemCpfReconhecidoNaoGeraAviso`)
- ✓ Validar com estabelecimento sem separador gera aviso estabelecimento sem código (`validarComEstabelecimentoSemSeparadorGeraAvisoEstabelecimentoSemCodigo`)
- ✓ Validar com nome com hífen e sem código gera aviso sem código em vez de código falso (`validarComNomeComHifenESemCodigoGeraAvisoSemCodigoEmVezDeCodigoFalso`)
- ✓ Validar com estabelecimento só com código gera aviso sem nome (`validarComEstabelecimentoSoComCodigoGeraAvisoSemNome`)
- ✓ Validar com estabelecimento com código não gera aviso (`validarComEstabelecimentoComCodigoNaoGeraAviso`)
- ✓ Validar com estabelecimento não informado gera aviso ausente agrupavel (`validarComEstabelecimentoNaoInformadoGeraAvisoAusenteAgrupavel`)
- ✓ Validar com horário no formato com h não gera aviso (`validarComHorarioNoFormatoComHNaoGeraAviso`)
- ✓ Validar com hora não reconhecida gera aviso hora inválida (`validarComHoraNaoReconhecidaGeraAvisoHoraInvalida`)
- ✓ Validar com hora vazia não gera aviso (`validarComHoraVaziaNaoGeraAviso`)
- ✓ Validar com hora só com espaco não separavel não gera aviso (`validarComHoraSoComEspacoNaoSeparavelNaoGeraAviso`)

**CBO do médico**

- ✓ Validar sem CBO gera erro CBO ausente com o médico (`validarSemCboGeraErroCboAusenteComOMedico`)
- ✓ Validar com CBO sem seis dígitos gera erro CBO inválido (`validarComCboSemSeisDigitosGeraErroCboInvalido`)
- ✓ Validar com CBO com máscara não gera erro (`validarComCboComMascaraNaoGeraErro`)

**Especialidade**

- ✓ Validar sem especialidade gera erro especialidade ausente com o médico (`validarSemEspecialidadeGeraErroEspecialidadeAusenteComOMedico`)
- ✓ Validar com especialidade só com espacos gera erro especialidade ausente (`validarComEspecialidadeSoComEspacosGeraErroEspecialidadeAusente`)

**Tipo de serviço**

- ✓ Validar sem tipo de serviço gera erro tipo serviço ausente (`validarSemTipoDeServicoGeraErroTipoServicoAusente`)
- ✓ Validar sem tipo de serviço para nutricionista ou psicologo gera só aviso com a especialidade (`validarSemTipoDeServicoParaNutricionistaOuPsicologoGeraSoAvisoComAEspecialidade`)
- ✓ Validar com tipo de serviço não reconhecido gera erro tipo serviço inválido (`validarComTipoDeServicoNaoReconhecidoGeraErroTipoServicoInvalido`)
- ✓ Validar com tipo de serviço em minusculas não gera erro (`validarComTipoDeServicoEmMinusculasNaoGeraErro`)

**Data de agendamento (data do atendimento)**

- ✓ Validar sem data de agendamento gera erro data ausente (`validarSemDataDeAgendamentoGeraErroDataAusente`)
- ✓ Validar com data de agendamento em formato não reconhecido gera erro data inválida (`validarComDataDeAgendamentoEmFormatoNaoReconhecidoGeraErroDataInvalida`)
- ✓ Validar com data de agendamento nos formatos aceitos não gera erro (`validarComDataDeAgendamentoNosFormatosAceitosNaoGeraErro`)

**CNS do profissional**

- ✓ Validar com médico sem CNS cadastrado gera aviso em cada linha com o mesmo valor da grafia (`validarComMedicoSemCnsCadastradoGeraAvisoEmCadaLinhaComOMesmoValorDaGrafia`)
- ✓ Validar preenche o valor do aviso separado da explicação (`validarPreencheOValorDoAvisoSeparadoDaExplicacao`)
- ✓ Validar com herança de CNS indica a herança no valor (`validarComHerancaDeCnsIndicaAHerancaNoValor`)
- ✓ Gerar log TXT agrupa por tipo com explicação uma vez e valores com linhas (`gerarLogTxtAgrupaPorTipoComExplicacaoUmaVezEValoresComLinhas`)
- ✓ Validar com grafia não cadastrada mas mesmo CPF com grafia cadastrada na planilha avisa herança (`validarComGrafiaNaoCadastradaMasMesmoCpfComGrafiaCadastradaNaPlanilhaAvisaHeranca`)
- ✓ Validar com grafia não cadastrada mas CPF com CNS conhecido no banco avisa herança (`validarComGrafiaNaoCadastradaMasCpfComCnsConhecidoNoBancoAvisaHeranca`)
- ✓ Validar com médico cadastrado por apelido sem acento não gera aviso (`validarComMedicoCadastradoPorApelidoSemAcentoNaoGeraAviso`)
- ✓ Validar com especialidade sem cabeçalho e só nomes de médico reporta coluna faltando e coluna sem cabeçalho (`validarComEspecialidadeSemCabecalhoESoNomesDeMedicoReportaColunaFaltandoEColunaSemCabecalho`)
- ✓ Validar planilha legado com linha sem separador gera erro na linha (`validarPlanilhaLegadoComLinhaSemSeparadorGeraErroNaLinha`)
- ✓ Validar com planilha legado não reporta estrutura inválida e gera aviso com exemplo real (`validarComPlanilhaLegadoNaoReportaEstruturaInvalidaEGeraAvisoComExemploReal`)
- ✓ Gerar log TXT separa erros e avisos em seções distintas (`gerarLogTxtSeparaErrosEAvisosEmSecoesDistintas`)
- ✓ Gerar log TXT com lista vazia indica planilha apta (`gerarLogTxtComListaVaziaIndicaPlanilhaApta`)

**Todas as linhas importadas: nome, nascimento, duplicidade, linhas vazias**

- ✓ Validar sem nome do paciente gera erro (`validarSemNomeDoPacienteGeraErro`)
- ✓ Validar com data de nascimento inválida gera erro (`validarComDataDeNascimentoInvalidaGeraErro`)

**Data de nascimento e CID (14/10/2026)**

- ✓ Validar sem data de nascimento gera erro (`validarSemDataDeNascimentoGeraErro`)
- ✓ Validar com nascimento depois do atendimento ou mais de 130 anos gera erro (`validarComNascimentoDepoisDoAtendimentoOuMaisDe130AnosGeraErro`)
- ✓ Validar com data inexistente gera erro no nascimento e no atendimento (`validarComDataInexistenteGeraErroNoNascimentoENoAtendimento`)
- ✓ Validar telefone sem DDD ou inválido gera só aviso (`validarTelefoneSemDddOuInvalidoGeraSoAviso`)
- ✓ Validar CEP de preenchimento gera erro e número longo gera aviso (`validarCepDePreenchimentoGeraErroENumeroLongoGeraAviso`)
- ✓ Validar complemento cortado e bairro igual município geram aviso mas bairro longo não (`validarComplementoCortadoEBairroIgualMunicipioGeramAvisoMasBairroLongoNao`)
- ✓ Validar sexo vazio ou não reconhecido gera erro e formas usuais passam (`validarSexoVazioOuNaoReconhecidoGeraErroEFormasUsuaisPassam`)
- ✓ Validar paciente em situação de rua sem CEP gera só aviso (`validarPacienteEmSituacaoDeRuaSemCepGeraSoAviso`)
- ✓ Validar sim em campo antes da vigência gera aviso agrupado por competência (`validarSimEmCampoAntesDaVigenciaGeraAvisoAgrupadoPorCompetencia`)
- ✓ Validar mesmo CPF com nomes diferentes gera aviso em todas as linhas do CPF (`validarMesmoCpfComNomesDiferentesGeraAvisoEmTodasAsLinhasDoCpf`)
- ✓ Validar paciente sem CPF com mesmo CNS e nomes diferentes gera aviso pelo CNS (`validarPacienteSemCpfComMesmoCnsENomesDiferentesGeraAvisoPeloCns`)
- ✓ Validar data de atendimento futura ou muito antiga gera aviso (`validarDataDeAtendimentoFuturaOuMuitoAntigaGeraAviso`)
- ✓ Validar nome longo ou suspeito gera avisos (`validarNomeLongoOuSuspeitoGeraAvisos`)
- ✓ Validar CID com traco gera aviso CID ausente (`validarCidComTracoGeraAvisoCidAusente`)
- ✓ Validar CPF com nome diferente do cadastrado no banco gera aviso (`validarCpfComNomeDiferenteDoCadastradoNoBancoGeraAviso`)
- ✓ Validar colunas opcionais presentes com celula vazia geram aviso (`validarColunasOpcionaisPresentesComCelulaVaziaGeramAviso`)
- ✓ Validar colunas opcionais ausentes não geram aviso de celula vazia (`validarColunasOpcionaisAusentesNaoGeramAvisoDeCelulaVazia`)
- ✓ Validar com município de MS e CEP de outro estado gera aviso (`validarComMunicipioDeMsECepDeOutroEstadoGeraAviso`)
- ✓ Validar coluna de logradouro não reconhecida gera só aviso (`validarColunaDeLogradouroNaoReconhecidaGeraSoAviso`)
- ✓ Validar sem CID gera só aviso (`validarSemCidGeraSoAviso`)
- ✓ Validar com CID sem código ou com dois gera erro e com descrição aceita (`validarComCidSemCodigoOuComDoisGeraErroEComDescricaoAceita`)
- ✓ Validar com linha repetida no mesmo horário gera erro (`validarComLinhaRepetidaNoMesmoHorarioGeraErro`)
- ✓ Validar mesmo atendimento com horário diferente gera só aviso de suspeita (`validarMesmoAtendimentoComHorarioDiferenteGeraSoAvisoDeSuspeita`)
- ✓ Validar ignora linhas vazias (`validarIgnoraLinhasVazias`)

### Pacote `repository`

Consultas ao banco (H2 em memória).

#### AtendimentoBPAiRepositoryTest

`src/test/java/br/gov/ses/fillbpai/repository/AtendimentoBPAiRepositoryTest.java` · 4 testes · 4 execuções · ✓ todas passaram

- ✓ Salvar e buscar todos devolve os atendimentos persistidos (`salvarEBuscarTodosDevolveOsAtendimentosPersistidos`)
- ✓ Buscar duplicata encontra pela chave natural exata (`buscarDuplicataEncontraPelaChaveNaturalExata`)
- ✓ Buscar duplicata considera o horário (`buscarDuplicataConsideraOHorario`)
- ✓ Buscar duplicata com SIGTAP diferente não encontra (`buscarDuplicataComSigtapDiferenteNaoEncontra`)

#### EstabelecimentoRepositoryTest

`src/test/java/br/gov/ses/fillbpai/repository/EstabelecimentoRepositoryTest.java` · 5 testes · 5 execuções · ✓ todas passaram

- ✓ Salvar e buscar por código encontra o estabelecimento (`salvarEBuscarPorCodigoEncontraOEstabelecimento`)
- ✓ Buscar por código com código inexistente devolve vazio (`buscarPorCodigoComCodigoInexistenteDevolveVazio`)
- ✓ Buscar por nome ignora acento e caixa (`buscarPorNomeIgnoraAcentoECaixa`)
- ✓ Buscar por nome com nome inexistente devolve vazio (`buscarPorNomeComNomeInexistenteDevolveVazio`)
- ✓ Buscar por nome com nome nulo ou vazio devolve vazio (`buscarPorNomeComNomeNuloOuVazioDevolveVazio`)

#### MedicoRepositoryTest

`src/test/java/br/gov/ses/fillbpai/repository/MedicoRepositoryTest.java` · 2 testes · 2 execuções · ✓ todas passaram

- ✓ Salvar e buscar por CPF encontra o médico (`salvarEBuscarPorCpfEncontraOMedico`)
- ✓ Buscar por CPF com CPF inexistente devolve vazio (`buscarPorCpfComCpfInexistenteDevolveVazio`)

#### PacienteRepositoryTest

`src/test/java/br/gov/ses/fillbpai/repository/PacienteRepositoryTest.java` · 2 testes · 2 execuções · ✓ todas passaram

Usa a persistence unit de teste `bpaPU-test` (H2 em memória, isolada do banco real).

- ✓ Salvar e buscar por CPF encontra o paciente com o endereço junto (`salvarEBuscarPorCpfEncontraOPacienteComOEnderecoJunto`)
- ✓ Buscar por CPF com CPF inexistente devolve vazio (`buscarPorCpfComCpfInexistenteDevolveVazio`)

### Pacote `config`

Pastas de dados do programa, senha do banco (H2 em arquivo, em pasta temporária) e pasta das remessas.

#### DatabaseInitializerTest

`src/test/java/br/gov/ses/fillbpai/config/DatabaseInitializerTest.java` · 2 testes · 2 execuções · ✓ todas passaram

Aviso "O PonteBPA já está aberto" (21/10/2026).

- ✓ Reconhece as duas formas do aviso do H2 (`reconheceAsDuasFormasDoAvisoDoH2`) — Um segundo programa só pode ser simulado com outro processo (dentro do mesmo Java o H2 compartilha o banco aberto); a mensagem "The file is locked" abaixo é a do teste real do pacote, com o PonteBPA aberto duas vezes (21/10/2026)
- ✓ Outros erros não sao confundidos com programa aberto (`outrosErrosNaoSaoConfundidosComProgramaAberto`)

#### PastaRemessasTest

`src/test/java/br/gov/ses/fillbpai/config/PastaRemessasTest.java` · 5 testes · 5 execuções · ✓ todas passaram

Pasta padrão das remessas BPA-I (21/10/2026).

- ✓ Primeira geração abre na pasta padrão e cria a pasta (`primeiraGeracaoAbreNaPastaPadraoECriaAPasta`)
- ✓ Depois de gerar abre na última pasta usada (`depoisDeGerarAbreNaUltimaPastaUsada`)
- ✓ Última pasta apagada volta para a padrão (`ultimaPastaApagadaVoltaParaAPadrao`)
- ✓ Preferencia ilegivel volta para a padrão (`preferenciaIlegivelVoltaParaAPadrao`)
- ✓ Pasta padrão fica em documentos ponte BPA remessas (`pastaPadraoFicaEmDocumentosPonteBpaRemessas`)

#### PastasAppTest

`src/test/java/br/gov/ses/fillbpai/config/PastasAppTest.java` · 6 testes · 6 execuções · ✓ todas passaram

Pastas de dados do programa (21/10/2026).

- ✓ Desenvolvimento usa as pastas do projeto (`desenvolvimentoUsaAsPastasDoProjeto`)
- ✓ Programa instalado usa a pasta do usuario (`programaInstaladoUsaAPastaDoUsuario`)
- ✓ Sem local app data usa app data local do usuario (`semLocalAppDataUsaAppDataLocalDoUsuario`)
- ✓ Preparar pastas cria as pastas e copia o cadastro de médicos do pacote (`prepararPastasCriaAsPastasECopiaOCadastroDeMedicosDoPacote`)
- ✓ Preparar pastas não sobrescreve o cadastro já existente (`prepararPastasNaoSobrescreveOCadastroJaExistente`)
- ✓ Desenvolvimento não cria nem copia nada (`desenvolvimentoNaoCriaNemCopiaNada`)

#### SenhaBancoTest

`src/test/java/br/gov/ses/fillbpai/config/SenhaBancoTest.java` · 7 testes · 7 execuções · ✓ todas passaram

Senha do banco H2 (21/10/2026) — com bancos H2 em arquivo, em pasta temporária.

- ✓ Primeira execucao sem banco gera e guarda a senha (`primeiraExecucaoSemBancoGeraEGuardaASenha`)
- ✓ Execucoes seguintes devolvem a mesma senha (`execucoesSeguintesDevolvemAMesmaSenha`)
- ✓ Banco existente sem senha recebe a senha sem perder dados (`bancoExistenteSemSenhaRecebeASenhaSemPerderDados`)
- ✓ Troca interrompida eh retomada na próxima abertura (`trocaInterrompidaEhRetomadaNaProximaAbertura`)
- ✓ Arquivo de senha perdido com banco protegido da mensagem clara (`arquivoDeSenhaPerdidoComBancoProtegidoDaMensagemClara`)
- ✓ Url não usa auto server (`urlNaoUsaAutoServer`)
- ✓ Senhas geradas sao diferentes (`senhasGeradasSaoDiferentes`)

### Pacote `app`

Inicialização do programa: registro dos erros inesperados.

#### RegistroErrosTest

`src/test/java/br/gov/ses/fillbpai/app/RegistroErrosTest.java` · 3 testes · 3 execuções · ✓ todas passaram

Registro dos erros inesperados do programa instalado (21/10/2026).

- ✓ Grava o erro com local e pilha e acrescenta os seguintes (`gravaOErroComLocalEPilhaEAcrescentaOsSeguintes`)
- ✓ Arquivo grande eh recomecado guardando o anterior (`arquivoGrandeEhRecomecadoGuardandoOAnterior`)
- ✓ Falha ao gravar não lanca exceção (`falhaAoGravarNaoLancaExcecao`)
