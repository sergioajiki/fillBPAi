# Gera o pacote de instalação do PonteBPA (fase 2 da instalação, 21/10/2026):
# uma pasta com o Java embutido (jpackage --type app-image), compactada em .zip,
# com o "LEIA-ME - Instalação.txt" na raiz.
#
# Uso (na raiz do projeto, num PowerShell):
#     powershell -ExecutionPolicy Bypass -File pacote\gerar-pacote.ps1
# Opções:
#     -SemTestes   pula os testes automatizados (não recomendado para o pacote oficial)
#
# Requisitos: JDK 21 com jpackage (JAVA_HOME ou jpackage no PATH). Não precisa de WiX
# nem de administrador. O resultado fica em target\pacote\:
#     PonteBPA-<versão>.zip           pacote para levar ao computador novo
#     PonteBPA-<versão>.zip.sha256    código de conferência do arquivo (SHA-256)
#
# O pacote roda em modo instalado (-Dpontebpa.instalado=true): banco, logs,
# cadastros e senha ficam em %LOCALAPPDATA%\PonteBPA (ver config/PastasApp.java).

param([switch]$SemTestes)

$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

function Passo($texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }

function Executar($programa, [string[]]$argumentos) {
    & $programa @argumentos
    if ($LASTEXITCODE -ne 0) { throw "Falhou: $programa $($argumentos -join ' ') (código $LASTEXITCODE)" }
}

# ---- Versão e nome do projeto (pom.xml) ----
[xml]$pom = Get-Content (Join-Path $raiz 'pom.xml') -Encoding UTF8
$versao = $pom.project.version
$artefato = $pom.project.artifactId
$jarPrincipal = "$artefato-$versao.jar"

# ---- jpackage do JDK 21 ----
$jpackage = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
} else {
    $cmd = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($cmd) { $jpackage = $cmd.Source }
}
if (-not $jpackage) { throw 'jpackage não encontrado. Defina JAVA_HOME apontando para um JDK 21.' }
$versaoJdk = (& $jpackage --version).Trim()
if (-not $versaoJdk.StartsWith('21')) { throw "O pacote deve usar o JDK 21 (jpackage encontrado: $versaoJdk em $jpackage)." }

Write-Host "PonteBPA $versao  |  jpackage $versaoJdk ($jpackage)"

$pastaPacote = Join-Path $raiz 'target\pacote'
$entrada = Join-Path $pastaPacote 'entrada'
$saida = Join-Path $pastaPacote 'saida'
$pastaApp = Join-Path $saida 'PonteBPA'
$zip = Join-Path $pastaPacote "PonteBPA-$versao.zip"

# ---- 1. Compilar (com testes) ----
Passo 'Compilando o projeto'
$argsMaven = @('-q', 'clean', 'package')
if ($SemTestes) { $argsMaven += '-DskipTests' } else { Write-Host '(com os testes automatizados - leva alguns minutos)' }
Executar (Join-Path $raiz 'mvnw.cmd') $argsMaven

# ---- 1b. Documentação gerada (técnica e testes), para o pacote sair com ela em dia ----
Passo 'Atualizando a documentação técnica e a dos testes'
$node = Get-Command node.exe -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Warning 'Node.js não encontrado: docs\DOCUMENTACAO_TECNICA.md e docs\DOCUMENTACAO_TESTES.md não foram atualizadas.'
} else {
    Executar $node.Source @((Join-Path $raiz 'docs\gerar-documentacao.js'))
    if ($SemTestes) {
        Write-Warning 'Sem testes: a documentação dos testes continua com a execução anterior.'
    } else {
        Executar $node.Source @((Join-Path $raiz 'docs\gerar-documentacao-testes.js'))
    }
}

# ---- 2. Juntar o programa e as bibliotecas ----
Passo 'Juntando o programa e as bibliotecas'
Executar (Join-Path $raiz 'mvnw.cmd') @('-q', 'dependency:copy-dependencies', '-DincludeScope=runtime', "-DoutputDirectory=$entrada")
Copy-Item (Join-Path $raiz "target\$jarPrincipal") $entrada
$qtdJars = (Get-ChildItem $entrada -Filter *.jar).Count
Write-Host "$qtdJars bibliotecas"

# ---- 3. Pasta com o Java embutido ----
Passo 'Gerando a pasta do programa com o Java embutido (jpackage)'
Executar $jpackage @(
    '--type', 'app-image',
    '--name', 'PonteBPA',
    '--app-version', $versao,
    '--vendor', 'SES-MS - Superintendencia de Saude Digital',
    '--description', 'PonteBPA - importacao de atendimentos e geracao do BPA-I',
    '--copyright', 'Uso interno - SES-MS',
    '--icon', (Join-Path $raiz 'pacote\PonteBPA.ico'),
    '--input', $entrada,
    '--main-jar', $jarPrincipal,
    '--main-class', 'br.gov.ses.fillbpai.app.Inicio',
    '--java-options', '-Dpontebpa.instalado=true',
    '--java-options', '-Djava.net.useSystemProxies=true',
    '--dest', $saida
)

# ---- 4. Orientações de instalação (versão preenchida, quebras de linha do Windows) ----
Passo 'Incluindo as orientações de instalação'
$leiaMe = Get-ChildItem (Join-Path $raiz 'pacote') -Filter 'LEIA-ME*.txt' | Select-Object -First 1
$texto = [System.IO.File]::ReadAllText($leiaMe.FullName, [System.Text.Encoding]::UTF8)
$texto = $texto.Replace('{{VERSAO}}', $versao) -replace "`r?`n", "`r`n"
[System.IO.File]::WriteAllText((Join-Path $pastaApp $leiaMe.Name), $texto, (New-Object System.Text.UTF8Encoding $true))

# ---- 5. Conferências ----
Passo 'Conferindo o pacote'
$exe = Join-Path $pastaApp 'PonteBPA.exe'
if (-not (Test-Path $exe)) { throw 'PonteBPA.exe não foi gerado.' }
$cfg = Get-Content (Join-Path $pastaApp 'app\PonteBPA.cfg') -Raw
foreach ($opcao in '-Dpontebpa.instalado=true', '-Djava.net.useSystemProxies=true') {
    if ($cfg -notlike "*$opcao*") { throw "Opção $opcao ausente em app\PonteBPA.cfg." }
}
if (-not (Get-ChildItem (Join-Path $pastaApp 'app') -Filter 'javafx-graphics-*-win.jar')) {
    throw 'Biblioteca do JavaFX para Windows ausente.'
}
# Bibliotecas do Visual C++ que vêm com o Java: precisam estar presentes e assinadas pela Microsoft
foreach ($dll in 'vcruntime140.dll', 'msvcp140.dll') {
    $arquivo = Join-Path $pastaApp "runtime\bin\$dll"
    if (-not (Test-Path $arquivo)) { throw "$dll ausente no Java embutido." }
    $assinatura = Get-AuthenticodeSignature $arquivo
    if ($assinatura.Status -ne 'Valid' -or $assinatura.SignerCertificate.Subject -notlike '*Microsoft*') {
        throw "$dll sem assinatura válida da Microsoft."
    }
}
Write-Host 'PonteBPA.exe, opções do modo instalado, JavaFX e Visual C++ (assinado pela Microsoft): ok'

# ---- 6. Compactar ----
Passo 'Compactando'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $zip) { Remove-Item $zip }
# Entradas com "/" (padrão do formato zip): o CreateFromDirectory do PowerShell 5.1 grava "\",
# que outros programas de descompactação transformam em parte do nome do arquivo
$arquivoZip = [System.IO.Compression.ZipFile]::Open($zip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($arquivo in Get-ChildItem $pastaApp -Recurse -File) {
        $relativo = $arquivo.FullName.Substring($pastaApp.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($arquivoZip, $arquivo.FullName, $relativo,
            [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $arquivoZip.Dispose()
}
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash
"$hash  PonteBPA-$versao.zip" | Out-File "$zip.sha256" -Encoding ascii

$tamanhoMb = [Math]::Round((Get-Item $zip).Length / 1MB, 1)
$pastaMb = [Math]::Round(((Get-ChildItem $pastaApp -Recurse -File | Measure-Object Length -Sum).Sum) / 1MB, 1)

Write-Host "`nPacote pronto:" -ForegroundColor Green
Write-Host "  $zip  ($tamanhoMb MB; $pastaMb MB depois de extraído)"
Write-Host "  SHA-256: $hash"
Write-Host '  Confira o SHA-256 no computador novo (PowerShell: Get-FileHash PonteBPA-<versão>.zip) antes de extrair.'

# ---- 7. Projeto em dia com o Git (pacote definitivo = último commit, documentação incluída) ----
$git = $null
$comando = Get-Command git.exe -ErrorAction SilentlyContinue
if ($comando) { $git = $comando.Source }
if (-not $git) {
    # Git for Windows instalado só para o usuário ou fora do PATH do PowerShell
    $git = @("$env:LOCALAPPDATA\Programs\Git\cmd\git.exe", "$env:LOCALAPPDATA\Programs\Git\mingw64\bin\git.exe",
        "$env:ProgramFiles\Git\cmd\git.exe", "$env:USERPROFILE\AppData\Local\Programs\Git\cmd\git.exe",
        (Join-Path (Split-Path $raiz -Qualifier) '\Users\*\AppData\Local\Programs\Git\cmd\git.exe')) |
        ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } | Select-Object -First 1 -ExpandProperty FullName
}
if ($git) {
    $commit = (& $git -C $raiz rev-parse --short HEAD 2>$null)
    $pendentes = @(& $git -C $raiz status --porcelain 2>$null)
    $statusOk = ($LASTEXITCODE -eq 0) -and $commit
    if (-not $statusOk) {
        Write-Warning ('O Git não conseguiu ler o projeto (ex.: pasta de outro usuário do Windows - "dubious ownership"); ' +
            'não foi possível conferir se o pacote corresponde ao último commit.')
    } elseif ($pendentes.Count -gt 0) {
        Write-Warning ("Há $($pendentes.Count) alteração(ões) não commitada(s) - este pacote NÃO corresponde ao commit $commit. " +
            'Para o pacote definitivo: revise o README.md, commite (inclusive a documentação gerada agora) e gere de novo.')
        $pendentes | Select-Object -First 20 | ForEach-Object { Write-Host "    $_" }
    } else {
        Write-Host "  Gerado a partir do commit $commit, sem alterações pendentes (documentação em dia)." -ForegroundColor Green
    }
} else {
    Write-Warning 'Git não encontrado: não foi possível conferir se o projeto está em dia com o último commit.'
}
