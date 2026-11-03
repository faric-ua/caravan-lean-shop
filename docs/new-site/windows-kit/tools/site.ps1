# Пункти 7/8: локальна копія сайту в Docker (docker-compose.yml у корені проєкту).
param([ValidateSet('up','down')][string]$Action = 'up')
. "$PSScriptRoot\common.ps1"
Set-Location $Global:CL_Root
if (-not (Test-Path (Join-Path $Global:CL_Root 'docker-compose.yml'))) { Warn "Ще нема docker-compose.yml - його створить агент на етапі 3."; exit 1 }
if (-not (Has docker)) { Fail "Немає Docker - пункт 14."; exit 1 }
docker info *> $null
if ($LASTEXITCODE -ne 0) { Fail "Docker не запущений: відкрий Docker Desktop, дочекайся 'Engine running' і повтори."; exit 1 }
if ($Action -eq 'up') {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { Fail "Не стартувало - покажи це агенту (пункт 3)."; exit 1 }
    $cfg = Get-ClConfig
    $url = 'http://localhost:8080/'
    if ($cfg -and $cfg.LocalUrl) { $url = $cfg.LocalUrl }
    Ok "Локальний сайт запущено: $url"
    Start-Process $url
} else {
    docker compose down   # без -v: база локальної копії лишається
    Ok "Локальний сайт зупинено (дані збережені)."
}
exit 0
