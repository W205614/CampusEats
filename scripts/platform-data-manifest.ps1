param([string]$ProjectName='campuseats-v1',[Parameter(Mandatory=$true)][string]$OutputPath,[string]$CompareManifest)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$resolved=[IO.Path]::GetFullPath($OutputPath)
$localRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '.local'))+[IO.Path]::DirectorySeparatorChar
if(!$resolved.StartsWith($localRoot)){throw 'Manifest must be kept in the workspace .local directory.'}
$columns=[ordered]@{
 employee='id,username,password,name,create_time'
 user='id,openid,name,phone,create_time'
 category='id,name,type,sort,status'
 dish='id,name,category_id,price,image,description,status,default_quota'
 dish_flavor='id,dish_id,name,value'
 setmeal='id,name,category_id,price,image,description,status'
 setmeal_dish='id,setmeal_id,dish_id,copies,name,price'
 orders='id,number,user_id,address_book_id,order_time,amount,remark,phone,address,consignee,snapshot_source,building_name,room,delivery_fee,packaging_fee'
 order_detail='id,order_id,name,image,dish_id,setmeal_id,dish_flavor,number,amount'
 address_book='id,user_id,building_id,room,consignee,phone,detail'
 payment_attempt='id,order_id,gateway_ref,created_at'
 refund_task='id,order_id,attempts,created_at'
 audit_log='id,actor_type,actor_id,action,target_id,detail,created_at'
}
$before=$null
if($CompareManifest){$before=Get-Content -LiteralPath $CompareManifest -Raw | ConvertFrom-Json -AsHashtable}
$result=[ordered]@{}
Push-Location $taskRoot
try{
 foreach($table in $columns.Keys){
  if($before){$ids=@($before[$table].ids)}else{$ids=@("SELECT id FROM $table ORDER BY id;" | docker compose -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N');if($LASTEXITCODE -ne 0){throw "Cannot inventory $table"}}
  if(@($ids|Where-Object {$_ -notmatch '^\d+$'}).Count){throw 'Invalid source IDs.'}
  $filter=if($ids.Count){' WHERE id IN ('+($ids -join ',')+')'}else{' WHERE 1=0'}
  $sql='SELECT '+$columns[$table]+" FROM $table"+$filter+' ORDER BY id;'
  $rows=@($sql | docker compose -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u campus sky_take_out -N')
  if($LASTEXITCODE -ne 0){throw "Cannot fingerprint $table"}
  $bytes=[Text.Encoding]::UTF8.GetBytes(($rows -join "`n"))
  $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
  $result[$table]=[ordered]@{ids=$ids;rows=$rows.Count;sha256=$hash}
  if($before -and ($hash -ne $before[$table].sha256 -or $rows.Count -ne $before[$table].rows)){throw "Historical source fields changed in $table"}
 }
 $result|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $resolved -Encoding utf8
 Write-Host "Historical record fingerprints verified: $resolved"
}finally{Pop-Location}
