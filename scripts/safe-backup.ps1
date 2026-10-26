param([string]$ProjectName='campuseats-v1',[string]$Destination,[string]$EnvFile='.env',[string]$OperationToken='')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
if(!$Destination){$Destination=Join-Path $taskRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-verified')}
$Destination=[IO.Path]::GetFullPath($Destination,$taskRoot)
$protected=[IO.Path]::GetFullPath((Join-Path $taskRoot '.local'))+[IO.Path]::DirectorySeparatorChar
if(!$Destination.StartsWith($protected,[StringComparison]::OrdinalIgnoreCase)){throw 'Backup must stay in workspace .local.'}
if(Test-Path -LiteralPath $Destination){throw 'Backup destination already exists; choose a new directory.'}
$composeArgs=@('compose','--env-file',$EnvFile,'-p',$ProjectName)
$running=@()
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
$operationLease=Enter-OperationsLock -Root $taskRoot -ProjectName $ProjectName -Token $OperationToken
Push-Location $taskRoot
try {
 foreach($service in @('mysql','redis','server','web')){
  $id=& docker @composeArgs ps -a -q $service
  if($LASTEXITCODE -ne 0){throw 'Cannot inventory service state.'}
  if($id -and (docker inspect --format '{{.State.Running}}' $id) -eq 'true'){$running+=$service}
 }
 try {
  $writers=@($running | Where-Object {$_ -in @('web','server')})
  if($writers.Count){& docker @composeArgs stop @writers; if($LASTEXITCODE -ne 0){throw 'Cannot pause application writers.'}}
  # Start existing dependencies, without replacing their containers or changing their images.
  $mysql=& docker @composeArgs ps -a -q mysql
  if(!$mysql){throw 'No existing database container to back up.'}
  if('mysql' -notin $running){& docker @composeArgs start mysql; if($LASTEXITCODE -ne 0){throw 'Cannot start backup database.'}}
  $ready=$false
  for($i=0;$i -lt 60;$i++){
   & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -Nse "SELECT 1"' 2>$null | Out-Null
   if($LASTEXITCODE -eq 0){$ready=$true;break}
   Start-Sleep -Seconds 2
  }
  if(!$ready){throw 'Backup database not ready.'}
   & (Join-Path $PSScriptRoot 'backup.ps1') -ProjectName $ProjectName -Destination $Destination -EnvFile $EnvFile -OperationToken $operationLease.Token
   $restoreImage=(& docker inspect --format '{{.Image}}' $mysql) -join ''
   if($LASTEXITCODE -ne 0 -or !$restoreImage){throw 'Cannot bind restore check to the source MySQL image.'}
   & (Join-Path $PSScriptRoot 'restore-check.ps1') -BackupDirectory $Destination -MySqlImage $restoreImage
  $manifest=Join-Path $Destination 'sha256.json'
  $marker=[ordered]@{project=$ProjectName;verifiedAt=[DateTime]::UtcNow.ToString('o');directory=$Destination;manifestSha256=(Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash}
 } finally {
  $restoreFailures=@()
  foreach($service in @('mysql','redis','server','web')){
   if($service -in $running){
    & docker @composeArgs start $service | Out-Null
    if($LASTEXITCODE -ne 0){$restoreFailures+=$service}
   }
  }
  foreach($service in @('web','server','redis','mysql')){
   if($service -notin $running){
    $id=& docker @composeArgs ps -a -q $service
    if($id -and (docker inspect --format '{{.State.Running}}' $id) -eq 'true'){
     & docker @composeArgs stop $service | Out-Null
     if($LASTEXITCODE -ne 0){$restoreFailures+=$service}
    }
   }
  }
  foreach($service in $running){
   $healthy=$false
   for($i=0;$i -lt 60;$i++){
    $id=& docker @composeArgs ps -a -q $service
    $state=if($id){docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' $id}else{'missing'}
    if($state -eq 'running healthy'){$healthy=$true;break}
    Start-Sleep -Seconds 2
   }
   if(!$healthy){$restoreFailures+=$service}
  }
  if($restoreFailures.Count){throw ('Cannot restore original service state: '+($restoreFailures -join ','))}
 }
 $marker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Destination 'verified.json') -Encoding utf8
 Write-Host "Backup independently restored and verified: $Destination"
} finally {Pop-Location;Exit-OperationsLock $operationLease}
