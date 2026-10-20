param([string]$ConfigFile,[string]$ReadyFile,[string]$CaptureFile)
$ErrorActionPreference='Stop'
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start()
[IO.File]::WriteAllText($ReadyFile,[string]$listener.LocalEndpoint.Port)
try {
 while($true){
  $connection=$listener.AcceptTcpClient()
  try {
   $stream=$connection.GetStream()
   $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$false,1024,$true)
   $length=0
   while($true){$line=$reader.ReadLine();if($null -eq $line -or $line -eq ''){break};if($line -match '^Content-Length:\s*(\d+)$'){$length=[int]$Matches[1]}}
   $buffer=New-Object char[] $length
   $offset=0
   while($offset -lt $length){$read=$reader.Read($buffer,$offset,$length-$offset);if(!$read){break};$offset+=$read}
   [IO.File]::AppendAllText($CaptureFile,((-join $buffer)+"`n"))
   $config=Get-Content -LiteralPath $ConfigFile -Raw | ConvertFrom-Json
   if($config.delayMilliseconds){[Threading.Thread]::Sleep([int]$config.delayMilliseconds)}
   $code=if($config.status){[int]$config.status}else{204}
   $bytes=[Text.Encoding]::ASCII.GetBytes("HTTP/1.1 $code Fixture`r`nContent-Length: 0`r`nConnection: close`r`n`r`n")
   try {$stream.Write($bytes,0,$bytes.Length)} catch {}
   $reader.Dispose()
  } finally {$connection.Dispose()}
 }
} finally {$listener.Stop()}
