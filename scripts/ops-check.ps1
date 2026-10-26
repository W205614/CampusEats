param(
 [string]$ProjectName='campuseats-v1',[string]$EnvFile='.env',
 [int]$MaxBackupAgeHours=48,[int]$MaxOutboxBacklog=100,[switch]$AllowTasksDisabled,
 # Explicit executable injection is for disposable script fixtures; production uses Docker itself.
 [string]$DockerExecutable='docker',[string[]]$DockerPrefixArguments=@()
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'docker-read.ps1')
$taskRoot=Split-Path $PSScriptRoot -Parent
$composeArgs=@('compose','--env-file',$EnvFile,'-p',$ProjectName)
$issues=[Collections.Generic.List[string]]::new()
$checks=[ordered]@{}
function Read-Docker([string[]]$Arguments,[string]$InputText='') {
 $text=Invoke-BoundedDocker -Arguments $Arguments -InputText $InputText -Executable $DockerExecutable -PrefixArguments $DockerPrefixArguments
 if(![string]::IsNullOrEmpty($text)){$text.TrimEnd("`r","`n") -split '\r?\n'}
}
function Read-Sql([string]$Sql) {
 Read-Docker -Arguments ($composeArgs+@('exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N')) -InputText $Sql
}
function Read-Server([string]$Command) { Read-Docker -Arguments ($composeArgs+@('exec','-T','server','sh','-c',$Command)) }
function Read-OperationsHealth {
 # The derived credential is scoped by the server to GET operations from container loopback.
 # Keep both the source secret and curl header out of process arguments, reports and files.
 $secretText=@(Read-Server 'printf %s "$JWT_ADMIN_SECRET"') -join ''
 if($secretText.Length -lt 32){throw 'Operations credential unavailable.'}
 $hmac=[Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secretText))
 try {$key=[Convert]::ToHexString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('campuseats:operations-read:v1'))).ToLowerInvariant()}
 finally {$hmac.Dispose();$secretText=$null}
 Read-Docker -Arguments ($composeArgs+@('exec','-T','server','sh','-c','curl --config - --silent --show-error --connect-timeout 2 --max-time 5 http://localhost:8080/actuator/health/operations')) -InputText ('header = "X-Campus-Operations-Key: '+$key+'"'+"`n")
}
Push-Location $taskRoot
try {
 foreach($service in @('mysql','redis','server','web')){
  try {
   $ids=@(Read-Docker -Arguments ($composeArgs+@('ps','-a','-q',$service)))
   if($ids.Count -gt 1){throw 'Ambiguous service inventory.'}
   $state=if($ids.Count){@(Read-Docker -Arguments @('inspect','--format','{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}',$ids[0])) -join ''}else{'missing'}
   $checks[$service]=$state
   if($state -ne 'running healthy'){$issues.Add("$service is not healthy")}
  } catch {$checks[$service]='unknown';$issues.Add("$service container check failed")}
 }
 if($checks.mysql -eq 'running healthy'){
  try {
  $sql=@'
SELECT COUNT(*) FROM refund_request WHERE state='FAILED';
SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED';
SELECT COUNT(*) FROM outbox_event WHERE state='FAILED';
SELECT COUNT(*) FROM evidence WHERE request_id IS NULL;
SELECT COUNT(*) FROM refund_task WHERE state='FAILED';
SELECT COUNT(*) FROM checkout WHERE state='PENDING' AND expires_at<DATE_SUB(NOW(),INTERVAL 120 SECOND);
SELECT COUNT(*) FROM orders WHERE checkout_id IS NULL AND status=1 AND pay_expires_at<DATE_SUB(NOW(),INTERVAL 120 SECOND);
SELECT COUNT(*) FROM refund_request WHERE (state='REQUESTED' AND next_attempt_at<DATE_SUB(NOW(),INTERVAL 120 SECOND)) OR (state='PROCESSING' AND (lease_until IS NULL OR lease_until<DATE_SUB(NOW(),INTERVAL 120 SECOND)));
SELECT COUNT(*) FROM refund_task WHERE (state='REQUESTED' AND next_attempt_at<DATE_SUB(NOW(),INTERVAL 120 SECOND)) OR (state='PROCESSING' AND (lease_until IS NULL OR lease_until<DATE_SUB(NOW(),INTERVAL 120 SECOND)));
SELECT COUNT(*) FROM outbox_event WHERE (state='REQUESTED' AND next_attempt_at<DATE_SUB(NOW(),INTERVAL 120 SECOND)) OR (state='PROCESSING' AND (lease_until IS NULL OR lease_until<DATE_SUB(NOW(),INTERVAL 120 SECOND)));
SELECT COUNT(*) FROM orders o LEFT JOIN employee e ON e.id=o.courier_id WHERE o.status IN (3,4) AND o.fulfillment='DELIVERY' AND (o.courier_id IS NOT NULL OR o.status=4) AND (e.id IS NULL OR e.status<>1 OR e.role<>'DELIVERER' OR e.campus_id IS NULL OR e.campus_id<>o.campus_id);
SELECT COUNT(*) FROM orders WHERE status=4 AND delivery_started_at<DATE_SUB(NOW(),INTERVAL 60 MINUTE);
'@
  $names=@('refundFailed','outboxBacklog','outboxFailed','unboundEvidence','legacyRefundFailed','checkoutOverdue','legacyOrderOverdue','refundOverdue','legacyRefundOverdue','outboxOverdue','invalidActiveCourier','deliveryOverdue')
  $counts=@(Read-Sql $sql)
  if($counts.Count -ne $names.Count -or @($counts | Where-Object {$_ -notmatch '^\d+$'}).Count){throw 'Invalid database counts.'}
  for($i=0;$i -lt $names.Count;$i++){$checks[$names[$i]]=[long]$counts[$i]}
  if($checks.refundFailed -gt 0 -or $checks.legacyRefundFailed -gt 0){$issues.Add('Failed refund requests need attention')}
  if($checks.outboxFailed -gt 0 -or $checks.outboxBacklog -gt $MaxOutboxBacklog){$issues.Add('Notification backlog needs attention')}
  foreach($name in $names[5..11]){if($checks[$name] -gt 0){$issues.Add("$name needs attention")}}
  } catch {$checks.databaseOperationalState='unknown';$issues.Add('Database operational check failed')}
 } else {$checks.databaseOperationalState='unknown';$issues.Add('Database operational check unavailable')}
 if($checks.server -eq 'running healthy') {
  try {
   $healthText=@(Read-OperationsHealth) -join "`n"
   $health=$healthText | ConvertFrom-Json
   $runtime=$health.components.runtimeOperations
   $details=$runtime.details
   if($health.status -notin @('UP','DOWN') -or $runtime.status -notin @('UP','DOWN') -or $details.tasksEnabled -isnot [bool] -or $null -eq $details.jobs -or $null -eq $details.issues){throw 'Invalid operations health.'}
   $checks.operationsHealth=$health.status
   $checks.tasksEnabled=$details.tasksEnabled
   if(!$details.tasksEnabled -and !$AllowTasksDisabled){$issues.Add('Background tasks are disabled')}
   $checks.backgroundJobs=[ordered]@{}
   $knownJobs=@('checkoutExpiry','platformRefunds','legacyRefunds','outbox','legacyExpiry','deliveryOverdue','fulfillmentExceptions','maintenance','metrics')
   if((@($details.jobs.PSObject.Properties.Name | Sort-Object) -join ',') -cne (@($knownJobs | Sort-Object) -join ',')){throw 'Missing or unknown operations job.'}
   foreach($name in $knownJobs) {
    $job=$details.jobs.$name
    if($null -eq $job -or $job.enabled -isnot [bool] -or $job.status -notin @('STARTING','OK','FAILED','STALE','DISABLED')){throw 'Invalid job status.'}
    if($job.enabled -and $job.status -eq 'DISABLED' -or !$job.enabled -and $job.status -ne 'DISABLED' -or (!$job.enabled -and ($name -eq 'metrics' -or $details.tasksEnabled))){throw 'Inconsistent job status.'}
    $checks.backgroundJobs[$name]=$job.status
    if($job.status -in @('FAILED','STALE')){$issues.Add("$name task check failed")}
   }
   # Codes and exception details supplied by the application are deliberately not echoed.
   if($health.status -ne 'UP' -or $runtime.status -ne 'UP' -or @($details.issues).Count){$issues.Add('Runtime operations health needs attention')}
  } catch {$checks.operationsHealth='unknown';$issues.Add('Runtime operations check failed')}
  try {
  $disk=@(Read-Server 'df -Pk /app/uploads')
  $parts=$disk[-1].Trim() -split '\s+'
  if($parts.Count -lt 6 -or $parts[3] -notmatch '^\d+$'){throw 'Invalid disk data.'}
  $free=([long]$parts[3])*1024
  $checks.uploadFreeBytes=$free
  $usage=@(Read-Server 'du -sb /app/uploads')[0] -split '\s+' | Select-Object -First 1
  $quota=@(Read-Server 'printf %s "${CAMPUS_UPLOAD_QUOTA_BYTES:-2147483648}"')[0]
  $minimum=@(Read-Server 'printf %s "${CAMPUS_UPLOAD_MIN_FREE_BYTES:-268435456}"')[0]
  if($usage -notmatch '^\d+$' -or $quota -notmatch '^\d+$' -or $minimum -notmatch '^\d+$'){throw 'Invalid upload limits.'}
  $checks.uploadUsedBytes=[long]$usage;$checks.uploadQuotaBytes=[long]$quota
  if([long]$usage -ge [long]$quota){$issues.Add('Upload byte quota reached')}
  if($free -lt [long]$minimum){$issues.Add('Upload disk free space is low')}
  } catch {$checks.uploadStorageState='unknown';$issues.Add('Upload disk check failed')}
  try {
  if($checks.mysql -ne 'running healthy'){throw 'Database unavailable.'}
  # Filesystem inventory only; never delete images or reveal customer paths/content.
  $evidenceRefs=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach($ref in @(Read-Sql 'SELECT DISTINCT path FROM evidence;')) {
   if($ref -notmatch '^[A-Za-z0-9_-]+\.png$'){throw 'Invalid evidence reference.'}
   [void]$evidenceRefs.Add($ref)
  }
  $productRefs=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach($ref in @(Read-Sql "SELECT DISTINCT image FROM dish WHERE image LIKE '/uploads/%' UNION SELECT DISTINCT image FROM setmeal WHERE image LIKE '/uploads/%';")) {
   if($ref -notmatch '^/uploads/([A-Za-z0-9_-]+\.png)$'){throw 'Invalid product reference.'}
   [void]$productRefs.Add($Matches[1])
  }
  $scan=@'
set -eu
if [ -e /app/uploads ] && { [ ! -d /app/uploads ] || [ ! -r /app/uploads ] || [ ! -x /app/uploads ]; }; then exit 1; fi
for kind in EVIDENCE PRODUCT TEMP; do
 case "$kind" in EVIDENCE) dir=/app/uploads/.private; pattern='*.png';; PRODUCT) dir=/app/uploads; pattern='*.png';; TEMP) dir=/app/uploads/.tmp; pattern='*.part';; esac
 if [ -e "$dir" ] || [ -L "$dir" ]; then
  if [ -L "$dir" ] || [ ! -d "$dir" ] || [ ! -r "$dir" ] || [ ! -x "$dir" ]; then exit 1; fi
  find "$dir" -maxdepth 1 -type f -name "$pattern" -printf "$kind\t%f\n"
 fi
done
'@
  $evidenceFiles=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $productFiles=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $temporary=0
  foreach($file in @(Read-Server $scan)){
   if($file -notmatch '^(EVIDENCE|PRODUCT|TEMP)\t([^\t]+)$'){throw 'Invalid image inventory.'}
   switch($Matches[1]){'EVIDENCE'{[void]$evidenceFiles.Add($Matches[2])};'PRODUCT'{[void]$productFiles.Add($Matches[2])};'TEMP'{$temporary++}}
  }
  $checks.missingEvidence=@($evidenceRefs | Where-Object {!$evidenceFiles.Contains($_)}).Count
  $checks.missingProductImages=@($productRefs | Where-Object {!$productFiles.Contains($_)}).Count
  $checks.suspectedOrphanEvidence=@($evidenceFiles | Where-Object {!$evidenceRefs.Contains($_)}).Count
  $checks.temporaryFiles=$temporary
  if($checks.missingEvidence -gt 0 -or $checks.missingProductImages -gt 0){$issues.Add('Referenced images are missing')}
  if($checks.suspectedOrphanEvidence -gt 0 -or $checks.temporaryFiles -gt 0){$issues.Add('Unreferenced or temporary images need manual review')}
  } catch {$checks.imageInventoryState='unknown';$issues.Add('Image reference check failed')}
 } else {$checks.operationsHealth='unknown';$checks.imageInventoryState='unknown';$issues.Add('Server operational checks unavailable')}
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
 [ordered]@{project=$ProjectName;checks=$checks;issues=@($issues.ToArray())} | ConvertTo-Json -Depth 7
 if($issues.Count){exit 1}
 exit 0
} catch {Write-Error 'Operational check failed; inspect container state locally.';exit 1}
finally {Pop-Location}
