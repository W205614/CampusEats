param(
  [Parameter(Mandatory)][string]$BundleDirectory, [Parameter(Mandatory)][string]$SourceSha,
  [Parameter(Mandatory)][string]$RunId, [Parameter(Mandatory)][string]$ManifestSha256,
  [Parameter(Mandatory)][ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+$')][string]$Version,
  [Parameter(Mandatory)][ValidatePattern('^[0-9]+$')][string]$DraftReleaseId
)
$ErrorActionPreference = 'Stop'
if ($env:CI -cne 'true' -or $env:GITHUB_REPOSITORY -cne 'W205614/CampusEats' -or !$env:GH_TOKEN) { throw 'Material transfer requires this repository workflow.' }
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$manifest = Read-VerifiedReleaseBundle -Directory $BundleDirectory -ExpectedSourceSha $SourceSha -ExpectedRunId $RunId -ExpectedManifestSha256 $ManifestSha256
Confirm-ReleaseWorkflow -SourceSha $SourceSha -RunId $RunId -RunAttempt $manifest.runAttempt
$headers = @{Authorization = 'Bearer ' + $env:GH_TOKEN; Accept = 'application/vnd.github+json' }
$api = 'https://api.github.com/repos/W205614/CampusEats'
$release = Invoke-RestMethod "$api/releases/$DraftReleaseId" -Headers $headers
$tag = Invoke-RestMethod "$api/git/ref/tags/$Version" -Headers $headers
if (!$release.draft -or $release.prerelease -or $release.tag_name -cne $Version -or $tag.object.type -cne 'commit' -or $tag.object.sha -cne $SourceSha) { throw 'An existing draft and exact source tag are required.' }
$members = @(
  @{name = 'runtime-sources.tar.gz'; sha256 = @($manifest.files | Where-Object path -CEQ 'runtime-sources.tar.gz')[0].sha256 },
  @{name = 'release-manifest.json'; sha256 = $ManifestSha256 }
)
foreach ($member in $members) {
  if (!$member.sha256 -or @($release.assets | Where-Object name -CEQ $member.name).Count) { throw 'Required sources are missing or a target attachment already exists; nothing will be overwritten.' }
}
$results = @()
foreach ($member in $members) {
  $file = Get-Item -LiteralPath (Join-Path $BundleDirectory $member.name)
  & gh release upload $Version $file.FullName --repo W205614/CampusEats
  if ($LASTEXITCODE -ne 0) { throw 'Upload failed; inspect the draft before another attempt.' }
  $current = Invoke-RestMethod "$api/releases/$($release.id)" -Headers $headers
  $asset = @($current.assets | Where-Object name -CEQ $member.name)
  if ($asset.Count -ne 1 -or $asset[0].state -cne 'uploaded' -or $asset[0].size -ne $file.Length -or $asset[0].digest -ine ('sha256:' + $member.sha256)) { throw 'Uploaded attachment identity differs from the verified CI material.' }
  $results += @{name = $member.name; assetId = $asset[0].id; bytes = $file.Length; sha256 = $member.sha256 }
}
@{sourceSha = $SourceSha; runId = $RunId; version = $Version; releaseId = $release.id; assets = $results; completedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Depth 6 | Set-Content '.local/release-materials.json'
