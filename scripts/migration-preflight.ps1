param([string]$ProjectName='campuseats-v1')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
 $sql=@'
SELECT 'duplicate order number' issue,GROUP_CONCAT(id ORDER BY id) record_ids FROM orders WHERE number IS NOT NULL GROUP BY number HAVING COUNT(*)>1;
SELECT 'duplicate user identity' issue,GROUP_CONCAT(id ORDER BY id) record_ids FROM user WHERE openid IS NOT NULL GROUP BY openid HAVING COUNT(*)>1;
SELECT 'invalid order owner' issue,o.id record_ids FROM orders o LEFT JOIN user u ON u.id=o.user_id WHERE u.id IS NULL;
SELECT 'legacy cart retained' note,COUNT(*) records FROM shopping_cart;
SELECT table_name,COUNT(*) columns_found FROM information_schema.columns WHERE table_schema='sky_take_out' AND table_name IN ('orders','employee','user','dish','address_book') GROUP BY table_name;
'@
 $sql | docker compose -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out'
 if($LASTEXITCODE -ne 0){throw 'Preflight failed'}
 $conflicts='SELECT (SELECT COUNT(*) FROM (SELECT number FROM orders WHERE number IS NOT NULL GROUP BY number HAVING COUNT(*)>1) a)+(SELECT COUNT(*) FROM (SELECT openid FROM user WHERE openid IS NOT NULL GROUP BY openid HAVING COUNT(*)>1) b)+(SELECT COUNT(*) FROM orders o LEFT JOIN user u ON u.id=o.user_id WHERE u.id IS NULL);' | docker compose -p $ProjectName exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0 -or $conflicts -ne '0'){throw 'Migration conflicts found. Resolve them explicitly; no data was deleted.'}
 Write-Host 'Duplicate reports must be empty. Verify schema against V1 before explicitly baselining a restored clone.'
} finally { Pop-Location }
