param(
 [Parameter(Mandatory)][string]$BundleDirectory,[Parameter(Mandatory)][string]$ExpectedSourceSha,
 [Parameter(Mandatory)][string]$ExpectedRunId,[Parameter(Mandatory)][string]$ExpectedManifestSha256,
 [string]$ProjectName='campuseats-v1',[string]$EnvFile='.env',[switch]$Initialize
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$lease=Enter-OperationsLock -Root $root -ProjectName $ProjectName
Push-Location $root
try {
 $manifest=Read-VerifiedReleaseBundle -Directory $BundleDirectory -ExpectedSourceSha $ExpectedSourceSha -ExpectedRunId $ExpectedRunId -ExpectedManifestSha256 $ExpectedManifestSha256
 Confirm-ReleaseWorkflow -SourceSha $ExpectedSourceSha -RunId $ExpectedRunId -RunAttempt $manifest.runAttempt
 $migrationDirectory=Join-Path $root 'sky-server/src/main/resources/db/migration'
 $current=@(Get-ChildItem -LiteralPath $migrationDirectory -Filter 'V*.sql')
 if($current.Count -ne @($manifest.migrations).Count){throw 'Checkout migration inventory differs from release.'}
 foreach($migration in $manifest.migrations){
  if($migration.name -notmatch '^V\d+__[a-zA-Z0-9_]+\.sql$' -or (Get-ReleaseMigrationHash -Path (Join-Path $migrationDirectory $migration.name)) -ine $migration.sha256){throw 'Checkout migration differs from the tested release.'}
 }
 $envPath=[IO.Path]::GetFullPath($EnvFile,$root);Set-SecurePathPermissions -Path $envPath
 $compose=@('compose','--env-file',$envPath,'-p',$ProjectName)
 & docker @compose config --quiet
 if($LASTEXITCODE -ne 0){throw 'Compose configuration is invalid.'}
 $existing=@(& docker @compose ps -a -q)
 if($LASTEXITCODE -ne 0){throw 'Cannot inventory project containers.'}
 if($Initialize){
  if($ProjectName -notmatch '^campuseats-(improvement|role-uat|perf)(-[a-z0-9]+)*$' -or $existing.Count){throw 'Initialization requires a fresh explicitly isolated acceptance project.'}
  $volumes=@(& docker volume ls --filter ('label=com.docker.compose.project='+$ProjectName) --format '{{.Name}}')
  if($LASTEXITCODE -ne 0 -or $volumes.Count){throw 'Initialization refuses existing project volumes.'}
 }elseif($existing.Count -ne 4){throw 'Daily release requires the existing four-service project.'}
 & docker image load --input (Join-Path ([IO.Path]::GetFullPath($BundleDirectory)) $manifest.archive)
 if($LASTEXITCODE -ne 0){throw 'Cannot import the tested image archive.'}
 Assert-LoadedReleaseImages -Manifest $manifest
 $id=[guid]::NewGuid().ToString('N');$releaseRoot=New-SecureDirectory -Path (Join-Path $root ('.local/releases/artifact-'+$id))
 $override=Join-Path $releaseRoot 'candidate.json'
 $gateOverride=Join-Path $releaseRoot 'migration-gate.json'
 $imageMap=@{};foreach($image in $manifest.images){$imageMap[$image.service]=$image.id}
 Write-OperationsJson -Path $override -Value @{services=@{server=@{image=$imageMap.server};web=@{image=$imageMap.web}}}
 Write-OperationsJson -Path $gateOverride -Value @{services=@{server=@{image=$imageMap.server;environment=@{TASKS_ENABLED='false'}}}}
 $record=[ordered]@{project=$ProjectName;sourceSha=$ExpectedSourceSha;ciRun=$ExpectedRunId;manifestSha256=$ExpectedManifestSha256;images=$imageMap;startedAt=[DateTimeOffset]::UtcNow.ToString('o');mode=$(if($Initialize){'isolated-initialization'}else{'existing-daily-release'});outcome='preparing';dependencies=@{};dependencyImages=@{};beforeBackup=$null;currentBackup=$null}
 if(!$Initialize){
  $previousServer=(& docker @compose ps -a -q server) -join ''
  $record['uploadsMount']=Get-ReleaseUploadsMount -ContainerId $previousServer
  foreach($service in @('mysql','redis')){
   $container=(& docker @compose ps -a -q $service) -join ''
   $running=(& docker inspect --format '{{.State.Running}}' $container) -join ''
   if($LASTEXITCODE -ne 0 -or $running -cne 'true'){throw 'Existing database dependencies must be running before release.'}
   $record.dependencies[$service]=$container
   $record.dependencyImages[$service]=((& docker inspect --format '{{.Image}}' $container) -join '')
  }
  $schema=Read-ReleaseSchema -ComposeArguments $compose
  if($schema.version -gt (@($manifest.migrations.version) | Measure-Object -Maximum).Maximum){throw 'Database is newer than the selected artifact.'}
  $record['schemaBefore']=$schema.version
  $before=Join-Path $root ('.local/backups/pre-artifact-'+$id)
  $writers=@()
  foreach($service in @('server','web')){
   $container=(& docker @compose ps -a -q $service) -join ''
   if(((& docker inspect --format '{{.State.Running}}' $container) -join '') -ceq 'true'){$writers+=$service}
  }
  & docker @compose stop web server
  if($LASTEXITCODE -ne 0){throw 'Cannot pause application writers before recovery capture.'}
  try {
   & (Join-Path $PSScriptRoot 'safe-backup.ps1') -ProjectName $ProjectName -EnvFile $envPath -Destination $before -OperationToken $lease.Token
  }catch{
   if($writers.Count){& docker @compose start @writers | Out-Null}
   $record.outcome='backup_failed';Write-OperationsJson -Path (Join-Path $releaseRoot 'release.json') -Value $record
   throw 'Pre-release recovery verification failed; original writer state was requested and no migration was started.'
  }
  $record.beforeBackup=$before
  # The nested backup observed stopped writers and leaves them stopped until candidate startup.
  & (Join-Path $PSScriptRoot 'platform-data-manifest.ps1') -ProjectName $ProjectName -EnvFile $envPath -OutputPath (Join-Path $releaseRoot 'historical-before.json')
 }else{
  & docker @compose up -d --wait --wait-timeout 180 mysql redis
  if($LASTEXITCODE -ne 0){throw 'Isolated database initialization failed.'}
  foreach($service in @('mysql','redis')){
   $container=(& docker @compose ps -a -q $service) -join ''
   $record.dependencies[$service]=$container
   $record.dependencyImages[$service]=((& docker inspect --format '{{.Image}}' $container) -join '')
  }
 }
 # Persist immutable references before starting, so a later plain compose up cannot restore old tags.
 # The pre-release recovery set retains the original configuration if migration requires recovery.
 $config=[IO.File]::ReadAllText($envPath)
 $pinned=@{SERVER_IMAGE=($manifest.images | Where-Object service -CEQ 'server').reference;WEB_IMAGE=($manifest.images | Where-Object service -CEQ 'web').reference;MYSQL_IMAGE=$record.dependencyImages.mysql;REDIS_IMAGE=$record.dependencyImages.redis}
 foreach($key in $pinned.Keys){
  if(!$pinned[$key]){throw 'Cannot pin a required runtime image.'}
  if($config -match ('(?m)^'+$key+'=')){$config=[regex]::Replace($config,('(?m)^'+$key+'=[^\r\n]*'),($key+'='+$pinned[$key]))}
  else {$config+="`n"+$key+'='+$pinned[$key]+"`n"}
 }
 Write-SecureTextFile -Path $envPath -Content $config
 $record.outcome='starting';Write-OperationsJson -Path (Join-Path $releaseRoot 'release.json') -Value $record
 try {
  # Flyway runs with all application jobs disabled and no Web ingress. Readiness proves only
  # that migration and DB access completed; it does not open traffic or permit background writes.
  & docker @compose -f compose.yaml -f $gateOverride up -d --no-build --no-deps --wait --wait-timeout 300 server
  if($LASTEXITCODE -ne 0){throw 'Migration gate did not become healthy.'}
  $container=(& docker @compose ps -a -q server) -join ''
  $actual=(& docker inspect --format '{{.Image}}' $container) -join ''
  if($LASTEXITCODE -ne 0 -or $actual -cne $imageMap.server){throw 'Migration image differs from the validated artifact.'}
  $disabled=(& docker inspect --format '{{range .Config.Env}}{{if eq . "TASKS_ENABLED=false"}}disabled{{end}}{{end}}' $container) -join ''
  if($LASTEXITCODE -ne 0 -or $disabled -cne 'disabled'){throw 'Migration gate did not disable background tasks.'}
  if(!$Initialize){Assert-ReleaseUploadsMount -Expected $record.uploadsMount -ContainerId $container}
  $schemaAfter=Read-ReleaseSchema -ComposeArguments $compose
  if($schemaAfter.version -ne (@($manifest.migrations.version) | Measure-Object -Maximum).Maximum){throw 'Migration did not reach the release schema.'}
  $record['schemaAfter']=$schemaAfter.version
  foreach($service in $record.dependencies.Keys){
   if(((& docker @compose ps -a -q $service) -join '') -cne $record.dependencies[$service]){throw 'Daily dependency container was unexpectedly replaced.'}
  }
  if(!$Initialize){
   & (Join-Path $PSScriptRoot 'platform-data-manifest.ps1') -ProjectName $ProjectName -EnvFile $envPath -OutputPath (Join-Path $releaseRoot 'historical-after.json') -CompareManifest (Join-Path $releaseRoot 'historical-before.json')
   & docker @compose stop web server
   if($LASTEXITCODE -ne 0){throw 'Cannot pause migration runtime before current recovery capture.'}
   $currentBackup=Join-Path $root ('.local/backups/current-artifact-'+$id)
   & (Join-Path $PSScriptRoot 'safe-backup.ps1') -ProjectName $ProjectName -EnvFile $envPath -Destination $currentBackup -OperationToken $lease.Token
   $record.currentBackup=$currentBackup
   $uploads=Compare-ReleaseUploads -BeforeBackup $record.beforeBackup -CurrentBackup $currentBackup
   $record['uploadedFilesUnchanged']=$uploads.unchanged;$record['uploadedFilesAdded']=$uploads.added
   $record['uploadsVerification']='passed'
  }
  # Only verified data and a verified current recovery set permit restoring normal task settings
  # and opening Web ingress. The environment difference recreates the gated server as needed.
  & docker @compose -f compose.yaml -f $override up -d --no-build --no-deps --wait --wait-timeout 300 server web
  if($LASTEXITCODE -ne 0){throw 'Verified artifact did not become healthy with normal task settings.'}
  foreach($service in @('server','web')){
   $container=(& docker @compose ps -a -q $service) -join ''
   $actual=(& docker inspect --format '{{.Image}}' $container) -join ''
   if($LASTEXITCODE -ne 0 -or $actual -cne $imageMap[$service]){throw 'Deployed image differs from the validated artifact.'}
   if(!$Initialize -and $service -ceq 'server'){Assert-ReleaseUploadsMount -Expected $record.uploadsMount -ContainerId $container;$record['uploadsMountVerification']='passed'}
  }
  $record.outcome='healthy';$record.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
  Write-OperationsJson -Path (Join-Path $releaseRoot 'release.json') -Value $record
  if($record.currentBackup){Write-OperationsJson -Path (Join-Path $root '.local/backups/LATEST.json') -Value @{directory=$record.currentBackup;sourceSha=$ExpectedSourceSha;runId=$ExpectedRunId};[IO.File]::WriteAllText((Join-Path $root '.local/backups/LATEST.txt'),$record.currentBackup)}
  Write-Host "Tested CI artifact deployed; release record: $releaseRoot"
 }catch{
  & docker @compose stop web server | Out-Null
  $record.outcome='failed_paused';$record.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
  Write-OperationsJson -Path (Join-Path $releaseRoot 'release.json') -Value $record
  throw 'Artifact deployment or data verification failed. Writers remain paused; preserve the pre-release recovery set and inspect the release record. No build or unsafe image rollback was attempted.'
 }
}finally{Pop-Location;Exit-OperationsLock $lease}
