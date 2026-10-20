# Fault injection at the actual Copy-Item boundary, with real source/destination hashing.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$fixture=Join-Path $root ('.local/replica-race-test-'+[guid]::NewGuid().ToString('N'))
$source=Join-Path $fixture 'source'
$replicas=Join-Path $fixture 'replicas'
[void][IO.Directory]::CreateDirectory((Join-Path $source 'uploads'))
. (Join-Path $PSScriptRoot 'operations-support.ps1')
foreach($name in @('database.sql','source-counts.txt','all-table-counts.txt','tables.txt','table-checksums.txt','images.json','mounts.txt','.env')){[IO.File]::WriteAllText((Join-Path $source $name),'fixture-original')}
$entries=@(Get-ChildItem -LiteralPath $source -File -Recurse -Force | ForEach-Object {[ordered]@{path=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
Write-OperationsJson -Path (Join-Path $source 'sha256.json') -Value $entries
Write-OperationsJson -Path (Join-Path $source 'verified.json') -Value @{project='campuseats-v1';verifiedAt=[DateTimeOffset]::UtcNow.ToString('o');manifestSha256=(Get-FileHash (Join-Path $source 'sha256.json')).Hash}
$script:fixtureMutation=$true
function Copy-Item {
 param([string]$LiteralPath,[string]$Destination,[switch]$Recurse,[switch]$Force,[object]$ErrorAction)
 if($script:fixtureMutation -and (Split-Path $LiteralPath -Leaf) -eq 'database.sql'){
  $script:fixtureMutation=$false
  [IO.File]::WriteAllText($LiteralPath,('x'*(Get-Item -LiteralPath $LiteralPath -Force).Length))
 }
 Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination -Recurse:$Recurse -Force:$Force -ErrorAction Stop
}
try {
 $failed=$false
 try {Copy-VerifiedBackupReplica -Source $source -ReplicaDirectory $replicas -ProjectName 'campuseats-v1' | Out-Null} catch {$failed=$true}
 if(!$failed -or $script:fixtureMutation){throw 'Same-length source mutation during copy was not exercised or rejected.'}
 if(@(Get-ChildItem -LiteralPath $replicas -Directory -Force | Where-Object Name -NotLike '.partial-*').Count -or @(Get-ChildItem -LiteralPath $replicas -Filter 'replica-complete.json' -Recurse -Force).Count){throw 'A corrupt copy published a complete replica.'}
 # Old backups must not become fresh merely by being copied again.
 [IO.File]::WriteAllText((Join-Path $source 'database.sql'),'fixture-original')
 $marker=Get-Content -LiteralPath (Join-Path $source 'verified.json') -Raw | ConvertFrom-Json
 $marker.verifiedAt=[DateTimeOffset]::UtcNow.AddHours(-49).ToString('o')
 Write-OperationsJson -Path (Join-Path $source 'verified.json') -Value $marker
 $failed=$false
 try {Copy-VerifiedBackupReplica -Source $source -ReplicaDirectory $replicas -ProjectName 'campuseats-v1' | Out-Null} catch {$failed=$true}
 if(!$failed){throw 'Old backup verification was reported fresh by a new copy.'}
 Write-Host 'Replica fault boundary: same-length mid-copy corruption, incomplete publication, and stale source verification passed.'
} finally {Remove-Item Function:\Copy-Item -ErrorAction SilentlyContinue}
