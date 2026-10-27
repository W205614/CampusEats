. (Join-Path $PSScriptRoot 'secure-files.ps1')
. (Join-Path $PSScriptRoot 'image-identity.ps1')

function Get-ReleaseMigrationHash {
 param([Parameter(Mandatory)][string]$Path)
 # Decode explicitly instead of ReadAllText: StreamReader BOM autodetection could otherwise
 # accept UTF-16 even when an explicit UTF-8 encoding was requested.
 $utf8=[Text.UTF8Encoding]::new($false,$true)
 $text=$utf8.GetString([IO.File]::ReadAllBytes([IO.Path]::GetFullPath($Path)))
 if($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF){$text=$text.Substring(1)}
 $text=$text.Replace("`r`n","`n").Replace("`r","`n")
 return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($text)))
}

function Read-VerifiedReleaseBundle {
 param([Parameter(Mandatory)][string]$Directory,[Parameter(Mandatory)][string]$ExpectedSourceSha,[Parameter(Mandatory)][string]$ExpectedRunId,[Parameter(Mandatory)][string]$ExpectedManifestSha256)
 if($ExpectedSourceSha -notmatch '^[a-f0-9]{40}$' -or $ExpectedRunId -notmatch '^[0-9]+$' -or $ExpectedManifestSha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Exact commit, run and trusted manifest hash are required.'}
 $directoryPath=[IO.Path]::GetFullPath($Directory);Assert-NoSecurePathLinks -Path $directoryPath
 $manifest=Join-Path $directoryPath 'release-manifest.json'
 if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -ine $ExpectedManifestSha256){throw 'Release manifest hash does not match the trusted CI result.'}
 $record=Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
 if($record.schemaVersion -notin @(1,2) -or $record.repository -cne 'W205614/CampusEats' -or $record.sourceSha -cne $ExpectedSourceSha -or [string]$record.runId -cne $ExpectedRunId -or $record.runAttempt -lt 1 -or $record.platform -cne 'linux/amd64' -or $record.migrationHashFormat -cne 'utf8-lf-v1'){throw 'Release bundle provenance or migration hash format differs from the requested CI run.'}
 if($record.schemaVersion -eq 2 -and $record.imageIdentityFormat -cne 'oci-uncompressed-v1'){throw 'Unsupported release image identity format.'}
 if(@($record.images).Count -ne 2 -or ((@($record.images.service | Sort-Object) -join ',') -cne 'server,web')){throw 'Expected exactly the two application images.'}
 foreach($image in $record.images){
  $sourceId=if($record.schemaVersion -eq 1){$image.id}else{$image.sourceRuntimeId}
  if($sourceId -notmatch '^sha256:[a-f0-9]{64}$' -or $image.revision -cne $ExpectedSourceSha -or $image.reference -cne ('campuseats-'+$image.service+':sha-'+$ExpectedSourceSha)){throw 'Unexpected application image identity.'}
 }
 if(!$record.files -or !$record.migrations -or $record.archive -cne 'application-images.tar'){throw 'Release bundle inventory is incomplete.'}
 $files=@{}
 foreach($file in $record.files){
  if($file.path -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._/-]*$' -or $file.path -match '(^|/)\.\.(/|$)' -or $file.sha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Invalid release member.'}
  $path=[IO.Path]::GetFullPath((Join-Path $directoryPath $file.path));Assert-NoSecurePathLinks -Path $path
  if(!$path.StartsWith($directoryPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-Item -LiteralPath $path -Force).Length -ne $file.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $file.sha256){throw 'Release member hash or size mismatch.'}
  if($files.ContainsKey($file.path)){throw 'Duplicate release member.'};$files[$file.path]=$true
 }
 if(!$files.ContainsKey('application-images.tar') -or !$files.ContainsKey('security/summary.json') -or !$files.ContainsKey('security/server-runtime-sbom.cdx.json')){throw 'Required image archive or security evidence is missing.'}
 # Version 1 is accepted only by deriving identities from its hash-verified OCI graph.
 # Version 2 must declare exactly the same identities; no revision-only fallback exists.
 $graph=@(Read-ReleaseImageGraph -Archive (Join-Path $directoryPath $record.archive) -SourceSha $ExpectedSourceSha)
 $scan=@(Get-Content -LiteralPath (Join-Path $directoryPath 'security/summary.json') -Raw | ConvertFrom-Json)
 $normalized=@()
 foreach($image in $record.images){
  $identity=@($graph | Where-Object service -CEQ $image.service)[0]
  $sourceId=if($record.schemaVersion -eq 1){$image.id}else{$image.sourceRuntimeId}
  if($sourceId -cnotin @($identity.configDigest,$identity.platformManifestDigest)){throw 'Scanned source runtime ID is outside the verified OCI graph.'}
  if($record.schemaVersion -eq 2){
   foreach($field in @('configDigest','platformManifestDigest','layerDigests','diffIds')){if((@($image.$field) -join ',') -cne (@($identity.$field) -join ',')){throw 'Declared image identity differs from the verified OCI graph.'}}
  }
  $evidence=@($scan | Where-Object service -CEQ $image.service)
  if($evidence.Count -ne 1 -or $evidence[0].status -cne 'passed' -or $evidence[0].imageId -cne $sourceId){throw 'Security evidence does not cover the selected source runtime image.'}
  $identity | Add-Member -NotePropertyName sourceRuntimeId -NotePropertyValue $sourceId
  $normalized+=$identity
 }
 $record.images=$normalized
 return $record
}

function Confirm-ReleaseWorkflow {
 param([Parameter(Mandatory)][string]$SourceSha,[Parameter(Mandatory)][string]$RunId,[Parameter(Mandatory)][int]$RunAttempt)
 if($SourceSha -notmatch '^[a-f0-9]{40}$' -or $RunId -notmatch '^[0-9]+$'){throw 'Invalid workflow identity.'}
 $workflow=Invoke-RestMethod -Uri ('https://api.github.com/repos/W205614/CampusEats/actions/runs/'+$RunId) -Headers @{Accept='application/vnd.github+json'} -TimeoutSec 20
 if($workflow.head_sha -cne $SourceSha -or $workflow.run_attempt -ne $RunAttempt -or $workflow.status -cne 'completed' -or $workflow.conclusion -cne 'success' -or $workflow.repository.full_name -cne 'W205614/CampusEats' -or $workflow.event -cne 'push' -or $workflow.head_branch -cne 'master'){throw 'Only the matching successful origin/master push workflow attempt may be deployed.'}
}

function Assert-LoadedReleaseImages {
 param([Parameter(Mandatory)]$Manifest)
 foreach($image in $Manifest.images){
  $raw=& docker image inspect $image.reference
  if($LASTEXITCODE -ne 0){throw 'Application image is missing after import.'}
  $actual=@($raw | ConvertFrom-Json)[0]
  Resolve-ReleaseImageIdentity -Expected $image -Actual $actual
 }
}

function Compare-ReleaseUploads {
 param([Parameter(Mandatory)][string]$BeforeBackup,[Parameter(Mandatory)][string]$CurrentBackup)
 $snapshots=@()
 foreach($backup in @($BeforeBackup,$CurrentBackup)){
  $files=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
  foreach($entry in @(Get-Content -LiteralPath (Join-Path $backup 'sha256.json') -Raw | ConvertFrom-Json)){
   if($entry.path -notmatch '^uploads[/\\]'){continue}
   $path=([string]$entry.path).Replace('\','/')
   if($files.ContainsKey($path) -or $entry.bytes -lt 0 -or $entry.sha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Invalid upload verification inventory.'}
   $files.Add($path,$entry)
  }
  $snapshots+=,$files
 }
 foreach($path in $snapshots[0].Keys){
  if(!$snapshots[1].ContainsKey($path) -or $snapshots[1][$path].bytes -ne $snapshots[0][$path].bytes -or $snapshots[1][$path].sha256 -ine $snapshots[0][$path].sha256){throw 'Historical upload is missing or changed after migration.'}
 }
 return [pscustomobject]@{unchanged=$snapshots[0].Count;added=($snapshots[1].Count-$snapshots[0].Count)}
}

function Get-ReleaseUploadsMount {
 param([Parameter(Mandatory)][string]$ContainerId)
 $raw=(& docker inspect --format '{{json .Mounts}}' $ContainerId) -join ''
 if($LASTEXITCODE -ne 0){throw 'Cannot inspect the application uploads mount.'}
 $mounts=@($raw | ConvertFrom-Json | Where-Object Destination -CEQ '/app/uploads')
 if($mounts.Count -ne 1 -or $mounts[0].Type -cne 'volume' -or !$mounts[0].Name -or !$mounts[0].Source){throw 'Daily uploads must use one explicit named volume.'}
 return [pscustomobject]@{Type=$mounts[0].Type;Name=$mounts[0].Name;Source=$mounts[0].Source;Destination=$mounts[0].Destination}
}

function Assert-ReleaseUploadsMount {
 param([Parameter(Mandatory)]$Expected,[Parameter(Mandatory)][string]$ContainerId)
 $actual=Get-ReleaseUploadsMount -ContainerId $ContainerId
 foreach($field in @('Type','Name','Source','Destination')){if($actual.$field -cne $Expected.$field){throw 'Application uploads volume differs from the original runtime.'}}
}
