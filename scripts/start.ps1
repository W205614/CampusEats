param([switch]$SkipVerify,[string]$ProjectName='campuseats-v1',[string]$EnvFile='.env')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$composeArgs=@('compose','--env-file',$EnvFile,'-p',$ProjectName)
Push-Location $taskRoot
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop Linux engine is unavailable.' }
    function New-TaskSecret { $bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Fill($bytes); return -join ($bytes | ForEach-Object { $_.ToString('x2') }) }
    if (-not (Test-Path -LiteralPath $EnvFile)) { Copy-Item -LiteralPath '.env.example' -Destination $EnvFile }
    $config = [IO.File]::ReadAllText(([IO.Path]::GetFullPath($EnvFile,$taskRoot)))
    foreach ($placeholder in @('root-password','app-password','redis-password','admin-signing-key','demo-password')) { $config = $config.Replace("replace-with-random-$placeholder", (New-TaskSecret)) }
    if ($config -notmatch '(?m)^DEMO_ADMIN_PASSWORD=') { $config += "`nDEMO_ADMIN_PASSWORD=$(New-TaskSecret)`n" }
    [IO.File]::WriteAllText(([IO.Path]::GetFullPath($EnvFile,$taskRoot)), $config, [Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Path '.local' -Force | Out-Null
    $credentialsFile=Join-Path $taskRoot (".local/"+$(if($ProjectName -eq "campuseats-v1"){"demo-credentials.txt"}else{$ProjectName+"-demo-credentials.txt"}))
    $demoPassword = [regex]::Match($config,'(?m)^DEMO_ADMIN_PASSWORD=([^\r\n]+)').Groups[1].Value
    [IO.File]::WriteAllText($credentialsFile, "Demo only. Platform administrator: admin`nInitial password: $demoPassword`nDemo managers: manager1, manager2, manager3, manager4`nDemo operators: operator1, operator2, operator3, operator4`nCampus couriers: courier1, courier1b, courier2, courier2b`nNew demo employees use the initial password above and must change it on first login.`nExisting accounts keep their password; changing .env does not reset it.`n")
    if ($IsWindows) {
        $owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe ([IO.Path]::GetFullPath($EnvFile,$taskRoot)) /inheritance:r /grant:r "${owner}:F" | Out-Null
        & icacls.exe $credentialsFile /inheritance:r /grant:r "${owner}:F" | Out-Null
    }
    if (-not $SkipVerify) {
        mvn -B -ntp clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
    }
    & docker @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
    $retainedTag='rollback-'+(Get-Date -Format 'yyyyMMddHHmmss')
    foreach($repository in @('campuseats-server','campuseats-web')) {
        $service=$repository -replace '^campuseats-',''
        $container=& docker @composeArgs ps -a -q $service
        $previousImage=$null
        if($container){$previousImage=docker inspect --format '{{.Image}}' $container}
        if(!$previousImage){$previousImage=docker image inspect "${repository}:v1" --format '{{.Id}}' 2>$null}
        if($LASTEXITCODE -eq 0) {
            docker image tag $previousImage "${repository}:$retainedTag"
            if($LASTEXITCODE -ne 0){throw 'Previous image retention failed.'}
            Write-Host "Retained ${repository}:$retainedTag"
        }
    }
    $revision=git rev-parse HEAD
    if($LASTEXITCODE -ne 0){$revision='unknown'}
    & docker @composeArgs build --build-arg "SOURCE_REVISION=$revision"
    if($LASTEXITCODE -ne 0){throw 'Source image build failed; running containers were preserved.'}
    # Dependencies must be reachable even when the whole previous stack was stopped.
    & docker @composeArgs up -d --wait --wait-timeout 180 mysql redis
    if($LASTEXITCODE -ne 0){throw 'Cannot start database dependencies; application was not upgraded.'}
    $tableCount='SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>''flyway_schema_history'';' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect existing database; deployment stopped.'}
    if([int]$tableCount -gt 0){
        $history='SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=''flyway_schema_history'';' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
        if($LASTEXITCODE -ne 0 -or $history -ne '1'){throw 'Existing business data has no Flyway history; automatic baselining is forbidden.'}
        $schemaVersion='SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1;' | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
        if($LASTEXITCODE -ne 0){throw 'Cannot determine schema version; deployment stopped.'}
        $latestVersion=(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'sky-server/src/main/resources/db/migration') -Filter 'V*.sql' | ForEach-Object {if($_.Name -match '^V(\d+)__'){[int]$Matches[1]}} | Measure-Object -Maximum).Maximum
        if([int]$schemaVersion -gt $latestVersion){throw 'Database is newer than this checkout; use a compatible release.'}
        $releaseBackup=Join-Path $taskRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-before-release')
        $writers=@()
        foreach($service in @('server','web')) {
            $id=& docker @composeArgs ps -a -q $service
            if($id -and (docker inspect --format '{{.State.Running}}' $id) -eq 'true'){$writers+=$service}
        }
        & docker @composeArgs stop web server
        if($LASTEXITCODE -ne 0){throw 'Cannot pause writers before release.'}
        try {
            & (Join-Path $PSScriptRoot 'safe-backup.ps1') -ProjectName $ProjectName -EnvFile $EnvFile -Destination $releaseBackup
        } catch {
            if($writers.Count){& docker @composeArgs start @writers | Out-Null}
            throw
        }
        Write-Host "Verified pre-release backup: $releaseBackup"
    }
    & docker @composeArgs up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Deployment failed; inspect docker compose logs.' }
    $portMatch = [regex]::Match($config,'(?m)^WEB_PORT=(\d+)'); $port=18083
    if ($portMatch.Success) { $port=[int]$portMatch.Groups[1].Value }
    Write-Host "Ordering: http://localhost:$port/"
    Write-Host "Staff workbench: http://localhost:$port/admin/"
    Write-Host "H5 direct link: http://localhost:$port/app/"
    Write-Host "Initial demo credentials: $credentialsFile (local only)."
    Write-Host 'Uses project-scoped persistent volumes. Before schema upgrades, writers are paused and a backup is independently restored.'
} finally { Pop-Location }
