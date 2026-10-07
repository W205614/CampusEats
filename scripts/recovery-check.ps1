$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
 [IO.File]::WriteAllText((Join-Path $taskRoot '.local/recovery.override.yml'),"services:`n  server:`n    environment:`n      TASKS_ENABLED: 'false'`n")
 if(-not(Test-Path 'scripts/recovery-probe.mjs')){throw 'Local fault-injection fixture is missing.'}
 docker compose -f compose.yaml -f .local/recovery.override.yml up -d --no-deps --wait --wait-timeout 180 server
 if($LASTEXITCODE -ne 0){throw 'Recovery preparation failed'}
 node scripts/recovery-probe.mjs prepare
 if($LASTEXITCODE -ne 0){throw 'Recovery order failed'}
 $order=Get-Content .local/recovery-order.json -Raw | ConvertFrom-Json
 if($order.id -notmatch '^\d+$'){throw 'Invalid fixture order id'}
 $sql="UPDATE refund_task SET state='PROCESSING',lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND),lease_token='interrupted-fixture' WHERE order_id=$($order.id); UPDATE outbox_event SET state='PROCESSING',lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND),lease_token='interrupted-fixture' WHERE aggregate_id=$($order.id); SELECT state FROM refund_task WHERE order_id=$($order.id);"
 $sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
 if($LASTEXITCODE -ne 0){throw 'Lease fixture failed'}
 docker compose up -d --no-deps --wait --wait-timeout 180 server
 if($LASTEXITCODE -ne 0){throw 'Restart failed'}
 node scripts/recovery-probe.mjs verify
 if($LASTEXITCODE -ne 0){throw 'Restart recovery failed'}
 $pending=1
 for($attempt=0;$attempt -lt 20;$attempt++){
  $pending="SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=$($order.id) AND state<>'SUCCEEDED';" | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
  if($LASTEXITCODE -ne 0){throw 'Recovered outbox check failed'}
  if($pending -eq '0'){break}
  Start-Sleep -Milliseconds 500
 }
 if($pending -ne '0'){throw 'Notification leases were not recovered'}
 Write-Host 'Process replacement recovered persisted refund and notification lease fixtures.'

 docker compose stop redis
 node scripts/recovery-probe.mjs redis
 if($LASTEXITCODE -ne 0){throw 'Redis degradation failed'}
 docker compose up -d --no-deps --wait --wait-timeout 120 redis | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Redis recovery failed'}
 docker compose stop mysql | Out-Null
 $health=Invoke-WebRequest 'http://localhost:18083/actuator/health/readiness' -SkipHttpErrorCheck -TimeoutSec 30
 if($health.StatusCode -ne 503){throw 'MySQL outage readiness did not reject traffic'}
 $business=Invoke-WebRequest 'http://localhost:18083/api/v1/user/auth/demo' -Method Post -ContentType 'application/json' -Body '{"account":3}' -SkipHttpErrorCheck -TimeoutSec 30
 $errorBody=$business.Content | ConvertFrom-Json
 if($business.StatusCode -ne 503 -or $errorBody.code -ne 'DATABASE_UNAVAILABLE' -or -not $errorBody.requestId){throw 'MySQL outage business response was not a uniform 503'}
 Write-Host 'MySQL outage: readiness and business traffic rejected with 503.'
} finally {
 docker compose up -d --no-deps --wait --wait-timeout 180 mysql | Out-Null
 docker compose up -d --no-deps --wait --wait-timeout 180 redis server | Out-Null
 Pop-Location
}
