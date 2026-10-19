# Targeted read-only operations tests use real subprocesses and disposable backups.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$fixture=Join-Path $root ('.local/ops-script-test-'+[guid]::NewGuid().ToString('N'))
$fixtureScripts=Join-Path $fixture 'scripts'
New-Item -ItemType Directory -Path $fixtureScripts -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ops-check.ps1'),(Join-Path $PSScriptRoot 'docker-read.ps1') -Destination $fixtureScripts
$configFile=Join-Path $fixture 'docker.json'
$fakeDocker=Join-Path $PSScriptRoot 'tests/ops-docker-fixture.ps1'
$pwsh=(Get-Process -Id $PID).Path
$backup=Join-Path $fixture '.local/backups/checked'
New-Item -ItemType Directory -Path $backup -Force | Out-Null
'[]' | Set-Content -LiteralPath (Join-Path $backup 'sha256.json')
@{project='campuseats-v1';verifiedAt=[DateTime]::UtcNow.ToString('o');manifestSha256=(Get-FileHash (Join-Path $backup 'sha256.json')).Hash} | ConvertTo-Json | Set-Content (Join-Path $backup 'verified.json')
function Check-Fixture([hashtable]$Config,[switch]$AllowTasksDisabled) {
 $Config | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $configFile
 $text=& (Join-Path $fixtureScripts 'ops-check.ps1') -DockerExecutable $pwsh -DockerPrefixArguments @('-NoProfile','-File',$fakeDocker,$configFile) -AllowTasksDisabled:$AllowTasksDisabled
 $exitCode=$LASTEXITCODE
 $text=$text -join "`n"
 if($text -match 'fixture-private'){throw 'Operational output leaked fixture details.'}
 $report=$text | ConvertFrom-Json
 if(($report.issues.Count -gt 0 -and $exitCode -ne 1) -or ($report.issues.Count -eq 0 -and $exitCode -ne 0)){throw 'Operational exit code did not match report.'}
 return @{report=$report;exitCode=$exitCode}
}
. (Join-Path $PSScriptRoot 'docker-read.ps1')
$deadline=[Diagnostics.Stopwatch]::StartNew()
$failed=$false
try {Invoke-BoundedDocker -Executable $pwsh -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 10') -TimeoutMilliseconds 250 | Out-Null} catch {$failed=$true}
if(!$failed -or $deadline.Elapsed.TotalSeconds -gt 5){throw 'Actual subprocess exceeded its deadline.'}
$failed=$false
try {Invoke-BoundedDocker -Executable $pwsh -Arguments @('-NoProfile','-Command','[Console]::Error.WriteLine("fixture-private-secret"); exit 3') | Out-Null} catch {
 if($_.Exception.Message -match 'fixture-private'){throw 'Docker failure leaked stderr.'}
 $failed=$true
}
if(!$failed){throw 'Nonzero process exit accepted.'}
# Exercise the actual POSIX scan, rather than only emulating find output.
$scanner=[regex]::Match((Get-Content (Join-Path $PSScriptRoot 'ops-check.ps1') -Raw),'(?s)\$scan=@''\r?\n(.*?)\r?\n''@').Groups[1].Value
if(!$scanner){throw 'Cannot locate image inventory command.'}
$scanner=$scanner.Replace('/app/uploads','$base/uploads')
$bash=if($IsWindows){Join-Path $env:ProgramFiles 'Git/bin/bash.exe'}else{(Get-Command bash).Source}
$setup='base=$(mktemp -d); mkdir -p "$base/uploads/.private"; touch "$base/uploads/.private/exists.png"; '
$inventory=Invoke-BoundedDocker -Executable $bash -Arguments @('-c',($setup+$scanner))
if($inventory.Trim() -ne "EVIDENCE`texists.png"){throw 'Actual image scan did not inventory files or tolerate absent empty directories.'}
$failed=$false
try {Invoke-BoundedDocker -Executable $bash -Arguments @('-c',('base=$(mktemp -d); mkdir -p "$base/uploads"; touch "$base/uploads/.private"; '+$scanner)) | Out-Null} catch {$failed=$true}
if(!$failed){throw 'A non-directory private path appeared healthy.'}
$ok=Check-Fixture @{}
if($ok.report.issues.Count -or $ok.report.checks.missingEvidence -ne 0 -or !$ok.report.checks.backupVerifiedAt){throw 'Empty initialized filesystem should be healthy.'}
$disabled=Check-Fixture @{disabled=$true}
if($disabled.report.issues -notcontains 'Background tasks are disabled'){throw 'Disabled jobs were not reported.'}
$allowed=Check-Fixture @{disabled=$true} -AllowTasksDisabled
if($allowed.report.issues.Count){throw 'Explicit disabled-task fixture was rejected.'}
$failed=Check-Fixture @{failedJob='maintenance';staleJob='outbox'}
if($failed.report.issues -notcontains 'maintenance task check failed' -or $failed.report.issues -notcontains 'outbox task check failed'){throw 'Job failure or staleness hidden.'}
$unknown=Check-Fixture @{invalidHealth=$true;invalidCounts=$true;scanFailure=$true}
if($unknown.report.checks.operationsHealth -ne 'unknown' -or $unknown.report.checks.databaseOperationalState -ne 'unknown' -or $unknown.report.checks.imageInventoryState -ne 'unknown'){throw 'Unknown checks appeared healthy.'}
$missing=Check-Fixture @{evidenceRefs=@('missing.png');productRefs=@('/uploads/product.png');files=@("EVIDENCE`torphan.png","TEMP`tinterrupted.part")}
if($missing.report.checks.missingEvidence -ne 1 -or $missing.report.checks.missingProductImages -ne 1 -or $missing.report.checks.suspectedOrphanEvidence -ne 1 -or $missing.report.checks.temporaryFiles -ne 1){throw 'Image references were not checked in both directions.'}
$present=Check-Fixture @{evidenceRefs=@('exists.png');productRefs=@('/uploads/product.png');files=@("EVIDENCE`texists.png","PRODUCT`tproduct.png")}
if($present.report.issues.Count){throw 'Referenced existing images appeared missing.'}
$overdue=Check-Fixture @{counts=@(0,0,0,0,1,1,1,1,1,1,1,1)}
foreach($name in @('legacyRefundFailed','checkoutOverdue','legacyOrderOverdue','refundOverdue','legacyRefundOverdue','outboxOverdue','invalidActiveCourier','deliveryOverdue')){
 if($overdue.report.checks.$name -ne 1){throw "Missing operational count: $name"}
}
if(!$overdue.report.issues.Count){throw 'Overdue records not reported.'}
Write-Host 'Operations checks: bounded subprocess, disabled/failed/stale jobs, unknown checks, overdue counts, and bidirectional images passed.'
