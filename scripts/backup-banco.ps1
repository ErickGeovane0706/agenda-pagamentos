# Backup do Postgres de produção.
#
# Por que existe: o plano Hobby do Railway NÃO inclui backup. Este dump não é
# um complemento do backup da plataforma — ele É o backup. Se este script
# parar de rodar, não existe cópia dos dados dos clientes em lugar nenhum.
#
# A credencial fica FORA do repositório, em $PASTA\.database-url, e este script
# só a lê. Nunca colar a connection string aqui dentro.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\backup-banco.ps1

$ErrorActionPreference = 'Stop'

$PASTA     = 'C:\Users\08001\Backups'
$MANTER    = 14          # dumps a preservar; o resto é apagado
# A produção no Railway roda Postgres 18. pg_dump recusa banco de major MAIOR
# que a dele ("server version mismatch"), então esta imagem precisa acompanhar
# o servidor — nunca o docker-compose local, que está em 16.
$IMAGEM    = 'postgres:18-alpine'

$arquivoUrl = Join-Path $PASTA 'DATABASE_PUBLIC_URL.txt'

if (-not (Test-Path $PASTA)) { New-Item -ItemType Directory -Force -Path $PASTA | Out-Null }

if (-not (Test-Path $arquivoUrl)) {
    Write-Error @"
Falta a credencial. Crie o arquivo:
  $arquivoUrl
com UMA linha: a DATABASE_PUBLIC_URL do Postgres
(painel do Railway > servico Postgres > Variables).

ATENCAO: e a PUBLIC_URL, nao a DATABASE_URL. Esta ultima aponta para a rede
privada do Railway (*.railway.internal) e so resolve de dentro de la - do seu
computador ela nem existe. A publica aponta para um proxy (*.proxy.rlwy.net)
com porta alta. Se ela nao aparecer na lista, ligue o Public Networking nas
Settings do servico Postgres.
"@
}

# Tolerante ao que o Bloco de Notas costuma deixar: BOM, aspas coladas ao
# copiar do painel, espaço no fim e mais de uma linha.
$url = (Get-Content $arquivoUrl -Raw)
$url = $url -replace "^﻿", ''
$url = ($url -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1).Trim().Trim('"').Trim("'")

if ([string]::IsNullOrWhiteSpace($url)) { Write-Error "O arquivo $arquivoUrl esta vazio." }
if ($url -notmatch '^postgres(ql)?://') {
    Write-Error "O conteudo nao parece uma connection string (deve comecar com postgresql://)."
}
if ($url -match 'railway\.internal') {
    Write-Error @"
Essa e a URL INTERNA (railway.internal) - ela so resolve dentro da rede do
Railway e nao funciona do seu computador. Pegue a DATABASE_PUBLIC_URL, que
aponta para *.proxy.rlwy.net com porta alta.
"@
}

$carimbo = Get-Date -Format 'yyyy-MM-dd_HHmm'
$nome    = "agenda-$carimbo.dump"

Write-Host "[backup] gerando $nome ..."

# -Fc = formato custom: comprimido e restauravel com pg_restore seletivo.
# A URL vai por variavel de ambiente para nao aparecer na linha de comando
# (onde outros processos da maquina conseguiriam ler).
# As aspas simples preservam $PGURL para o shell do container resolver; só o
# nome do arquivo é interpolado aqui pelo PowerShell.
$comando = 'pg_dump "$PGURL" -Fc -f /saida/' + $nome

docker run --rm -e PGURL="$url" -v "${PASTA}:/saida" $IMAGEM sh -c $comando

if ($LASTEXITCODE -ne 0) { Write-Error "pg_dump falhou (codigo $LASTEXITCODE). Backup NAO gerado." }

$destino = Join-Path $PASTA $nome
if (-not (Test-Path $destino)) { Write-Error "pg_dump terminou sem erro mas o arquivo nao existe: $destino" }

$tamanho = (Get-Item $destino).Length
if ($tamanho -lt 1024) { Write-Error "Dump gerado com $tamanho bytes - pequeno demais para ser valido." }

Write-Host ("[backup] ok: {0} ({1:N1} MB)" -f $nome, ($tamanho / 1MB))

# Poda: mantem os N mais recentes.
$antigos = Get-ChildItem $PASTA -Filter 'agenda-*.dump' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $MANTER
foreach ($a in $antigos) {
    Remove-Item $a.FullName -Force
    Write-Host "[backup] removido antigo: $($a.Name)"
}

Write-Host "[backup] concluido. $(( Get-ChildItem $PASTA -Filter 'agenda-*.dump').Count) dumps em $PASTA"
