# Two overlapping recovery contexts share the same wall-clock second. Docker itself is a fixture.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$root=Split-Path $PSScriptRoot -Parent
$fixture=New-SecureDirectory -Path (Join-Path $root ('.local/restore-ownership-test-'+[guid]::NewGuid().ToString('N')))
foreach($name in @('first','second')){
 $directory=New-SecureDirectory -Path (Join-Path $fixture $name)
 New-SecureDirectory -Path (Join-Path $directory 'uploads') | Out-Null
 [IO.File]::WriteAllText((Join-Path $directory 'database.sql'),'fixture SQL')
 [IO.File]::WriteAllText((Join-Path $directory 'uploads/example.png'),'fixture image')
 @('1','1','1','0','0','0') | Set-Content -LiteralPath (Join-Path $directory 'source-counts.txt')
 @('orders','order_detail','user') | Set-Content -LiteralPath (Join-Path $directory 'tables.txt')
 @('sky_take_out.orders 1','sky_take_out.order_detail 2','sky_take_out.user 3') | Set-Content -LiteralPath (Join-Path $directory 'table-checksums.txt')
 $entries=@(Get-ChildItem -LiteralPath $directory -File -Recurse -Force | ForEach-Object {@{path=[IO.Path]::GetRelativePath($directory,$_.FullName);bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
 $entries | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $directory 'sha256.json')
}
$global:restoreOwnershipFirstId='a'*64
$global:restoreOwnershipToken=$null
$global:restoreOwnershipNames=[Collections.Generic.List[string]]::new()
$global:restoreOwnershipRemoved=[Collections.Generic.List[string]]::new()
$global:restoreOwnershipNested=$false
$global:restoreOwnershipSecondFailed=$false
$global:restoreOwnershipScript=Join-Path $PSScriptRoot 'restore-check.ps1'
$global:restoreOwnershipSecond=Join-Path $fixture 'second'
function global:Get-Date {param([string]$Format);$date=[datetime]::new(2026,10,26,3,0,0);if($Format){return $date.ToString($Format)};return $date}
function global:docker {
 $global:LASTEXITCODE=0;$a=@($args)
 if($a[0] -eq 'run'){
  $name=$a[[Array]::IndexOf($a,'--name')+1]
  $global:restoreOwnershipNames.Add($name)
  if($global:restoreOwnershipNested){$global:LASTEXITCODE=1;return}
  $label=@($a | Where-Object {$_ -like 'campuseats.restore-token=*'})
  if($label.Count -ne 1){throw 'A unique recovery token was not supplied.'}
  $global:restoreOwnershipToken=$label[0].Substring('campuseats.restore-token='.Length)
  # The first fake container now exists. A second context fails at its create boundary before
  # the first caller has received its successful ID, reproducing the old name-cleanup hazard.
  $global:restoreOwnershipNested=$true
  try {& $global:restoreOwnershipScript -BackupDirectory $global:restoreOwnershipSecond}catch{$global:restoreOwnershipSecondFailed=$true}
  if(!$global:restoreOwnershipSecondFailed -or $global:restoreOwnershipRemoved.Count){throw 'Failed concurrent creation removed another recovery context.'}
  $global:restoreOwnershipNested=$false;$global:LASTEXITCODE=0
  return $global:restoreOwnershipFirstId
 }
 if($a[0] -eq 'exec'){
  if($a -notcontains $global:restoreOwnershipFirstId){throw 'Recovery used a mutable container name instead of its exact ID.'}
  if($a[-1] -like '*SELECT COUNT(*) orders*'){return @('1','1','1','0','0','0','0','0','0')}
  $sql=$input | Out-String
  if($sql -like '*CHECKSUM TABLE*'){return @('sky_take_out.orders 1','sky_take_out.order_detail 2','sky_take_out.user 3')}
  if($a[-1] -like '*SELECT 1*'){return '1'}
  return
 }
 if($a[0] -eq 'inspect'){
  if($a[-1] -cne $global:restoreOwnershipFirstId -or $a[2] -notlike '*campuseats.restore-token*'){throw 'Cleanup inspected a name or only a shared-purpose label.'}
  return $global:restoreOwnershipToken
 }
 if($a[0] -eq 'rm'){
  if($a[-1] -cne $global:restoreOwnershipFirstId){throw 'Cleanup removed an unrelated resource.'}
  $global:restoreOwnershipRemoved.Add($a[-1]);return
 }
 throw 'Unexpected recovery Docker request.'
}
try {
 & (Join-Path $PSScriptRoot 'restore-check.ps1') -BackupDirectory (Join-Path $fixture 'first')
 if($global:restoreOwnershipNames.Count -ne 2 -or $global:restoreOwnershipNames[0] -ceq $global:restoreOwnershipNames[1] -or !$global:restoreOwnershipSecondFailed -or $global:restoreOwnershipRemoved.Count -ne 1){throw 'Recovery context isolation was not proven.'}
 Write-Host 'Restore ownership: same-second overlapping contexts, failed create isolation, token binding and exact-ID cleanup passed.'
}finally{
 Remove-Item Function:\docker,Function:\Get-Date -ErrorAction SilentlyContinue
 Remove-Variable restoreOwnershipFirstId,restoreOwnershipToken,restoreOwnershipNames,restoreOwnershipRemoved,restoreOwnershipNested,restoreOwnershipSecondFailed,restoreOwnershipScript,restoreOwnershipSecond -Scope Global -ErrorAction SilentlyContinue
}
