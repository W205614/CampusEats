param([int[]]$Users=@(20,50,100),[string]$OtherDuration='2m',[string]$MainDuration='10m',[string]$WarmupDuration='30s',[ValidatePattern('^campuseats-load[-a-z0-9]*$')][string]$ProjectName=('campuseats-load-'+(Get-Date -Format 'yyyyMMddHHmmss')))
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
 if(Test-Path '.local/loadtest/load.env'){throw 'Existing load fixture retained; move it to an evidence folder before a new run.'}
 node scripts/setup-load.mjs
 if($LASTEXITCODE -ne 0){throw 'Fixture generation failed'}
 docker compose -p $ProjectName --env-file .local/loadtest/load.env up -d --wait --wait-timeout 240
 if($LASTEXITCODE -ne 0){throw 'Load stack startup failed'}
 Get-Content .local/loadtest/fixture.sql | docker compose -p $ProjectName --env-file .local/loadtest/load.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out'
 if($LASTEXITCODE -ne 0){throw 'Fixture import failed'}
 $ProjectName | Set-Content .local/loadtest/project.txt
 docker compose -p $ProjectName --env-file .local/loadtest/load.env images --format json | Set-Content .local/loadtest/images.json
 docker info --format 'CPUs={{.NCPU}} memory={{.MemTotal}}' | Set-Content .local/loadtest/docker-resources.txt
 docker version --format '{{.Server.Version}}' | Set-Content .local/loadtest/environment.txt
 docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' | Add-Content .local/loadtest/environment.txt
 if($WarmupDuration){
  docker run --rm --network "${ProjectName}_default" --mount "type=bind,source=$taskRoot/.local/loadtest,target=/data" --mount "type=bind,source=$taskRoot/scripts/load.js,target=/scripts/load.js,readonly" -e VUS=20 -e "DURATION=$WarmupDuration" grafana/k6:1.3.0 run --no-thresholds --summary-export /data/warmup.json /scripts/load.js > .local/loadtest/warmup.log 2>&1
  if($LASTEXITCODE -ne 0){throw 'Load warmup failed'}
 }
 $loadFailed=$false
 foreach($vus in $Users){
  $duration=if($vus -eq 50){$MainDuration}else{$OtherDuration}
  docker run --rm --network "${ProjectName}_default" --mount "type=bind,source=$taskRoot/.local/loadtest,target=/data" --mount "type=bind,source=$taskRoot/scripts/load.js,target=/scripts/load.js,readonly" -e "VUS=$vus" -e "DURATION=$duration" grafana/k6:1.3.0 run --summary-export "/data/results-$vus.json" /scripts/load.js > ".local/loadtest/run-$vus.log" 2>&1
  $code=$LASTEXITCODE
  Write-Host "Load run $vus users, ${duration}: exit $code"
  if($code -ne 0){$loadFailed=$true}
 }
 $sql='SELECT COUNT(*) FROM orders WHERE request_id IS NOT NULL; SELECT COUNT(*) FROM (SELECT user_id,request_id FROM orders WHERE request_id IS NOT NULL GROUP BY user_id,request_id HAVING COUNT(*)>1) x; SELECT COUNT(*) FROM daily_quota WHERE total<reserved+consumed OR reserved<0 OR consumed<0; SELECT COALESCE(SUM(reserved),0),COALESCE(SUM(consumed),0) FROM daily_quota;'
 $sql | docker compose -p $ProjectName --env-file .local/loadtest/load.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N' | Set-Content .local/loadtest/integrity.txt
 if($LASTEXITCODE -ne 0){throw 'Integrity query failed'}
 if($loadFailed){throw 'One or more capacity thresholds failed; full evidence retained in .local/loadtest.'}
} finally {
 docker compose -p $ProjectName --env-file .local/loadtest/load.env stop | Out-Null
 Pop-Location
}
