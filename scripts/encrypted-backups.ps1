. (Join-Path $PSScriptRoot 'secure-files.ps1')

function Get-PinnedAge {
 param([string]$ToolRoot=(Join-Path (Split-Path $PSScriptRoot -Parent) '.local/tools/age'))
 if([Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne [Runtime.InteropServices.Architecture]::X64){throw 'Pinned age supports x64 Windows/Linux.'}
 $platform=if($IsWindows){'windows'}elseif($IsLinux){'linux'}else{throw 'Unsupported age platform.'}
 $version='1.3.2';$extension=if($IsWindows){'zip'}else{'tar.gz'}
 $asset="age-v$version-$platform-amd64.$extension"
 $expected=if($IsWindows){'f48d8f8f9ebe903ab5027ed067652f2cc1db94bc206976430133b905dcd8e8c7'}else{'cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10'}
 $directory=New-SecureDirectory -Path (Join-Path $ToolRoot "$version-$platform")
 $archive=Join-Path $directory $asset
 if(!(Test-Path -LiteralPath $archive)){Invoke-WebRequest -Uri "https://github.com/FiloSottile/age/releases/download/v$version/$asset" -OutFile $archive -TimeoutSec 120}
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected){throw 'Pinned age archive checksum mismatch.'}
 if($IsWindows){Expand-Archive -LiteralPath $archive -DestinationPath $directory -Force}else{& tar -xzf $archive -C $directory;if($LASTEXITCODE -ne 0){throw 'Pinned age extraction failed.'}}
 $binary=Join-Path $directory ('age/age'+$(if($IsWindows){'.exe'}else{''}))
 $detected=& $binary --version 2>&1
 if($LASTEXITCODE -ne 0 -or ($detected -join '').Trim() -notin @('v1.3.2','1.3.2')){throw 'Unexpected age version.'}
 return $binary
}

function Invoke-AgeBounded {
 param([Parameter(Mandatory)][string]$Executable,[Parameter(Mandatory)][string[]]$Arguments)
 $process=[Diagnostics.Process]::new();$process.StartInfo=[Diagnostics.ProcessStartInfo]::new($Executable)
 $process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
 $process.StartInfo.RedirectStandardOutput=$true;$process.StartInfo.RedirectStandardError=$true
 foreach($argument in $Arguments){$process.StartInfo.ArgumentList.Add($argument)}
 try {
  [void]$process.Start();$output=$process.StandardOutput.ReadToEndAsync();$errorText=$process.StandardError.ReadToEndAsync()
  if(!$process.WaitForExit(600000)){$process.Kill($true);throw 'age operation exceeded its time budget.'}
  [void]$output.GetAwaiter().GetResult();[void]$errorText.GetAwaiter().GetResult()
  if($process.ExitCode -ne 0){throw 'age operation failed; no success marker was published.'}
 } finally {$process.Dispose()}
}

function Copy-EncryptedBackupReplica {
 param([Parameter(Mandatory)][string]$Source,[Parameter(Mandatory)][string]$ReplicaDirectory,[Parameter(Mandatory)][string]$ProjectName,[Parameter(Mandatory)][string]$Recipient,[string]$AgeExecutable='')
 if($Recipient -notmatch '^age1[0-9a-z]{20,120}$'){throw 'A valid age public recipient is required before exporting a backup.'}
 if(!$AgeExecutable){$AgeExecutable=Get-PinnedAge}
 $root=Split-Path $PSScriptRoot -Parent
 $stageRoot=New-SecureDirectory -Path (Join-Path $root ('.local/encryption-stage/'+[guid]::NewGuid().ToString('N')))
 # Reuse source-marker and copy-race validation, but keep the plaintext copy in protected .local.
 $copy=Copy-VerifiedBackupReplica -Source $Source -ReplicaDirectory (Join-Path $stageRoot 'verified-copy') -ProjectName $ProjectName
 $entries=@(Get-Content -LiteralPath (Join-Path $copy 'sha256.json') -Raw | ConvertFrom-Json)
 $members=@($entries.path)+@('sha256.json','verified.json')
 foreach($member in $members){if($member -match '(^[/\\]|^[a-zA-Z]:|(^|[/\\])\.\.([/\\]|$)|[\r\n])'){throw 'Unsafe backup member.'}}
 $list=Join-Path $stageRoot 'members.txt';Write-SecureTextFile -Path $list -Content ($members -join "`n")
 $plain=Join-Path $stageRoot 'backup.tar.gz';Write-SecureTextFile -Path $plain -Content ''
 & tar -czf $plain -C $copy -T $list
 if($LASTEXITCODE -ne 0){throw 'Protected backup archive failed.'}
 Set-SecurePathPermissions -Path $plain
 $destinationRoot=New-SecureDirectory -Path $ReplicaDirectory
 $id=[guid]::NewGuid().ToString('N');$partial=New-SecureDirectory -Path (Join-Path $destinationRoot ('.partial-'+$id))
 $cipher=Join-Path $partial 'backup.tar.gz.age'
 Invoke-AgeBounded -Executable $AgeExecutable -Arguments @('-r',$Recipient,'-o',$cipher,$plain)
 $sourceMarker=Get-Content -LiteralPath (Join-Path $copy 'verified.json') -Raw | ConvertFrom-Json
 $record=[ordered]@{schemaVersion=1;format='age-v1';ageVersion='1.3.2';project=$ProjectName;completedAt=[DateTimeOffset]::UtcNow.ToString('o');backupVerifiedAt=$sourceMarker.verifiedAt;sourceManifestSha256=$sourceMarker.manifestSha256;archive='backup.tar.gz.age';archiveSha256=(Get-FileHash -LiteralPath $cipher -Algorithm SHA256).Hash;bytes=(Get-Item -LiteralPath $cipher).Length}
 Write-OperationsJson -Path (Join-Path $partial 'encrypted-replica.json') -Value $record
 $published=Join-Path $destinationRoot ($ProjectName+'-'+$id)
 [IO.Directory]::Move($partial,$published)
 # No plaintext file is ever written to ReplicaDirectory. Recycle owned Windows staging only
 # after the encrypted copy is complete; non-Windows ephemeral CI retains it until disposal.
 Move-ProjectFileToRecycleBin -Path $stageRoot
 return $published
}

function Find-VerifiedEncryptedBackupReplica {
 param([Parameter(Mandatory)][string]$ReplicaDirectory,[Parameter(Mandatory)][string]$ProjectName,[int]$MaxAgeHours=48)
 if(!(Test-Path -LiteralPath $ReplicaDirectory -PathType Container)){throw 'Encrypted replica directory is unavailable.'}
 foreach($candidate in @(Get-ChildItem -LiteralPath $ReplicaDirectory -Directory -Force | Where-Object Name -NotLike '.partial-*' | Sort-Object LastWriteTimeUtc -Descending)){
  try {
   Assert-NoSecurePathLinks -Path $candidate.FullName
   $record=Get-Content -LiteralPath (Join-Path $candidate.FullName 'encrypted-replica.json') -Raw | ConvertFrom-Json
   $age=([DateTimeOffset]::UtcNow-(ConvertTo-OperationsTimestamp $record.backupVerifiedAt)).TotalHours
   if($record.schemaVersion -ne 1 -or $record.format -ne 'age-v1' -or $record.project -ne $ProjectName -or $age -lt 0 -or $age -gt $MaxAgeHours -or $record.archive -cne 'backup.tar.gz.age'){continue}
   $cipher=Join-Path $candidate.FullName $record.archive
   Assert-NoSecurePathLinks -Path $cipher
   if((Get-Item -LiteralPath $cipher).Length -ne $record.bytes -or (Get-FileHash -LiteralPath $cipher -Algorithm SHA256).Hash -cne $record.archiveSha256){continue}
   return $candidate.FullName
  } catch {continue}
 }
 throw 'No recent verified encrypted replica is available.'
}

function Expand-EncryptedBackupReplica {
 param([Parameter(Mandatory)][string]$ReplicaDirectory,[Parameter(Mandatory)][string]$Destination,[Parameter(Mandatory)][string]$IdentityFile,[string]$AgeExecutable='')
 $root=Split-Path $PSScriptRoot -Parent;$destinationPath=[IO.Path]::GetFullPath($Destination,$root)
 $local=[IO.Path]::GetFullPath((Join-Path $root '.local'))+[IO.Path]::DirectorySeparatorChar
 if(!$destinationPath.StartsWith($local,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $destinationPath)){throw 'Decrypt into a new protected workspace .local directory.'}
 $record=Get-Content -LiteralPath (Join-Path $ReplicaDirectory 'encrypted-replica.json') -Raw | ConvertFrom-Json
 $cipher=Join-Path $ReplicaDirectory 'backup.tar.gz.age'
 Assert-NoSecurePathLinks -Path $cipher;Set-SecurePathPermissions -Path $IdentityFile
 if($record.schemaVersion -ne 1 -or $record.archive -cne 'backup.tar.gz.age' -or (Get-FileHash -LiteralPath $cipher -Algorithm SHA256).Hash -cne $record.archiveSha256){throw 'Encrypted replica hash verification failed.'}
 if(!$AgeExecutable){$AgeExecutable=Get-PinnedAge}
 $stage=New-SecureDirectory -Path (Join-Path $root ('.local/decryption-stage/'+[guid]::NewGuid().ToString('N')))
 $plain=Join-Path $stage 'backup.tar.gz';Write-SecureTextFile -Path $plain -Content ''
 Invoke-AgeBounded -Executable $AgeExecutable -Arguments @('-d','-i',[IO.Path]::GetFullPath($IdentityFile),'-o',$plain,$cipher)
 $destinationPath=New-SecureDirectory -Path $destinationPath
 $stream=[IO.File]::OpenRead($plain);$gzip=[IO.Compression.GZipStream]::new($stream,[IO.Compression.CompressionMode]::Decompress)
 $reader=[System.Formats.Tar.TarReader]::new($gzip)
 try {
  while($entry=$reader.GetNextEntry()){
   $relative=$entry.Name -replace '^\./',''
   if(!$relative -or $relative -eq '.'){continue}
   if($relative -match '(^[/\\]|^[a-zA-Z]:|(^|[/\\])\.\.([/\\]|$))'){throw 'Unsafe archive path.'}
   $target=[IO.Path]::GetFullPath((Join-Path $destinationPath $relative))
   if(!$target.StartsWith($destinationPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive leaves protected destination.'}
   if($entry.EntryType -eq [System.Formats.Tar.TarEntryType]::Directory){New-SecureDirectory -Path $target | Out-Null;continue}
   if($entry.EntryType -notin @([System.Formats.Tar.TarEntryType]::RegularFile,[System.Formats.Tar.TarEntryType]::V7RegularFile)){throw 'Links and special archive entries are forbidden.'}
   New-SecureDirectory -Path (Split-Path $target -Parent) | Out-Null
   Write-SecureTextFile -Path $target -Content ''
   $out=[IO.File]::Open($target,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None)
   try {$entry.DataStream.CopyTo($out)} finally {$out.Dispose()}
  }
 } finally {$reader.Dispose();$gzip.Dispose();$stream.Dispose()}
 $manifest=Join-Path $destinationPath 'sha256.json'
 if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -cne $record.sourceManifestSha256){throw 'Decrypted source manifest differs.'}
 foreach($entry in @(Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json)){
  $path=[IO.Path]::GetFullPath((Join-Path $destinationPath $entry.path))
  if(!$path.StartsWith($destinationPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-Item -LiteralPath $path -Force).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $entry.sha256){throw 'Decrypted backup does not match its source.'}
 }
 Move-ProjectFileToRecycleBin -Path $stage
 return $destinationPath
}
