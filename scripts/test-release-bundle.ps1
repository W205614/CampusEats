$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/release-bundle-test-'+[guid]::NewGuid().ToString('N')))
New-SecureDirectory -Path (Join-Path $fixture 'security') | Out-Null
$sha='a'*40;$run='12345';$images=@(@{service='server';id=('sha256:'+('1'*64));reference=('campuseats-server:sha-'+$sha);revision=$sha},@{service='web';id=('sha256:'+('2'*64));reference=('campuseats-web:sha-'+$sha);revision=$sha})
[IO.File]::WriteAllText((Join-Path $fixture 'application-images.tar'),'isolated-fixture-not-a-real-image')
Write-OperationsJson -Path (Join-Path $fixture 'security/summary.json') -Value @($images | ForEach-Object {@{service=$_.service;status='passed';imageId=$_.id}})
[IO.File]::WriteAllText((Join-Path $fixture 'security/server-runtime-sbom.cdx.json'),'{}')
$files=@(Get-ChildItem -LiteralPath $fixture -File -Recurse | ForEach-Object {@{path=[IO.Path]::GetRelativePath($fixture,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
$manifest=@{schemaVersion=1;repository='W205614/CampusEats';sourceSha=$sha;runId=$run;runAttempt=1;platform='linux/amd64';archive='application-images.tar';images=$images;files=$files;migrationHashFormat='utf8-lf-v1';migrations=@(@{name='V1__fixture.sql';version=1;sha256=('b'*64)})}
$path=Join-Path $fixture 'release-manifest.json';Write-OperationsJson -Path $path -Value $manifest
$hash=(Get-FileHash -LiteralPath $path).Hash
[void](Read-VerifiedReleaseBundle -Directory $fixture -ExpectedSourceSha $sha -ExpectedRunId $run -ExpectedManifestSha256 $hash)
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
