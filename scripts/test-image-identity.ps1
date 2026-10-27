$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
. (Join-Path $PSScriptRoot 'image-identity.ps1')
. (Join-Path $PSScriptRoot 'oci-image-fixture.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/image-identity-test-'+[guid]::NewGuid().ToString('N')))
$archive=Join-Path $fixture 'images.tar';$sha='a'*40
$expected=@(New-ReleaseOciFixture -Path $archive -SourceSha $sha)
$graph=@(Read-ReleaseImageGraph -Archive $archive -SourceSha $sha)
if($graph.Count -ne 2){throw 'OCI fixture graph was incomplete.'}
foreach($image in $expected){
 foreach($runtimeId in @($image.configDigest,$image.platformManifestDigest)){
  $actual=[pscustomobject]@{Id=$runtimeId;Os='linux';Architecture='amd64';Config=@{Labels=@{'org.opencontainers.image.revision'=$sha}};RootFS=@{Type='layers';Layers=@($image.diffIds)}}
  if($runtimeId -eq $image.platformManifestDigest){$actual | Add-Member Descriptor @{digest=$image.platformManifestDigest}}
  $resolved=Resolve-ReleaseImageIdentity -Expected $image -Actual $actual
  if($resolved.runtimeId -cne $runtimeId){throw 'Runtime identity was not resolved exactly.'}
 }
 foreach($fault in @('unknown-id','layers','platform','revision','descriptor')){
  $actual=@{Id=$image.platformManifestDigest;Os='linux';Architecture='amd64';Config=@{Labels=@{'org.opencontainers.image.revision'=$sha}};RootFS=@{Type='layers';Layers=@($image.diffIds)};Descriptor=@{digest=$image.platformManifestDigest}}
  switch($fault){'unknown-id'{$actual.Id='sha256:'+('f'*64)};'layers'{$actual.RootFS.Layers=@('sha256:'+('e'*64))};'platform'{$actual.Architecture='arm64'};'revision'{$actual.Config.Labels.'org.opencontainers.image.revision'='b'*40};'descriptor'{$actual.Descriptor.digest='sha256:'+('f'*64)}}
  $rejected=$false;try{Resolve-ReleaseImageIdentity -Expected $image -Actual ([pscustomobject]$actual) | Out-Null}catch{$rejected=$true}
  if(!$rejected){throw "Unverified loaded image accepted: $fault"}
 }
}
foreach($fault in @('layer-bytes','config-bytes','mapping','diff-id','layer-size','auxiliary-unknown','auxiliary-id','auxiliary-bytes')){
 [void](New-ReleaseOciFixture -Path $archive -SourceSha $sha -Fault $fault)
 $rejected=$false;try{Read-ReleaseImageGraph -Archive $archive -SourceSha $sha | Out-Null}catch{$rejected=$true}
 if(!$rejected){throw "Corrupt OCI graph accepted: $fault"}
}
Write-Host 'Image identity: config/manifest runtime IDs resolve; unknown IDs, platform/revision/rootfs changes and substituted OCI blobs/mapping fail closed. No Docker used.'
