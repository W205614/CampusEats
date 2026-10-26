# Real release orchestration, permission checks and environment writes; external services are isolated fixtures.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'secure-files.ps1')
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/artifact-release-test-'+[guid]::NewGuid().ToString('N')))
$fixtureScripts=New-SecureDirectory -Path (Join-Path $fixture 'scripts')
foreach($name in @('release.ps1','release-bundle.ps1','secure-files.ps1','operations-lock.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $fixtureScripts}
# Only the GitHub network boundary is replaced; actual source/run/file hashes stay enforced.
Add-Content -LiteralPath (Join-Path $fixtureScripts 'release-bundle.ps1') -Value @'
function Confirm-ReleaseWorkflow {param($SourceSha,$RunId,$RunAttempt);if($SourceSha -cne ('a'*40) -or $RunId -cne '12345' -or $RunAttempt -ne 1){throw 'Unexpected workflow request.'}}
'@
@'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
function Write-OperationsJson {param($Path,$Value);[void][IO.Directory]::CreateDirectory((Split-Path $Path -Parent));[IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 12))}
function Read-ReleaseSchema {param($ComposeArguments);return @{version=1;fingerprint='fixture'}}
'@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'operations-support.ps1')
@'
param($ProjectName,$Destination,$EnvFile,$OperationToken)
if($global:artifactFixtureRunning.server -or $global:artifactFixtureRunning.web){throw 'Backup observed an open writer.'}
if($global:artifactFixtureCase -eq 'backup-failed'){throw 'Injected independent restore failure.'}
[void][IO.Directory]::CreateDirectory($Destination)
'{}' | Set-Content -LiteralPath (Join-Path $Destination 'verified.json')
$hash=if($Destination -like '*current-artifact-*' -and $global:artifactFixtureCase -eq 'upload-changed'){'f'*64}else{'e'*64}
@(@{path='uploads/example.png';bytes=3;sha256=$hash}) | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $Destination 'sha256.json')
'@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'safe-backup.ps1')
@'
param($ProjectName,$EnvFile,$OutputPath,$CompareManifest)
if($CompareManifest -and ($global:artifactFixtureRunning.web -or $global:artifactFixtureTasksEnabled)){throw 'Historical comparison observed open Web or active jobs.'}
if($CompareManifest -and $global:artifactFixtureCase -eq 'data-changed'){throw 'Injected original data change.'}
'{}' | Set-Content -LiteralPath $OutputPath
'@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'platform-data-manifest.ps1')
$migration=New-SecureDirectory -Path (Join-Path $fixture 'sky-server/src/main/resources/db/migration')
'-- fixture' | Set-Content -LiteralPath (Join-Path $migration 'V1__fixture.sql')
$bundle=New-SecureDirectory -Path (Join-Path $fixture '.local/bundle')
New-SecureDirectory -Path (Join-Path $bundle 'security') | Out-Null
$sha='a'*40
$images=@(@{service='server';id=('sha256:'+('1'*64));reference=('campuseats-server:sha-'+$sha);revision=$sha},@{service='web';id=('sha256:'+('2'*64));reference=('campuseats-web:sha-'+$sha);revision=$sha})
[IO.File]::WriteAllText((Join-Path $bundle 'application-images.tar'),'isolated-docker-fixture')
@($images | ForEach-Object {@{service=$_.service;status='passed';imageId=$_.id}}) | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $bundle 'security/summary.json')
'{}' | Set-Content -LiteralPath (Join-Path $bundle 'security/server-runtime-sbom.cdx.json')
$files=@(Get-ChildItem -LiteralPath $bundle -Recurse -File | ForEach-Object {@{path=[IO.Path]::GetRelativePath($bundle,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
$migrationHash=Get-ReleaseMigrationHash -Path (Join-Path $migration 'V1__fixture.sql')
$manifest=@{schemaVersion=1;repository='W205614/CampusEats';sourceSha=$sha;runId='12345';runAttempt=1;platform='linux/amd64';archive='application-images.tar';images=$images;files=$files;migrationHashFormat='utf8-lf-v1';migrations=@(@{name='V1__fixture.sql';version=1;sha256=$migrationHash})}
$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $bundle 'release-manifest.json')
$hash=(Get-FileHash -LiteralPath (Join-Path $bundle 'release-manifest.json')).Hash
function global:docker {
 $global:LASTEXITCODE=0;$a=@($args)
 if($a[0] -eq 'image' -and $a[1] -eq 'load'){$global:artifactFixtureLoads++;return}
 if($a[0] -eq 'image' -and $a[1] -eq 'inspect'){
  $kind=if($a[2] -like 'campuseats-server:*'){'1'}else{'2'}
  @{Id=('sha256:'+($kind*64));Os='linux';Architecture='amd64';Config=@{Labels=@{'org.opencontainers.image.revision'=('a'*40)}}} | ConvertTo-Json -Depth 5 -AsArray
  return
 }
 if($a[0] -eq 'volume'){
  if($global:artifactFixtureCase -eq 'volume-exists'){return 'owned-existing-volume'}
  if($global:artifactFixtureCase -eq 'unlabeled-volume-exists' -and $a -notcontains '--filter'){return 'isolated-fixture-uploads'}
  return
 }
 if($a[0] -eq 'inspect'){
  $service=$a[-1] -replace '-container$',''
  if($a[2] -like '*TASKS_ENABLED=false*'){if(!$global:artifactFixtureTasksEnabled){return 'disabled'};return ''}
  if($a[2] -like '*Mounts*'){
   $name=if($global:artifactFixtureCase -eq 'mount-changed' -and $global:artifactFixtureCandidateStarts -gt 0){'incorrect-uploads'}else{'original-uploads'}
   @(@{Type='volume';Name=$name;Source=('/var/lib/docker/volumes/'+$name+'/_data');Destination='/app/uploads'}) | ConvertTo-Json -AsArray
   return
  }
  if($a[2] -like '*State.Running*'){return $global:artifactFixtureRunning[$service].ToString().ToLowerInvariant()}
  if($a[2] -like '*Image*'){return 'sha256:'+(@{server='1';web='2';mysql='3';redis='4'}[$service]*64)}
  throw 'Unexpected fixture inspection.'
 }
 if($a -contains 'config'){
  if($a -contains '--format'){@{volumes=@{mysql=@{name='isolated-fixture-mysql'};redis=@{name='isolated-fixture-redis'};uploads=@{name='isolated-fixture-uploads'}};services=@{web=@{ports=@(@{published='28087'})}}} | ConvertTo-Json -Depth 6}
  return
 }
 if($a -contains 'ps'){
  $service=$a[-1]
  if($service -in @('mysql','redis','server','web')){return $service+'-container'}
  if(!$global:artifactFixtureCase.StartsWith('init') -and $global:artifactFixtureCase -notin @('volume-exists','unlabeled-volume-exists')){return @('mysql-container','redis-container','server-container','web-container')}
  return
 }
 if($a -contains 'stop' -or $a -contains 'start'){
  foreach($service in @('mysql','redis','server','web')){if($a -contains $service){$global:artifactFixtureRunning[$service]=($a -contains 'start')}}
  return
 }
 if($a -contains 'up'){
  if($a -contains '--no-deps'){
   if($a -notcontains '--no-build'){throw 'Artifact release attempted a rebuild.'}
   $global:artifactFixtureCandidateStarts++
   $gate=@($a | Where-Object {$_ -like '*migration-gate.json'})
   if($gate.Count){
    $definition=Get-Content -LiteralPath $gate[0] -Raw | ConvertFrom-Json
    if($a -contains 'web' -or $definition.services.server.environment.TASKS_ENABLED -cne 'false'){throw 'Migration stage enabled traffic or tasks.'}
    $global:artifactFixtureTasksEnabled=$false
   }else{$global:artifactFixtureTasksEnabled=$true}
   foreach($service in @('server','web')){if($a -contains $service){$global:artifactFixtureRunning[$service]=$true}}
  }else{$global:artifactFixtureRunning.mysql=$true;$global:artifactFixtureRunning.redis=$true}
  return
 }
 throw 'Unexpected Docker mutation in artifact release.'
}
try {
 foreach($case in @('init-success','volume-exists','unlabeled-volume-exists','backup-failed','data-changed','upload-changed','mount-changed','daily-success')){
  $global:artifactFixtureCase=$case;$global:artifactFixtureLoads=0;$global:artifactFixtureCandidateStarts=0
  $global:artifactFixtureRunning=@{mysql=$true;redis=$true;server=$true;web=$true}
  if($case.StartsWith('init')){$global:artifactFixtureRunning.server=$false;$global:artifactFixtureRunning.web=$false}
  $global:artifactFixtureTasksEnabled=$true
  $envPath=Join-Path $fixture '.env';Write-SecureTextFile -Path $envPath -Content "MYSQL_PASSWORD=fixture-only`nWEB_PORT=28087`n"
  $failed=$false
  try {& (Join-Path $fixtureScripts 'release.ps1') -BundleDirectory $bundle -ExpectedSourceSha $sha -ExpectedRunId '12345' -ExpectedManifestSha256 $hash -ProjectName 'campuseats-improvement-artifactfixture' -EnvFile $envPath -Initialize:($case.StartsWith('init') -or $case -in @('volume-exists','unlabeled-volume-exists'))}catch{$failed=$true}
  if($failed -ne ($case -in @('volume-exists','unlabeled-volume-exists','backup-failed','data-changed','upload-changed','mount-changed'))){throw "Unexpected artifact release result: $case"}
  if($case -in @('volume-exists','unlabeled-volume-exists') -and ($global:artifactFixtureLoads -or $global:artifactFixtureCandidateStarts)){throw 'Initialization reused old or unlabeled volumes.'}
  if($case -eq 'backup-failed' -and ($global:artifactFixtureCandidateStarts -or !$global:artifactFixtureRunning.server -or !$global:artifactFixtureRunning.web)){throw 'Backup failure crossed the migration gate or failed to request original writer restoration.'}
  if($case -in @('data-changed','upload-changed','mount-changed') -and ($global:artifactFixtureRunning.server -or $global:artifactFixtureRunning.web -or $global:artifactFixtureCandidateStarts -ne 1)){throw 'Failed post-migration data/upload/mount verification opened traffic.'}
  if($case -eq 'daily-success'){
   $config=[IO.File]::ReadAllText($envPath)
   if($config -notmatch ('SERVER_IMAGE=campuseats-server:sha-'+$sha) -or $config -notmatch ('MYSQL_IMAGE=sha256:'+('3'*64)) -or !(Test-Path -LiteralPath (Join-Path $fixture '.local/backups/LATEST.json')) -or $global:artifactFixtureCandidateStarts -ne 2 -or !$global:artifactFixtureTasksEnabled){throw 'Successful artifact release did not persist image/recovery references and restore tasks.'}
  }
 }
 Write-Host 'Artifact release orchestration: fresh isolation, existing-volume rejection, backup gate, failed-data pause and persistent immutable references passed.'
}finally{
 Remove-Item Function:\docker -ErrorAction SilentlyContinue
 Remove-Variable artifactFixtureCase,artifactFixtureLoads,artifactFixtureCandidateStarts,artifactFixtureRunning,artifactFixtureTasksEnabled -Scope Global -ErrorAction SilentlyContinue
}
