$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'release-bundle.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/migration-hash-test-'+[guid]::NewGuid().ToString('N')))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$sql="-- 校园迁移夹具`nSELECT '中文',1;`n"
$lf=Join-Path $fixture 'lf.sql';[IO.File]::WriteAllBytes($lf,$utf8.GetBytes($sql))
$crlf=Join-Path $fixture 'crlf.sql';[IO.File]::WriteAllBytes($crlf,$utf8.GetBytes($sql.Replace("`n","`r`n")))
$bom=Join-Path $fixture 'bom-cr.sql';[IO.File]::WriteAllBytes($bom,([byte[]]@(239,187,191)+$utf8.GetBytes($sql.Replace("`n","`r"))))
$baseline=Get-ReleaseMigrationHash -Path $lf
if((Get-ReleaseMigrationHash -Path $crlf) -cne $baseline -or (Get-ReleaseMigrationHash -Path $bom) -cne $baseline){throw 'Equivalent UTF-8 migration line endings differed.'}
$changed=Join-Path $fixture 'changed.sql';[IO.File]::WriteAllBytes($changed,$utf8.GetBytes($sql.Replace(',1;',',2;')))
if((Get-ReleaseMigrationHash -Path $changed) -ceq $baseline){throw 'SQL content changes were ignored.'}
foreach($invalid in @(@{name='invalid.sql';bytes=[byte[]]@(195,40)},@{name='utf16.sql';bytes=[byte[]]@(255,254,65,0)})){
 $path=Join-Path $fixture $invalid.name;[IO.File]::WriteAllBytes($path,$invalid.bytes);$rejected=$false
 try {Get-ReleaseMigrationHash -Path $path | Out-Null}catch{$rejected=$true}
 if(!$rejected){throw 'Non-UTF-8 migration was accepted.'}
}
Write-Host 'Migration hash utf8-lf-v1: LF/CRLF/CR and UTF-8 BOM equality, SQL-content difference, malformed UTF-8 and UTF-16 rejection passed.'
