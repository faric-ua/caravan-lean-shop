# Пункт 9: перевірки проєкту (тести, аудит). Набір перевірок агент кладе в scripts\check.ps1.
. "$PSScriptRoot\common.ps1"
Set-Location $Global:CL_Root
$c = Join-Path $Global:CL_Root 'scripts\check.ps1'
if (Test-Path $c) {
    & $c; $rc = $LASTEXITCODE
    if ($rc -eq 0) { Ok "Перевірки пройшли." } else { Fail "Є помилки - покажи агенту (пункт 3)." }
    exit $rc
}
Warn "Ще нема scripts\check.ps1 - агент додасть разом з першим кодом."
exit 0
