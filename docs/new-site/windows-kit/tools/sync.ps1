# Пункт 1: оновити меню й проєкт з GitHub. Лише fast-forward: нічого не стирає й не скидає.
# Код виходу: 0 - оновлено, 10 - уже актуально, інше - проблема.
param([switch]$Quiet)
. "$PSScriptRoot\common.ps1"
Set-Location $Global:CL_Root
if (-not (Has git)) { Fail "Немає git - пункт 14."; exit 2 }
git remote get-url origin *> $null
if ($LASTEXITCODE -ne 0) { if (-not $Quiet) { Warn "Репо ще не підключене до GitHub (етап 1, крок «створити репо»)." }; exit 5 }
$branch = (git branch --show-current 2>$null)
if ($branch -ne 'main') { Fail "Зараз гілка '$branch', а меню працює з main. Нічого не змінюю."; exit 3 }
git diff --quiet; $d1 = $LASTEXITCODE
git diff --cached --quiet; $d2 = $LASTEXITCODE
if ($d1 -ne 0 -or $d2 -ne 0) {
    Fail "У папці є незбережені зміни - оновлення зупинено, нічого не стерто."
    git status --short | Select-Object -First 10
    exit 4
}
git fetch --quiet --prune origin main 2>$null
if ($LASTEXITCODE -ne 0) { Warn "GitHub недоступний - працюю з тим, що є."; exit 5 }
$local = (git rev-parse HEAD); $remote = (git rev-parse origin/main)
if ($local -eq $remote) { if (-not $Quiet) { Ok "Уже актуально: $(git rev-parse --short HEAD)" }; exit 10 }
git merge-base --is-ancestor $local $remote
if ($LASTEXITCODE -eq 0) {
    git merge --quiet --ff-only $remote
    if ($LASTEXITCODE -eq 0) { Ok "Оновлено: $($local.Substring(0,7)) -> $(git rev-parse --short HEAD)"; exit 0 }
    Fail "Git зупинив оновлення - файли не перезаписані."; exit 6
}
Fail "Копія на ноутбуці розійшлась з GitHub. Нічого не скидаю - скажи агенту (пункт 3)."
exit 7
