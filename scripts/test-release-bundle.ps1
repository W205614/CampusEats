$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
. (Join-Path $PSScriptRoot 'oci-image-fixture.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/release-bundle-test-'+[guid]::NewGuid().ToString('N')))
New-SecureDirectory -Path (Join-Path $fixture 'security') | Out-Null
$sha='a'*40;$run='12345';$images=@(New-ReleaseOciFixture -Path (Join-Path $fixture 'application-images.tar') -SourceSha $sha)
Write-OperationsJson -Path (Join-Path $fixture 'security/summary.json') -Value @($images | ForEach-Object {@{service=$_.service;status='passed';imageId=$_.sourceRuntimeId}})
[IO.File]::WriteAllText((Join-Path $fixture 'security/server-runtime-sbom.cdx.json'),'{}')
$files=@(Get-ChildItem -LiteralPath $fixture -File -Recurse | ForEach-Object {@{path=[IO.Path]::GetRelativePath($fixture,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
$manifest=@{schemaVersion=2;imageIdentityFormat='oci-uncompressed-v1';repository='W205614/CampusEats';sourceSha=$sha;runId=$run;runAttempt=1;platform='linux/amd64';archive='application-images.tar';images=$images;files=$files;migrationHashFormat='utf8-lf-v1';migrations=@(@{name='V1__fixture.sql';version=1;sha256=('b'*64)})}
$path=Join-Path $fixture 'release-manifest.json';Write-OperationsJson -Path $path -Value $manifest
$hash=(Get-FileHash -LiteralPath $path).Hash
[void](Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $sha -ExpectedRunId $run -ExpectedManifestSha256 $hash)
foreach($field in @('configDigest','platformManifestDigest','layerDigests','diffIds','sourceRuntimeId')){
 $saved=$images[0].$field;$images[0].$field=if($field -in @('layerDigests','diffIds')){@('sha256:'+('f'*64))}else{'sha256:'+('f'*64)}
 Write-OperationsJson -Path $path -Value $manifest
 $mismatchHash=(Get-FileHash -LiteralPath $path).Hash;$rejected=$false
 try{Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $sha -ExpectedRunId $run -ExpectedManifestSha256 $mismatchHash | Out-Null}catch{$rejected=$true}
 if(!$rejected){throw "Declared image substitution was accepted: $field"}
 $images[0].$field=$saved
}
# Legacy IDs are accepted only after deriving their config/platform identities from the
# same fully verified archive, with the source scan still bound to the legacy runtime ID.
$manifest.schemaVersion=1;$manifest.images=@($images | ForEach-Object {@{service=$_.service;reference=$_.reference;revision=$_.revision;id=$_.sourceRuntimeId}})
Write-OperationsJson -Path $path -Value $manifest
[void](Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $sha -ExpectedRunId $run -ExpectedManifestSha256 (Get-FileHash -LiteralPath $path).Hash)
$manifest.schemaVersion=2;$manifest.images=$images
$manifest.migrationHashFormat='unknown-format'
Write-OperationsJson -Path $path -Value $manifest
$unknownHash=(Get-FileHash -LiteralPath $path).Hash;$rejected=$false
try {Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $sha -ExpectedRunId $run -ExpectedManifestSha256 $unknownHash | Out-Null}catch{$rejected=$true}
if(!$rejected){throw 'Unknown migration hash format was accepted.'}
$manifest.migrationHashFormat='utf8-lf-v1';Write-OperationsJson -Path $path -Value $manifest;$hash=(Get-FileHash -LiteralPath $path).Hash
foreach($case in @('source','run','hash','archive')){
 $source=$sha;$expectedRun=$run;$expectedHash=$hash
 if($case -eq 'source'){$source='b'*40};if($case -eq 'run'){$expectedRun='999'};if($case -eq 'hash'){$expectedHash='0'*64}
 if($case -eq 'archive'){[IO.File]::AppendAllText((Join-Path $fixture 'application-images.tar'),'corrupt')}
 $rejected=$false
 try {Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $source -ExpectedRunId $expectedRun -ExpectedManifestSha256 $expectedHash | Out-Null}catch{$rejected=$true}
 if(!$rejected){throw "Release boundary accepted $case mismatch."}
}
Write-Host 'Release bundle: exact source/run, trusted manifest and all archive hashes fail closed; no Docker writes were used.'
