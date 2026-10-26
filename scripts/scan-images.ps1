param(
 [Parameter(Mandatory)][string]$ServerImage,[Parameter(Mandatory)][string]$WebImage,
 [string]$OutputDirectory='.local/security',[string]$TrivyExecutable='',
 [ValidateSet('Both','Server','Web')][string]$Service='Both'
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$outputRoot=[IO.Path]::GetFullPath($OutputDirectory,$taskRoot)
[void][IO.Directory]::CreateDirectory($outputRoot)
$version='0.76.0'
if(!$TrivyExecutable){
 if([Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne [Runtime.InteropServices.Architecture]::X64){throw 'Pinned Trivy bootstrap supports x64 Windows/Linux only; provide a verified executable on other platforms.'}
 $tools=Join-Path $taskRoot ('.local/tools/trivy/'+$version+$(if($IsWindows){'-windows'}else{'-linux'}))
 [void][IO.Directory]::CreateDirectory($tools)
 $asset=if($IsWindows){"trivy_${version}_windows-64bit.zip"}elseif($IsLinux){"trivy_${version}_Linux-64bit.tar.gz"}else{throw 'Provide a verified Trivy executable on this platform.'}
 $expected=if($IsWindows){'7b4762a5a5326cb4b83dfd2bb5d9aaf15a1ed867c75cad7dd8b38840a28e7be6'}else{'e24b1c51293c1b15e4ba8d45bbc7891ba053f90278ec2698f3562c993e145fe7'}
 $archive=Join-Path $tools $asset
 # Always verify the pinned archive before extracting, including cached installations.
 if(!(Test-Path -LiteralPath $archive)){Invoke-WebRequest -Uri "https://github.com/aquasecurity/trivy/releases/download/v$version/$asset" -OutFile $archive -TimeoutSec 120}
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected){throw 'Pinned Trivy archive checksum mismatch.'}
 if($IsWindows){Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force;$TrivyExecutable=Join-Path $tools 'trivy.exe'}
 else {& tar -xzf $archive -C $tools trivy;if($LASTEXITCODE -ne 0){throw 'Cannot extract pinned Trivy.'};$TrivyExecutable=Join-Path $tools 'trivy'}
}
$detected=& $TrivyExecutable --version 2>&1
if($LASTEXITCODE -ne 0 -or ($detected -join "`n") -notmatch ('(?m)^Version:\s*'+[regex]::Escape($version)+'\s*$')){throw 'The scanner must be the pinned Trivy version.'}
$cache=Join-Path $taskRoot '.local/trivy-cache'
function Invoke-VulnerabilityScan([string]$Kind,[string]$Target,[string]$Report,[string]$Log){
 for($attempt=1;$attempt -le 2;$attempt++){
  $repositories=if($attempt -eq 1){'ghcr.io/aquasecurity/trivy-db:2,mirror.gcr.io/aquasec/trivy-db:2'}else{'mirror.gcr.io/aquasec/trivy-db:2,ghcr.io/aquasecurity/trivy-db:2'}
  $packages=if($Kind -eq 'image'){'os'}else{'library'}
  & $TrivyExecutable $Kind --cache-dir $cache --timeout 5m --no-progress --scanners vuln --pkg-types $packages --list-all-pkgs --db-repository $repositories --format json --output $Report $Target *>> $Log
  $code=$LASTEXITCODE
  if($code -eq 0){return 0}
 }
 return $code
}

function Test-VulnerabilityGate([string]$Report,[string]$Gate,[string]$ExpectedClass){
 $data=Get-Content -LiteralPath $Report -Raw | ConvertFrom-Json
 if($null -eq $data.Results -or !@($data.Results | Where-Object {$_.Class -eq $ExpectedClass}).Count){throw 'Required package class is missing from the full scan report.'}
 $findings=@(foreach($result in $data.Results){foreach($finding in @($result.Vulnerabilities)){
  if($finding -and ($finding -is [string] -or !$finding.VulnerabilityID -or $finding.Severity -notin @('UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL'))){throw 'Invalid vulnerability record.'}
  if($finding -and $finding.Severity -in @('HIGH','CRITICAL') -and ![string]::IsNullOrWhiteSpace([string]$finding.FixedVersion)){
   [ordered]@{target=$result.Target;class=$result.Class;type=$result.Type;id=$finding.VulnerabilityID;package=$finding.PkgName;severity=$finding.Severity;installedVersion=$finding.InstalledVersion;fixedVersion=$finding.FixedVersion}
  }
 }})
 [ordered]@{policy='HIGH/CRITICAL with a nonempty FixedVersion';count=$findings.Count;findings=$findings} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Gate -Encoding utf8
 if($findings.Count){return 1};return 0
}

function Export-ImageRuntimeBom([string]$ImageId,[string]$Log){
 $containerId=$null
 $bom=Join-Path $outputRoot 'server-runtime-sbom.cdx.json'
 $jar=Join-Path $outputRoot ('.runtime-jar-'+[guid]::NewGuid().ToString('N'))
 $manifest=Join-Path $outputRoot 'server-runtime-manifest.sha256'
 try {
  # create never starts a process. The exact resolved image ID and unique label bind all copies.
  $containerId=(& docker create --name ('campuseats-sbom-'+[guid]::NewGuid().ToString('N')) --label campuseats.purpose=sbom-extract --network none --entrypoint /bin/true $ImageId 2>> $Log) -join ''
  if($LASTEXITCODE -ne 0 -or $containerId -notmatch '^[a-f0-9]{64}$'){throw 'Cannot create isolated SBOM extraction container.'}
  $actualImage=(& docker inspect --format '{{.Image}}' $containerId 2>> $Log) -join ''
  if($LASTEXITCODE -ne 0 -or $actualImage -cne $ImageId){throw 'Extraction container image differs from the requested image.'}
  foreach($copy in @(@{source='/app/sbom.cdx.json';target=$bom},@{source='/app/app.jar';target=$jar},@{source='/app/runtime-manifest.sha256';target=$manifest})){
   & docker cp ($containerId+':'+$copy.source) $copy.target *>> $Log
   if($LASTEXITCODE -ne 0){throw 'The actual image is missing its runtime BOM, JAR or build manifest.'}
  }
  $hashes=@{}
  foreach($line in @(Get-Content -LiteralPath $manifest)){
   if($line -notmatch '^([a-fA-F0-9]{64})\s+\*?(app\.jar|sbom\.cdx\.json)$'){throw 'Invalid runtime build manifest.'}
   $hashes[$Matches[2]]=$Matches[1]
  }
  if($hashes.Count -ne 2 -or (Get-FileHash -LiteralPath $bom -Algorithm SHA256).Hash -ne $hashes['sbom.cdx.json'] -or (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hashes['app.jar']){throw 'Runtime BOM/JAR hashes do not match the same-build manifest.'}
  $data=Get-Content -LiteralPath $bom -Raw | ConvertFrom-Json
  $maven=@($data.components | Where-Object {$_.purl -like 'pkg:maven/*'})
  if($data.bomFormat -ne 'CycloneDX' -or $data.metadata.component.name -ne 'sky-server' -or $maven.Count -lt 20){throw 'Runtime BOM lacks the expected Maven application dependency inventory.'}
  [ordered]@{imageId=$ImageId;appJarSha256=$hashes['app.jar'];sbomSha256=$hashes['sbom.cdx.json'];mavenComponents=$maven.Count;source='BOM and JAR extracted from the same non-running image container'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot 'server-runtime-provenance.json') -Encoding utf8
  return $bom
 } finally {
  if(Test-Path -LiteralPath $jar){Move-ProjectFileToRecycleBin -Path $jar}
  if($containerId -match '^[a-f0-9]{64}$'){
   $label=(& docker inspect --format '{{index .Config.Labels "campuseats.purpose"}}' $containerId 2>> $Log) -join ''
   if($LASTEXITCODE -eq 0 -and $label -ceq 'sbom-extract'){
    & docker rm $containerId *>> $Log
    if($LASTEXITCODE -ne 0){throw 'The owned SBOM extraction container could not be removed.'}
   } else {throw 'Cannot verify the owned SBOM extraction container for cleanup.'}
  }
 }
}

$failed=$false
$summary=[Collections.Generic.List[object]]::new()
foreach($item in @(@{name='server';image=$ServerImage},@{name='web';image=$WebImage})){
 if($Service -ne 'Both' -and $item.name -ine $Service){continue}
 $name=$item.name
 $log=Join-Path $outputRoot ($name+'-scan.log')
 $record=[ordered]@{service=$name;image=$item.image;imageId=$null;scannerVersion=$version;osScanExit=-1;osSbomExit=-1;osGateExit=-1;javaScanExit=$(if($name -eq 'server'){-1}else{$null});javaGateExit=$(if($name -eq 'server'){-1}else{$null});status='failed'}
 try {
  $imageId=(& docker image inspect --format '{{.Id}}' $item.image 2>> $log) -join ''
  if($LASTEXITCODE -ne 0 -or $imageId -notmatch '^sha256:[a-f0-9]{64}$'){throw 'Cannot resolve the exact application image.'}
  $record.imageId=$imageId
  $osReport=Join-Path $outputRoot ($name+'-os-vulnerabilities.json')
  $record.osScanExit=Invoke-VulnerabilityScan -Kind 'image' -Target $imageId -Report $osReport -Log $log
  if($record.osScanExit -eq 0){
   & $TrivyExecutable convert --format cyclonedx --output (Join-Path $outputRoot ($name+'-os-sbom.cdx.json')) $osReport *>> $log
   $record.osSbomExit=$LASTEXITCODE
   $record.osGateExit=Test-VulnerabilityGate -Report $osReport -Gate (Join-Path $outputRoot ($name+'-os-fixable-high-critical.json')) -ExpectedClass 'os-pkgs'
  }
  # Java scanning is mandatory for server, even when the OS gate fails. A missing BOM is failure.
  if($name -eq 'server'){
   $runtimeBom=Export-ImageRuntimeBom -ImageId $imageId -Log $log
   $javaReport=Join-Path $outputRoot 'server-java-vulnerabilities.json'
   $record.javaScanExit=Invoke-VulnerabilityScan -Kind 'sbom' -Target $runtimeBom -Report $javaReport -Log $log
   if($record.javaScanExit -eq 0){$record.javaGateExit=Test-VulnerabilityGate -Report $javaReport -Gate (Join-Path $outputRoot 'server-java-fixable-high-critical.json') -ExpectedClass 'lang-pkgs'}
  }
  if($record.osScanExit -eq 0 -and $record.osSbomExit -eq 0 -and $record.osGateExit -eq 0 -and ($name -eq 'web' -or ($record.javaScanExit -eq 0 -and $record.javaGateExit -eq 0))){$record.status='passed'}
 } catch {'Image resolution, required SBOM extraction, report validation or scan failed; inspect local evidence.' | Add-Content -LiteralPath $log}
 if($record.status -ne 'passed'){$failed=$true}
 $summary.Add($record)
}
$summary | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $outputRoot 'summary.json') -Encoding utf8
if($failed){throw 'Image security validation failed; full OS/Java findings and scanner status are retained in the output directory.'}
Write-Host 'Selected images passed required OS and Java runtime dependency gates; full findings and CycloneDX inventories were retained.'
