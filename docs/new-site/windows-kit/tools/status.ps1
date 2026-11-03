# Пункт 2: що зараз - проєкт, GitHub, сайти.
. "$PSScriptRoot\common.ps1"
Set-Location $Global:CL_Root
$cfg = Get-ClConfig
Write-Host "Caravan Lean - статус`n"
if (Has git) {
    Info "Проєкт: $(git branch --show-current) · $(git rev-parse --short HEAD) · $(git log -1 --format=%cd --date=format:'%d.%m %H:%M')"
    $ch = (git status --short | Measure-Object).Count
    if ($ch -eq 0) { Ok "Незбережених змін нема" } else { Warn "Незбережених змін: $ch" }
}
if ((Has gh) -and $cfg -and $cfg.RepoSlug -and $cfg.RepoSlug -notlike 'OWNER/*') {
    Write-Host "`nОстанні перевірки на GitHub:"
    # JSON розбираємо PowerShell-ом (лапки в запитах gh -q Windows PowerShell 5.1 ламає)
    $j = (gh run list -R $cfg.RepoSlug -L 5 --json workflowName,status,conclusion,headBranch 2>$null | Out-String)
    if ($LASTEXITCODE -eq 0 -and $j.Trim()) {
        foreach ($r in ($j | ConvertFrom-Json)) { Info "$($r.workflowName) · $($r.headBranch) · $($r.status) $($r.conclusion)" }
    } else { Warn "Не вдалося прочитати (gh увійшов? пункт 15)" }
}
if ($cfg) {
    Write-Host "`nСайти:"
    Info "Робочий:  $($cfg.ProdUrl) -> $(Test-Url $cfg.ProdUrl)"
    if ($cfg.StagingUrl) { Info "Тестовий: $($cfg.StagingUrl) -> $(Test-Url $cfg.StagingUrl)" }
    Info "Локальний: $($cfg.LocalUrl) -> $(Test-Url $cfg.LocalUrl)"
    if ($cfg.DrivePath) { if (Test-Path $cfg.DrivePath) { Ok "Google Диск: $($cfg.DrivePath)" } else { Warn "Google Диск не знайдено: $($cfg.DrivePath)" } }
}
exit 0
