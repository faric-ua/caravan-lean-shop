# Пункт 15: увійти в GitHub (через браузер). Також налаштовує git на роботу через gh.
. "$PSScriptRoot\common.ps1"
if (-not (Has gh)) { Fail "Немає gh - пункт 14."; exit 1 }
gh auth status -h github.com *> $null
if ($LASTEXITCODE -eq 0) { Ok "Уже увійшов:"; gh auth status -h github.com; exit 0 }
gh auth login -h github.com -p https -w
gh auth setup-git
if (-not (git config --global user.name)) {
    $n = Read-Host "Ім'я для підпису змін у git (напр. Ivan Petrenko)"
    if ($n) { git config --global user.name $n }
}
if (-not (git config --global user.email)) {
    $login = (gh api user -q .login 2>$null); $id = (gh api user -q .id 2>$null)
    if ($login -and $id) { git config --global user.email "$id+$login@users.noreply.github.com"; Ok "git email: приватний адрес GitHub" }
}
exit 0
