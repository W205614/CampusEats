$ErrorActionPreference = 'Stop'
$scriptsDir = Split-Path $PSScriptRoot -Parent
. (Join-Path $scriptsDir 'github-artifact-transfer.ps1')
$root = Split-Path $scriptsDir -Parent
$fixture = New-SecureDirectory -Path (Join-Path $root ('.local/artifact-range-test-' + [guid]::NewGuid().ToString('N')))

# In-memory HttpResponseMessage fixtures: no listener, socket, Docker or real URL is used.
if (!('CampusEatsArtifactFixtureStream' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
public sealed class CampusEatsArtifactFixtureStream : Stream {
 readonly byte[] bytes; readonly int failAfter; readonly bool stall; int position;
 public CampusEatsArtifactFixtureStream(byte[] bytes, int failAfter, bool stall) {
  this.bytes=bytes; this.failAfter=failAfter; this.stall=stall;
 }
 public override bool CanRead=>true; public override bool CanSeek=>false; public override bool CanWrite=>false;
 public override long Length=>bytes.Length;
 public override long Position { get=>position; set=>throw new NotSupportedException(); }
 public override int Read(byte[] buffer,int offset,int count) {
  if(failAfter>=0 && position>=failAfter)throw new IOException("fixture interrupted chunk");
  int available=bytes.Length-position;
  if(failAfter>=0)available=Math.Min(available,failAfter-position);
  int size=Math.Min(count,available); if(size<=0)return 0;
  Array.Copy(bytes,position,buffer,offset,size);position+=size;return size;
 }
 public override async Task<int> ReadAsync(byte[] buffer,int offset,int count,CancellationToken cancellation) {
  cancellation.ThrowIfCancellationRequested();
  if(stall){await Task.Delay(1000,cancellation);return 0;}
  return Read(buffer,offset,count);
 }
 public override void Flush(){}
 public override long Seek(long offset,SeekOrigin origin)=>throw new NotSupportedException();
 public override void SetLength(long value)=>throw new NotSupportedException();
 public override void Write(byte[] buffer,int offset,int count)=>throw new NotSupportedException();
}
'@
}
function New-RangeFixture {
  param([string]$Name, [string]$Mode = 'normal')
  return [pscustomobject]@{
    Mode = $Mode; Bytes = [Text.Encoding]::ASCII.GetBytes('0123456789'); Elapsed = 0L;
    Starts = [Collections.Generic.List[long]]::new();
    Clients = [Collections.Generic.HashSet[Net.Http.HttpClient]]::new();
    Refreshes = 0; RefreshTokenChecked = $false;
    Output = (Join-Path $fixture ($Name + '.zip'))
  }
}
function New-RangeSender {
  param($State)
  return {
    param($StorageClient, $RangeRequest, $CancellationToken)
    if ($StorageClient.DefaultRequestHeaders.Authorization -or $RangeRequest.Headers.Authorization) {
      throw 'Storage transport received an authorization header.'
    }
    [void]$State.Clients.Add($StorageClient)
    $range = @($RangeRequest.Headers.Range.Ranges)[0]
    $start = [long]$range.From
    $last = [long]$range.To
    [void]$State.Starts.Add($start)
    if ($State.Mode.StartsWith('refresh-') -and $start -ge 4) {
      $firstAtOffset = @($State.Starts | Where-Object { $_ -eq $start }).Count -eq 1
      if (($State.Mode -eq 'refresh-multi-limit' -and $firstAtOffset) -or
          ($State.Mode -ne 'refresh-multi-limit' -and $RangeRequest.RequestUri.AbsolutePath -eq '/local')) {
        $status = if ($State.Mode -eq 'refresh-unauthorized') { [Net.HttpStatusCode]::Unauthorized }
          elseif ($State.Mode -eq 'refresh-nonauth') { [Net.HttpStatusCode]::InternalServerError }
          else { [Net.HttpStatusCode]::Forbidden }
        return [Net.Http.HttpResponseMessage]::new($status)
      }
    }
    $length = [int]($last - $start + 1)
    $bytes = [byte[]]::new($length)
    [Array]::Copy($State.Bytes, [int]$start, $bytes, 0, $length)
    $failAfter = -1
    $stall = $false
    if ($State.Mode -eq 'interrupt' -and $start -eq 4 -and @($State.Starts | Where-Object { $_ -eq 4 }).Count -eq 1) {
      $failAfter = 2
    }
    if ($State.Mode -eq 'short') { $bytes = $bytes[0..($length - 2)] }
    if ($State.Mode -eq 'long') { $bytes = [byte[]](@($bytes) + @(255)) }
    if ($State.Mode -eq 'timeout') { $stall = $true }
    if ($State.Mode -eq 'budget') { $State.Elapsed += 600 }
    $response = [Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::PartialContent)
    $response.Content = [Net.Http.StreamContent]::new([CampusEatsArtifactFixtureStream]::new($bytes, $failAfter, $stall))
    $response.Content.Headers.ContentRange = [Net.Http.Headers.ContentRangeHeaderValue]::new($start, $last, [long]$State.Bytes.Length)
    $response.Content.Headers.ContentLength = $length
    if ($State.Mode -eq 'range') {
      $response.Content.Headers.ContentRange = [Net.Http.Headers.ContentRangeHeaderValue]::new($start + 1, $last + 1, [long]($State.Bytes.Length + 1))
    }
    if ($State.Mode -eq 'length') { $response.Content.Headers.ContentLength = $length - 1 }
    if ($State.Mode -eq 'status') { $response.StatusCode = [Net.HttpStatusCode]::OK }
    return $response
  }.GetNewClosure()
}
function New-RangeRefresher {
  param($State)
  return {
    param($CancellationToken)
    $State.Refreshes++
    if (!$CancellationToken.CanBeCanceled) { throw 'Refresh request was not cancellable.' }
    $State.RefreshTokenChecked = $true
    switch ($State.Mode) {
      'refresh-http' { return [uri]'http://artifact-fixture.invalid/refreshed' }
      'refresh-userinfo' { return [uri]'https://credential@artifact-fixture.invalid/refreshed' }
      'refresh-fragment' { return [uri]'https://artifact-fixture.invalid/refreshed#private' }
      'refresh-relative' { return [uri]'/refreshed' }
      'refresh-budget' { $State.Elapsed += 1100 }
      'refresh-timeout' { [void][Threading.Tasks.Task]::Delay(1000,$CancellationToken).GetAwaiter().GetResult() }
      'refresh-error' { throw 'fixture-sensitive-signed-url-or-token' }
    }
    return [uri]'https://another-artifact-fixture.invalid/refreshed'
  }.GetNewClosure()
}
function Invoke-RangeFixture {
  param($State, [string]$Digest = '', [TimeSpan]$OverallTimeout = ([TimeSpan]::FromMinutes(15)), [TimeSpan]$RequestTimeout = ([TimeSpan]::FromSeconds(90)))
  if (!$Digest) {
    $Digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($State.Bytes))
  }
  $arguments = @{StorageUri = [uri]'https://artifact-fixture.invalid/local'; Path = $State.Output; ExpectedBytes = $State.Bytes.Length;
    ExpectedSha256 = $Digest; ChunkBytes = 4; SendRange = (New-RangeSender $State); OverallTimeout = $OverallTimeout; RequestTimeout = $RequestTimeout }
  if ($State.Mode -in @('budget','refresh-budget')) { $arguments.ElapsedMilliseconds = { $State.Elapsed }.GetNewClosure() }
  if ($State.Mode.StartsWith('refresh-')) { $arguments.RefreshStorageUri = New-RangeRefresher $State }
  Receive-GitHubArtifactRanges @arguments
}
function Assert-RangeRejected {
  param($State, [string]$Digest = '', [TimeSpan]$OverallTimeout = ([TimeSpan]::FromMinutes(15)), [TimeSpan]$RequestTimeout = ([TimeSpan]::FromSeconds(90)))
  $rejected = $false
  try {
    Invoke-RangeFixture -State $State -Digest $Digest -OverallTimeout $OverallTimeout -RequestTimeout $RequestTimeout
  } catch {
    $rejected = $true
  }
  if (!$rejected) { throw ('Artifact transfer accepted invalid fixture: ' + $State.Mode) }
}
function Assert-RangeBytes {
  param($State)
  if ((Get-FileHash -LiteralPath $State.Output -Algorithm SHA256).Hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($State.Bytes))) {
    throw 'Range transport changed fixture bytes.'
  }
}

$normal = New-RangeFixture 'normal'
Invoke-RangeFixture $normal
Assert-RangeBytes $normal
if (($normal.Starts -join ',') -cne '0,4,8' -or $normal.Clients.Count -ne 3) {
  throw 'Normal artifact was not requested in independent bounded ranges.'
}

$interrupt = New-RangeFixture 'interrupted' 'interrupt'
Invoke-RangeFixture $interrupt
Assert-RangeBytes $interrupt
if (($interrupt.Starts -join ',') -cne '0,4,4,8' -or $interrupt.Clients.Count -ne 4) {
  throw 'Interrupted chunk did not retry exactly its confirmed offset with a fresh client.'
}

foreach ($mode in @('range', 'length', 'long', 'status')) {
  $invalid = New-RangeFixture $mode $mode
  Assert-RangeRejected $invalid
  if ($invalid.Starts.Count -ne 1 -or (Get-Item -LiteralPath $invalid.Output).Length -ne 0) {
    throw ('Invalid range metadata/body was retained or retried: ' + $mode)
  }
}
$short = New-RangeFixture 'short' 'short'
Assert-RangeRejected $short
if (($short.Starts -join ',') -cne '0,0,0' -or (Get-Item -LiteralPath $short.Output).Length -ne 0) {
  throw 'Incomplete range escaped the three-attempt bound or retained uncertain bytes.'
}

$digest = New-RangeFixture 'digest'
Assert-RangeRejected -State $digest -Digest ('0' * 64)
if ($digest.Starts.Count -ne 3) { throw 'Digest mismatch caused an unexpected whole-file retry.' }

$budget = New-RangeFixture 'budget' 'budget'
Assert-RangeRejected -State $budget -OverallTimeout ([TimeSpan]::FromMilliseconds(1000))
if (($budget.Starts -join ',') -cne '0,4' -or (Get-Item -LiteralPath $budget.Output).Length -ne 4) {
  throw 'Overall budget did not preserve only the previously confirmed chunk.'
}

$timeout = New-RangeFixture 'timeout' 'timeout'
$watch = [Diagnostics.Stopwatch]::StartNew()
Assert-RangeRejected -State $timeout -OverallTimeout ([TimeSpan]::FromSeconds(3)) -RequestTimeout ([TimeSpan]::FromMilliseconds(40))
$watch.Stop()
if ($timeout.Starts.Count -ne 3 -or $watch.Elapsed.TotalSeconds -ge 2 -or (Get-Item -LiteralPath $timeout.Output).Length -ne 0) {
  throw 'The request cancellation token did not bound body reads and retries.'
}

$existing = New-RangeFixture 'existing'
Write-SecureTextFile -Path $existing.Output -Content 'keep-this-file'
Assert-RangeRejected $existing
if ($existing.Starts.Count -ne 0 -or [IO.File]::ReadAllText($existing.Output) -cne 'keep-this-file') {
  throw 'Transport reused or rewrote an unknown existing output.'
}
$badUrl = New-RangeFixture 'bad-url'
$rejected = $false
try {
  Receive-GitHubArtifactRanges -StorageUri 'http://artifact-fixture.invalid/local' -Path $badUrl.Output -ExpectedBytes 10 -ExpectedSha256 ('0' * 64) -SendRange (New-RangeSender $badUrl)
} catch {
  $rejected = $true
}
if (!$rejected -or $badUrl.Starts.Count -ne 0 -or (Test-Path -LiteralPath $badUrl.Output)) {
  throw 'Invalid storage URL was accepted or attempted.'
}

Write-Host 'Artifact range transport: normal chunks, interrupted chunk recovery, strict headers/body/digest, fresh clients without authorization, bounded body timeout, overall budget and unknown-file refusal passed without network or Docker.'

foreach ($mode in @('refresh-normal','refresh-unauthorized')) {
  $refreshed = New-RangeFixture $mode $mode
  Invoke-RangeFixture $refreshed
  Assert-RangeBytes $refreshed
  if (($refreshed.Starts -join ',') -cne '0,4,4,8' -or $refreshed.Refreshes -ne 1 -or !$refreshed.RefreshTokenChecked) {
    throw 'Expired storage authorization did not refresh only the confirmed range prefix.'
  }
}
$nonAuth = New-RangeFixture 'refresh-nonauth' 'refresh-nonauth'
Assert-RangeRejected $nonAuth
if ($nonAuth.Refreshes -ne 0 -or ($nonAuth.Starts -join ',') -cne '0,4' -or (Get-Item -LiteralPath $nonAuth.Output).Length -ne 4) {
  throw 'A nonauthorization error triggered URL refresh or retained uncertain bytes.'
}
foreach ($mode in @('refresh-http','refresh-userinfo','refresh-fragment','refresh-relative','refresh-error','refresh-budget','refresh-timeout','refresh-multi-limit')) {
  $state = New-RangeFixture $mode $mode
  $expectedCode = if ($mode -eq 'refresh-multi-limit') { 'ARTIFACT_REFRESH_LIMIT' }
    elseif ($mode -eq 'refresh-budget') { 'ARTIFACT_TRANSFER_BUDGET' }
    elseif ($mode -in @('refresh-error','refresh-timeout')) { 'ARTIFACT_REFRESH_FAILED' }
    else { 'ARTIFACT_REFRESH_URI' }
  if ($mode -eq 'refresh-multi-limit') { $state.Bytes = [Text.Encoding]::ASCII.GetBytes('0123456789abcdef') }
  $arguments = @{State=$state}
  if ($mode -eq 'refresh-budget') { $arguments.OverallTimeout = [TimeSpan]::FromMilliseconds(1000) }
  if ($mode -eq 'refresh-timeout') { $arguments.RequestTimeout = [TimeSpan]::FromMilliseconds(40) }
  $rejected = $false
  try { Invoke-RangeFixture @arguments } catch {
    if ($_.Exception.Message -notlike ($expectedCode + ':*') -or $_.Exception.Message -match 'https?://|credential|fixture-sensitive') {
      throw 'Refresh failure classification was unsafe or incorrect.'
    }
    $rejected = $true
  }
  $prefix = if ($mode -eq 'refresh-multi-limit') { 12 } else { 4 }
  if (!$rejected -or (Get-Item -LiteralPath $state.Output).Length -ne $prefix -or !$state.RefreshTokenChecked) {
    throw 'Refresh failure retained uncertain bytes or bypassed cancellation.'
  }
  if ($mode -eq 'refresh-multi-limit' -and ($state.Refreshes -ne 2 -or ($state.Starts -join ',') -cne '0,4,4,8,8,12')) {
    throw 'Storage refresh exceeded the global two-refresh bound.'
  }
}
Write-Host 'Artifact refresh: confirmed prefix, official-API callback cancellation, global two-refresh limit, invalid URLs, safe failure classification and shared overall/request budgets passed without network or Docker.'

# Execute the real download entry's RHS expressions, including its local-function capture.
# Only the HTTP handler is a fixture; the callback and URI validator are read from source AST.
$tokens = $null; $parseErrors = $null
$downloadAst = [Management.Automation.Language.Parser]::ParseFile((Join-Path $scriptsDir 'download-release.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Download entry could not be parsed.' }
$actualAssignments = @{}
foreach ($name in @('artifactEndpoint','validateStorageUri','refreshStorage')) {
  $matches = @($downloadAst.FindAll({param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and $node.Left.Extent.Text -ceq ('$' + $name)}, $true))
  if ($matches.Count -ne 1) { throw 'Download callback source assignment is missing or ambiguous.' }
  $actualAssignments[$name] = $matches[0].Right.Extent.Text
}
$helperAst = [Management.Automation.Language.Parser]::ParseFile((Join-Path $scriptsDir 'github-artifact-transfer.ps1'), [ref]$tokens, [ref]$parseErrors)
$validatorDefinition = $helperAst.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Assert-GitHubArtifactStorageUri'}, $true).Extent.Text
if (!('CampusEatsArtifactRedirectHandler' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
public sealed class CampusEatsArtifactRedirectHandler : HttpMessageHandler {
 public int Status = 302; public Uri Location = new Uri("https://storage-fixture.invalid/refreshed");
 public bool Stall; public int Calls; public bool OfficialEndpoint = true;
 public bool Authorized = true; public bool Cancellable = true;
 protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellation) {
  Calls++;
  OfficialEndpoint &= request.RequestUri.AbsoluteUri == "https://api.github.com/repos/W205614/CampusEats/actions/artifacts/13501263137/zip";
  Authorized &= request.Headers.Authorization?.Scheme == "Bearer" && request.Headers.Authorization?.Parameter == "fixture-in-memory-token";
  Cancellable &= cancellation.CanBeCanceled;
  if(Stall) await Task.Delay(1000,cancellation);
  cancellation.ThrowIfCancellationRequested();
  var response = new HttpResponseMessage((HttpStatusCode)Status);
  response.Headers.Location = Location;
  return response;
 }
}
'@
}
function New-ActualDownloadRedirectCallback {
  param([Net.Http.HttpClient]$Client)
  . ([scriptblock]::Create($validatorDefinition))
  $artifact = @{id='13501263137'}
  $artifactEndpoint = . ([scriptblock]::Create($actualAssignments['artifactEndpoint']))
  $validateStorageUri = . ([scriptblock]::Create($actualAssignments['validateStorageUri']))
  $refreshStorage = . ([scriptblock]::Create($actualAssignments['refreshStorage']))
  return $refreshStorage
}
$apiHandler = [CampusEatsArtifactRedirectHandler]::new()
$apiClient = [Net.Http.HttpClient]::new($apiHandler)
$apiClient.Timeout = [Threading.Timeout]::InfiniteTimeSpan
$apiClient.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer','fixture-in-memory-token')
$actualCallback = New-ActualDownloadRedirectCallback $apiClient
$requestCancellation = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(5))
try {
  $actual = New-RangeFixture 'actual-entry-closure' 'refresh-normal'
  Receive-GitHubArtifactRanges -StorageUri 'https://artifact-fixture.invalid/local' -Path $actual.Output -ExpectedBytes $actual.Bytes.Length -ExpectedSha256 ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($actual.Bytes))) -ChunkBytes 4 -SendRange (New-RangeSender $actual) -RefreshStorageUri $actualCallback
  Assert-RangeBytes $actual
  if ($apiHandler.Calls -ne 1 -or !$apiHandler.OfficialEndpoint -or !$apiHandler.Authorized -or !$apiHandler.Cancellable -or ($actual.Starts -join ',') -cne '0,4,4,8') { throw 'Actual download callback did not preserve endpoint, auth, cancellation or prefix boundaries.' }
  foreach ($case in @('status','http','userinfo','fragment','relative','missing')) {
    $apiHandler.Status = if ($case -eq 'status') { 200 } else { 302 }
    $apiHandler.Location = switch ($case) {
      'http' { [uri]'http://storage-fixture.invalid/refreshed' }
      'userinfo' { [uri]'https://credential@storage-fixture.invalid/refreshed' }
      'fragment' { [uri]'https://storage-fixture.invalid/refreshed#private' }
      'relative' { [uri]'/refreshed' }
      'missing' { $null }
      default { [uri]'https://storage-fixture.invalid/refreshed' }
    }
    $rejected = $false
    try { [void](& $actualCallback $requestCancellation.Token) } catch { $rejected = $true }
    if (!$rejected) { throw 'Actual callback accepted a non-302 or invalid redirect.' }
  }
  $apiHandler.Status = 302; $apiHandler.Stall = $true
  $cancel = [Threading.CancellationTokenSource]::new([TimeSpan]::FromMilliseconds(40))
  try {
    $rejected = $false
    try { [void](& $actualCallback $cancel.Token) } catch { $rejected = $true }
    if (!$rejected -or !$cancel.IsCancellationRequested) { throw 'Actual API callback ignored its cancellation token.' }
  } finally { $cancel.Dispose() }
} finally { $requestCancellation.Dispose(); $apiClient.Dispose(); $apiHandler.Dispose() }
Write-Host 'Actual download entry AST closure: local validator capture, real HttpClient fixture handler, fixed official endpoint auth, prefix refresh, 302/URI rejection and cancellation passed without network or Docker.'
