param([string]$ProjectName='campuseats-v1',[string]$EnvFile='.env',[int]$MaxBackupAgeHours=48,[int]$MaxOutboxBacklog=100)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$composeArgs=@('compose','--env-file',$EnvFile,'-p',$ProjectName)
$issues=[Collections.Generic.List[string]]::new()
$checks=[ordered]@{}
Push-Location $taskRoot
try {
 foreach($service in @('mysql','redis','server','web')){
  $id=& docker @composeArgs ps -a -q $service
  if($LASTEXITCODE -ne 0){throw 'Cannot inventory containers.'}
  $state=if($id){docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' $id}else{'missing'}
  $checks[$service]=$state
  if($state -ne 'running healthy'){$issues.Add("$service is not healthy")}
 }
 if($checks.mysql -eq 'running healthy'){
  $sql=@'
SELECT COUNT(*) FROM refund_request WHERE state='FAILED';
SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED';
SELECT COUNT(*) FROM outbox_event WHERE state='FAILED';
SELECT COUNT(*) FROM evidence WHERE request_id IS NULL;
'@
  $counts=@($sql | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N')
  if($LASTEXITCODE -ne 0 -or $counts.Count -ne 4){throw 'Cannot query operational state.'}
  $checks.refundFailed=[long]$counts[0];$checks.outboxBacklog=[long]$counts[1];$checks.outboxFailed=[long]$counts[2];$checks.unboundEvidence=[long]$counts[3]
  if($checks.refundFailed -gt 0){$issues.Add('Failed refund requests need attention')}
  if($checks.outboxFailed -gt 0 -or $checks.outboxBacklog -gt $MaxOutboxBacklog){$issues.Add('Notification backlog needs attention')}
 }
 if($checks.server -eq 'running healthy'){
  $disk=@(& docker @composeArgs exec -T server sh -c 'df -Pk /app/uploads | tail -1')
  if($LASTEXITCODE -ne 0){throw 'Cannot inspect upload disk.'}
  $free=([long](($disk[-1].Trim() -split '\s+')[3]))*1024
  $checks.uploadFreeBytes=$free
  $usage=& docker @composeArgs exec -T server sh -c 'du -sb /app/uploads | cut -f1'
  if($LASTEXITCODE -ne 0){throw 'Cannot inspect upload usage.'}
  $quota=& docker @composeArgs exec -T server sh -c 'printf %s "${CAMPUS_UPLOAD_QUOTA_BYTES:-2147483648}"'
  if($LASTEXITCODE -ne 0){throw 'Cannot inspect upload quota.'}
  $checks.uploadUsedBytes=[long]$usage;$checks.uploadQuotaBytes=[long]$quota
  if([long]$usage -ge [long]$quota){$issues.Add('Upload byte quota reached')}
  $minimum=& docker @composeArgs exec -T server sh -c 'printf %s "${CAMPUS_UPLOAD_MIN_FREE_BYTES:-268435456}"'
  if($LASTEXITCODE -ne 0 -or $free -lt [long]$minimum){$issues.Add('Upload disk free space is low')}
  # Filesystem inventory only; never delete images or reveal customer paths/content.
  $refs=@('SELECT path FROM evidence;' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N')
  if($LASTEXITCODE -ne 0){throw 'Cannot inventory evidence references.'}
  $files=@(& docker @composeArgs exec -T server sh -c 'find /app/uploads/.private -maxdepth 1 -type f -name "*.png" -printf "%f\n" 2>/dev/null; find /app/uploads/.tmp -maxdepth 1 -type f -name "*.part" -printf "TEMP\n" 2>/dev/null; true')
  $checks.suspectedOrphanEvidence=@($files | Where-Object {$_ -ne 'TEMP' -and $_ -notin $refs}).Count
  $checks.temporaryFiles=@($files | Where-Object {$_ -eq 'TEMP'}).Count
  if($checks.suspectedOrphanEvidence -gt 0 -or $checks.temporaryFiles -gt 0){$issues.Add('Unreferenced or temporary images need manual review')}
 }
 $markers=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot '.local/backups') -Filter verified.json -File -Recurse -Force -ErrorAction SilentlyContinue | Sort-Object LastWriteTimeUtc -Descending)
 $latest=$null
 foreach($markerFile in $markers){
  try {
   $marker=Get-Content -LiteralPath $markerFile.FullName -Raw | ConvertFrom-Json
   if($marker.project -ne $ProjectName){continue}
   $manifest=Join-Path $markerFile.DirectoryName 'sha256.json'
   if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -ne $marker.manifestSha256){continue}
   $entries=Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
   $complete=$true
   foreach($entry in $entries){
    $path=[IO.Path]::GetFullPath((Join-Path $markerFile.DirectoryName $entry.path))
    if(!$path.StartsWith($markerFile.DirectoryName+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){$complete=$false;break}
    # The protected .env snapshot is hidden on Linux (and may have the Hidden attribute on Windows).
    if(!(Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path -Force).Length -ne $entry.bytes){$complete=$false;break}
   }
   if(!$complete){continue}
   $latest=if($marker.verifiedAt -is [DateTime]){[DateTimeOffset]::new($marker.verifiedAt)}else{[DateTimeOffset]::Parse([string]$marker.verifiedAt,[Globalization.CultureInfo]::InvariantCulture)}
   break
  } catch {continue}
 }
 $checks.backupVerifiedAt=if($latest){$latest.ToString('o')}else{$null}
 if(!$latest -or ([DateTimeOffset]::UtcNow-$latest).TotalHours -gt $MaxBackupAgeHours){$issues.Add('No recent independently verified backup')}
 [ordered]@{project=$ProjectName;checks=$checks;issues=@($issues.ToArray())} | ConvertTo-Json -Depth 5
 if($issues.Count){exit 1}
} catch {Write-Error 'Operational check failed; inspect container state locally.';exit 1}
finally {Pop-Location}
