# Exercise real promotion orchestration with isolated registry/GitHub boundaries.
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixture = Join-Path $root ('.local/publish-images-test-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory((Join-Path $fixture 'scripts'))
[void][IO.Directory]::CreateDirectory((Join-Path $fixture '.local'))
Copy-Item -LiteralPath (Join-Path $root 'scripts/publish-images.ps1') -Destination (Join-Path $fixture 'scripts/publish-images.ps1')
@'
function Read-VerifiedReleaseBundle {param($Directory,$ExpectedSourceSha,$ExpectedRunId,$ExpectedManifestSha256)
  if($ExpectedSourceSha -cne ('a'*40) -or $ExpectedRunId -ne '123' -or $ExpectedManifestSha256 -ne ('b'*64)){throw 'Unexpected provenance request.'}
  return @{runAttempt=1;archive='application-images.tar';files=@(@{path='runtime-sources.tar.gz';sha256=('c'*64)})}
}
function Confirm-ReleaseWorkflow {param($SourceSha,$RunId,$RunAttempt);if($RunAttempt -ne 1){throw 'Unexpected workflow.'}}
function Assert-LoadedReleaseImages {param($Manifest)
  foreach($service in @('server','web')){ @{service=$service;runtimeId=('sha256:'+('d'*64));configDigest=('sha256:'+('d'*64));diffIds=@('sha256:'+('e'*64))} }
}
'@ | Set-Content -LiteralPath (Join-Path $fixture 'scripts/release-bundle.ps1')
function global:Invoke-RestMethod {
  param($Uri,$Headers,[switch]$SkipHttpErrorCheck,$StatusCodeVariable)
  if($Uri -like '*/releases/tags/*'){
    return @{draft=$false;prerelease=$false;assets=@(@{name='runtime-sources.tar.gz';digest=$(if($global:promotionCase -eq 'missing-source'){'sha256:'+('f'*64)}else{'sha256:'+('c'*64)})})}
  }
  if($Uri -like '*/git/ref/tags/*'){return @{object=@{type='commit';sha=('a'*40)}}}
  if($Uri -like '*/packages/container/*'){
    Set-Variable -Name $StatusCodeVariable -Value $(if($global:promotionCase -eq 'inventory-denied'){403}else{200}) -Scope 1
    if($global:promotionCase -eq 'duplicate'){return @(@{metadata=@{container=@{tags=@('v1.0.3')}}})}
    return @()
  }
  throw 'Unexpected GitHub request.'
}
function global:docker {
  $global:LASTEXITCODE=0
  $a=@($args)
  if($a[0] -eq 'push'){$global:promotionPushes++;return}
  if($a[0] -eq 'buildx'){
    @{config=@{digest=$(if($global:promotionCase -eq 'config-mismatch'){'sha256:'+('f'*64)}else{'sha256:'+('d'*64)})}}|ConvertTo-Json -Compress
    return
  }
  if($a[0] -eq 'image' -and $a[1] -eq 'inspect'){
    $repository=($a[2] -split '[:@]',2)[0]
    @{RepoDigests=@($repository+'@sha256:'+('f'*64));RootFS=@{Layers=@('sha256:'+('e'*64))}}|ConvertTo-Json -Depth 4 -AsArray
    return
  }
  if($a[0] -in @('login','logout','tag','pull') -or ($a[0] -eq 'image' -and $a[1] -eq 'load')){return}
  throw 'Unexpected registry operation.'
}
$original=@{}
foreach($key in @('CI','GITHUB_REPOSITORY','GH_TOKEN','GITHUB_ACTOR')){$original[$key]=[Environment]::GetEnvironmentVariable($key)}
Push-Location $fixture
try{
  $env:CI='true';$env:GITHUB_REPOSITORY='W205614/CampusEats';$env:GH_TOKEN='fixture';$env:GITHUB_ACTOR='fixture'
  foreach($case in @('missing-source','duplicate','inventory-denied','config-mismatch','success')){
    $global:promotionCase=$case;$global:promotionPushes=0;$failed=$false
    try{& ./scripts/publish-images.ps1 -BundleDirectory .local -SourceSha ('a'*40) -RunId 123 -ManifestSha256 ('b'*64) -Version v1.0.3}catch{$failed=$true}
    if($failed -ne ($case -ne 'success')){throw "Unexpected promotion result: $case"}
    $expected=if($case -eq 'success'){4}elseif($case -eq 'config-mismatch'){2}else{0}
    if($global:promotionPushes -ne $expected){throw "Promotion crossed a failed gate: $case"}
    if($case -eq 'success'){
      $record=Get-Content .local/package-publication.json -Raw|ConvertFrom-Json
      if($record.images.Count -ne 2 -or @($record.images|Where-Object digest -CNE ('sha256:'+('f'*64))).Count){throw 'Published digest receipt differs.'}
    }
  }
  Write-Host 'Package promotion: source/duplicate/permission gates, registry identity rejection, two-image push/pull and digest receipts passed.'
}finally{
  Pop-Location
  foreach($key in $original.Keys){[Environment]::SetEnvironmentVariable($key,$original[$key])}
  Remove-Item Function:\docker,Function:\Invoke-RestMethod
  Remove-Variable promotionCase,promotionPushes -Scope Global -ErrorAction SilentlyContinue
}
