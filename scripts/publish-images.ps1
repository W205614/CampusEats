param(
  [Parameter(Mandatory)][string]$BundleDirectory, [Parameter(Mandatory)][string]$SourceSha,
  [Parameter(Mandatory)][string]$RunId, [Parameter(Mandatory)][string]$ManifestSha256,
  [Parameter(Mandatory)][ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+$')][string]$Version
)
$ErrorActionPreference = 'Stop'
if ($env:CI -cne 'true' -or $env:GITHUB_REPOSITORY -cne 'W205614/CampusEats' -or !$env:GH_TOKEN) { throw 'Package promotion requires this repository workflow.' }
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$manifest = Read-VerifiedReleaseBundle -Directory $BundleDirectory -ExpectedSourceSha $SourceSha -ExpectedRunId $RunId -ExpectedManifestSha256 $ManifestSha256
Confirm-ReleaseWorkflow -SourceSha $SourceSha -RunId $RunId -RunAttempt $manifest.runAttempt
$headers = @{Authorization = 'Bearer ' + $env:GH_TOKEN; Accept = 'application/vnd.github+json' }
$api = 'https://api.github.com'
$release = Invoke-RestMethod "$api/repos/W205614/CampusEats/releases/tags/$Version" -Headers $headers
$tag = Invoke-RestMethod "$api/repos/W205614/CampusEats/git/ref/tags/$Version" -Headers $headers
if ($release.draft -or $release.prerelease -or $tag.object.type -cne 'commit' -or $tag.object.sha -cne $SourceSha) { throw 'Published stable release and exact source tag must match.' }
$sources = Join-Path $BundleDirectory 'runtime-sources.tar.gz'
$sourceEntry = @($manifest.files | Where-Object path -CEQ 'runtime-sources.tar.gz')
$sourceAsset = @($release.assets | Where-Object name -CEQ 'runtime-sources.tar.gz')
if ($sourceEntry.Count -ne 1 -or $sourceAsset.Count -ne 1 -or $sourceAsset[0].digest -ine ('sha256:' + $sourceEntry[0].sha256)) { throw 'Matching runtime sources must be available with the release before package publication.' }
# Enumerate every existing version before publishing; never overwrite a release or source tag.
foreach ($service in @('server', 'web')) {
  $name = 'campuseats-' + $service
  for ($page = 1; ; $page++) {
    $versions = Invoke-RestMethod "$api/users/W205614/packages/container/$name/versions?per_page=100&page=$page" -Headers $headers -SkipHttpErrorCheck -StatusCodeVariable status
    if ($status -eq 404) { break }
    if ($status -ne 200) { throw "Cannot inventory existing $name versions (HTTP $status)." }
    if (@($versions | Where-Object { $Version -cin $_.metadata.container.tags -or ('sha-' + $SourceSha) -cin $_.metadata.container.tags }).Count) { throw 'A target package tag already exists; no tag will be overwritten.' }
    if (@($versions).Count -lt 100) { break }
  }
}
& docker image load --input (Join-Path $BundleDirectory $manifest.archive)
if ($LASTEXITCODE -ne 0) { throw 'Cannot load the verified image archive.' }
$images = @(Assert-LoadedReleaseImages -Manifest $manifest)
$results = [Collections.Generic.List[object]]::new()
try {
  $env:GH_TOKEN | & docker login ghcr.io --username $env:GITHUB_ACTOR --password-stdin
  if ($LASTEXITCODE -ne 0) { throw 'GHCR login failed.' }
  foreach ($service in @('server', 'web')) {
    $image = @($images | Where-Object service -CEQ $service)[0]
    $repository = 'ghcr.io/w205614/campuseats-' + $service
    foreach ($suffix in @(('sha-' + $SourceSha), $Version)) {
      $target = $repository + ':' + $suffix
      & docker tag $image.runtimeId $target
      if ($LASTEXITCODE -ne 0) { throw 'Cannot tag the tested image.' }
      & docker push $target
      if ($LASTEXITCODE -ne 0) { throw "Package push failed: $target" }
    }
    $raw = (& docker buildx imagetools inspect ($repository + ':' + $Version) --raw) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the published registry manifest.' }
    $registryManifest = $raw | ConvertFrom-Json
    if ($registryManifest.config.digest -cne $image.configDigest) { throw 'Registry config differs from the tested image.' }
    & docker pull ($repository + ':' + $Version)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot pull the published version.' }
    $pulled = @(((& docker image inspect ($repository + ':' + $Version)) -join "`n") | ConvertFrom-Json)[0]
    $references = @($pulled.RepoDigests | Where-Object { $_.StartsWith($repository + '@sha256:', [StringComparison]::Ordinal) })
    if ($LASTEXITCODE -ne 0 -or $references.Count -ne 1) { throw 'Cannot resolve a unique registry digest.' }
    $digest = $references[0].Substring($repository.Length + 1)
    & docker pull ($repository + '@' + $digest)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot pull the published immutable image.' }
    $actual = @(((& docker image inspect ($repository + '@' + $digest)) -join "`n") | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0 -or (@($actual.RootFS.Layers) -join ',') -cne (@($image.diffIds) -join ',')) { throw 'Published runtime layers differ from the tested image.' }
    $results.Add(@{service = $service; repository = $repository; version = $Version; digest = $digest; configDigest = $image.configDigest; diffIds = $image.diffIds })
  }
} finally {
  & docker logout ghcr.io | Out-Null
  @{sourceSha = $SourceSha; runId = $RunId; version = $Version; images = @($results.ToArray()); sourcesSha256 = $sourceEntry[0].sha256; completedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Depth 8 | Set-Content '.local/package-publication.json'
}
