# Пункт 16: ярлик «Caravan Lean» на робочому столі (аналог Termux:Widget). -Quiet: тихо, якщо вже є.
param([switch]$Quiet)
. "$PSScriptRoot\common.ps1"
$desk = [Environment]::GetFolderPath('Desktop')
if (-not $desk) { if (-not $Quiet) { Warn "Робочий стіл не знайдено." }; exit 0 }
$lnk = Join-Path $desk 'Caravan Lean.lnk'
$target = Join-Path $Global:CL_Root 'menu.cmd'
if ($Quiet -and (Test-Path $lnk)) { exit 0 }
try {
    $sh = New-Object -ComObject WScript.Shell
    $s = $sh.CreateShortcut($lnk)
    $s.TargetPath = $target
    $s.WorkingDirectory = $Global:CL_Root
    $s.IconLocation = "$env:SystemRoot\System32\shell32.dll,43"
    $s.Description = 'Меню Caravan Lean'
    $s.Save()
    if (-not $Quiet) { Ok "Ярлик 'Caravan Lean' на робочому столі." }
} catch { if (-not $Quiet) { Warn "Не вдалося створити ярлик: $($_.Exception.Message)" } }
exit 0
