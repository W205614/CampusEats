. (Join-Path $PSScriptRoot 'secure-files.ps1')

function Receive-PinnedToolArchive {
  param([Parameter(Mandatory)][uri]$Uri, [Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$ExpectedSha256)
  Assert-NoSecurePathLinks -Path $Path
  if (Test-Path -LiteralPath $Path) {
    if ((Get-FileHash -LiteralPath $Path).Hash -ine $ExpectedSha256) { throw 'Pinned tool archive checksum mismatch.' }
    return
  }
  $temporaryRoot = New-SecureDirectory -Path (Join-Path (Split-Path $PSScriptRoot -Parent) '.local/tool-downloads')
  $temporary = Join-Path $temporaryRoot ([guid]::NewGuid().ToString('N') + '.partial')
  try {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
      try {
        Invoke-WebRequest -Uri $Uri -OutFile $temporary -HttpVersion 1.1 -TimeoutSec 120 -ErrorAction Stop
        break
      } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($attempt -eq 3 -or $status -notin @(502,503,504)) { throw }
        Write-Warning "Tool download received HTTP $status; retrying in 2 seconds ($attempt/2)."
        Start-Sleep -Seconds 2
      }
    }
    if ((Get-FileHash -LiteralPath $temporary).Hash -ine $ExpectedSha256) { throw 'Pinned tool archive checksum mismatch.' }
    Move-Item -LiteralPath $temporary -Destination $Path -ErrorAction Stop
  } finally {
    if (Test-Path -LiteralPath $temporary) { Move-ProjectFileToRecycleBin -Path $temporary }
  }
}
