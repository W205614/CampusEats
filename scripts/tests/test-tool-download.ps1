$ErrorActionPreference = 'Stop'
$scriptsDir = Split-Path $PSScriptRoot -Parent
. (Join-Path $scriptsDir 'tool-download.ps1')
$root = Split-Path $scriptsDir -Parent
$fixture = New-SecureDirectory -Path (Join-Path $root ('.local/tool-download-test-' + [guid]::NewGuid().ToString('N')))
$ready = Join-Path $fixture 'ready.txt'
$events = Join-Path $fixture 'events.txt'
$process = [Diagnostics.Process]::new()
$process.StartInfo = [Diagnostics.ProcessStartInfo]::new((Get-Command node).Source)
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.CreateNoWindow = $true
foreach ($argument in @((Join-Path $PSScriptRoot 'tool-download-http-fixture.mjs'),$ready,$events)) { $process.StartInfo.ArgumentList.Add($argument) }
try {
  [void]$process.Start()
  for ($attempt = 0; $attempt -lt 100 -and !(Test-Path -LiteralPath $ready); $attempt++) { Start-Sleep -Milliseconds 100 }
  if (!(Test-Path -LiteralPath $ready)) { throw 'HTTP fixture did not start.' }
  $origin = 'http://127.0.0.1:' + (Get-Content $ready -Raw)
  $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes('verified tool fixture')))
  foreach ($case in @(@{route='transient';attempts=3;success=$true},@{route='unavailable';attempts=3;success=$false},@{route='missing';attempts=1;success=$false},@{route='corrupt';attempts=1;success=$false})) {
    $path = Join-Path $fixture ($case.route + '.archive')
    $failed = $false
    try { Receive-PinnedToolArchive -Uri ($origin+'/'+$case.route) -Path $path -ExpectedSha256 $digest } catch { $failed = $true }
    if ($failed -eq $case.success -or (Test-Path -LiteralPath $path) -ne $case.success) { throw ('Unexpected download publication: '+$case.route) }
    if (@(Get-Content $events | Where-Object { $_ -ceq ('/'+$case.route) }).Count -ne $case.attempts) { throw ('Incorrect HTTP attempts: '+$case.route) }
  }
  $cached = Join-Path $fixture 'transient.archive'
  Receive-PinnedToolArchive -Uri ($origin+'/transient') -Path $cached -ExpectedSha256 $digest
  if (@(Get-Content $events | Where-Object { $_ -ceq '/transient' }).Count -ne 3) { throw 'Valid cache downloaded again.' }
  [IO.File]::WriteAllText($cached,'damaged cached archive')
  $failed = $false
  try { Receive-PinnedToolArchive -Uri ($origin+'/transient') -Path $cached -ExpectedSha256 $digest } catch { $failed = $true }
  if (!$failed -or [IO.File]::ReadAllText($cached) -cne 'damaged cached archive') { throw 'Corrupt existing cache was accepted or overwritten.' }
  if (@(Get-Content $events | Where-Object { $_ -ceq '/transient' }).Count -ne 3) { throw 'Corrupt cache triggered an unrequested replacement download.' }
  Write-Host 'Real HTTP tool download: transient retry, three-attempt limit, 404 fail-fast, checksum rejection and cache preservation passed.'
} finally {
  if (!$process.HasExited) { $process.Kill($true); $process.WaitForExit() }
  $process.Dispose()
  Move-ProjectFileToRecycleBin -Path $fixture
}
