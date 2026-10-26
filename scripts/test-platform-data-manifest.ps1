$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/data-manifest-test-'+[guid]::NewGuid().ToString('N')))
$global:manifestFixtureCase='baseline'
function global:docker {
 $global:LASTEXITCODE=0
 $sql=$input | Out-String
 if($sql -like '*SHOW TABLES*'){return 'example'}
 if($sql -like '*information_schema.COLUMNS*'){return @('id','value')}
 if($sql -like '*information_schema.KEY_COLUMN_USAGE*'){return 'id'}
 if($sql -notlike '*SHA2(CONCAT_WS*' -or $sql -notlike "*IS NULL,'N',CONCAT('V',HEX(CAST*" -or $sql -like '*extra_column*'){throw 'Original columns were not encoded safely.'}
 $first=if($global:manifestFixtureCase -eq 'changed'){'c'*64}else{'a'*64}
 "V31`t$first"
 if($global:manifestFixtureCase -ne 'missing'){"V32`t$('b'*64)"}
 if($global:manifestFixtureCase -eq 'additive'){"V33`t$('d'*64)"}
}
try {
 $before=Join-Path $fixture 'before.json'
 & (Join-Path $PSScriptRoot 'platform-data-manifest.ps1') -ProjectName 'campuseats-manifest-fixture' -OutputPath $before
 $global:manifestFixtureCase='additive'
 $after=Join-Path $fixture 'after.json'
 & (Join-Path $PSScriptRoot 'platform-data-manifest.ps1') -ProjectName 'campuseats-manifest-fixture' -OutputPath $after -CompareManifest $before
 $record=Get-Content -LiteralPath $after -Raw | ConvertFrom-Json
 if($record.tables.example.rows -ne 2 -or $record.tables.example.newRows -ne 1){throw 'Added rows were not separated from original data.'}
 foreach($case in @('changed','missing')){
  $global:manifestFixtureCase=$case;$rejected=$false
  try {& (Join-Path $PSScriptRoot 'platform-data-manifest.ps1') -ProjectName 'campuseats-manifest-fixture' -OutputPath (Join-Path $fixture ($case+'.json')) -CompareManifest $before}catch{$rejected=$true}
  if(!$rejected){throw 'Historical mutation was accepted.'}
 }
 Write-Host 'Historical fingerprint: all original columns, additive rows, changed values and deleted rows passed.'
}finally{Remove-Item Function:\docker -ErrorAction SilentlyContinue;Remove-Variable manifestFixtureCase -Scope Global -ErrorAction SilentlyContinue}
