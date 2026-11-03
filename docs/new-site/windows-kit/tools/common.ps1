# Спільне для всіх пунктів меню Caravan Lean (Windows).
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$Global:CL_Root = Split-Path -Parent $PSScriptRoot
$Global:CL_ConfigDir = Join-Path $env:USERPROFILE '.caravan-lean'
$Global:CL_ConfigFile = Join-Path $Global:CL_ConfigDir 'config.psd1'

function Ok($m)   { Write-Host "[OK]  $m" -ForegroundColor Green }
function Info($m) { Write-Host "      $m" }
function Warn($m) { Write-Host "[!]   $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "[X]   $m" -ForegroundColor Red }

function Has($cmd) { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

function Get-ClConfig {
    if (-not (Test-Path $Global:CL_ConfigFile)) {
        Warn "Немає налаштувань ноутбука: $Global:CL_ConfigFile"
        Info "Пункт 13 покаже, що робити (скопіювати config.example.psd1 і заповнити)."
        return $null
    }
    try { return Import-PowerShellDataFile -Path $Global:CL_ConfigFile }
    catch { Fail "Не читається $Global:CL_ConfigFile : $($_.Exception.Message)"; return $null }
}

# Python, що справді працює (на свіжому Windows `python` - це заглушка Microsoft Store у WindowsApps).
function Test-Python {
    foreach ($c in @('py', 'python')) {
        $cmd = Get-Command $c -ErrorAction SilentlyContinue
        if (-not $cmd -or $cmd.Source -like '*\WindowsApps\*') { continue }
        if ($c -eq 'py') { $v = (& py -3 --version 2>&1 | Out-String) } else { $v = (& python --version 2>&1 | Out-String) }
        if ($LASTEXITCODE -eq 0 -and $v -match 'Python 3') { return $true }
    }
    return $false
}

function Test-Url($url) {
    if (-not $url) { return '-' }
    $code = $null
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 20 -MaximumRedirection 5 -ErrorAction Stop
        $code = [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } else { return 'не відповідає' }
    }
    switch ($code) {
        { $_ -ge 200 -and $_ -lt 300 } { return "$code - працює" }
        401 { return '401 - закрито паролем (для тестового сайту так і треба)' }
        403 { return '403 - доступ заборонено (перевір у панелі)' }
        429 { return '429 - захист хостингу від ботів (сайт живий, спробуй пізніше)' }
        { $_ -ge 500 } { return "$code - помилка на сервері!" }
        default { return "$code" }
    }
}

# «так» будь-якою розкладкою: так / т / tak / y / yes
function Read-Yes($prompt) {
    $a = Read-Host "$prompt (так/ні)"
    if ($null -eq $a) { return $false }
    return ($a.Trim().ToLower() -in @('так', 'т', 'tak', 'y', 'yes'))
}

# Слово-підтвердження для небезпечних дій: ТАК (або TAK латиницею)
function Confirm-Word($prompt) {
    $a = Read-Host "$prompt (щоб продовжити - набери ТАК)"
    if ($null -eq $a) { return $false }
    return ($a.Trim() -cin @('ТАК', 'TAK'))
}
