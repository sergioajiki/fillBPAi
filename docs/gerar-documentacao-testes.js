// Gera docs/DOCUMENTACAO_TESTES.md a partir do código dos testes e dos
// relatórios do Maven (target/surefire-reports).
//
// Uso (na raiz do projeto, depois de rodar os testes):
//   mvnw.cmd test
//   node docs/gerar-documentacao-testes.js
//
// Para cada classe de teste de src/test/java/br/gov/ses/fillbpai/: a
// descrição (javadoc da classe), as seções marcadas no código
// (// ===== ... =====) e cada teste (@Test / @ParameterizedTest) com uma frase
// montada do nome do método, o comentário que estiver acima dele, os valores
// dos casos parametrizados e o resultado da última execução. O cabeçalho
// (como rodar, ambiente dos testes) fica em docs/documentacao-testes-cabecalho.md
// e é editado à mão; {{DATA}} vira a data do dia.
//
// Só são lidos os relatórios das classes de teste que existem: relatórios
// antigos de testes apagados (ex.: testes de sondagem) que ficam em target/
// enquanto não se roda "mvn clean" não entram na contagem.

const fs = require('fs');
const path = require('path');

const RAIZ_TESTES = 'src/test/java/br/gov/ses/fillbpai';
const RELATORIOS = 'target/surefire-reports';

const PACOTES = [
  ['util', 'Regras por campo (CPF, CNS, CEP, datas, endereço, raça, etnia…) — funções puras, sem banco'],
  ['service', 'Análise da planilha, importação, geração do BPA-I, logs e janela Sobre'],
  ['repository', 'Consultas ao banco (H2 em memória)'],
];

// Acentos recolocados nas frases montadas a partir dos nomes dos métodos
const ACENTOS = {
  nao: 'não', numero: 'número', numeros: 'números', codigo: 'código', codigos: 'códigos', cabecalho: 'cabeçalho',
  invalido: 'inválido', invalida: 'inválida', invalidos: 'inválidos', invalidas: 'inválidas',
  valido: 'válido', valida: 'válida', validos: 'válidos', validas: 'válidas',
  municipio: 'município', municipios: 'municípios', digito: 'dígito', digitos: 'dígitos',
  analise: 'análise', importacao: 'importação', reimportacao: 'reimportação', geracao: 'geração',
  competencia: 'competência', medico: 'médico', medicos: 'médicos', raca: 'raça', indigena: 'indígena',
  situacao: 'situação', conteudo: 'conteúdo', endereco: 'endereço', numeracao: 'numeração',
  ja: 'já', so: 'só', tambem: 'também', ate: 'até', mes: 'mês', apos: 'após', alem: 'além', esta: 'está',
  horario: 'horário', servico: 'serviço', periodo: 'período', inicio: 'início', pais: 'país', mae: 'mãe',
  ultima: 'última', ultimo: 'último', ultimas: 'últimas', ultimos: 'últimos',
  unico: 'único', unica: 'única', unicos: 'únicos', proxima: 'próxima', proximo: 'próximo',
  minimo: 'mínimo', maximo: 'máximo', pagina: 'página', tres: 'três', util: 'útil', area: 'área',
  validacao: 'validação', padrao: 'padrão', informacao: 'informação', orgao: 'órgão', versao: 'versão',
  compilacao: 'compilação', variacao: 'variação', variacoes: 'variações', abreviacao: 'abreviação',
  conferencia: 'conferência', ciencia: 'ciência', vigencia: 'vigência', sequencia: 'sequência',
  referencia: 'referência', ausencia: 'ausência', presenca: 'presença', diferenca: 'diferença',
  mascara: 'máscara', criterio: 'critério', historico: 'histórico', repeticao: 'repetição',
  excecao: 'exceção', posicao: 'posição', posicoes: 'posições', descricao: 'descrição', separacao: 'separação',
  migracao: 'migração', remocao: 'remoção', conversao: 'conversão', funcao: 'função', rotulo: 'rótulo',
  titulo: 'título', titulos: 'títulos', explicacao: 'explicação', ordenacao: 'ordenação',
  duplicacao: 'duplicação', correcao: 'correção', sugestao: 'sugestão', normalizacao: 'normalização',
  atualizacao: 'atualização', heranca: 'herança', saida: 'saída', acentuacao: 'acentuação',
  obrigatorio: 'obrigatório', obrigatoria: 'obrigatória', ambigua: 'ambígua', ambiguo: 'ambíguo',
  homonimo: 'homônimo', homonimos: 'homônimos', simbolo: 'símbolo', simbolos: 'símbolos',
  parenteses: 'parênteses', hifen: 'hífen', estavel: 'estável', estaveis: 'estáveis',
  automatico: 'automático', automatica: 'automática', especifico: 'específico', especifica: 'específica',
  especificos: 'específicos', generico: 'genérico', publico: 'público', basico: 'básico',
  alfanumerico: 'alfanumérico', numerico: 'numérico', numerica: 'numérica', logica: 'lógica',
  secoes: 'seções', resolucao: 'resolução', pontuacao: 'pontuação', notacao: 'notação', negacao: 'negação',
  gravacao: 'gravação', derivacao: 'derivação', classificacao: 'classificação', abreviacoes: 'abreviações',
  documentacao: 'documentação', cpfs: 'CPFs',
};
const SIGLAS = new Set(['cpf', 'cns', 'cep', 'ibge', 'cid', 'cbo', 'ine', 'bpa', 'sigtap', 'csv', 'dv', 'dvs',
  'ddd', 'ddi', 'sn', 'uf', 'ms', 'h2', 'txt', 'rn', 'cnes', 'cnpj', 'xlsx', 'dne', 'dto', 'api', 'apis', 'sus',
  'sia', 'iso', 'utf', 'crlf', 'lf', 'cr', 'n', 's', 'pdf', 'html']);

function frase(nome) {
  const palavras = nome
    .replace(/([a-z])([A-Z0-9])/g, '$1 $2')
    .replace(/([0-9])([A-Za-z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .toLowerCase()
    .split(/\s+/);
  if (palavras[0] === 'is' && palavras.length > 1) {
    palavras.shift(); // isValido… → "Válido…"
  }
  const texto = palavras
    .map(p => (SIGLAS.has(p) ? p.toUpperCase() : (ACENTOS[p] || p)))
    .join(' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function umaLinha(s) {
  return s.replace(/\s+/g, ' ').trim();
}

function limparJavadoc(doc) {
  return umaLinha(doc
    .replace(/^\s*\/\*\*|\*\/\s*$/g, '')
    .split('\n').map(l => l.replace(/^\s*\* ?/, '')).join(' ')
    .split(/\s@(param|return|throws)\b/)[0]
    .replace(/\{@(?:code|link|linkplain|literal)\s+([^}]*)\}/g, (m, a) => '`' + a.trim() + '`')
    .replace(/<\/?(p|ul|ol|li|b|i|em|strong)>/g, ' ')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&'));
}

// Comentário logo acima das anotações do teste (javadoc ou linhas //), sem marcadores de seção
function comentarioAcima(src, inicio) {
  const linhas = src.slice(0, inicio).split('\n');
  let i = linhas.length - 1;
  if (linhas[i].trim() === '') i--;
  if (i >= 0 && linhas[i].trim().endsWith('*/')) {
    let j = i;
    while (j >= 0 && !linhas[j].includes('/*')) j--;
    return limparJavadoc(linhas.slice(j, i + 1).join('\n'));
  }
  const coletadas = [];
  while (i >= 0 && /^\s*\/\//.test(linhas[i]) && !/=====/.test(linhas[i])) {
    coletadas.unshift(linhas[i].replace(/^\s*\/\/\s?/, ''));
    i--;
  }
  return umaLinha(coletadas.join(' '));
}

// Valores de @ValueSource / @CsvSource (texto como está no código, encurtado)
function casosDaAnotacao(anotacoes) {
  const m = anotacoes.match(/@(ValueSource|CsvSource)\s*\(([\s\S]*)\)\s*$/m);
  if (!m) return '';
  const texto = umaLinha(m[2])
    .replace(/^delimiter\s*=\s*'.'\s*,\s*/, '')
    .replace(/^(strings|value)\s*=\s*/, '')
    .replace(/^\{\s*|\s*\}$/g, '');
  return texto.length > 220 ? texto.slice(0, 217) + '…' : texto;
}

function lerTestes(arquivo) {
  const src = fs.readFileSync(arquivo, 'utf8').replace(/\r\n/g, '\n');
  const classe = path.basename(arquivo, '.java');

  const mClasse = src.match(/(\/\*\*[\s\S]*?\*\/)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:final\s+)?class\s+\w+/);
  const descricao = mClasse ? limparJavadoc(mClasse[1]) : '';

  const secoes = [...src.matchAll(/\n[ \t]*\/\/\s*=====\s*(.*?)\s*=====/g)]
    .map(m => ({ pos: m.index, titulo: m[1].trim() }));

  const testes = [];
  for (const m of src.matchAll(/\n([ \t]+)@(Test|ParameterizedTest)\b/g)) {
    const inicio = m.index + 1;
    const resto = src.slice(inicio);
    const mv = resto.match(/void\s+(\w+)\s*\(/);
    if (!mv) continue;
    const anotacoes = resto.slice(0, mv.index);
    const secao = secoes.filter(s => s.pos < inicio).pop();
    testes.push({
      nome: mv[1],
      parametrizado: m[2] === 'ParameterizedTest',
      comentario: comentarioAcima(src, inicio),
      casos: m[2] === 'ParameterizedTest' ? casosDaAnotacao(anotacoes) : '',
      secao: secao ? secao.titulo : '',
    });
  }

  return { classe, descricao, testes };
}

// Resultado da última execução: execuções, falhas e tempo por método
function lerRelatorio(pacote, classe) {
  const xml = path.join(RELATORIOS, `TEST-br.gov.ses.fillbpai.${pacote}.${classe}.xml`);
  if (!fs.existsSync(xml)) return null;
  const conteudo = fs.readFileSync(xml, 'utf8');
  const atributo = nome => {
    const m = conteudo.match(new RegExp(`<testsuite[^>]*\\b${nome}="([^"]*)"`));
    return m ? m[1] : '0';
  };
  const porMetodo = {};
  for (const bloco of conteudo.split('<testcase ').slice(1)) {
    const nome = (bloco.match(/^name="([^"]*)"/) || [])[1];
    if (!nome) continue;
    const base = nome.replace(/\(.*$/, '');
    const r = porMetodo[base] || (porMetodo[base] = { execucoes: 0, falhas: 0 });
    r.execucoes++;
    const fim = bloco.indexOf('</testcase>');
    const corpo = bloco.slice(0, fim < 0 ? bloco.length : fim);
    if (/<(failure|error)\b/.test(corpo)) r.falhas++;
  }
  return {
    tempo: parseFloat(atributo('time')),
    execucoes: parseInt(atributo('tests')),
    falhas: parseInt(atributo('errors')) + parseInt(atributo('failures')),
    pulados: parseInt(atributo('skipped')),
    porMetodo,
    quando: fs.statSync(xml).mtime,
  };
}

const classes = [];
for (const [pacote] of PACOTES) {
  const dir = path.join(RAIZ_TESTES, pacote);
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter(f => f.endsWith('Test.java')).sort()) {
    const info = lerTestes(path.join(dir, f));
    classes.push({ pacote, ...info, relatorio: lerRelatorio(pacote, info.classe) });
  }
}

const out = [];

// ---- Resultado da última execução ----
const comRelatorio = classes.filter(c => c.relatorio);
const soma = campo => comRelatorio.reduce((s, c) => s + c.relatorio[campo], 0);
const total = {
  metodos: classes.reduce((s, c) => s + c.testes.length, 0),
  execucoes: soma('execucoes'),
  falhas: soma('falhas'),
  pulados: soma('pulados'),
  tempo: soma('tempo'),
};
const ultima = comRelatorio.map(c => c.relatorio.quando).sort((a, b) => b - a)[0];
const marcaResultado = n => (n ? '✗ ' + n : '✓');

out.push('## Resultado da última execução\n');
if (ultima) {
  out.push(`Execução de ${ultima.toLocaleDateString('pt-BR')} às `
    + `${ultima.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}: `
    + `**${total.execucoes} execuções** de ${total.metodos} testes em ${classes.length} classes — `
    + (total.falhas === 0 ? '**todas passaram** ✓' : `**${total.falhas} com falha** ✗`)
    + (total.pulados ? `, ${total.pulados} pulada(s)` : '')
    + ` (${total.tempo.toFixed(1)} s somando as classes).\n`);
} else {
  out.push('Sem relatórios em `target/surefire-reports` — rodar os testes antes de gerar.\n');
}

out.push('| Classe | Pacote | Testes | Execuções | Resultado | Tempo |');
out.push('|---|---|---:|---:|:---:|---:|');
for (const c of classes) {
  const r = c.relatorio;
  out.push(`| [${c.classe}](#${c.classe.toLowerCase()}) | ${c.pacote} | ${c.testes.length} | `
    + `${r ? r.execucoes : '—'} | ${r ? marcaResultado(r.falhas) : '—'} | ${r ? r.tempo.toFixed(2) + ' s' : '—'} |`);
}
out.push(`| **Total** | | **${total.metodos}** | **${total.execucoes}** | ${marcaResultado(total.falhas)} | `
  + `${total.tempo.toFixed(1)} s |`);
out.push('\n"Testes" = métodos de teste; "Execuções" conta cada caso dos testes parametrizados.\n\n---\n');

// ---- Testes por classe ----
out.push('## Testes por classe\n');
for (const [pacote, descricaoPacote] of PACOTES) {
  const doPacote = classes.filter(c => c.pacote === pacote);
  if (!doPacote.length) continue;
  out.push(`### Pacote \`${pacote}\`\n\n${descricaoPacote}.\n`);
  for (const c of doPacote) {
    const r = c.relatorio;
    out.push(`#### ${c.classe}\n`);
    out.push(`\`src/test/java/br/gov/ses/fillbpai/${c.pacote}/${c.classe}.java\` · ${c.testes.length} testes`
      + (r ? ` · ${r.execucoes} execuções · ${r.falhas ? '✗ ' + r.falhas + ' com falha' : '✓ todas passaram'}` : '')
      + '\n');
    if (c.descricao) out.push(c.descricao + '\n');
    let secaoAtual = null;
    for (const t of c.testes) {
      if (t.secao !== secaoAtual) {
        secaoAtual = t.secao;
        if (secaoAtual) out.push(`\n**${secaoAtual}**\n`);
      }
      const res = r && r.porMetodo[t.nome];
      const marca = !res ? '—' : (res.falhas ? '✗' : '✓');
      let linha = `- ${marca} ${frase(t.nome)} (\`${t.nome}\`)`;
      if (t.comentario) linha += ` — ${t.comentario}`;
      if (t.parametrizado) {
        linha += ` · *${res ? res.execucoes : '?'} casos*` + (t.casos ? `: ${t.casos.replace(/\|/g, '\\|')}` : '');
      }
      out.push(linha);
    }
    out.push('');
  }
}

const cabecalho = fs.readFileSync('docs/documentacao-testes-cabecalho.md', 'utf8')
  .replace('{{DATA}}', new Date().toLocaleDateString('pt-BR'));
const destino = process.argv[2] || 'docs/DOCUMENTACAO_TESTES.md';
fs.writeFileSync(destino, cabecalho + out.join('\n'));
console.log(`Documentação dos testes gerada em ${destino} — ${total.metodos} testes, `
  + `${total.execucoes} execuções, ${total.falhas} falha(s)`);
