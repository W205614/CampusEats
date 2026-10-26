param([string]$ProjectName='campuseats-v1',[Parameter(Mandatory=$true)][string]$OutputPath,[string]$CompareManifest,[string]$EnvFile='.env')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$resolved=[IO.Path]::GetFullPath($OutputPath)
$localRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '.local'))+[IO.Path]::DirectorySeparatorChar
if(!$resolved.StartsWith($localRoot)){throw 'Manifest must be kept in the workspace .local directory.'}
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$before=$null
if($CompareManifest){
 $before=Get-Content -LiteralPath $CompareManifest -Raw | ConvertFrom-Json -AsHashtable
 if($before.schemaVersion -ne 2 -or !$before.tables){throw 'Capture a new v2 manifest before comparing an upgrade; legacy manifests are never guessed.'}
}
function Read-ManifestSql([string]$Sql){
 $rows=@($Sql | docker compose --env-file $EnvFile -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -B --raw -u campus sky_take_out -N')
 if($LASTEXITCODE -ne 0){throw 'Historical database fingerprint query failed.'}
 return $rows
}
function Get-ManifestExpression([string[]]$Columns){
 $parts=@(foreach($column in $Columns){
  if($column -notmatch '^[a-zA-Z0-9_]+$'){throw 'Invalid column inventory.'}
  "IF(``$column`` IS NULL,'N',CONCAT('V',HEX(CAST(``$column`` AS BINARY))))"
 })
 return 'CONCAT_WS('':'' ,'+($parts -join ',')+')'
}
$result=[ordered]@{schemaVersion=2;project=$ProjectName;tables=[ordered]@{}}
Push-Location $taskRoot
try{
 $tables=if($before){@($before.tables.Keys)}else{@(Read-ManifestSql 'SHOW TABLES;')}
 foreach($table in $tables){
  if($table -notmatch '^[a-zA-Z0-9_]+$'){throw 'Invalid table inventory.'}
  $columns=if($before){@($before.tables[$table].columns)}else{@(Read-ManifestSql "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='$table' ORDER BY ORDINAL_POSITION;")}
  $primary=if($before){@($before.tables[$table].primaryKey)}else{@(Read-ManifestSql "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='$table' AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION;")}
  if(!$columns.Count -or !$primary.Count){throw 'Historical fingerprint requires explicit columns and a primary key.'}
  $keyExpression=Get-ManifestExpression $primary;$rowExpression=Get-ManifestExpression $columns
  # Encode values before hashing: NULL and empty text differ, delimiters cannot collide with HEX.
  # Query the original columns only; schema additions and newly inserted rows are recorded separately.
  $rows=@(Read-ManifestSql ('SELECT '+$keyExpression+',SHA2('+$rowExpression+',256) FROM `'+$table+'`;'))
  $expected=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  if($before){foreach($key in @($before.tables[$table]['keys'])){[void]$expected.Add([string]$key)}}
  $keys=[Collections.Generic.List[string]]::new();$fingerprints=[Collections.Generic.List[string]]::new();$newRows=0
  foreach($row in $rows){
   $fields=([string]$row) -split "`t",2
   if($fields.Count -ne 2 -or $fields[0] -notmatch '^[NV0-9A-F:]+$' -or $fields[1] -notmatch '^[a-f0-9]{64}$'){throw 'Invalid fingerprint result.'}
   if($before -and !$expected.Contains($fields[0])){$newRows++;continue}
   $keys.Add($fields[0]);$fingerprints.Add($fields[0]+':'+$fields[1])
  }
  $keys.Sort([StringComparer]::Ordinal);$fingerprints.Sort([StringComparer]::Ordinal)
  $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($fingerprints -join "`n"))))
  $result.tables[$table]=[ordered]@{columns=$columns;primaryKey=$primary;keys=@($keys.ToArray());rows=$keys.Count;newRows=$newRows;sha256=$hash}
  if($before -and ($hash -cne $before.tables[$table].sha256 -or $keys.Count -ne @($before.tables[$table]['keys']).Count)){throw "Historical source fields changed or disappeared in $table"}
 }
 Write-SecureTextFile -Path $resolved -Content ($result | ConvertTo-Json -Depth 8)
 Write-Host "Historical original-column fingerprints verified for $($tables.Count) tables: $resolved"
}finally{Pop-Location}
