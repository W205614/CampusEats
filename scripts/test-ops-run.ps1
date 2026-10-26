# Real scripts, real exclusive file handles, real loopback HTTP. No daily Docker/project is used.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'test-backup-replica.ps1')
$fixture=Join-Path $root ('.local/ops-run-test-'+[guid]::NewGuid().ToString('N'))
$fixtureScripts=Join-Path $fixture 'scripts'
[void][IO.Directory]::CreateDirectory($fixtureScripts)
foreach($script in @('ops-run.ps1','operations-support.ps1','secure-files.ps1','encrypted-backups.ps1','operations-lock.ps1','ops-check.ps1','docker-read.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $script) -Destination $fixtureScripts}
$pwsh=(Get-Process -Id $PID).Path
$dockerFixture=Join-Path $PSScriptRoot 'tests/ops-docker-fixture.ps1'
$dockerConfig=Join-Path $fixture 'docker.json'
'{}' | Set-Content -LiteralPath $dockerConfig
$replicaRoot=Join-Path $fixture 'replicas'
$configPath=Join-Path $fixture '.local/ops-config.json'
$httpConfig=Join-Path $fixture 'http.json'
$ready=Join-Path $fixture 'http-ready.txt'
$capture=Join-Path $fixture 'http-payloads.jsonl'
'{"status":204}' | Set-Content -LiteralPath $httpConfig
$httpProcess=[Diagnostics.Process]::new()
$httpProcess.StartInfo=[Diagnostics.ProcessStartInfo]::new($pwsh)
$httpProcess.StartInfo.UseShellExecute=$false;$httpProcess.StartInfo.CreateNoWindow=$true
foreach($argument in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'tests/alert-http-fixture.ps1'),'-ConfigFile',$httpConfig,'-ReadyFile',$ready,'-CaptureFile',$capture)){$httpProcess.StartInfo.ArgumentList.Add($argument)}
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'encrypted-backups.ps1')
. (Join-Path $PSScriptRoot 'docker-read.ps1')
function New-FixtureBackup([string]$Destination){
 [void][IO.Directory]::CreateDirectory((Join-Path $Destination 'uploads/.private'))
 foreach($name in @('database.sql','source-counts.txt','all-table-counts.txt','tables.txt','table-checksums.txt','images.json','mounts.txt','.env','uploads/.private/evidence.png')){[IO.File]::WriteAllText((Join-Path $Destination $name),'fixture-private-'+$name)}
 if($IsWindows){[IO.File]::SetAttributes((Join-Path $Destination '.env'),[IO.FileAttributes]::Hidden)}
 $entries=@(Get-ChildItem -LiteralPath $Destination -File -Recurse -Force | ForEach-Object {[ordered]@{path=[IO.Path]::GetRelativePath($Destination,$_.FullName);bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
 Write-OperationsJson -Path (Join-Path $Destination 'sha256.json') -Value $entries
 Write-OperationsJson -Path (Join-Path $Destination 'verified.json') -Value @{project='campuseats-v1';verifiedAt=[DateTimeOffset]::UtcNow.ToString('o');manifestSha256=(Get-FileHash (Join-Path $Destination 'sha256.json')).Hash}
}
function Invoke-Runner([string]$Action='Check'){
 $text=& (Join-Path $fixtureScripts 'ops-run.ps1') -Action $Action -ConfigFile $configPath -DockerExecutable $pwsh -DockerPrefixArguments @('-NoProfile','-File',$dockerFixture,$dockerConfig)
 $code=$LASTEXITCODE
 $joined=$text -join "`n"
 if($joined -match 'fixture-private|token-secret'){throw 'Runner output leaked fixture data.'}
 return @{report=($joined | ConvertFrom-Json);code=$code}
}
function Payload-Count {if(Test-Path -LiteralPath $capture){return @(Get-Content -LiteralPath $capture).Count};return 0}
try {
 # Cross-process contention and same-process nested token both exercise the OS lock, not a mock.
 $lease=Enter-OperationsLock -Root $fixture -ProjectName 'campuseats-v1'
 try {
  $nested=Enter-OperationsLock -Root $fixture -ProjectName 'campuseats-v1' -Token $lease.Token
  Exit-OperationsLock $nested
  $child=Join-Path $fixture 'lock-child.ps1'
  @'
param($Helper,$Root)
. $Helper
try {$lease=Enter-OperationsLock -Root $Root -ProjectName 'campuseats-v1';Exit-OperationsLock $lease;exit 2} catch {exit 0}
'@ | Set-Content -LiteralPath $child
  & $pwsh -NoProfile -File $child (Join-Path $PSScriptRoot 'operations-lock.ps1') $fixture
  if($LASTEXITCODE -ne 0){throw 'A second process entered an active operation.'}
  $invalid=$false
  try {Enter-OperationsLock -Root $fixture -ProjectName 'campuseats-v1' -Token 'incorrect' | Out-Null} catch {$invalid=$true}
  if(!$invalid){throw 'An invalid reentry token was accepted.'}
 } finally {Exit-OperationsLock $lease}
 $again=Enter-OperationsLock -Root $fixture -ProjectName 'campuseats-v1';Exit-OperationsLock $again
 $source=Join-Path $fixture '.local/backups/source'
 New-FixtureBackup $source
 $replica=Copy-VerifiedBackupReplica -Source $source -ReplicaDirectory $replicaRoot -ProjectName 'campuseats-v1'
 if(!(Test-Path -LiteralPath (Join-Path $replica '.env')) -or !(Test-Path -LiteralPath (Join-Path $replica 'replica-complete.json'))){throw 'Atomic replica omitted hidden files or completion marker.'}
 if((Find-VerifiedBackupReplica -ReplicaDirectory $replicaRoot -ProjectName 'campuseats-v1') -ne $replica){throw 'Completed replica could not be verified.'}
 [IO.File]::WriteAllText((Join-Path $source 'database.sql'),'tampered-source')
 $rejected=$false
 try {Copy-VerifiedBackupReplica -Source $source -ReplicaDirectory $replicaRoot -ProjectName 'campuseats-v1' | Out-Null} catch {$rejected=$true}
 if(!$rejected -or @(Get-ChildItem -LiteralPath $replicaRoot -Directory -Force | Where-Object Name -NotLike '.partial-*').Count -ne 1){throw 'Corrupt source backup published a replica.'}
 [IO.File]::WriteAllText((Join-Path $replica 'database.sql'),'tampered-copy')
 $rejected=$false
 try {Find-VerifiedBackupReplica -ReplicaDirectory $replicaRoot -ProjectName 'campuseats-v1' | Out-Null} catch {$rejected=$true}
 if(!$rejected){throw 'Corrupt replica appeared verified.'}
 # Recreate a valid source/copy for runner state and HTTP tests.
 $source2=Join-Path $fixture '.local/backups/source2';New-FixtureBackup $source2
 $fixtureAge=Get-PinnedAge
 $fixtureKeygen=Join-Path (Split-Path $fixtureAge -Parent) ('age-keygen'+$(if($IsWindows){'.exe'}else{''}))
 $fixtureIdentity=Join-Path $fixture 'identity.txt'
 $fixturePrivate=(& $fixtureKeygen 2>$null) -join "`n"
 if($LASTEXITCODE -ne 0){throw 'Fixture identity failed.'}
 Write-SecureTextFile -Path $fixtureIdentity -Content $fixturePrivate;$fixturePrivate=$null
 $fixtureRecipient=(& $fixtureKeygen -y $fixtureIdentity) -join ''
 Copy-EncryptedBackupReplica -Source $source2 -ReplicaDirectory $replicaRoot -ProjectName 'campuseats-v1' -Recipient $fixtureRecipient -AgeExecutable $fixtureAge | Out-Null
 $unconfigured=Invoke-Runner
 if($unconfigured.code -ne 1 -or $unconfigured.report.integrations.alert -ne 'not_configured' -or $unconfigured.report.integrations.replica -ne 'not_configured'){throw 'Missing integrations were hidden.'}
 [void]$httpProcess.Start()
 $timer=[Diagnostics.Stopwatch]::StartNew()
 while(!(Test-Path -LiteralPath $ready) -and $timer.Elapsed.TotalSeconds -lt 10){Start-Sleep -Milliseconds 50}
 if(!(Test-Path -LiteralPath $ready)){throw 'Loopback HTTP fixture did not start.'}
 $port=[int](Get-Content -LiteralPath $ready)
 $config=@{schemaVersion=1;replicaDirectory=$replicaRoot;replicaRecipient=$fixtureRecipient;alertWebhookUrl="http://127.0.0.1:$port/hook?token-secret=never-echo";alertTimeoutSeconds=1;alertMaxAttempts=2}
 Write-OperationsJson -Path $configPath -Value $config
 $healthy=Invoke-Runner
 if($healthy.code -ne 0 -or $healthy.report.notification -ne 'sent' -or (Payload-Count) -ne 1){throw 'Recovery after configuration did not notify once.'}
 $same=Invoke-Runner
 if($same.code -ne 0 -or (Payload-Count) -ne 1){throw 'Unchanged health sent duplicate alerts.'}
 '{"failedJob":"maintenance"}' | Set-Content -LiteralPath $dockerConfig
 $alarm=Invoke-Runner
 if($alarm.code -ne 1 -or (Payload-Count) -ne 2){throw 'A new operational failure did not notify.'}
 $same=Invoke-Runner
 if((Payload-Count) -ne 2){throw 'Repeated failure spammed the webhook.'}
 '{"failedJob":"maintenance","staleJob":"outbox"}' | Set-Content -LiteralPath $dockerConfig
 $changed=Invoke-Runner
 if((Payload-Count) -ne 3){throw 'Changed failure was suppressed.'}
 # A failed recovery must remain pending and retry on a later invocation.
 '{"status":503}' | Set-Content -LiteralPath $httpConfig
 '{}' | Set-Content -LiteralPath $dockerConfig
 $failed=Invoke-Runner
 if($failed.code -ne 1 -or $failed.report.notification -ne 'failed' -or (Payload-Count) -ne 5){throw 'Bounded HTTP retries or delivery failure status is incorrect.'}
 '{"status":204}' | Set-Content -LiteralPath $httpConfig
 $recovered=Invoke-Runner
 if($recovered.code -ne 0 -or $recovered.report.notification -ne 'sent' -or (Payload-Count) -ne 6){throw 'Pending recovery did not retry successfully.'}
 $all=Get-Content -LiteralPath $capture -Raw
 if($all -match 'fixture-private|token-secret|database.sql|uploads|maintenance|outbox'){throw 'Webhook payload included raw operational details.'}
 $payloads=@(Get-Content -LiteralPath $capture | ForEach-Object {$_ | ConvertFrom-Json})
 if($payloads[-1].status -ne 'RECOVERED'){throw 'Recovery payload was missing.'}
 # This boundary fixture supplies an already verified backup; the real safe-backup state restoration
 # and start -> safe-backup -> backup lock chain are exercised in test-release-safety.ps1.
 @'
param($ProjectName,$EnvFile,$Destination,$OperationToken)
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
$lease=Enter-OperationsLock -Root (Split-Path $PSScriptRoot -Parent) -ProjectName $ProjectName -Token $OperationToken
try {New-FixtureBackup $Destination} finally {Exit-OperationsLock $lease}
'@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'safe-backup.ps1')
 $backupRun=Invoke-Runner 'Backup'
 if($backupRun.code -ne 0 -or $backupRun.report.integrations.replica -ne 'copied_verified' -or !(Test-Path -LiteralPath (Join-Path $backupRun.report.replica 'encrypted-replica.json')) -or (Payload-Count) -ne 6){throw 'Backup runner did not reuse the lock and publish a verified replica quietly.'}
 # Actual HTTP timeout is tested directly with a delayed local response, never an external endpoint.
 '{"status":204,"delayMilliseconds":2000}' | Set-Content -LiteralPath $httpConfig
 $timer=[Diagnostics.Stopwatch]::StartNew()
 $sent=Send-OperationsAlert -Url ([uri]"http://127.0.0.1:$port/slow") -Payload @{status='ALARM'} -TimeoutSeconds 1 -MaxAttempts 1
 if($sent -or $timer.Elapsed.TotalSeconds -gt 3){throw 'HTTP timeout was not bounded.'}
 Write-Host 'Operations runner: real lock contention/reentry, hidden atomic replicas, corruption rejection, unconfigured integrations, change/recovery alerts, retry and timeout passed.'
} finally {
 if($httpProcess.Id -and !$httpProcess.HasExited){$httpProcess.Kill($true);$httpProcess.WaitForExit()}
 $httpProcess.Dispose()
}
