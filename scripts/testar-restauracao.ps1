# Restaura o dump mais recente num Postgres descartável e confere se os dados
# chegaram lá.
#
# Por que existe: backup nunca restaurado é hipótese, não backup. A hora de
# descobrir que o arquivo não presta não é quando ele é a única cópia que sobrou.
#
# Não toca em produção em momento nenhum — sobe um container isolado numa porta
# alta, restaura, conta as linhas e destrói tudo.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\testar-restauracao.ps1

$ErrorActionPreference = 'Stop'

$PASTA  = 'C:\Users\08001\Backups'
$PORTA  = 55432
$SENHA  = 'teste-restauracao'
$NOME   = 'agenda-teste-restore'
$IMAGEM = 'postgres:18-alpine'   # acompanha a producao; ver nota em backup-banco.ps1

$dump = Get-ChildItem $PASTA -Filter 'agenda-*.dump' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $dump) { Write-Error "Nenhum dump encontrado em $PASTA. Rode scripts\backup-banco.ps1 antes." }

Write-Host "[restore] testando $($dump.Name) ($('{0:N1}' -f ($dump.Length/1MB)) MB)"

try { docker rm -f $NOME 2>$null | Out-Null } catch {}

docker run --rm -d --name $NOME -e POSTGRES_PASSWORD=$SENHA -p "${PORTA}:5432" $IMAGEM | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "Nao consegui subir o container de teste." }

try {
    Write-Host '[restore] aguardando o banco aceitar conexao...'
    $pronto = $false
    foreach ($i in 1..30) {
        docker exec $NOME pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $pronto = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $pronto) { Write-Error 'O Postgres de teste nao subiu em 30s.' }

    docker cp $dump.FullName "${NOME}:/tmp/teste.dump" | Out-Null

    # pg_restore devolve codigo != 0 por avisos benignos (owner/extensao que nao
    # existem no container limpo). Por isso a prova nao e o codigo de saida, e
    # sim a contagem de linhas logo abaixo.
    docker exec -e PGPASSWORD=$SENHA $NOME `
        pg_restore -U postgres -d postgres --no-owner --no-privileges /tmp/teste.dump 2>&1 |
        Select-String -Pattern 'error|fatal' -CaseSensitive:$false |
        Select-Object -First 5 | ForEach-Object { Write-Host "   $_" }

    Write-Host '[restore] conferindo o conteudo...'
    $sql = @"
SELECT 'empresas=' || (SELECT count(*) FROM empresas)
    || ' usuarios=' || (SELECT count(*) FROM usuarios)
    || ' assinaturas=' || (SELECT count(*) FROM assinaturas)
    || ' boletos=' || (SELECT count(*) FROM boletos);
"@
    $saida = docker exec -e PGPASSWORD=$SENHA $NOME psql -U postgres -d postgres -t -A -c $sql

    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($saida)) {
        Write-Error 'RESTAURACAO FALHOU: as tabelas nao existem no banco restaurado.'
    }

    Write-Host ''
    Write-Host "  RESTAURACAO OK -> $($saida.Trim())"
    Write-Host ''
    Write-Host '  Se as contagens baterem com a producao, o backup presta.'
}
finally {
    docker rm -f $NOME 2>$null | Out-Null
    Write-Host '[restore] container de teste removido.'
}
