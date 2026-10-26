param([switch]$SkipVerify,[string]$ProjectName='campuseats-v1',[string]$EnvFile='.env')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$composeArgs=@('compose','--env-file',$EnvFile,'-p',$ProjectName)
. (Join-Path $PSScriptRoot 'operations-lock.ps1')
. (Join-Path $PSScriptRoot 'operations-support.ps1')
. (Join-Path $PSScriptRoot 'secure-files.ps1')
$operationLease=Enter-OperationsLock -Root $taskRoot -ProjectName $ProjectName
Push-Location $taskRoot
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop Linux engine is unavailable.' }
    function New-TaskSecret { $bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Fill($bytes); return -join ($bytes | ForEach-Object { $_.ToString('x2') }) }
    $envPath=[IO.Path]::GetFullPath($EnvFile,$taskRoot)
    if (Test-Path -LiteralPath $envPath) {Set-SecurePathPermissions -Path $envPath;$config=[IO.File]::ReadAllText($envPath)}
    else {$config=[IO.File]::ReadAllText((Join-Path $taskRoot '.env.example'))}
    if($config -match '(?m)^SERVER_IMAGE=campuseats-server:sha-[a-f0-9]{40}\s*$'){throw 'This environment uses tested CI artifacts. Upgrade with scripts/release.ps1; source rebuilding must not overwrite immutable release references.'}
    foreach ($placeholder in @('root-password','app-password','redis-password','admin-signing-key','demo-password')) { $config = $config.Replace("replace-with-random-$placeholder", (New-TaskSecret)) }
    if ($config -notmatch '(?m)^DEMO_ADMIN_PASSWORD=') { $config += "`nDEMO_ADMIN_PASSWORD=$(New-TaskSecret)`n" }
    Write-SecureTextFile -Path $envPath -Content $config
    New-SecureDirectory -Path (Join-Path $taskRoot '.local') | Out-Null
    $credentialsFile=Join-Path $taskRoot (".local/"+$(if($ProjectName -eq "campuseats-v1"){"demo-credentials.txt"}else{$ProjectName+"-demo-credentials.txt"}))
    $demoPassword = [regex]::Match($config,'(?m)^DEMO_ADMIN_PASSWORD=([^\r\n]+)').Groups[1].Value
    Write-SecureTextFile -Path $credentialsFile -Content "Demo only. Platform administrator: admin`nInitial password: $demoPassword`nDemo managers: manager1, manager2, manager3, manager4`nDemo operators: operator1, operator2, operator3, operator4`nCampus couriers: courier1, courier1b, courier2, courier2b`nNew demo employees use the initial password above and must change it on first login.`nExisting accounts keep their password; changing .env does not reset it.`n"
    if (-not $SkipVerify) {
        mvn -B -ntp clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
    }
    & docker @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
    $retainedTag='rollback-'+(Get-Date -Format 'yyyyMMddHHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
    $previousImages=[ordered]@{}
    $previousRunning=[ordered]@{}
    foreach($repository in @('campuseats-server','campuseats-web')) {
        $service=$repository -replace '^campuseats-',''
        $container=& docker @composeArgs ps -a -q $service
        $previousImage=$null
        if($container){
            $previousImage=docker inspect --format '{{.Image}}' $container
            $previousRunning[$service]=((docker inspect --format '{{.State.Running}}' $container) -eq 'true')
        } else {$previousRunning[$service]=$false}
        if(!$previousImage){$previousImage=docker image inspect "${repository}:v1" --format '{{.Id}}' 2>$null}
        if($LASTEXITCODE -eq 0) {
            $previousImages[$service]=[string]$previousImage
            docker image tag $previousImage "${repository}:$retainedTag"
            if($LASTEXITCODE -ne 0){throw 'Previous image retention failed.'}
            Write-Host "Retained ${repository}:$retainedTag"
        }
    }
    $revision=git rev-parse HEAD
    if($LASTEXITCODE -ne 0){$revision='unknown'}
    & docker @composeArgs build --pull --build-arg "SOURCE_REVISION=$revision" --build-arg ('RUNTIME_REFRESH='+[DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
    if($LASTEXITCODE -ne 0){throw 'Source image build failed; running containers were preserved.'}
    # Dependencies must be reachable even when the whole previous stack was stopped.
    & docker @composeArgs up -d --wait --wait-timeout 180 mysql redis
    if($LASTEXITCODE -ne 0){throw 'Cannot start database dependencies; application was not upgraded.'}
    $tableCount='SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>''flyway_schema_history'';' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect existing database; deployment stopped.'}
    $schemaBefore=0
    $releaseRecord=[ordered]@{project=$ProjectName;startedAt=[DateTimeOffset]::UtcNow.ToString('o');candidateRevision=[string]$revision;previousImages=$previousImages;previousRunning=$previousRunning;schemaBefore=$null;schemaAfter=$null;schemaFingerprintBefore=$null;schemaFingerprintAfter=$null;outcome='preparing';rollback='not_attempted'}
    $releaseRecordPath=Join-Path $taskRoot ('.local/releases/'+$retainedTag+'.json')
    if([int]$tableCount -gt 0){
        $history='SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=''flyway_schema_history'';' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
        if($LASTEXITCODE -ne 0 -or $history -ne '1'){throw 'Existing business data has no Flyway history; automatic baselining is forbidden.'}
        $schemaVersion='SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1;' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
        if($LASTEXITCODE -ne 0){throw 'Cannot determine schema version; deployment stopped.'}
        $schemaBefore=[int]$schemaVersion
        $beforeSchema=Read-ReleaseSchema -ComposeArguments $composeArgs
        $releaseRecord.schemaFingerprintBefore=$beforeSchema.fingerprint
        $releaseRecord.schemaBefore=$schemaBefore
        $latestVersion=(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'sky-server/src/main/resources/db/migration') -Filter 'V*.sql' | ForEach-Object {if($_.Name -match '^V(\d+)__'){[int]$Matches[1]}} | Measure-Object -Maximum).Maximum
        if([int]$schemaVersion -gt $latestVersion){throw 'Database is newer than this checkout; use a compatible release.'}
        $releaseBackup=Join-Path $taskRoot ('.local/backups/'+$retainedTag+'-before-release')
        $releaseRecord.outcome='verifying_backup'
        $releaseRecord.backup=$releaseBackup
        Write-OperationsJson -Path $releaseRecordPath -Value $releaseRecord
        $writers=@()
        foreach($service in @('server','web')) {
            $id=& docker @composeArgs ps -a -q $service
            if($id -and (docker inspect --format '{{.State.Running}}' $id) -eq 'true'){$writers+=$service}
        }
        & docker @composeArgs stop web server
        if($LASTEXITCODE -ne 0){throw 'Cannot pause writers before release.'}
        try {
            & (Join-Path $PSScriptRoot 'safe-backup.ps1') -ProjectName $ProjectName -EnvFile $EnvFile -Destination $releaseBackup -OperationToken $operationLease.Token
        } catch {
            if($writers.Count){& docker @composeArgs start @writers | Out-Null}
            $releaseRecord.outcome='backup_failed'
            $releaseRecord.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
            Write-OperationsJson -Path $releaseRecordPath -Value $releaseRecord
            throw
        }
        Write-Host "Verified pre-release backup: $releaseBackup"
    }
    $releaseRecord.schemaBefore=$schemaBefore
    $releaseRecord.outcome='starting'
    Write-OperationsJson -Path $releaseRecordPath -Value $releaseRecord
    try {
        & docker @composeArgs up -d --wait --wait-timeout 300
        if ($LASTEXITCODE -ne 0) { throw 'Candidate deployment did not become healthy.' }
        $releaseRecord.outcome='healthy'
        $releaseRecord.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
        Write-OperationsJson -Path $releaseRecordPath -Value $releaseRecord
    } catch {
        $releaseRecord.outcome='failed'
        # Stop the failed candidate before observing schema: no candidate may migrate after this check.
        & docker @composeArgs stop web server | Out-Null
        $paused=$LASTEXITCODE -eq 0
        $after=$null;$afterSchema=$null
        if($paused){
            try {$afterSchema=Read-ReleaseSchema -ComposeArguments $composeArgs;$after=$afterSchema.version} catch {$after=$null}
        }
        $releaseRecord.schemaAfter=$after
        if($afterSchema){$releaseRecord.schemaFingerprintAfter=$afterSchema.fingerprint}
        $schemaUnchanged=$null -ne $after -and [int]$after -eq $schemaBefore -and $releaseRecord.schemaFingerprintBefore -and $releaseRecord.schemaFingerprintBefore -ceq $releaseRecord.schemaFingerprintAfter
        $canRollback=$paused -and $schemaUnchanged -and $schemaBefore -gt 0 -and $previousImages.server -and $previousImages.web -and $previousRunning.server -and $previousRunning.web
        if($canRollback){
            $overridePath=Join-Path $taskRoot ('.local/releases/'+$retainedTag+'-rollback.json')
            Write-OperationsJson -Path $overridePath -Value @{services=@{server=@{image=$previousImages.server};web=@{image=$previousImages.web}}}
            & docker @composeArgs -f compose.yaml -f $overridePath up -d --no-build --no-deps --wait --wait-timeout 300 server web | Out-Null
            $releaseRecord.rollback=if($LASTEXITCODE -eq 0){'healthy'}else{'failed'}
        } else {
            $releaseRecord.rollback=if(!$paused){'blocked_writers_not_stopped'}elseif($null -eq $after){'blocked_schema_unknown'}elseif(!$schemaUnchanged){'blocked_schema_changed'}else{'blocked_previous_runtime_unavailable'}
        }
        $releaseRecord.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
        Write-OperationsJson -Path $releaseRecordPath -Value $releaseRecord
        if($releaseRecord.rollback -eq 'healthy'){throw 'Deployment failed; previous images are healthy again. Inspect the release record before retrying.'}
        throw 'Deployment failed; automatic image rollback was unsafe or unsuccessful. Preserve the release record, database and files for manual recovery.'
    }
    $portMatch = [regex]::Match($config,'(?m)^WEB_PORT=(\d+)'); $port=18083
    if ($portMatch.Success) { $port=[int]$portMatch.Groups[1].Value }
    Write-Host "Ordering: http://localhost:$port/"
    Write-Host "Staff workbench: http://localhost:$port/admin/"
    Write-Host "H5 direct link: http://localhost:$port/app/"
    Write-Host "Initial demo credentials: $credentialsFile (local only)."
    Write-Host 'Uses project-scoped persistent volumes. Before schema upgrades, writers are paused and a backup is independently restored.'
} finally { Pop-Location;Exit-OperationsLock $operationLease }
