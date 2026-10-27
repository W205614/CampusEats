$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'github-artifact-transfer.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/artifact-range-test-'+[guid]::NewGuid().ToString('N')))

# In-memory HttpResponseMessage fixtures: no listener, socket, Docker or real URL is used.
if(!('CampusEatsArtifactFixtureStream' -as [type])){
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
 param([string]$Name,[string]$Mode='normal')
 return [pscustomobject]@{
  Mode=$Mode;Bytes=[Text.Encoding]::ASCII.GetBytes('0123456789');Elapsed=0L;
  Starts=[Collections.Generic.List[long]]::new();
  Clients=[Collections.Generic.HashSet[Net.Http.HttpClient]]::new();
  Output=(Join-Path $fixture ($Name+'.zip'))
 }
}
function New-RangeSender {
 param($State)
 return {
  param($StorageClient,$RangeRequest,$CancellationToken)
  if($StorageClient.DefaultRequestHeaders.Authorization -or $RangeRequest.Headers.Authorization){throw 'Storage transport received an authorization header.'}
  [void]$State.Clients.Add($StorageClient)
  $range=@($RangeRequest.Headers.Range.Ranges)[0];$start=[long]$range.From;$last=[long]$range.To
  [void]$State.Starts.Add($start)
  $length=[int]($last-$start+1);$bytes=[byte[]]::new($length)
  [Array]::Copy($State.Bytes,[int]$start,$bytes,0,$length)
  $failAfter=-1;$stall=$false
  if($State.Mode -eq 'interrupt' -and $start -eq 4 -and @($State.Starts | Where-Object {$_ -eq 4}).Count -eq 1){$failAfter=2}
  if($State.Mode -eq 'short'){$bytes=$bytes[0..($length-2)]}
  if($State.Mode -eq 'long'){$bytes=[byte[]](@($bytes)+@(255))}
  if($State.Mode -eq 'timeout'){$stall=$true}
  if($State.Mode -eq 'budget'){$State.Elapsed+=600}
  $response=[Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::PartialContent)
  $response.Content=[Net.Http.StreamContent]::new([CampusEatsArtifactFixtureStream]::new($bytes,$failAfter,$stall))
  $response.Content.Headers.ContentRange=[Net.Http.Headers.ContentRangeHeaderValue]::new($start,$last,[long]$State.Bytes.Length)
  $response.Content.Headers.ContentLength=$length
  if($State.Mode -eq 'range'){$response.Content.Headers.ContentRange=[Net.Http.Headers.ContentRangeHeaderValue]::new($start+1,$last+1,[long]($State.Bytes.Length+1))}
  if($State.Mode -eq 'length'){$response.Content.Headers.ContentLength=$length-1}
  if($State.Mode -eq 'status'){$response.StatusCode=[Net.HttpStatusCode]::OK}
  return $response
 }.GetNewClosure()
}
function Invoke-RangeFixture {
 param($State,[string]$Digest='',[TimeSpan]$OverallTimeout=([TimeSpan]::FromMinutes(15)),[TimeSpan]$RequestTimeout=([TimeSpan]::FromSeconds(90)))
 if(!$Digest){$Digest=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($State.Bytes))}
 $arguments=@{StorageUri=[uri]'https://artifact-fixture.invalid/local';Path=$State.Output;ExpectedBytes=$State.Bytes.Length;
  ExpectedSha256=$Digest;ChunkBytes=4;SendRange=(New-RangeSender $State);OverallTimeout=$OverallTimeout;RequestTimeout=$RequestTimeout}
 if($State.Mode -eq 'budget'){$arguments.ElapsedMilliseconds={ $State.Elapsed }.GetNewClosure()}
 Receive-GitHubArtifactRanges @arguments
}
function Assert-RangeRejected {
 param($State,[string]$Digest='',[TimeSpan]$OverallTimeout=([TimeSpan]::FromMinutes(15)),[TimeSpan]$RequestTimeout=([TimeSpan]::FromSeconds(90)))
 $rejected=$false
 try{Invoke-RangeFixture -State $State -Digest $Digest -OverallTimeout $OverallTimeout -RequestTimeout $RequestTimeout}catch{$rejected=$true}
 if(!$rejected){throw ('Artifact transfer accepted invalid fixture: '+$State.Mode)}
}
function Assert-RangeBytes {
 param($State)
 if((Get-FileHash -LiteralPath $State.Output -Algorithm SHA256).Hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($State.Bytes))){throw 'Range transport changed fixture bytes.'}
}

$normal=New-RangeFixture 'normal';Invoke-RangeFixture $normal;Assert-RangeBytes $normal
if(($normal.Starts -join ',') -cne '0,4,8' -or $normal.Clients.Count -ne 3){throw 'Normal artifact was not requested in independent bounded ranges.'}

$interrupt=New-RangeFixture 'interrupted' 'interrupt';Invoke-RangeFixture $interrupt;Assert-RangeBytes $interrupt
if(($interrupt.Starts -join ',') -cne '0,4,4,8' -or $interrupt.Clients.Count -ne 4){throw 'Interrupted chunk did not retry exactly its confirmed offset with a fresh client.'}

foreach($mode in @('range','length','long','status')){
 $invalid=New-RangeFixture $mode $mode;Assert-RangeRejected $invalid
 if($invalid.Starts.Count -ne 1 -or (Get-Item -LiteralPath $invalid.Output).Length -ne 0){throw ('Invalid range metadata/body was retained or retried: '+$mode)}
}
$short=New-RangeFixture 'short' 'short';Assert-RangeRejected $short
if(($short.Starts -join ',') -cne '0,0,0' -or (Get-Item -LiteralPath $short.Output).Length -ne 0){throw 'Incomplete range escaped the three-attempt bound or retained uncertain bytes.'}

$digest=New-RangeFixture 'digest';Assert-RangeRejected -State $digest -Digest ('0'*64)
if($digest.Starts.Count -ne 3){throw 'Digest mismatch caused an unexpected whole-file retry.'}

$budget=New-RangeFixture 'budget' 'budget';Assert-RangeRejected -State $budget -OverallTimeout ([TimeSpan]::FromMilliseconds(1000))
if(($budget.Starts -join ',') -cne '0,4' -or (Get-Item -LiteralPath $budget.Output).Length -ne 4){throw 'Overall budget did not preserve only the previously confirmed chunk.'}

$timeout=New-RangeFixture 'timeout' 'timeout';$watch=[Diagnostics.Stopwatch]::StartNew()
Assert-RangeRejected -State $timeout -OverallTimeout ([TimeSpan]::FromSeconds(3)) -RequestTimeout ([TimeSpan]::FromMilliseconds(40))
$watch.Stop()
if($timeout.Starts.Count -ne 3 -or $watch.Elapsed.TotalSeconds -ge 2 -or (Get-Item -LiteralPath $timeout.Output).Length -ne 0){throw 'The request cancellation token did not bound body reads and retries.'}

$existing=New-RangeFixture 'existing';Write-SecureTextFile -Path $existing.Output -Content 'keep-this-file'
Assert-RangeRejected $existing
if($existing.Starts.Count -ne 0 -or [IO.File]::ReadAllText($existing.Output) -cne 'keep-this-file'){throw 'Transport reused or rewrote an unknown existing output.'}
$badUrl=New-RangeFixture 'bad-url';$rejected=$false
try{Receive-GitHubArtifactRanges -StorageUri 'http://artifact-fixture.invalid/local' -Path $badUrl.Output -ExpectedBytes 10 -ExpectedSha256 ('0'*64) -SendRange (New-RangeSender $badUrl)}catch{$rejected=$true}
if(!$rejected -or $badUrl.Starts.Count -ne 0 -or (Test-Path -LiteralPath $badUrl.Output)){throw 'Invalid storage URL was accepted or attempted.'}

Write-Host 'Artifact range transport: normal chunks, interrupted chunk recovery, strict headers/body/digest, fresh clients without authorization, bounded body timeout, overall budget and unknown-file refusal passed without network or Docker.'
