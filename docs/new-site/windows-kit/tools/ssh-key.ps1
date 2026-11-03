# Пункт 17: SSH-ключ для хостингу (вхід без пароля). Ключ лишається на ноутбуці, у репо - ніколи.
. "$PSScriptRoot\common.ps1"
if (-not (Has ssh-keygen)) { Fail "Немає OpenSSH. Параметри -> Додаткові компоненти -> OpenSSH Client."; exit 1 }
$dir = Join-Path $env:USERPROFILE '.ssh'
$key = Join-Path $dir 'caravan_lean'
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
if (Test-Path $key) { Ok "Ключ уже є: $key" }
else {
    # через cmd: порожній пароль ключа однаково передається і в PowerShell 5.1, і в 7.x
    cmd /c "ssh-keygen -q -t ed25519 -f `"$key`" -N `"`" -C caravan-lean-$env:COMPUTERNAME"
    if (Test-Path $key) { Ok "Ключ створено: $key" } else { Fail "ssh-keygen не створив ключ."; exit 1 }
}
$pub = Get-Content "$key.pub" -Raw
try { Set-Clipboard -Value $pub; Ok "Публічна частина ключа - у буфері обміну." } catch {}
Write-Host "`nПублічний ключ (його МОЖНА показувати):`n$pub"
Info "Додай його в панелі Хостинг Україна (adm.tools) -> «SSH-доступ» -> форма «додати публічний ключ»."
Info "Якщо там увімкнене обмеження SSH за IP - додай IP цього ноутбука (дізнатись: https://ifconfig.me)."
$cfg = Get-ClConfig
if ($cfg -and $cfg.SshHost -and $cfg.SshUser) {
    if (Read-Yes "Перевірити вхід на $($cfg.SshUser)@$($cfg.SshHost) зараз?") {
        ssh -i $key -p $cfg.SshPort -o StrictHostKeyChecking=accept-new "$($cfg.SshUser)@$($cfg.SshHost)" "echo SSH-OK; uname -a; php -v | head -1; mysql --version"
    }
}
exit 0
