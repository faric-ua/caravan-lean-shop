# Пункт 14: встановити все, чого бракує (winget). Можна запускати скільки завгодно разів.
# Windows кілька разів спитає «Дозволити цій програмі вносити зміни?» (UAC) - натискати «Так».
. "$PSScriptRoot\common.ps1"
if (-not (Has winget)) {
    Fail "Немає winget. Microsoft Store -> 'App Installer' (Інсталятор програм) -> Оновити/Встановити. Потім знову пункт 14."
    exit 1
}
$apps = @(
    @{ id = 'Microsoft.PowerShell';      cmd = 'pwsh';   name = 'PowerShell 7' },
    @{ id = 'Git.Git';                   cmd = 'git';    name = 'Git for Windows (Git Bash)' },
    @{ id = 'GitHub.cli';                cmd = 'gh';     name = 'GitHub CLI' },
    @{ id = 'OpenJS.NodeJS.LTS';         cmd = 'node';   name = 'Node.js LTS' },
    @{ id = 'Python.Python.3.12';        cmd = 'python'; name = 'Python 3.12' },
    @{ id = 'Google.GoogleDrive';        cmd = '';       name = "Google Drive для комп'ютера" },
    @{ id = 'Microsoft.VisualStudioCode'; cmd = 'code';  name = 'VS Code (переглядати файли)' },
    @{ id = 'Docker.DockerDesktop';      cmd = 'docker'; name = 'Docker Desktop (локальна копія сайту)' }
)
foreach ($a in $apps) {
    if ($a.cmd -eq 'python') { if (Test-Python) { Ok "$($a.name) - уже є" ; continue } }
    elseif ($a.cmd -and (Has $a.cmd)) { Ok "$($a.name) - уже є"; continue }
    if (-not $a.cmd) {
        winget list --id $a.id -e --accept-source-agreements *> $null
        if ($LASTEXITCODE -eq 0) { Ok "$($a.name) - уже є"; continue }
    }
    Write-Host "Встановлюю $($a.name)..."
    winget install --id $a.id -e --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -eq 0) { Ok "$($a.name) встановлено" } else { Warn "$($a.name): winget повернув код $LASTEXITCODE (можливо, вже встановлено або треба перезапуск)" }
}
# PATH - підхопити щойно встановлене без перезапуску вікна
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not (Has claude)) {
    Write-Host "Встановлюю агента Claude Code (офіційний інсталятор, сам оновлюється)..."
    try {
        Invoke-RestMethod https://claude.ai/install.ps1 | Invoke-Expression
        $env:Path = $env:Path + ';' + (Join-Path $env:USERPROFILE '.local\bin')
    } catch { Warn "Не вдалось: $($_.Exception.Message)" }
}
if (Has claude) { Ok "Claude Code: $(claude --version 2>$null)" }
Write-Host ""
Warn "Docker Desktop потребує WSL 2. Якщо Docker пише про WSL - один раз відкрий PowerShell ВІД АДМІНІСТРАТОРА:"
Info "    wsl --install"
Info "і перезавантаж ноутбук. Якщо Docker пише про групу docker-users - вийди з Windows і зайди знову."
Info "Далі: закрий і знову відкрий меню -> 15 (GitHub) -> 13 (перевірка) -> 17 (ключ хостингу) -> 13."
exit 0
