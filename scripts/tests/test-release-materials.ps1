# Exercise the real transfer script, isolating only GitHub and the already-tested bundle verifier.
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixture = Join-Path $root ('.local/release-materials-test-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory((Join-Path $fixture 'scripts'))
[void][IO.Directory]::CreateDirectory((Join-Path $fixture '.local'))
Copy-Item -LiteralPath (Join-Path $root 'scripts/prepare-release-materials.ps1') -Destination (Join-Path $fixture 'scripts/prepare-release-materials.ps1')
@'
function Read-VerifiedReleaseBundle {param($Directory,$ExpectedSourceSha,$ExpectedRunId,$ExpectedManifestSha256)
  if($global:materialCase -eq 'provenance' -or $ExpectedSourceSha -cne ('a'*40) -or $ExpectedRunId -ne '123' -or $ExpectedManifestSha256 -ne ('b'*64)){throw 'Provenance rejected.'}
  return @{runAttempt=1;files=@(@{path='runtime-sources.tar.gz';sha256=('c'*64)})}
}
function Confirm-ReleaseWorkflow {param($SourceSha,$RunId,$RunAttempt);if($global:materialCase -eq 'failed-run' -or $RunAttempt -ne 1){throw 'Workflow rejected.'}}
'@ | Set-Content -LiteralPath (Join-Path $fixture 'scripts/release-bundle.ps1')
'source fixture' | Set-Content -LiteralPath (Join-Path $fixture '.local/runtime-sources.tar.gz')
'manifest fixture' | Set-Content -LiteralPath (Join-Path $fixture '.local/release-manifest.json')
function global:Invoke-RestMethod {
  param($Uri,$Headers)
  if($Uri -like '*/git/ref/tags/*'){return @{object=@{type='commit';sha=$(if($global:materialCase -eq 'wrong-tag'){'d'*40}else{'a'*40})}}}
  if($Uri -like '*/releases/456' -and $global:materialUploads -eq 0){
    return @{id=456;draft=($global:materialCase -ne 'published');prerelease=$false;tag_name='v1.0.4';assets=$(if($global:materialCase -eq 'duplicate'){@(@{name='runtime-sources.tar.gz'})}else{@()})}
  }
  if($Uri -like '*/releases/456'){return @{assets=$global:materialAssets}}
  throw 'Unexpected GitHub boundary.'
}
function global:gh {
  $global:LASTEXITCODE=0
  if($args[0] -cne 'release' -or $args[1] -cne 'upload' -or $args[2] -cne 'v1.0.4' -or $args[4] -cne '--repo' -or $args[5] -cne 'W205614/CampusEats' -or $args -contains '--clobber'){throw 'Unexpected upload boundary.'}
  $file=Get-Item -LiteralPath $args[3]
  $digest=if($file.Name -ceq 'runtime-sources.tar.gz'){'c'*64}else{'b'*64}
  if($global:materialCase -eq 'upload-mismatch'){$digest='d'*64}
  $global:materialUploads++
  $global:materialAssets+=@{id=$global:materialUploads;name=$file.Name;state='uploaded';size=$file.Length;digest=('sha256:'+$digest)}
}
$original=@{}
foreach($key in @('CI','GITHUB_REPOSITORY','GH_TOKEN')){$original[$key]=[Environment]::GetEnvironmentVariable($key)}
Push-Location $fixture
try{
  $env:CI='true';$env:GITHUB_REPOSITORY='W205614/CampusEats';$env:GH_TOKEN='fixture'
  foreach($case in @('provenance','failed-run','wrong-tag','published','duplicate','upload-mismatch','success')){
    $global:materialCase=$case;$global:materialUploads=0;$global:materialAssets=@();$failed=$false
    try{& ./scripts/prepare-release-materials.ps1 -BundleDirectory .local -SourceSha ('a'*40) -RunId 123 -ManifestSha256 ('b'*64) -Version v1.0.4 -DraftReleaseId 456}catch{$failed=$true}
    if($failed -ne ($case -ne 'success')){throw "Unexpected material transfer result: $case"}
    $expected=if($case -eq 'success'){2}elseif($case -eq 'upload-mismatch'){1}else{0}
    if($global:materialUploads -ne $expected){throw "Material upload crossed a failed gate: $case"}
    if($case -eq 'success'){
      $record=Get-Content .local/release-materials.json -Raw|ConvertFrom-Json
      if($record.releaseId -ne 456 -or $record.assets.Count -ne 2 -or $record.sourceSha -cne ('a'*40)){throw 'Material receipt differs.'}
    }
  }
  Write-Host 'Release materials: provenance/run/tag/draft/duplicate gates, upload identity rejection and exact two-asset transfer passed.'
}finally{
  Pop-Location
  foreach($key in $original.Keys){[Environment]::SetEnvironmentVariable($key,$original[$key])}
  Remove-Item Function:\gh,Function:\Invoke-RestMethod
  Remove-Variable materialCase,materialUploads,materialAssets -Scope Global -ErrorAction SilentlyContinue
}
