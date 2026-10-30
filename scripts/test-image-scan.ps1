# Check fail-closed orchestration without downloading a scanner or reading Docker.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$fixture=Join-Path $root ('.local/image-scan-test-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
$fake=Join-Path $fixture 'trivy-fixture.ps1'
@'
if($args[0] -eq '--version'){Write-Output 'Version: 0.76.0';exit 0}
$outputIndex=[Array]::IndexOf($args,'--output')
$output=if($outputIndex -ge 0){$args[$outputIndex+1]}else{$null}
if($args[0] -in @('image','sbom')){
 if($env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'scanner-failed'){exit 2}
 $kind=$args[0]
 if($kind -eq 'sbom' -and $env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'java-scanner-failed'){exit 2}
 $packageIndex=[Array]::IndexOf($args,'--pkg-types')
 $expected=if($kind -eq 'image'){'os'}else{'library'}
 if($args -contains '--ignore-unfixed' -or $args -contains '--java-db-repository' -or $args -notcontains '--list-all-pkgs' -or $args[$packageIndex+1] -ne $expected){throw 'Required full OS/SBOM package scope is incorrect.'}
 if($env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'invalid-report'){'{}' | Set-Content -LiteralPath $output;exit 0}
 $mode=$env:CAMPUS_TRIVY_FIXTURE_MODE
 $affected=$mode -eq 'unfixed-only' -or ($kind -eq 'image' -and $mode -in @('os-fixable-high','medium-unfixed','low-fixable')) -or ($kind -eq 'sbom' -and $mode -eq 'java-fixable-high') -or ($kind -eq 'image' -and (($mode -eq 'mysql-unfixed' -and $output -like '*mysql-os-*') -or ($mode -eq 'redis-unfixed' -and $output -like '*redis-os-*')))
 $fixed=if($mode -in @('os-fixable-high','java-fixable-high','low-fixable')){'2.0.0'}else{''}
 $severity=if($mode -eq 'medium-unfixed'){'MEDIUM'}elseif($mode -eq 'low-fixable'){'LOW'}else{'CRITICAL'}
 $vulnerabilities=if($affected){@(@{VulnerabilityID='FIXTURE-VULNERABILITY';Severity=$severity;FixedVersion=$fixed})}else{@()}
 $class=if($kind -eq 'image'){'os-pkgs'}else{'lang-pkgs'}
 $packages=if($kind -eq 'image' -and $mode -eq 'unreported-expat'){@(@{Name='libexpat';Version='2.9.0-r0'})}elseif($mode -eq 'missing-package-inventory'){@()}else{@(@{Name='fixture-clean-package';Version='1.0'})}
 @{Results=@(@{Class=$class;Type=$(if($kind -eq 'image'){'alpine'}else{'jar'});Vulnerabilities=@($vulnerabilities);Packages=@($packages)})} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $output
 exit 0
}
if($args -contains 'cyclonedx'){'{"bomFormat":"CycloneDX","specVersion":"1.6","components":[]}' | Set-Content -LiteralPath $output;exit 0}
# convert has no --ignore-unfixed flag in pinned Trivy. The gate must inspect the full JSON.
exit 3
'@ | Set-Content -LiteralPath $fake
$originalMode=$env:CAMPUS_TRIVY_FIXTURE_MODE
$global:fixtureSbomImage='sha256:'+('a'*64)
$global:fixtureSbomContainer='c'*64
$global:fixtureSbomRoot=$fixture
$global:fixtureRemoved=0
[IO.File]::WriteAllText((Join-Path $fixture 'app.jar'),'fixture-actual-application-jar')
@{bomFormat='CycloneDX';specVersion='1.6';metadata=@{component=@{name='sky-server'}};components=@(1..20 | ForEach-Object {@{name="component-$_";version='1';purl="pkg:maven/fixture/component-$_@1"}})} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $fixture 'sbom.cdx.json')
@('app.jar','sbom.cdx.json') | ForEach-Object {((Get-FileHash (Join-Path $fixture $_)).Hash.ToLowerInvariant()+'  '+$_)} | Set-Content -LiteralPath (Join-Path $fixture 'runtime-manifest.sha256')
function global:docker {
 $global:LASTEXITCODE=0
 $a=@($args)
 if($a[0] -eq 'image'){return $global:fixtureSbomImage}
 if($a[0] -eq 'create'){
  if($a -notcontains '--network' -or $a -notcontains 'none' -or $a -notcontains 'campuseats.purpose=sbom-extract'){throw 'Extraction was not explicitly scoped.'}
  return $global:fixtureSbomContainer
 }
 if($a[0] -eq 'inspect'){
  if($a[-1] -cne $global:fixtureSbomContainer){throw 'Inspect targeted an unrelated container.'}
  if($a[2] -like '*Labels*'){return 'sbom-extract'}
  if($env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'wrong-image'){return 'sha256:'+('b'*64)}
  return $global:fixtureSbomImage
 }
 if($a[0] -eq 'cp'){
  $source=($a[1] -split ':',2)[1]
  if($a[1] -notlike ($global:fixtureSbomContainer+':/app/*')){throw 'Copy targeted an unrelated container.'}
  if($env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'missing-bom'){$global:LASTEXITCODE=1;return}
  Copy-Item -LiteralPath (Join-Path $global:fixtureSbomRoot (Split-Path $source -Leaf)) -Destination $a[2]
  if($env:CAMPUS_TRIVY_FIXTURE_MODE -eq 'tampered-bom' -and $source -like '*.json'){'{}' | Set-Content -LiteralPath $a[2]}
  return
 }
 if($a[0] -eq 'rm'){
  if($a.Count -ne 2 -or $a[1] -cne $global:fixtureSbomContainer){throw 'Cleanup was not restricted to the newly created ID without volume deletion.'}
  $global:fixtureRemoved++;return
 }
 throw 'Unexpected Docker operation in SBOM extraction fixture.'
}
try {
 foreach($mode in @('clean','unfixed-only','os-fixable-high','java-fixable-high','medium-unfixed','low-fixable','mysql-unfixed','redis-unfixed','unreported-expat','missing-package-inventory','scanner-failed','java-scanner-failed','invalid-report','missing-bom','tampered-bom','wrong-image')){
  $env:CAMPUS_TRIVY_FIXTURE_MODE=$mode
  $out=Join-Path $fixture $mode
  $failed=$false
  try {& (Join-Path $PSScriptRoot 'scan-images.ps1') -ServerImage 'fixture:server' -WebImage 'fixture:web' -MySqlImage 'fixture:mysql' -RedisImage 'fixture:redis' -OutputDirectory $out -TrivyExecutable $fake 6>$null} catch {$failed=$true}
  if($failed -ne ($mode -ne 'clean')){throw ('Scanner accepted a vulnerability/failure or rejected a clean report: '+$mode)}
  $summary=@(Get-Content -LiteralPath (Join-Path $out 'summary.json') -Raw | ConvertFrom-Json)
  if(($summary.service -join ',') -cne 'server,web,mysql,redis'){throw 'Scanner did not report all four service images.'}
  if($mode -eq 'unreported-expat'){
   $gate=Get-Content -LiteralPath (Join-Path $out 'server-os-gate.json') -Raw | ConvertFrom-Json
   if($gate.count -ne 1 -or $gate.findings[0].id -cne 'CVE-2025-66382'){throw 'Known unresolved upstream Expat vulnerability was hidden by an empty vendor report.'}
  }
  if($mode -eq 'unfixed-only'){
   foreach($service in @('server','web','mysql','redis')){
    if((Get-Content -LiteralPath (Join-Path $out ($service+'-os-vulnerabilities.json')) -Raw) -notmatch 'FIXTURE-VULNERABILITY' -or !(Test-Path -LiteralPath (Join-Path $out ($service+'-os-sbom.cdx.json')))){throw 'Full unfixed OS report or SBOM was missing.'}
   }
   if((Get-Content -LiteralPath (Join-Path $out 'server-java-vulnerabilities.json') -Raw) -notmatch 'FIXTURE-VULNERABILITY' -or !(Test-Path -LiteralPath (Join-Path $out 'server-runtime-provenance.json'))){throw 'Mandatory Java SBOM report or actual-image provenance was missing.'}
  }
 }
 if($global:fixtureRemoved -lt 7){throw 'Owned extraction containers were not cleaned after both success and failure.'}
 Write-Host 'Image scan gate: four services, clean acceptance, all severities and unfixed rejection, exact-image BOM/JAR hashes, scanner/report/missing/tampered BOM failures, and scoped non-running cleanup passed.'
} finally {$env:CAMPUS_TRIVY_FIXTURE_MODE=$originalMode;Remove-Item Function:\docker -ErrorAction SilentlyContinue;Remove-Variable fixtureSbomImage,fixtureSbomContainer,fixtureSbomRoot,fixtureRemoved -Scope Global -ErrorAction SilentlyContinue}
