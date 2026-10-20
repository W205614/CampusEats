function Write-OperationsJson {
 param([string]$Path,$Value)
 [void][IO.Directory]::CreateDirectory((Split-Path $Path -Parent))
 $temporary=$Path+'.tmp-'+[guid]::NewGuid().ToString('N')
 try {
  [IO.File]::WriteAllText($temporary,($Value | ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
  [IO.File]::Move($temporary,$Path,$true)
 } finally {if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

function Get-OperationsFingerprint {
 param([string[]]$Issues)
 $bytes=[Text.Encoding]::UTF8.GetBytes((@($Issues | Sort-Object -Unique) -join "`n"))
 return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function ConvertTo-OperationsTimestamp {
 param($Value)
 if($Value -is [DateTimeOffset]){return $Value}
 if($Value -is [DateTime]){return [DateTimeOffset]::new($Value)}
 return [DateTimeOffset]::Parse([string]$Value,[Globalization.CultureInfo]::InvariantCulture)
}

function Read-ReleaseSchema {
 param([string[]]$ComposeArguments)
 $version='SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1;' | & docker @ComposeArguments exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N' 2>$null
 if($LASTEXITCODE -ne 0 -or [string]$version -notmatch '^\d+$'){throw 'Cannot inspect schema version.'}
 $history='SELECT installed_rank,version,checksum,success FROM flyway_schema_history ORDER BY installed_rank;' | & docker @ComposeArguments exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N' 2>$null
 if($LASTEXITCODE -ne 0){throw 'Cannot inspect migration history.'}
 $ddl=& docker @ComposeArguments exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --no-data --single-transaction --routines --events --skip-comments --skip-dump-date --no-tablespaces --set-gtid-purged=OFF -u campus sky_take_out' 2>$null
 if($LASTEXITCODE -ne 0 -or !$ddl){throw 'Cannot inspect database schema.'}
 # AUTO_INCREMENT changes reflect writes, not DDL. Failed/partial Flyway DDL and failed history rows
 # still change this fingerprint even when the highest successful version remains unchanged.
 $text=(@($ddl) -join "`n") -replace 'AUTO_INCREMENT=\d+','AUTO_INCREMENT=0'
 $text+="`nMIGRATIONS`n"+(@($history) -join "`n")
 $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
 return [pscustomobject]@{version=[int]$version;fingerprint=$hash}
}

function Copy-VerifiedBackupReplica {
 param([string]$Source,[string]$ReplicaDirectory,[string]$ProjectName)
 $sourceRoot=[IO.Path]::GetFullPath($Source)
 $replicaRoot=[IO.Path]::GetFullPath($ReplicaDirectory)
 if($replicaRoot.Equals($sourceRoot,[StringComparison]::OrdinalIgnoreCase) -or $replicaRoot.StartsWith($sourceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
  throw 'Replica must be outside the source backup.'
 }
 $marker=Get-Content -LiteralPath (Join-Path $sourceRoot 'verified.json') -Raw | ConvertFrom-Json
 $manifestPath=Join-Path $sourceRoot 'sha256.json'
 if($marker.project -ne $ProjectName -or (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -ne $marker.manifestSha256){throw 'Verified backup marker is invalid.'}
 $markerHash=(Get-FileHash -LiteralPath (Join-Path $sourceRoot 'verified.json') -Algorithm SHA256).Hash
 $verifiedTimestamp=ConvertTo-OperationsTimestamp $marker.verifiedAt
 $sourceAge=([DateTimeOffset]::UtcNow-$verifiedTimestamp).TotalHours
 if($sourceAge -lt 0 -or $sourceAge -gt 48){throw 'Source backup verification is not recent.'}
 $entries=@(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
 foreach($required in @('database.sql','source-counts.txt','all-table-counts.txt','tables.txt','table-checksums.txt','images.json','mounts.txt','.env')){
  if($required -notin @($entries.path)){throw 'Backup manifest is incomplete.'}
 }
 if(!(Test-Path -LiteralPath (Join-Path $sourceRoot 'uploads') -PathType Container)){throw 'Backup uploads directory is missing.'}
 # Never follow links out of the protected backup, including hidden items.
 $inventory=@(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Force)
 if(@($inventory | Where-Object {($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0}).Count){throw 'Backup contains unsupported links.'}
 foreach($entry in $entries){
  $path=[IO.Path]::GetFullPath((Join-Path $sourceRoot $entry.path))
  if(!$path.StartsWith($sourceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $path -PathType Leaf)) {throw 'Invalid backup manifest path.'}
  if((Get-Item -LiteralPath $path -Force).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Backup hash verification failed.'}
 }
 [void][IO.Directory]::CreateDirectory($replicaRoot)
 $id=[guid]::NewGuid().ToString('N')
 $stage=Join-Path $replicaRoot ('.partial-'+$id)
 $destination=Join-Path $replicaRoot ($ProjectName+'-'+(Split-Path $sourceRoot -Leaf)+'-'+$id)
 [void][IO.Directory]::CreateDirectory($stage)
 try {
  # Enumerating -Force is essential for .env on Linux and Hidden files on Windows.
  Get-ChildItem -LiteralPath $sourceRoot -Force | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $stage -Recurse -Force -ErrorAction Stop}
  # Check the destination against the ORIGINAL verified manifest, not a freshly rehashed source.
  # This rejects source mutation between the first verification and the actual copy.
  if((Get-FileHash -LiteralPath (Join-Path $stage 'sha256.json') -Algorithm SHA256).Hash -ne $marker.manifestSha256 -or (Get-FileHash -LiteralPath (Join-Path $stage 'verified.json') -Algorithm SHA256).Hash -ne $markerHash){throw 'Backup metadata changed during copying.'}
  foreach($entry in $entries){
   $target=[IO.Path]::GetFullPath((Join-Path $stage $entry.path))
   if(!$target.StartsWith($stage+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $target -PathType Leaf) -or (Get-Item -LiteralPath $target -Force).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Copied backup differs from the verified manifest.'}
  }
  $copied=[Collections.Generic.List[object]]::new()
  foreach($file in @($inventory | Where-Object {!$_.PSIsContainer})){
   $relative=[IO.Path]::GetRelativePath($sourceRoot,$file.FullName)
   $target=Join-Path $stage $relative
   $hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
   if(!(Test-Path -LiteralPath $target -PathType Leaf) -or (Get-Item -LiteralPath $target -Force).Length -ne $file.Length -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash){throw 'Replica hash verification failed.'}
   $copied.Add([ordered]@{path=$relative;bytes=$file.Length;sha256=$hash})
  }
  $copyManifest=Join-Path $stage 'replica-sha256.json'
  Write-OperationsJson -Path $copyManifest -Value @($copied.ToArray())
  Write-OperationsJson -Path (Join-Path $stage 'replica-complete.json') -Value ([ordered]@{
   project=$ProjectName;completedAt=[DateTimeOffset]::UtcNow.ToString('o');backupVerifiedAt=$verifiedTimestamp.ToString('o');sourceManifestSha256=$marker.manifestSha256
   replicaManifestSha256=(Get-FileHash -LiteralPath $copyManifest -Algorithm SHA256).Hash;files=$copied.Count
  })
  # Same-parent rename publishes only an already verified copy, never a partial directory.
  [IO.Directory]::Move($stage,$destination)
  return $destination
 } catch {
  # A failed .partial directory is retained for diagnosis and never has a public final name.
  if(Test-Path -LiteralPath (Join-Path $stage 'replica-complete.json')){Remove-Item -LiteralPath (Join-Path $stage 'replica-complete.json')}
  throw 'Backup replica was not completed; inspect the local replica directory.'
 }
}

function Find-VerifiedBackupReplica {
 param([string]$ReplicaDirectory,[string]$ProjectName,[int]$MaxAgeHours=48)
 if(!(Test-Path -LiteralPath $ReplicaDirectory -PathType Container)){throw 'Replica directory is unavailable.'}
 $candidates=@(Get-ChildItem -LiteralPath $ReplicaDirectory -Directory -Force | Where-Object Name -NotLike '.partial-*' | Sort-Object LastWriteTimeUtc -Descending)
 foreach($candidate in $candidates){
  try {
   if(($candidate.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){continue}
   $complete=Get-Content -LiteralPath (Join-Path $candidate.FullName 'replica-complete.json') -Raw | ConvertFrom-Json
   if($complete.project -ne $ProjectName){continue}
   $copyAge=([DateTimeOffset]::UtcNow-(ConvertTo-OperationsTimestamp $complete.completedAt)).TotalHours
   $backupAge=([DateTimeOffset]::UtcNow-(ConvertTo-OperationsTimestamp $complete.backupVerifiedAt)).TotalHours
   if($copyAge -lt 0 -or $copyAge -gt $MaxAgeHours -or $backupAge -lt 0 -or $backupAge -gt $MaxAgeHours){continue}
   $manifestPath=Join-Path $candidate.FullName 'replica-sha256.json'
   if((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -ne $complete.replicaManifestSha256){continue}
   $files=@(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
   if($files.Count -ne $complete.files -or '.env' -notin @($files.path) -or 'verified.json' -notin @($files.path)){continue}
   foreach($file in $files){
    $path=[IO.Path]::GetFullPath((Join-Path $candidate.FullName $file.path))
    if(!$path.StartsWith($candidate.FullName+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $path -PathType Leaf)){throw 'Invalid replica file.'}
    $item=Get-Item -LiteralPath $path -Force
    if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or $item.Length -ne $file.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256){throw 'Replica file hash mismatch.'}
   }
   return $candidate.FullName
  } catch {continue}
 }
 throw 'No recent complete verified replica is available.'
}

function Send-OperationsAlert {
 param([uri]$Url,$Payload,[int]$TimeoutSeconds=5,[int]$MaxAttempts=2)
 if($Url.Scheme -ne 'https' -and !($Url.Scheme -eq 'http' -and $Url.IsLoopback)){throw 'Alert URL requires HTTPS (HTTP is allowed only for loopback fixtures).'}
 if($Url.UserInfo){throw 'Alert URL must not contain user information.'}
 $handler=[Net.Http.HttpClientHandler]::new()
 $handler.AllowAutoRedirect=$false
 $client=[Net.Http.HttpClient]::new($handler)
 $client.Timeout=[TimeSpan]::FromSeconds($TimeoutSeconds)
 $json=$Payload | ConvertTo-Json -Compress -Depth 6
 try {
  for($attempt=1;$attempt -le $MaxAttempts;$attempt++){
   $content=[Net.Http.StringContent]::new($json,[Text.Encoding]::UTF8,'application/json')
   $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,$Url)
   $request.Content=$content
   $response=$null
   try {
    $response=$client.SendAsync($request,[Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    if($response.IsSuccessStatusCode){return $true}
    # Do not echo response bodies, request URLs, headers, or exception details.
   } catch {} finally {if($response){$response.Dispose()};$request.Dispose()}
   if($attempt -lt $MaxAttempts){[Threading.Thread]::Sleep(200)}
  }
  return $false
 } finally {$client.Dispose();$handler.Dispose()}
}
