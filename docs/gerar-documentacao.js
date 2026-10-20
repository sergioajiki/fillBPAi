// Gera docs/DOCUMENTACAO_TECNICA.md a partir dos javadocs do código.
//
// Uso (na raiz do projeto):  node docs/gerar-documentacao.js
//
// O texto vem da documentação (javadoc) de cada classe e método de
// src/main/java/br/gov/ses/fillbpai/, pacote por pacote. Entram os métodos
// públicos/de pacote e os privados que têm javadoc; getters/setters sem
// javadoc ficam de fora. O cabeçalho (introdução, sumário e a tabela de
// pastas e arquivos em execução) fica em docs/documentacao-tecnica-cabecalho.md
// e é editado à mão; {{DATA}} vira a data do dia.
//
// Regenerar sempre que a documentação dos métodos mudar.

const fs=require('fs'),path=require('path');
const root='src/main/java/br/gov/ses/fillbpai';
const order=['app','config','controller','ui','service','util','repository','model','dto'];
function clean(doc){ // javadoc text -> markdown-ish one paragraph(s)
  let t=doc.replace(/^\/\*\*|\*\/$/g,'').split('\n').map(l=>l.replace(/^\s*\* ?/,'')).join('\n');
  t=t.split(/\n\s*@(param|return|throws)/)[0];
  t=t.replace(/\{@(?:code|link|linkplain|literal)\s+([^}]*)\}/g,(m,a)=>'`'+a.trim()+'`');
  t=t.replace(/<\/?(p|ul|ol)>/g,'\n').replace(/<li>/g,'\n- ').replace(/<\/li>/g,'').replace(/<\/?(b|strong)>/g,'**').replace(/<\/?(i|em)>/g,'*').replace(/<br\s*\/?>/g,'\n').replace(/<\/?pre>/g,'').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');
  // join paragraph lines
  return t.split(/\n\s*\n/).map(p=>p.split('\n').map(s=>s.trim()).filter(Boolean).join(' ').replace(/ - /g,'\n- ').trim()).filter(Boolean).join('\n\n').replace(/^\n- /,'- ');
}
function oneLine(s){return s.replace(/\s+/g,' ').trim();}
let out=[];
for(const pkg of order){
  const dir=path.join(root,pkg); if(!fs.existsSync(dir)) continue;
  out.push(`## Pacote \`${pkg}\`\n`);
  for(const f of fs.readdirSync(dir).filter(f=>f.endsWith('.java')).sort()){
    const src=fs.readFileSync(path.join(dir,f),'utf8');
    const cls=f.replace('.java','');
    out.push(`### \`${cls}\`\n\`src/main/java/br/gov/ses/fillbpai/${pkg}/${f}\`\n`);
    const cm=src.match(/(\/\*\*[\s\S]*?\*\/)\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:final\s+|abstract\s+)*(class|record|enum|interface)\s+\w+/);
    if(cm) out.push(clean(cm[1])+'\n');
    // members: javadoc + signature (methods, constructors, records, enums)
    const re=/(\/\*\*(?:(?!\*\/)[\s\S])*?\*\/)?\s*((?:@\w+(?:\([^)]*\))?\s*)*)((?:public|protected|private|static|final|synchronized|abstract|default|\s)*)\s*(?:<[^>]+>\s*)?([\w.<>\[\], ?]+?\s+)?(\w+)\s*\(([^)]*)\)\s*(?:throws [\w., ]+)?\s*\{/g;
    let m, items=[];
    const kw=new Set(['if','for','while','switch','catch','synchronized','return','new','else','try','do']);
    while((m=re.exec(src))){
      const name=m[5]; if(kw.has(name)) continue;
      const mods=m[3]||''; const ret=(m[4]||'').trim();
      if(ret && /^(return|new|else|throw)$/.test(ret)) continue;
      // indentation level check: method declarations at 1 indent (tab or 4 spaces) or nested class 2
      const lineStart=src.lastIndexOf('\n',m.index+m[0].indexOf(m[5]+"(",(m[1]||"").length))+1;
      const ind=src.slice(lineStart).match(/^[	 ]*/)[0].replace(/    /g,"	").replace(/ /g,"").length;
      if(ind<1||ind>2) continue;
      if(!ret && name!==cls && !/^[A-Z]/.test(name)) continue;
      const isPrivate=/private/.test(mods);
      const doc=m[1];
      if(isPrivate && !doc) continue;
      if(/\boverride\b/i.test(m[2]) && !doc && /^(toString|equals|hashCode)$/.test(name)) continue;
      const params=oneLine(m[6]).replace(/final /g,'');
      let dono="";
      if(ind===2){
        const antes=src.slice(0,m.index);
        const nm=[...antes.matchAll(/\n(?:\t| {4})[a-z ]*(?:class|record|enum)\s+(\w+)/g)].pop();
        if(nm) dono=nm[1]+".";
      }
      const sig=ret?`${dono}${name}(${params}): ${ret}`:`${name}(${params}) (construtor)`;
      const vis=isPrivate?' *(privado)*':'';
      const desc=doc?oneLine(clean(doc).replace(/\n- /g,'; ')):'';
      // skip trivial getters/setters without doc
      if(!doc && /^(get|set|is)[A-Z]/.test(name)) continue;
      items.push(`- \`${sig}\`${vis}${desc?' — '+desc:''}`);
    }
    // records/enums nested
    const rre=/(\/\*\*(?:(?!\*\/)[\s\S])*?\*\/)\s*(?:public\s+|private\s+)?(?:static\s+)?(record|enum)\s+(\w+)(\([^)]*\))?/g;
    while((m=rre.exec(src))){ if(m[3]===cls) continue; items.push(`- ${m[2]} \`${m[3]}${m[4]?oneLine(m[4]):''}\` — ${oneLine(clean(m[1]).replace(/\n- /g,'; '))}`);}
    const getters=(src.match(/public [\w<>]+ (get|is)[A-Z]\w*\(\)/g)||[]).length;
    if(items.length){ out.push('**Métodos:**'); out.push(items.join('\n')); }
    if(getters && (pkg==='model'||pkg==='dto')) out.push(`\nAlém disso, getters/setters dos campos.`);
    out.push('');
  }
  out.push('---\n');
}

const cabecalho = fs.readFileSync('docs/documentacao-tecnica-cabecalho.md', 'utf8')
    .replace('{{DATA}}', new Date().toLocaleDateString('pt-BR'));
const destino = process.argv[2] || 'docs/DOCUMENTACAO_TECNICA.md';
fs.writeFileSync(destino, cabecalho + out.join('\n'));
console.log('Documentação gerada em ' + destino);
