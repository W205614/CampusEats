param([string]$ProjectName='campuseats-v1',[string]$Destination,[string]$EnvFile='.env',[string]$OperationToken='')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
if(-not $Destination){$Destination=Join-Path $taskRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
$backupRoot=[IO.Path]::GetFullPath($Destination)
$localRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '.local'))
if(-not $backupRoot.StartsWith($localRoot+[IO.Path]::DirectorySeparatorChar)){throw 'Backups must remain inside the protected workspace .local directory.'}
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
$operationLease=Enter-OperationsLock -Root $taskRoot -ProjectName $ProjectName -Token $OperationToken
Push-Location $taskRoot
try {
 New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
 $countSql='SELECT COUNT(*) FROM orders; SELECT COUNT(*) FROM order_detail; SELECT COUNT(*) FROM user; SELECT COUNT(*) FROM refund_task; SELECT COUNT(*) FROM outbox_event; SELECT COUNT(*) FROM daily_quota;'
 $before=$countSql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0){throw 'Backup source checks failed.'}
 $tables=@('SHOW TABLES;' | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N')
 if($LASTEXITCODE -ne 0 -or !$tables.Count -or @($tables | Where-Object {$_ -notmatch '^[a-zA-Z0-9_]+$'}).Count){throw 'Invalid database table inventory.'}
 $tables | Set-Content -LiteralPath (Join-Path $backupRoot 'tables.txt') -Encoding utf8
 $checksumSql='CHECKSUM TABLE '+($tables -join ',')+' EXTENDED;'
 $allCountSql=($tables | ForEach-Object {"SELECT '$_',COUNT(*) FROM $_;"}) -join ' '
 $allCounts=$allCountSql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0){throw 'Full table count capture failed.'}
 $allCounts | Set-Content -LiteralPath (Join-Path $backupRoot 'all-table-counts.txt') -Encoding utf8
 $checksums=$checksumSql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0){throw 'Source checksums failed.'}
 $sql=docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF -u campus sky_take_out'
 if($LASTEXITCODE -ne 0){throw 'Database dump failed.'}
 [IO.File]::WriteAllLines((Join-Path $backupRoot 'database.sql'),$sql,[Text.UTF8Encoding]::new($false))
 $after=$countSql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0 -or ($before -join ',') -ne ($after -join ',')){throw 'Backup row counts changed during capture; retry in a quiet window.'}
 $afterChecksums=$checksumSql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0 -or ($checksums -join ',') -ne ($afterChecksums -join ',')){throw 'Backup data changed during capture; retry in a quiet window.'}
 $checksums | Set-Content -LiteralPath (Join-Path $backupRoot 'table-checksums.txt') -Encoding utf8
 $before | Set-Content -LiteralPath (Join-Path $backupRoot 'source-counts.txt') -Encoding utf8
 docker compose --env-file $EnvFile -p $ProjectName images --format json | Set-Content -LiteralPath (Join-Path $backupRoot 'images.json') -Encoding utf8
 docker compose --env-file $EnvFile -p $ProjectName cp server:/app/uploads (Join-Path $backupRoot 'uploads')
 if($LASTEXITCODE -ne 0){throw 'Upload backup failed.'}
 Copy-Item -LiteralPath ([IO.Path]::GetFullPath($EnvFile,$taskRoot)) -Destination (Join-Path $backupRoot '.env')
 $mounts=docker compose --env-file $EnvFile -p $ProjectName ps -q | ForEach-Object { docker inspect --format '{{.Name}} {{range .Mounts}}{{.Name}}:{{.Destination}} {{end}}' $_ }
 $mounts | Set-Content -LiteralPath (Join-Path $backupRoot 'mounts.txt') -Encoding utf8
 Get-ChildItem -LiteralPath $backupRoot -Recurse -Force -File | Where-Object Name -ne 'sha256.json' | ForEach-Object { [pscustomobject]@{path=$_.FullName.Substring($backupRoot.Length+1);bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $backupRoot 'sha256.json') -Encoding utf8
 if($IsWindows) {
  $owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
  & icacls.exe $backupRoot /inheritance:r /grant:r "${owner}:(OI)(CI)F" | Out-Null
 }
 Write-Host "Verified backup files saved: $backupRoot"
} finally { Pop-Location;Exit-OperationsLock $operationLease }
