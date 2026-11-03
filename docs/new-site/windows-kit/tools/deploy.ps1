# Пункти 11/12: викласти сайт на хостинг через GitHub Actions (workflow deploy.yml, input target).
# Робочий сайт - лише з ПОВНОЮ резервною копією за сьогодні (папка з DONE.txt) і словом ТАК.
param([ValidateSet('staging', 'production')][string]$Target = 'staging')
. "$PSScriptRoot\common.ps1"
$cfg = Get-ClConfig
if (-not $cfg) { exit 1 }
if (-not (Has gh)) { Fail "Немає gh - пункт 14."; exit 1 }
gh workflow view deploy.yml -R $cfg.RepoSlug *> $null
if ($LASTEXITCODE -ne 0) { Warn "У репо ще нема .github/workflows/deploy.yml - його створить агент на етапі 5."; exit 1 }
if ($Target -eq 'production') {
    $today = Get-Date -Format 'yyyyMMdd'
    $bk = Get-ChildItem (Join-Path $cfg.DrivePath 'backups') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "$today-*" -and (Test-Path (Join-Path $_.FullName 'DONE.txt')) }
    if (-not $bk) { Fail "Сьогодні ще не було ПОВНОЇ резервної копії. Спершу пункт 10."; exit 1 }
    Ok "Резервна копія за сьогодні: $(@($bk)[-1].Name)"
    if (-not (Confirm-Word "Викласти на РОБОЧИЙ сайт $($cfg.ProdUrl)?")) { Warn "Скасовано."; exit 1 }
}
$start = (Get-Date).ToUniversalTime().AddSeconds(-10)
gh workflow run deploy.yml -R $cfg.RepoSlug --ref main -f target=$Target
if ($LASTEXITCODE -ne 0) { Fail "Не вдалося запустити."; exit 1 }
Ok "Запущено ($Target). Шукаю цей запуск..."
$id = $null
for ($i = 0; $i -lt 12 -and -not $id; $i++) {
    Start-Sleep -Seconds 5
    $j = (gh run list -R $cfg.RepoSlug -w deploy.yml -L 5 --json databaseId,createdAt,event 2>$null | Out-String)
    if (-not $j.Trim()) { continue }
    foreach ($r in ($j | ConvertFrom-Json)) {
        $t = $r.createdAt
        if ($t -is [datetime]) { $t = $t.ToUniversalTime() }
        else { $t = [datetime]::Parse("$t", [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AdjustToUniversal) }
        if ($r.event -eq 'workflow_dispatch' -and $t -ge $start) { $id = $r.databaseId; break }
    }
}
if (-not $id) { Warn "Запуск не знайшовся за хвилину - подивись пунктом 2."; exit 1 }
gh run watch $id -R $cfg.RepoSlug --exit-status
if ($LASTEXITCODE -eq 0) { Ok "Готово."; exit 0 }
Fail "Викладання не вдалося - покажи агенту (пункт 3)."
exit 1
