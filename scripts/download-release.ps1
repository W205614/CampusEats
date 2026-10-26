param([Parameter(Mandatory)][string]$RunId,[Parameter(Mandatory)][string]$SourceSha,[Parameter(Mandatory)][string]$ManifestSha256,[Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
if($RunId -notmatch '^[0-9]+$' -or $SourceSha -notmatch '^[a-f0-9]{40}$' -or $ManifestSha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Exact trusted release identifiers are required.'}
$destinationPath=[IO.Path]::GetFullPath($Destination,$root)
$local=[IO.Path]::GetFullPath((Join-Path $root '.local'))+[IO.Path]::DirectorySeparatorChar
if(!$destinationPath.StartsWith($local,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $destinationPath)){throw 'Download into a fresh protected workspace .local directory.'}
function Read-GitHubReleaseToken {
 $token=if($env:CAMPUS_GITHUB_TOKEN){$env:CAMPUS_GITHUB_TOKEN}elseif($env:GITHUB_TOKEN){$env:GITHUB_TOKEN}elseif($env:GH_TOKEN){$env:GH_TOKEN}else{$null}
 if($token){return $token}
 # Ask the configured Git credential helper for this repository only. Never write its output,
 # token or authenticated URLs to the terminal, command arguments, logs or disk.
 $git=(Get-Command git -ErrorAction Stop).Source
 $process=[Diagnostics.Process]::new();$process.StartInfo=[Diagnostics.ProcessStartInfo]::new($git)
 $process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
 $process.StartInfo.RedirectStandardInput=$true;$process.StartInfo.RedirectStandardOutput=$true;$process.StartInfo.RedirectStandardError=$true
 $process.StartInfo.WorkingDirectory=$root
 $process.StartInfo.Environment['GIT_TERMINAL_PROMPT']='0';$process.StartInfo.Environment['GCM_INTERACTIVE']='never'
 $process.StartInfo.ArgumentList.Add('credential');$process.StartInfo.ArgumentList.Add('fill')
 try {
  [void]$process.Start();$output=$process.StandardOutput.ReadToEndAsync();$errorText=$process.StandardError.ReadToEndAsync()
  $process.StandardInput.Write("protocol=https`nhost=github.com`npath=W205614/CampusEats.git`n`n");$process.StandardInput.Close()
  if(!$process.WaitForExit(10000)){$process.Kill($true);throw 'GitHub credential helper timed out.'}
  $filled=$output.GetAwaiter().GetResult();[void]$errorText.GetAwaiter().GetResult()
  if($process.ExitCode -ne 0){throw 'GitHub Actions read credentials are unavailable.'}
  $match=[regex]::Match($filled,'(?m)^password=([^\r\n]+)');$filled=$null
  if(!$match.Success){throw 'Provide CAMPUS_GITHUB_TOKEN with Actions read access.'}
  return $match.Groups[1].Value
 }finally{$process.Dispose()}
}
$token=$null;$client=$null;$handler=$null;$response=$null
try {
 $token=Read-GitHubReleaseToken
 $handler=[Net.Http.HttpClientHandler]::new();$handler.AllowAutoRedirect=$false
 $client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromMinutes(10)
 $client.DefaultRequestHeaders.UserAgent.ParseAdd('CampusEats-release/1.0')
 $client.DefaultRequestHeaders.Accept.ParseAdd('application/vnd.github+json')
 $client.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
 $runRaw=$client.GetStringAsync('https://api.github.com/repos/W205614/CampusEats/actions/runs/'+$RunId).GetAwaiter().GetResult()
 $run=$runRaw | ConvertFrom-Json
 if($run.head_sha -cne $SourceSha -or $run.status -cne 'completed' -or $run.conclusion -cne 'success' -or $run.event -cne 'push' -or $run.head_branch -cne 'master'){throw 'Selected workflow is not a successful matching master push.'}
 $raw=$client.GetStringAsync('https://api.github.com/repos/W205614/CampusEats/actions/runs/'+$RunId+'/artifacts?per_page=100').GetAwaiter().GetResult()
 $artifacts=($raw | ConvertFrom-Json).artifacts
 $selected=@($artifacts | Where-Object {$_.name -ceq ('release-'+$SourceSha) -and !$_.expired})
 if($selected.Count -ne 1 -or $selected[0].digest -notmatch '^sha256:[a-f0-9]{64}$'){throw 'A unique unexpired release artifact with a service-provided digest is required.'}
 $artifact=$selected[0]
 $response=$client.GetAsync('https://api.github.com/repos/W205614/CampusEats/actions/artifacts/'+$artifact.id+'/zip',[Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
 if([int]$response.StatusCode -ne 302){throw 'GitHub did not return the expected artifact redirect.'}
 $location=$response.Headers.Location
 if(!$location.IsAbsoluteUri -or $location.Scheme -cne 'https' -or $location.UserInfo){throw 'Invalid artifact storage redirect.'}
 $parent=New-SecureDirectory -Path (Split-Path $destinationPath -Parent)
 $zip=Join-Path $parent ('download-'+[guid]::NewGuid().ToString('N')+'.zip')
 Write-SecureTextFile -Path $zip -Content ''
 # The signed storage URL is fetched WITHOUT the GitHub authorization header.
 $storage=[Net.Http.HttpClient]::new();$storage.Timeout=[TimeSpan]::FromMinutes(10)
 try {$stream=$storage.GetStreamAsync($location).GetAwaiter().GetResult();$out=[IO.File]::Open($zip,[IO.FileMode]::Create);try{$stream.CopyTo($out)}finally{$out.Dispose();$stream.Dispose()}}finally{$storage.Dispose()}
 if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() -cne $artifact.digest.Substring(7)){throw 'Downloaded GitHub artifact digest mismatch.'}
 New-SecureDirectory -Path $destinationPath | Out-Null
 $archive=[IO.Compression.ZipFile]::OpenRead($zip)
 try {
  $total=0L
  foreach($entry in $archive.Entries){
   $total+=$entry.Length
   if($total -gt 8589934592 -or $entry.FullName -match '(^[/\\]|^[a-zA-Z]:|(^|[/\\])\.\.([/\\]|$))' -or (($entry.ExternalAttributes -shr 16) -band 61440) -eq 40960){throw 'Unsafe or oversized release ZIP.'}
   $target=[IO.Path]::GetFullPath((Join-Path $destinationPath $entry.FullName))
   if(!$target.StartsWith($destinationPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Release ZIP escapes destination.'}
   if($entry.FullName.EndsWith('/')){New-SecureDirectory -Path $target | Out-Null;continue}
   New-SecureDirectory -Path (Split-Path $target -Parent) | Out-Null
   Write-SecureTextFile -Path $target -Content ''
   $inputStream=$entry.Open();$out=[IO.File]::Open($target,[IO.FileMode]::Create)
   try {$inputStream.CopyTo($out)}finally{$inputStream.Dispose();$out.Dispose()}
  }
 }finally{$archive.Dispose()}
 $manifest=Read-VerifiedReleaseBundle -Directory $destinationPath -ExpectedSourceSha $SourceSha -ExpectedRunId $RunId -ExpectedManifestSha256 $ManifestSha256
 if($manifest.runAttempt -ne $run.run_attempt){throw 'Downloaded artifact belongs to another workflow attempt.'}
 $downloadRecord=@{repository='W205614/CampusEats';sourceSha=$SourceSha;runId=$RunId;runAttempt=$manifest.runAttempt;artifactId=$artifact.id;artifactDigest=$artifact.digest;manifestSha256=$ManifestSha256;downloadedAt=[DateTimeOffset]::UtcNow.ToString('o')}
 Write-SecureTextFile -Path (Join-Path $destinationPath 'download-record.json') -Content ($downloadRecord | ConvertTo-Json)
 Move-ProjectFileToRecycleBin -Path $zip
 Write-Host "Matching successful CI bundle downloaded and strictly verified: $destinationPath"
}catch{throw 'Release download or verification failed. No deployment was performed; retained partial files are not usable releases.'}
finally{if($response){$response.Dispose()};if($client){$client.Dispose()};if($handler){$handler.Dispose()};$token=$null}
