# Пункт 10: повна резервна копія сайтів з хостингу (файли + бази) -> Google Диск\backups\РРРРММДД-ГГХХ\.
# Архіви робляться НА хостингу, качаються scp, звіряються за SHA-256, і лише тоді прибираються з хостингу.
# Успішна копія = папка з файлом DONE.txt (без нього пункт 12 копію не визнає).
# База: mysqldump бере логін/пароль з ~/.my.cnf НА ХОСТИНГУ (створюється один раз, інструкція - етап 1).
. "$PSScriptRoot\common.ps1"
$cfg = Get-ClConfig
if (-not $cfg) { exit 1 }
foreach ($k in 'SshHost', 'SshUser', 'DrivePath') { if (-not $cfg[$k]) { Fail "У config.psd1 не заповнено $k"; exit 1 } }
$sites = @($cfg.Sites | Where-Object { $_.Dir })
if ($sites.Count -eq 0) { Fail "У config.psd1 у Sites не вказано жодної папки сайту (Dir)."; exit 1 }
$key = Join-Path $env:USERPROFILE '.ssh\caravan_lean'
if (-not (Test-Path $key)) { Fail "Немає SSH-ключа - пункт 17."; exit 1 }

$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$dest = Join-Path $cfg.DrivePath "backups\$stamp"
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$target = "$($cfg.SshUser)@$($cfg.SshHost)"
$sshArgs = @('-i', $key, '-p', "$($cfg.SshPort)", '-o', 'StrictHostKeyChecking=accept-new')
$scpArgs = @('-i', $key, '-P', "$($cfg.SshPort)", '-o', 'StrictHostKeyChecking=accept-new')
$remoteFiles = @()
$failed = $false

foreach ($s in $sites) {
    $n = "cl-backup-$stamp-$($s.Name)"
    # Лише одинарні лапки всередині (подвійні Windows PowerShell 5.1 передає в ssh неправильно).
    # tar: код 1 = «файл змінився під час читання» на живому сайті - не помилка.
    # Дамп: перевіряємо останній рядок «Dump completed» - так видно, що mysqldump дійшов до кінця.
    $cmd = "cd ~ && { tar -czf $n-files.tgz -C '$($s.Dir)' . ; rc=`$?; [ `$rc -le 1 ] || exit 21; }"
    if ($s.Db) {
        $cmd += " && mysqldump --no-tablespaces --single-transaction --routines --default-character-set=utf8mb4 '$($s.Db)' | gzip > $n-db.sql.gz" +
                " && gzip -t $n-db.sql.gz && zcat $n-db.sql.gz | tail -n 1 | grep -q 'Dump completed' || exit 22"
    }
    $cmd += " ; sha256sum $n-*"
    Write-Host "Пакую «$($s.Name)» на хостингу (може тривати кілька хвилин)..."
    $out = (ssh @sshArgs $target $cmd 2>&1 | Out-String)
    $rc = $LASTEXITCODE
    if ($rc -ne 0) {
        Fail "«$($s.Name)»: хостинг не зміг зробити копію (код $rc)."
        if ($rc -eq 22) { Info "Дамп бази неповний: перевір ~/.my.cnf на хостингу і назву бази (Db)." }
        Info ($out.Trim())
        $failed = $true
        continue
    }
    $sums = @{}
    foreach ($line in ($out -split "`n")) { if ($line -match '^([0-9a-f]{64})\s+(\S+)$') { $sums[$Matches[2]] = $Matches[1] } }
    foreach ($f in $sums.Keys) {
        $local = Join-Path $dest $f
        scp @scpArgs "${target}:$f" $local
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $local)) { Fail "$f не завантажився."; $failed = $true; continue }
        $h = (Get-FileHash $local -Algorithm SHA256).Hash.ToLower()
        if ($h -ne $sums[$f]) { Fail "$f пошкодився при завантаженні (SHA-256 не збігся)."; $failed = $true; continue }
        "$h  $f" | Add-Content (Join-Path $dest 'SHA256SUMS.txt')
        $remoteFiles += $f
        Ok ("{0}  {1:N1} МБ" -f $f, ((Get-Item $local).Length / 1MB))
    }
}

if ($failed) {
    Rename-Item $dest "$stamp-НЕПОВНА" -ErrorAction SilentlyContinue
    Fail "Копія НЕПОВНА (папка $stamp-НЕПОВНА). Архіви на хостингу не видаляю - покажи це агенту (пункт 3)."
    exit 1
}
ssh @sshArgs $target ("rm -f " + ($remoteFiles -join ' ')) | Out-Null
"Повна копія: $(Get-Date -Format 'yyyy-MM-dd HH:mm'); сайти: $(($sites | ForEach-Object { $_.Name }) -join ', ')" | Set-Content (Join-Path $dest 'DONE.txt')
Ok "Копія готова й перевірена: $dest"
exit 0
