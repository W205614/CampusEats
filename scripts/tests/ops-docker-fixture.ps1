# Disposable subprocess fixture. It never invokes Docker or touches application data.
# No param block: Docker flags such as -p/-c must remain literal positional arguments.
$ErrorActionPreference='Stop'
$ConfigFile=$args[0]
$config=Get-Content -LiteralPath $ConfigFile -Raw | ConvertFrom-Json
$a=$args[1..($args.Count-1)]
if($a[0] -eq 'inspect'){'running healthy';exit}
if($a -contains 'ps'){$a[-1];exit}
if($a -notcontains 'exec'){throw 'Unexpected fixture command.'}
$command=$a[-1]
if($command -like 'curl *'){
 if($command -notlike '*--max-time 5*' -or $command -notlike '*localhost:8080/actuator/health/operations*'){throw 'HTTP deadline or private health URL missing.'}
 if($config.invalidHealth){'unparseable';exit}
 $jobs=[ordered]@{}
 foreach($name in @('checkoutExpiry','platformRefunds','legacyRefunds','outbox','legacyExpiry','deliveryOverdue','maintenance','metrics')){
  $enabled=$name -eq 'metrics' -or !$config.disabled
  $status=if($enabled){'OK'}else{'DISABLED'}
  if($config.failedJob -eq $name){$status='FAILED'}
  if($config.staleJob -eq $name){$status='STALE'}
  $jobs[$name]=@{enabled=$enabled;status=$status;lastError='fixture-private-detail'}
 }
 $status=if($config.failedJob -or $config.staleJob){'DOWN'}else{'UP'}
 @{status=$status;components=@{runtimeOperations=@{status=$status;details=@{tasksEnabled=(!$config.disabled);jobs=$jobs;issues=@()}}}} | ConvertTo-Json -Depth 8 -Compress
 exit
}
if($command -like 'df -Pk*'){'Filesystem 1024-blocks Used Available Capacity Mounted';'overlay 1073741824 0 1073741824 0% /';exit}
if($command -like 'du -sb*'){"100`t/app/uploads";exit}
if($command -like '*CAMPUS_UPLOAD_QUOTA_BYTES*'){'2147483648';exit}
if($command -like '*CAMPUS_UPLOAD_MIN_FREE_BYTES*'){'268435456';exit}
if($command.StartsWith('set -eu')){
 if($command -like '*2>/dev/null*' -or $command -like '*; true*'){throw 'Scan errors must not be hidden.'}
 if($config.scanFailure){[Console]::Error.WriteLine('fixture-private-path');exit 7}
 if($config.files){$config.files}
 exit
}
$sql=[Console]::In.ReadToEnd()
if($sql -like '*SELECT COUNT(*) FROM refund_request*'){
 if($config.invalidCounts){'unknown';exit}
 if($config.counts){$config.counts}else{1..12 | ForEach-Object {'0'}}
 exit
}
if($sql -like '*SELECT DISTINCT path FROM evidence*'){if($config.evidenceRefs){$config.evidenceRefs};exit}
if($sql -like '*SELECT DISTINCT image FROM dish*'){if($config.productRefs){$config.productRefs};exit}
throw 'Unexpected fixture SQL.'
