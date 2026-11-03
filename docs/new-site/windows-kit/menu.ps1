# Caravan Lean - меню для Windows (аналог Termux-меню на телефоні).
# Правило: номери пунктів ніколи не зсуваються; нове - наступним вільним номером.
# Один пункт без меню (так запускає агент, у т.ч. з Git Bash):
#   pwsh -NoProfile -ExecutionPolicy Bypass -File menu.ps1 -Run 2
# Пункти 12, 15, 17 питають людину з клавіатури - їх -Run не виконує.
param([string]$Run = '')

. "$PSScriptRoot\tools\common.ps1"
$Root = $PSScriptRoot
$Tools = Join-Path $Root 'tools'
Set-Location $Root

function Pause-Menu { Write-Host ''; Read-Host 'Натисни Enter, щоб повернутись у меню' | Out-Null }
function Tool($name, [hashtable]$toolArgs = @{}) { & (Join-Path $Tools $name) @toolArgs }
function Restart-Menu { & (Join-Path $Root 'menu.ps1'); exit }

function Show-Banner {
    $line = '━' * 37
    Write-Host $line -ForegroundColor DarkYellow
    Write-Host '     █▀▀ █▀█ █▀▄ █▀█ █ █ █▀█ █▀▄' -ForegroundColor Yellow
    Write-Host '     █   █▀█ █▀▄ █▀█ ▀▄▀ █▀█ █ █' -ForegroundColor Yellow
    Write-Host '     ▀▀▀ ▀ ▀ ▀ ▀ ▀ ▀  ▀  ▀ ▀ ▀ ▀' -ForegroundColor Yellow
    Write-Host '           █   █▀▀ █▀█ █▀▄' -ForegroundColor Yellow
    Write-Host '           █   █▀  █▀█ █ █' -ForegroundColor Yellow
    Write-Host '           ▀▀▀ ▀▀▀ ▀ ▀ ▀ ▀    САЙТ' -ForegroundColor Yellow
    Write-Host $line -ForegroundColor DarkYellow
}

function Run-MenuItem([string]$choice) {
    $cfg = $null
    switch ($choice) {
        '1' {
            Tool 'sync.ps1'
            if ($LASTEXITCODE -eq 0) { Write-Host 'Перезапускаю меню...'; Start-Sleep 1; Restart-Menu }
        }
        '2' { Tool 'status.ps1' }
        '3' {
            if (-not (Has claude)) { Fail 'Агента ще нема - пункт 14.'; break }
            Write-Host 'Агент у папці проєкту. Вийти назад у меню: /exit або Ctrl+C двічі.'
            claude
        }
        '4' {
            Write-Host 'Командний рядок у папці проєкту. Назад у меню: exit'
            $sh = 'powershell'; if (Has pwsh) { $sh = 'pwsh' }
            & $sh -NoLogo
        }
        '5' { Start-Process explorer.exe $Root; Ok 'Відкрито папку проєкту.' }
        '6' {
            $cfg = Get-ClConfig
            if ($cfg -and $cfg.DrivePath -and (Test-Path $cfg.DrivePath)) { Start-Process explorer.exe $cfg.DrivePath; Ok "Відкрито: $($cfg.DrivePath)" }
            else { Warn 'Папку Google Диска не знайдено - пункт 13.' }
        }
        '7' { Tool 'site.ps1' @{ Action = 'up' } }
        '8' { Tool 'site.ps1' @{ Action = 'down' } }
        '9' { Tool 'checks.ps1' }
        '10' { Tool 'backup.ps1' }
        '11' { Tool 'deploy.ps1' @{ Target = 'staging' } }
        '12' { Tool 'deploy.ps1' @{ Target = 'production' } }
        '13' { Tool 'doctor.ps1' }
        '14' { Tool 'setup.ps1' }
        '15' { Tool 'gh-login.ps1' }
        '16' { Tool 'shortcut.ps1' }
        '17' { Tool 'ssh-key.ps1' }
    }
}
# Вивід пунктів іде прямо на екран (функція нічого не повертає - інакше PowerShell «з'їв» би вивід git/gh).
$Valid = 1..17 | ForEach-Object { "$_" }

if ($Run) {
    if ($Valid -notcontains $Run) { Fail "Нема пункту $Run."; exit 2 }
    if ($Run -in @('12', '15', '17')) { Fail "Пункт $Run питає людину - запускати лише з меню."; exit 2 }
    if ($Run -in @('3', '4')) { Fail "Пункт $Run - інтерактивний, лише з меню."; exit 2 }
    if ($Run -eq '1') { Tool 'sync.ps1'; exit $LASTEXITCODE }   # без перезапуску меню
    Run-MenuItem $Run
    exit $LASTEXITCODE
}

# Раз на запуск - тихо оновити меню з GitHub (0 - оновлено -> перезапуск, 10 - актуально).
if (-not $env:CL_MENU_SYNCED) {
    $env:CL_MENU_SYNCED = '1'
    if (Has git) {
        & (Join-Path $Tools 'sync.ps1') -Quiet
        if ($LASTEXITCODE -eq 0) { Restart-Menu }
        elseif ($LASTEXITCODE -in @(4, 6, 7)) { Read-Host 'Натисни Enter, щоб відкрити меню' | Out-Null }
        # 3 (не main), 5 (нема GitHub / ще нема репо), 10 (актуально) - тихо далі
    }
}
& (Join-Path $Tools 'shortcut.ps1') -Quiet

while ($true) {
    Clear-Host
    Show-Banner
    if (Has git) { Write-Host "$(git branch --show-current 2>$null) · $(git rev-parse --short HEAD 2>$null)" }
    Write-Host ''
    Write-Host ' ЩОДНЯ'
    Write-Host '  1 — Оновити меню й проєкт з GitHub'
    Write-Host '  2 — Статус: проєкт, GitHub, сайти'
    Write-Host '  3 — Агент (Claude Code) у папці проєкту'
    Write-Host '  4 — Командний рядок у папці проєкту'
    Write-Host '  5 — Відкрити папку проєкту'
    Write-Host "  6 — Відкрити папку на Google Диску"
    Write-Host ''
    Write-Host ' ЛОКАЛЬНИЙ САЙТ (на цьому ноутбуці)'
    Write-Host '  7 — Запустити й відкрити в браузері'
    Write-Host '  8 — Зупинити'
    Write-Host '  9 — Перевірки (тести)'
    Write-Host ''
    Write-Host ' ХОСТИНГ'
    Write-Host ' 10 — Резервна копія сайту на Google Диск'
    Write-Host ' 11 — Викласти на ТЕСТОВИЙ сайт'
    Write-Host ' 12 — Викласти на РОБОЧИЙ сайт (з підтвердженням)'
    Write-Host ''
    Write-Host ' ПЕРШЕ НАЛАШТУВАННЯ'
    Write-Host ' 13 — Перевірити робоче місце'
    Write-Host ' 14 — Встановити все, чого бракує'
    Write-Host ' 15 — Увійти в GitHub'
    Write-Host ' 16 — Ярлик меню на робочий стіл'
    Write-Host ' 17 — SSH-ключ для хостингу'
    Write-Host ''
    Write-Host '  0 — Вийти'
    Write-Host ''
    $raw = Read-Host 'Вибір'
    if ($null -eq $raw) { break }   # кінець вводу (не з клавіатури) - вийти, а не крутитись
    $c = $raw.Trim()
    if ($c -in @('0', 'q', 'Q')) { break }
    if ($Valid -notcontains $c) { continue }
    Clear-Host
    Run-MenuItem $c
    Pause-Menu
}
