# Exercises release failure policy with fake Docker and a disposable copy of the real scripts.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$fixture=Join-Path $root ('.local/release-script-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $fixture 'sky-server/src/main/resources/db/migration') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start.ps1'),(Join-Path $PSScriptRoot 'safe-backup.ps1'),(Join-Path $PSScriptRoot 'ops-check.ps1'),(Join-Path $PSScriptRoot 'docker-read.ps1') -Destination (Join-Path $fixture 'scripts')
'-- fixture' | Set-Content (Join-Path $fixture 'sky-server/src/main/resources/db/migration/V8__fixture.sql')
'DEMO_ADMIN_PASSWORD=FixturePasswordOnly2026' | Set-Content (Join-Path $fixture '.env')
@'
param($ProjectName,$Destination,$EnvFile)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
'[]' | Set-Content (Join-Path $Destination 'sha256.json')
'@ | Set-Content (Join-Path $fixture 'scripts/backup.ps1')
@'
param($BackupDirectory)
if($global:fixtureRestoreFailure){throw 'Expected restore verification failure'}
'@ | Set-Content (Join-Path $fixture 'scripts/restore-check.ps1')
$global:fixtureState=@{mysql=$false;redis=$false;server=$false;web=$false}
$global:fixtureFullUp=$false
$global:fixtureRestoreFailure=$true
function global:docker {
 $global:LASTEXITCODE=0
 $a=@($args)
 if($a[0] -eq 'info'){return 'fixture'}
 if($a[0] -eq 'inspect'){
  $service=$a[-1]
  if($a[2] -like '*State.Running*'){return $global:fixtureState[$service].ToString().ToLowerInvariant()}
  if($a[2] -like '*State.Status*'){if($global:fixtureState[$service]){return 'running healthy'}else{return 'exited'}}
  return 'sha256:fixture'
 }
 if($a[0] -eq 'image'){return 'sha256:fixture'}
 if($a[0] -eq 'tag'){return}
 if($a -contains 'ps'){return $a[-1]}
 if($a -contains 'stop'){
  foreach($service in @('mysql','redis','server','web')){if($a -contains $service){$global:fixtureState[$service]=$false}}
  return
 }
 if($a -contains 'start' -or $a -contains 'up'){
  $specified=@('mysql','redis','server','web') | Where-Object {$a -contains $_}
  if(!$specified){$global:fixtureFullUp=$true;$specified=@('mysql','redis','server','web')}
  foreach($service in $specified){$global:fixtureState[$service]=$true}
  return
 }
 if($a -contains 'exec'){
  $command=$a[-1]
  if($command -like 'df -Pk*'){return 'overlay 1073741824 0 1073741824 0% /'}
  if($command -like 'du -sb*'){return '100'}
  if($command -like '*CAMPUS_UPLOAD_QUOTA_BYTES*'){return '2147483648'}
  if($command -like '*CAMPUS_UPLOAD_MIN_FREE_BYTES*'){return '268435456'}
  if($command -like 'find *'){return}
  $sql=$input | Out-String
  if($sql -like '*SELECT COUNT(*) FROM refund_request*'){return @('0','0','0','0')}
  if($sql -like '*SELECT path FROM evidence*'){return}
  if($sql -like '*MAX(CAST(version*'){return '7'}
  return '1'
 }
}
try {
 $failed=$false
 try { & (Join-Path $fixture 'scripts/start.ps1') -SkipVerify } catch {
  if($_.Exception.Message -notlike '*Expected restore verification failure*'){throw}
  $failed=$true
 }
 if(!$failed -or $global:fixtureFullUp -or $global:fixtureState.server -or $global:fixtureState.web){throw 'Release crossed failed backup gate.'}
 if(@(Get-ChildItem (Join-Path $fixture '.local') -Filter verified.json -Recurse -ErrorAction SilentlyContinue).Count){throw 'Failure wrote success marker.'}
 $global:fixtureState=@{mysql=$true;redis=$true;server=$true;web=$false}
 $failed=$false
 try { & (Join-Path $fixture 'scripts/safe-backup.ps1') -Destination (Join-Path $fixture '.local/failed-backup') } catch {
  if($_.Exception.Message -notlike '*Expected restore verification failure*'){throw}
  $failed=$true
 }
 if(!$failed -or !$global:fixtureState.server -or $global:fixtureState.web){throw 'Backup failure did not restore original writer state.'}
 $global:fixtureRestoreFailure=$false
 & (Join-Path $fixture 'scripts/safe-backup.ps1') -Destination (Join-Path $fixture '.local/successful-backup')
 if(!(Test-Path (Join-Path $fixture '.local/successful-backup/verified.json'))){throw 'Verified backup missing marker.'}
 if(!$global:fixtureState.server -or $global:fixtureState.web){throw 'Successful backup changed original writer state.'}
 # Backups contain a hidden .env file. Recognition must use the same manifest checks on both OSes.
 $checked=Join-Path $fixture '.local/backups/checked'
 New-Item -ItemType Directory -Path $checked -Force | Out-Null
 $hidden=Join-Path $checked '.env'
 'fixture-only' | Set-Content -LiteralPath $hidden
 if($IsWindows){[IO.File]::SetAttributes($hidden,[IO.FileAttributes]::Hidden)}
 @([ordered]@{path='.env';bytes=(Get-Item -LiteralPath $hidden -Force).Length;sha256=(Get-FileHash -LiteralPath $hidden).Hash}) | ConvertTo-Json -AsArray | Set-Content (Join-Path $checked 'sha256.json')
 [ordered]@{project='campuseats-v1';verifiedAt=[DateTime]::UtcNow.ToString('o');manifestSha256=(Get-FileHash (Join-Path $checked 'sha256.json')).Hash} | ConvertTo-Json | Set-Content (Join-Path $checked 'verified.json')
 $global:fixtureState=@{mysql=$true;redis=$true;server=$true;web=$true}
 $opsConfig=Join-Path $fixture 'ops-docker.json'
 '{}' | Set-Content -LiteralPath $opsConfig
 $report=& (Join-Path $fixture 'scripts/ops-check.ps1') -DockerExecutable (Get-Process -Id $PID).Path -DockerPrefixArguments @('-NoProfile','-File',(Join-Path $PSScriptRoot 'tests/ops-docker-fixture.ps1'),$opsConfig) | ConvertFrom-Json
 if(!$report.checks.backupVerifiedAt -or $report.issues.Count){throw 'Hidden backup file caused a false operational failure.'}
 if(([DateTimeOffset]::UtcNow-([DateTimeOffset]$report.checks.backupVerifiedAt)).TotalMinutes -gt 2){throw 'Backup verification timestamp lost its timezone.'}
 Write-Host 'Release safety: stopped-database upgrade gate, failed backup state restoration, and success-marker policy passed.'
 Write-Host 'Backup recognition: hidden files and UTC timestamp passed.'
} finally {
 Remove-Item Function:\docker -ErrorAction SilentlyContinue
 Remove-Variable fixtureState,fixtureFullUp,fixtureRestoreFailure -Scope Global -ErrorAction SilentlyContinue
 # Keep fixture evidence under ignored .local; never delete guessed paths or project data.
}
