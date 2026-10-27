. (Join-Path $PSScriptRoot 'secure-files.ps1')

# Storage transport only. The caller obtains this signed URL, length and digest from the
# fixed official GitHub API. No credential is accepted or forwarded to storage requests.
function Receive-GitHubArtifactRanges {
 param(
  [Parameter(Mandatory)][uri]$StorageUri,
  [Parameter(Mandatory)][string]$Path,
  [Parameter(Mandatory)][long]$ExpectedBytes,
  [Parameter(Mandatory)][string]$ExpectedSha256,
  [int]$ChunkBytes=4194304,[int]$MaxAttempts=3,
  [TimeSpan]$OverallTimeout=([TimeSpan]::FromMinutes(15)),
  [TimeSpan]$RequestTimeout=([TimeSpan]::FromSeconds(90)),
  [scriptblock]$SendRange,[scriptblock]$ElapsedMilliseconds,[switch]$ReportProgress
 )
 if(!$StorageUri.IsAbsoluteUri -or $StorageUri.Scheme -cne 'https' -or $StorageUri.UserInfo -or $StorageUri.Fragment){throw 'Invalid artifact storage URL.'}
 if($ExpectedBytes -le 0 -or $ExpectedBytes -gt 8589934592 -or $ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$'){throw 'Invalid trusted artifact length or digest.'}
 if($ChunkBytes -lt 1 -or $ChunkBytes -gt 4194304 -or $MaxAttempts -lt 1 -or $MaxAttempts -gt 3){throw 'Invalid artifact range limits.'}
 if($OverallTimeout.TotalMilliseconds -lt 1 -or $OverallTimeout.TotalMinutes -gt 15 -or $RequestTimeout.TotalMilliseconds -lt 1 -or $RequestTimeout.TotalSeconds -gt 90){throw 'Invalid artifact transfer time budget.'}
 $target=[IO.Path]::GetFullPath($Path)
 Assert-NoSecurePathLinks -Path $target
 if(Test-Path -LiteralPath $target){throw 'Artifact transport requires a new output file; unknown files are never resumed.'}
 New-SecureDirectory -Path (Split-Path $target -Parent) | Out-Null
 Write-SecureTextFile -Path $target -Content ''
 $transferWatch=[Diagnostics.Stopwatch]::StartNew()
 if(!$ElapsedMilliseconds){$ElapsedMilliseconds={ $transferWatch.ElapsedMilliseconds }.GetNewClosure()}
 if(!$SendRange){
  $SendRange={param($StorageClient,$RangeRequest,$CancellationToken)
   $StorageClient.SendAsync($RangeRequest,[Net.Http.HttpCompletionOption]::ResponseHeadersRead,$CancellationToken).GetAwaiter().GetResult()
  }
 }
 $budgetMilliseconds=[long][Math]::Floor($OverallTimeout.TotalMilliseconds)
 $remaining={ $budgetMilliseconds-[long](& $ElapsedMilliseconds) }.GetNewClosure()
 $confirmed=0L;$buffer=[byte[]]::new(65536)
 $output=[IO.File]::Open($target,[IO.FileMode]::Open,[IO.FileAccess]::Write,[IO.FileShare]::None)
 try {
  while($confirmed -lt $ExpectedBytes){
   if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
   $last=[Math]::Min($confirmed+[long]$ChunkBytes-1,$ExpectedBytes-1)
   $chunkLength=$last-$confirmed+1;$accepted=$false
   for($attempt=1;$attempt -le $MaxAttempts -and !$accepted;$attempt++){
    $left=[long](& $remaining)
    if($left -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
    $milliseconds=[Math]::Min($left,[long][Math]::Floor($RequestTimeout.TotalMilliseconds))
    $cancellation=[Threading.CancellationTokenSource]::new([TimeSpan]::FromMilliseconds($milliseconds))
    $storageHandler=[Net.Http.HttpClientHandler]::new();$storageHandler.AllowAutoRedirect=$false
    $storageClient=[Net.Http.HttpClient]::new($storageHandler)
    # The cancellation token covers both headers and body; HttpClient.Timeout alone
    # ceases to protect the body when ResponseHeadersRead is used.
    $storageClient.Timeout=[Threading.Timeout]::InfiniteTimeSpan
    $rangeRequest=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,$StorageUri)
    $rangeRequest.Headers.Range=[Net.Http.Headers.RangeHeaderValue]::new($confirmed,$last)
    $rangeResponse=$null;$rangeStream=$null;$invalidResponse=$false
    try {
     $rangeResponse=& $SendRange $storageClient $rangeRequest $cancellation.Token
     if($rangeResponse -isnot [Net.Http.HttpResponseMessage]){$invalidResponse=$true;throw 'Invalid artifact storage response.'}
     $range=$rangeResponse.Content.Headers.ContentRange
     if([int]$rangeResponse.StatusCode -ne 206 -or $null -eq $range -or $range.Unit -ine 'bytes' -or !$range.HasRange -or !$range.HasLength -or $range.From -ne $confirmed -or $range.To -ne $last -or $range.Length -ne $ExpectedBytes -or $rangeResponse.Content.Headers.ContentLength -ne $chunkLength){$invalidResponse=$true;throw 'Artifact range headers do not match the trusted request.'}
     $rangeStream=$rangeResponse.Content.ReadAsStreamAsync($cancellation.Token).GetAwaiter().GetResult()
     $output.Position=$confirmed;$received=0L
     while($received -lt $chunkLength){
      if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
      $cancellation.Token.ThrowIfCancellationRequested()
      $count=$rangeStream.ReadAsync($buffer,0,[int][Math]::Min($buffer.Length,$chunkLength-$received),$cancellation.Token).GetAwaiter().GetResult()
      if($count -le 0){throw 'Artifact range ended before its declared length.'}
      [void]$output.WriteAsync($buffer,0,$count,$cancellation.Token).GetAwaiter().GetResult()
      $received+=$count
     }
     if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
     if($rangeStream.ReadAsync($buffer,0,1,$cancellation.Token).GetAwaiter().GetResult() -ne 0){$invalidResponse=$true;throw 'Artifact range exceeded its declared length.'}
     $cancellation.Token.ThrowIfCancellationRequested()
     if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
     $output.Flush();$accepted=$true
    }catch{
     # The only resumable prefix is the one confirmed inside THIS invocation. A
     # partially received chunk is removed before a bounded retry of that same range.
     $output.SetLength($confirmed);$output.Position=$confirmed
     if($invalidResponse){throw 'Artifact storage returned invalid range metadata or length.'}
     if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
     if($attempt -eq $MaxAttempts){throw 'Artifact range transfer failed after bounded retries.'}
    }finally{
     if($rangeStream){$rangeStream.Dispose()};if($rangeResponse){$rangeResponse.Dispose()}
     $rangeRequest.Dispose();$storageClient.Dispose();$storageHandler.Dispose();$cancellation.Dispose()
    }
   }
   $confirmed=$last+1
   if($ReportProgress){Write-Host ('GitHub artifact bytes received: '+$confirmed+'/'+$ExpectedBytes)}
  }
 }finally{$output.Dispose()}
 if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
 if((Get-Item -LiteralPath $target).Length -ne $ExpectedBytes -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ine $ExpectedSha256){throw 'Downloaded GitHub artifact digest mismatch.'}
 if((& $remaining) -le 0){throw 'Artifact transfer exceeded its overall time budget.'}
 $transferWatch.Stop()
}
