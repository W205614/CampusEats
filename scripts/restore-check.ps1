param([Parameter(Mandatory=$true)][string]$BackupDirectory)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$backupRoot=[IO.Path]::GetFullPath($BackupDirectory)
if(-not $backupRoot.StartsWith([IO.Path]::GetFullPath((Join-Path $taskRoot '.local'))+[IO.Path]::DirectorySeparatorChar)){throw 'Expected local protected backup.'}
$manifest=Get-Content -LiteralPath (Join-Path $backupRoot 'sha256.json') -Raw | ConvertFrom-Json
foreach($file in $manifest){$path=[IO.Path]::GetFullPath((Join-Path $backupRoot $file.path));if(-not $path.StartsWith($backupRoot+[IO.Path]::DirectorySeparatorChar)){throw 'Invalid backup manifest path'};if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256){throw "Backup hash mismatch: $($file.path)"}}
$name='campuseats-restore-'+(Get-Date -Format 'yyyyMMddHHmmss')
$bytes=New-Object byte[] 24;[Security.Cryptography.RandomNumberGenerator]::Fill($bytes);$password=-join($bytes|ForEach-Object{$_.ToString('x2')})
$secretFile=Join-Path $taskRoot ".local/$name.env"
[IO.File]::WriteAllText($secretFile,"MYSQL_ROOT_PASSWORD=$password`nMYSQL_DATABASE=sky_take_out`n")
try {
 docker run -d --name $name --label campuseats.purpose=restore-check --memory 768m --env-file $secretFile mysql:8.4 | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Restore container creation failed'}
 $ready=$false
 for($i=0;$i -lt 60;$i++){docker exec $name sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h 127.0.0.1 -u root sky_take_out -Nse "SELECT 1"' 2>$null | Out-Null;if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Seconds 2}
 if(-not $ready){throw 'Restore database did not start'}
 Get-Content -LiteralPath (Join-Path $backupRoot 'database.sql') | docker exec -i $name sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root sky_take_out'
 if($LASTEXITCODE -ne 0){throw 'Restore import failed'}
 $result=docker exec $name sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root sky_take_out -Nse "SELECT COUNT(*) orders FROM orders; SELECT COUNT(*) details FROM order_detail; SELECT COUNT(*) users FROM user; SELECT COUNT(*) FROM refund_task; SELECT COUNT(*) FROM outbox_event; SELECT COUNT(*) FROM daily_quota; SELECT COUNT(*) FROM orders o LEFT JOIN user u ON u.id=o.user_id WHERE u.id IS NULL; SELECT COUNT(*) FROM daily_quota WHERE total<reserved+consumed OR reserved<0 OR consumed<0; SELECT COUNT(*) FROM order_detail d LEFT JOIN orders o ON o.id=d.order_id WHERE o.id IS NULL;"'
 if($LASTEXITCODE -ne 0){throw 'Restore checks failed'}
 $expected=@(Get-Content -LiteralPath (Join-Path $backupRoot 'source-counts.txt'))
 if(($result[0..5] -join ',') -ne ($expected -join ',')){throw 'Restored row counts do not match source.'}
 if($result[6] -ne '0' -or $result[7] -ne '0' -or $result[8] -ne '0'){throw 'Restored ownership, quota or detail integrity check failed.'}
 $restoredChecksums=docker exec $name sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root sky_take_out -Nse "CHECKSUM TABLE orders,order_detail,user,employee,address_book,dish,dish_flavor,setmeal,setmeal_dish,campus_building,shop_settings,refund_task,outbox_event,daily_quota,order_reservation,audit_log EXTENDED;"'
 if($LASTEXITCODE -ne 0 -or ($restoredChecksums -join ',') -ne ((Get-Content -LiteralPath (Join-Path $backupRoot 'table-checksums.txt')) -join ',')){throw 'Restored business table checksums differ.'}
 $restoreUploads=Join-Path $backupRoot ('restore-files-'+(Get-Date -Format 'yyyyMMddHHmmss'))
 Copy-Item -LiteralPath (Join-Path $backupRoot 'uploads') -Destination $restoreUploads -Recurse
 foreach($file in $manifest | Where-Object {$_.path -like 'uploads*'}){$target=Join-Path $restoreUploads ($file.path -replace '^uploads[/\\]?','');if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256){throw 'Restored upload hash mismatch'}}
 $result | Set-Content -LiteralPath (Join-Path $backupRoot 'restore-check.txt') -Encoding utf8
 Write-Host "Restored into independent container $name. Counts and integrity results: restore-check.txt"
} finally {
 if((docker inspect --format '{{index .Config.Labels "campuseats.purpose"}}' $name 2>$null) -eq 'restore-check'){docker rm -f -v $name | Out-Null}
 if(Test-Path -LiteralPath $secretFile){Remove-Item -LiteralPath $secretFile}
}
