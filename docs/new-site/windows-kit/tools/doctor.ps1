# Пункт 13: перевірити робоче місце - що є, чого бракує (нічого не змінює).
. "$PSScriptRoot\common.ps1"
Write-Host "Перевірка робочого місця Caravan Lean`n"
$miss = 0
function Need($name, $ok, $hint) {
    if ($ok) { Ok $name } else { Fail "$name - $hint"; $script:miss++ }
}
$os = [Environment]::OSVersion.Version
Need "Windows $($os.Major).$($os.Minor) build $($os.Build)" ($os.Build -ge 17763) "потрібен Windows 10 1809+ або 11"
Need "winget (встановлення програм)" (Has winget) "Microsoft Store -> 'App Installer' (Інсталятор програм)"
Need "PowerShell 7 (pwsh)" (Has pwsh) "пункт 14"
Need "Git for Windows" (Has git) "пункт 14"
Need "GitHub CLI (gh)" (Has gh) "пункт 14"
if (Has gh) { gh auth status -h github.com *> $null; Need "gh увійшов у GitHub" ($LASTEXITCODE -eq 0) "пункт 15" }
Need "Node.js LTS" (Has node) "пункт 14"
Need "Python 3" (Test-Python) "пункт 14 (заглушка Microsoft Store не рахується)"
Need "Docker Desktop" (Has docker) "пункт 14 (потребує WSL 2 - див. інструкцію)"
if (Has docker) { docker info *> $null; Need "Docker запущений" ($LASTEXITCODE -eq 0) "відкрий Docker Desktop і дочекайся 'Engine running'" }
Need "OpenSSH (ssh, scp)" ((Has ssh) -and (Has scp)) "Параметри -> Додаткові компоненти -> OpenSSH Client"
Need "Агент Claude Code (claude)" (Has claude) "пункт 14 (потім 'claude' і вхід у браузері)"
$key = Join-Path $env:USERPROFILE '.ssh\caravan_lean'
Need "SSH-ключ для хостингу" (Test-Path $key) "пункт 17"
$cfg = Get-ClConfig
Need "Налаштування ноутбука ($Global:CL_ConfigFile)" ($null -ne $cfg) "скопіюй config.example.psd1 з папки проєкту туди й заповни"
if ($cfg) {
    Need "Google Диск: $($cfg.DrivePath)" ((Test-Path $cfg.DrivePath)) "встанови Google Drive для комп'ютера, увійди, створи папку"
    Need "Репо вказано (RepoSlug)" ($cfg.RepoSlug -and $cfg.RepoSlug -notlike 'OWNER/*') "впиши RepoSlug у config.psd1"
}
Write-Host ""
if ($miss -eq 0) { Ok "Усе готово." } else { Warn "Бракує: $miss. Почни з пункту 14, потім знову 13." }
exit $miss
