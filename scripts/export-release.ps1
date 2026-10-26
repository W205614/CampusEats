param([string]$OutputDirectory='.local/release-bundle',[string]$SecurityDirectory='.local/security',[string]$SourceSha=$env:GITHUB_SHA,[string]$RunId=$env:GITHUB_RUN_ID,[string]$ServerImage='campuseats-server:v1',[string]$WebImage='campuseats-web:v1')
$ErrorActionPreference='Stop'
if($env:CI -cne 'true' -or $env:GITHUB_REPOSITORY -cne 'W205614/CampusEats' -or $SourceSha -notmatch '^[a-f0-9]{40}$' -or $RunId -notmatch '^[0-9]+$'){throw 'Release export requires the repository CI commit and run identity.'}
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$output=[IO.Path]::GetFullPath($OutputDirectory,$root)
if(Test-Path -LiteralPath $output){throw 'Release output already exists.'}
New-SecureDirectory -Path $output | Out-Null
$security=[IO.Path]::GetFullPath($SecurityDirectory,$root)
$scan=@(Get-Content -LiteralPath (Join-Path $security 'summary.json') -Raw | ConvertFrom-Json)
$images=@()
foreach($pair in @(@{service='server';image=$ServerImage},@{service='web';image=$WebImage})){
 $raw=& docker image inspect $pair.image
 if($LASTEXITCODE -ne 0){throw 'Cannot inspect tested image.'}
 $image=@($raw | ConvertFrom-Json)[0]
 if($image.Config.Labels.'org.opencontainers.image.revision' -cne $SourceSha -or $image.Os -cne 'linux' -or $image.Architecture -cne 'amd64'){throw 'Tested image does not match source revision and platform.'}
 $matching=@($scan | Where-Object {$_.service -ceq $pair.service -and $_.imageId -ceq $image.Id -and $_.status -ceq 'passed'})
 if($matching.Count -ne 1){throw 'Tested image did not pass its actual runtime scan.'}
 $reference='campuseats-'+$pair.service+':sha-'+$SourceSha
 & docker image tag $image.Id $reference
 if($LASTEXITCODE -ne 0){throw 'Cannot assign immutable source reference.'}
 $images+=@{service=$pair.service;reference=$reference;id=$image.Id;revision=$SourceSha}
}
$archive=Join-Path $output 'application-images.tar'
& docker image save --output $archive @($images.reference)
if($LASTEXITCODE -ne 0){throw 'Tested image export failed.'}
$securityOutput=New-SecureDirectory -Path (Join-Path $output 'security')
# Explicit whitelist: no logs, .env, credentials, source backup or extracted JAR enters the artifact.
foreach($name in @('summary.json','server-runtime-sbom.cdx.json','server-runtime-manifest.sha256','server-runtime-provenance.json','server-os-sbom.cdx.json','web-os-sbom.cdx.json','server-os-fixable-high-critical.json','web-os-fixable-high-critical.json','server-java-fixable-high-critical.json')){
 Copy-Item -LiteralPath (Join-Path $security $name) -Destination $securityOutput -ErrorAction Stop
}
$migrations=@(Get-ChildItem -LiteralPath (Join-Path $root 'sky-server/src/main/resources/db/migration') -Filter 'V*.sql' | Sort-Object Name | ForEach-Object {if($_.Name -notmatch '^V(\d+)__'){throw 'Unexpected migration name.'};@{name=$_.Name;version=[int]$Matches[1];sha256=(Get-ReleaseMigrationHash -Path $_.FullName);fileSha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
$files=@(Get-ChildItem -LiteralPath $output -File -Recurse | ForEach-Object {@{path=[IO.Path]::GetRelativePath($output,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
$manifest=@{schemaVersion=1;repository='W205614/CampusEats';sourceSha=$SourceSha;runId=$RunId;runAttempt=[int]$env:GITHUB_RUN_ATTEMPT;createdAt=[DateTimeOffset]::UtcNow.ToString('o');platform='linux/amd64';archive='application-images.tar';images=$images;migrationHashFormat='utf8-lf-v1';migrations=$migrations;files=$files}
$path=Join-Path $output 'release-manifest.json';Write-OperationsJson -Path $path -Value $manifest
$hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
[void](Read-VerifiedReleaseBundle -Directory $output -ExpectedSourceSha $SourceSha -ExpectedRunId $RunId -ExpectedManifestSha256 $hash)
Write-Host "RELEASE_MANIFEST_SHA256=$hash"
Write-Host "RELEASE_SOURCE_SHA=$SourceSha"
Write-Host "RELEASE_RUN_ID=$RunId"
