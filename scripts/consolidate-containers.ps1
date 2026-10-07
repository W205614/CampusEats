param([Parameter(Mandatory=$true)][string]$VerifiedBackupDirectory)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$backupRoot=[IO.Path]::GetFullPath($VerifiedBackupDirectory)
if(-not $backupRoot.StartsWith((Join-Path $taskRoot '.local')+[IO.Path]::DirectorySeparatorChar)){throw 'Expected a protected workspace backup.'}
if(-not (Test-Path -LiteralPath (Join-Path $backupRoot 'restore-check.txt'))){throw 'Run restore-check.ps1 successfully before consolidating containers.'}
$manifest=Get-Content -LiteralPath (Join-Path $backupRoot 'sha256.json') -Raw | ConvertFrom-Json
foreach($file in $manifest){
 $path=[IO.Path]::GetFullPath((Join-Path $backupRoot $file.path))
 if(-not $path.StartsWith($backupRoot+[IO.Path]::DirectorySeparatorChar)){throw 'Invalid backup path.'}
 if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256){throw 'Backup verification failed.'}
}
$ids=@(docker ps -aq --no-trunc)
if($LASTEXITCODE -ne 0){throw 'Container inventory failed.'}
$containers=@(docker inspect $ids | ConvertFrom-Json)
if($LASTEXITCODE -ne 0){throw 'Container inspection failed.'}
$owned=@($containers | Where-Object {
 $_.Config.Labels.'com.docker.compose.project' -match '^campuseats(?:-v1|-load(?:-[a-z0-9-]+)?)?$' -and
 $_.Config.Labels.'com.docker.compose.project.working_dir' -and
 [IO.Path]::GetFullPath($_.Config.Labels.'com.docker.compose.project.working_dir') -eq $taskRoot
})
$final=@($owned | Where-Object {$_.Config.Labels.'com.docker.compose.project' -eq 'campuseats-v1'})
if($final.Count -ne 4 -or @($final | Where-Object {$_.State.Status -ne 'running' -or $_.State.Health.Status -ne 'healthy'}).Count){throw 'Expected four healthy final containers.'}
$obsolete=@($owned | Where-Object {$_.Config.Labels.'com.docker.compose.project' -ne 'campuseats-v1'})
if(@($obsolete | Where-Object {$_.State.Status -ne 'exited'}).Count){throw 'Stop obsolete CampusEats containers before removing them.'}
$record=Join-Path $taskRoot ('.local/container-consolidation-'+(Get-Date -Format 'yyyyMMddHHmmss')+'.json')
$owned | ForEach-Object {[pscustomobject]@{
 id=$_.Id;name=$_.Name;image=$_.Image;project=$_.Config.Labels.'com.docker.compose.project';
 state=$_.State.Status;retained=$_.Config.Labels.'com.docker.compose.project' -eq 'campuseats-v1';
 mounts=@($_.Mounts | Select-Object Type,Name,Source,Destination)
}} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $record -Encoding utf8
# No -v: historical database/upload volumes and rollback images remain recoverable.
foreach($container in $obsolete){
 docker rm $container.Id | Out-Null
 if($LASTEXITCODE -ne 0){throw "Could not remove $($container.Name)."}
}
$after=@(docker ps -aq --no-trunc)
if($LASTEXITCODE -ne 0){throw 'Final inventory failed.'}
$untouched=@($containers | Where-Object {$_.Id -notin $obsolete.Id})
foreach($container in $untouched){if($container.Id -notin $after){throw 'An unrelated or final container is missing.'}}
Write-Host "Removed $($obsolete.Count) obsolete containers; retained four final containers. Volumes and images preserved. Inventory: $record"
