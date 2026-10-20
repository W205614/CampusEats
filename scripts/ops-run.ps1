param(
 [Parameter(Mandatory)][ValidateSet('Check','Backup')][string]$Action,
 [string]$ProjectName='campuseats-v1',[string]$EnvFile='.env',[string]$ConfigFile='.local/ops-config.json',
 [string]$DockerExecutable='docker',[string[]]$DockerPrefixArguments=@()
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
. (Join-Path $PSScriptRoot 'operations-support.ps1')
$lease=$null
try {
 $lease=Enter-OperationsLock -Root $taskRoot -ProjectName $ProjectName
 $issues=[Collections.Generic.List[string]]::new()
 $codes=[Collections.Generic.List[string]]::new()
 $config=@{schemaVersion=1;replicaDirectory=$null;alertWebhookUrl=$null;alertTimeoutSeconds=5;alertMaxAttempts=2}
 $configPath=[IO.Path]::GetFullPath($ConfigFile,$taskRoot)
 if(Test-Path -LiteralPath $configPath){
  try {
   $loaded=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json -AsHashtable
   if($loaded.schemaVersion -ne 1){throw 'Unsupported operations configuration.'}
   foreach($key in $loaded.Keys){if(!$config.ContainsKey($key)){throw 'Unknown operations configuration field.'};$config[$key]=$loaded[$key]}
   if($config.alertTimeoutSeconds -isnot [long] -and $config.alertTimeoutSeconds -isnot [int]){throw 'Invalid timeout.'}
   if($config.alertMaxAttempts -isnot [long] -and $config.alertMaxAttempts -isnot [int]){throw 'Invalid attempts.'}
   if($config.alertTimeoutSeconds -lt 1 -or $config.alertTimeoutSeconds -gt 10 -or $config.alertMaxAttempts -lt 1 -or $config.alertMaxAttempts -gt 3){throw 'Alert bounds are invalid.'}
   foreach($field in @('replicaDirectory','alertWebhookUrl')){if($null -ne $config[$field] -and $config[$field] -isnot [string]){throw 'Invalid integration value.'}}
  } catch {throw 'Operations configuration is invalid; inspect it locally.'}
 }
 $integrations=[ordered]@{replica='not_configured';alert='not_configured'}
 if($config.replicaDirectory){$integrations.replica='configured'}else{$issues.Add('Backup replica is not configured');$codes.Add('REPLICA_NOT_CONFIGURED')}
 if($config.alertWebhookUrl){
  try {
   $alertUri=[uri]$config.alertWebhookUrl
   if(!$alertUri.IsAbsoluteUri -or ($alertUri.Scheme -ne 'https' -and !($alertUri.Scheme -eq 'http' -and $alertUri.IsLoopback)) -or $alertUri.UserInfo){throw 'Invalid endpoint.'}
  } catch {throw 'Alert configuration requires an HTTPS endpoint; inspect it locally.'}
  $integrations.alert='configured'
 }else{$issues.Add('Alert webhook is not configured');$codes.Add('ALERT_NOT_CONFIGURED')}
 $backup=$null;$replica=$null
 if($Action -eq 'Backup'){
  $backup=Join-Path $taskRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-ops-'+[guid]::NewGuid().ToString('N'))
  try {
   & (Join-Path $PSScriptRoot 'safe-backup.ps1') -ProjectName $ProjectName -EnvFile $EnvFile -Destination $backup -OperationToken $lease.Token 6>$null | Out-Null
   if(!(Test-Path -LiteralPath (Join-Path $backup 'verified.json'))){throw 'Backup completion marker missing.'}
   if($config.replicaDirectory){
    $replicaRoot=[IO.Path]::GetFullPath($config.replicaDirectory,$taskRoot)
    $replica=Copy-VerifiedBackupReplica -Source $backup -ReplicaDirectory $replicaRoot -ProjectName $ProjectName
    $integrations.replica='copied_verified'
   }
  } catch {$issues.Add('Backup or replica did not complete');$codes.Add('BACKUP_FAILED')}
 }
 if($config.replicaDirectory){
  try {
   $replica=Find-VerifiedBackupReplica -ReplicaDirectory ([IO.Path]::GetFullPath($config.replicaDirectory,$taskRoot)) -ProjectName $ProjectName
   $integrations.replica=if($Action -eq 'Backup' -and $integrations.replica -eq 'copied_verified'){'copied_verified'}else{'verified'}
  } catch {$integrations.replica='unavailable';$issues.Add('No recent complete verified replica is available');$codes.Add('REPLICA_UNAVAILABLE')}
 }
 try {
  $raw=& (Join-Path $PSScriptRoot 'ops-check.ps1') -ProjectName $ProjectName -EnvFile $EnvFile -DockerExecutable $DockerExecutable -DockerPrefixArguments $DockerPrefixArguments
  $checkExit=$LASTEXITCODE
  $check=$raw | ConvertFrom-Json
  if($checkExit -notin @(0,1) -or $null -eq $check.checks -or $null -eq $check.issues){throw 'Invalid check result.'}
  foreach($issue in $check.issues){$issues.Add([string]$issue)}
  if($check.issues.Count){$codes.Add('OPERATIONS_UNHEALTHY')}
 } catch {$issues.Add('Operations check did not complete');$codes.Add('CHECK_FAILED')}
 $statePath=Join-Path $taskRoot ('.local/operations-state/'+$ProjectName+'.json')
 $previous=$null
 if(Test-Path -LiteralPath $statePath){
  try {$previous=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json}
  catch {$issues.Add('Previous operations state is invalid');$codes.Add('STATE_INVALID')}
 }
 $fingerprint=Get-OperationsFingerprint -Issues @($issues.ToArray())
 $status=if($issues.Count){'ALARM'}else{'OK'}
 $notification='not_configured'
 $lastNotified=if($previous){$previous.notifiedFingerprint}else{$null}
 $pendingRecovery=$status -eq 'OK' -and $previous -and ($previous.status -eq 'ALARM' -or $previous.pendingRecovery)
 $shouldNotify=$config.alertWebhookUrl -and ($fingerprint -ne $lastNotified) -and ($issues.Count -gt 0 -or $pendingRecovery)
 if($config.alertWebhookUrl){$notification='unchanged'}
 if($shouldNotify){
  $payload=[ordered]@{schemaVersion=1;project=$ProjectName;status=$(if($status -eq 'OK'){'RECOVERED'}else{'ALARM'});observedAt=[DateTimeOffset]::UtcNow.ToString('o');fingerprint=$fingerprint;issueCodes=@($codes.ToArray() | Sort-Object -Unique)}
  if(Send-OperationsAlert -Url $alertUri -Payload $payload -TimeoutSeconds $config.alertTimeoutSeconds -MaxAttempts $config.alertMaxAttempts){$notification='sent';$lastNotified=$fingerprint;$pendingRecovery=$false}
  else {$notification='failed';$issues.Add('Alert delivery failed; next invocation will retry')}
 }
 # First healthy observation is quiet; failed notification never advances the delivered fingerprint.
 if(!$previous -and $status -eq 'OK'){$lastNotified=$fingerprint}
 Write-OperationsJson -Path $statePath -Value ([ordered]@{schemaVersion=1;project=$ProjectName;status=$status;fingerprint=$fingerprint;notifiedFingerprint=$lastNotified;pendingRecovery=[bool]$pendingRecovery;observedAt=[DateTimeOffset]::UtcNow.ToString('o')})
 $report=[ordered]@{project=$ProjectName;action=$Action;status=$status;integrations=$integrations;notification=$notification;backup=$backup;replica=$replica;checks=$check.checks;issues=@($issues.ToArray())}
 Write-OperationsJson -Path (Join-Path $taskRoot ('.local/operations-state/'+$ProjectName+'-last-run.json')) -Value $report
 $report | ConvertTo-Json -Depth 10
 if($issues.Count){exit 1};exit 0
} catch {
 Write-Error 'Operations runner failed; inspect local configuration and project lock. No external details were printed.'
 exit 1
} finally {Exit-OperationsLock $lease}
