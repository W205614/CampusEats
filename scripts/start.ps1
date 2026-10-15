param([switch]$SkipVerify)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop Linux engine is unavailable.' }
    function New-TaskSecret { $bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Fill($bytes); return -join ($bytes | ForEach-Object { $_.ToString('x2') }) }
    if (-not (Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
    $config = [IO.File]::ReadAllText((Join-Path $taskRoot '.env'))
    foreach ($placeholder in @('root-password','app-password','redis-password','admin-signing-key','demo-password')) { $config = $config.Replace("replace-with-random-$placeholder", (New-TaskSecret)) }
    if ($config -notmatch '(?m)^DEMO_ADMIN_PASSWORD=') { $config += "`nDEMO_ADMIN_PASSWORD=$(New-TaskSecret)`n" }
    [IO.File]::WriteAllText((Join-Path $taskRoot '.env'), $config, [Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Path '.local' -Force | Out-Null
    $demoPassword = [regex]::Match($config,'(?m)^DEMO_ADMIN_PASSWORD=([^\r\n]+)').Groups[1].Value
    [IO.File]::WriteAllText((Join-Path $taskRoot '.local/demo-credentials.txt'), "Demo only. Platform administrator: admin`nInitial password: $demoPassword`nDemo managers: manager1, manager2, manager3, manager4`nDemo operators: operator1, operator2, operator3, operator4`nCampus couriers: courier1, courier1b, courier2, courier2b`nNew demo employees use the initial password above and must change it on first login.`nExisting accounts keep their password; changing .env does not reset it.`n")
    if ($IsWindows) {
        $owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe (Join-Path $taskRoot '.env') /inheritance:r /grant:r "${owner}:F" | Out-Null
        & icacls.exe (Join-Path $taskRoot '.local/demo-credentials.txt') /inheritance:r /grant:r "${owner}:F" | Out-Null
    }
    if (-not $SkipVerify) {
        mvn -B -ntp clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
    }
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
    $retainedTag='rollback-'+(Get-Date -Format 'yyyyMMddHHmmss')
    foreach($repository in @('campuseats-server','campuseats-web')) {
        $service=$repository -replace '^campuseats-',''
        $container=docker compose ps -a -q $service
        $previousImage=$null
        if($container){$previousImage=docker inspect --format '{{.Image}}' $container}
        if(!$previousImage){$previousImage=docker image inspect "${repository}:v1" --format '{{.Id}}' 2>$null}
        if($LASTEXITCODE -eq 0) {
            docker image tag $previousImage "${repository}:$retainedTag"
            if($LASTEXITCODE -ne 0){throw 'Previous image retention failed.'}
            Write-Host "Retained ${repository}:$retainedTag"
        }
    }
    docker compose build
    if($LASTEXITCODE -ne 0){throw 'Source image build failed; running containers were preserved.'}
    $mysql=docker compose ps -q mysql
    if($mysql){
        $schemaVersion='SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1;' | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out -N'
        if($LASTEXITCODE -ne 0){throw 'Cannot determine current schema version; deployment stopped.'}
        $latestVersion=(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'sky-server/src/main/resources/db/migration') -Filter 'V*.sql' | ForEach-Object {if($_.Name -match '^V(\d+)__'){[int]$Matches[1]}} | Measure-Object -Maximum).Maximum
        if([int]$schemaVersion -lt $latestVersion){
            docker compose stop web server
            if($LASTEXITCODE -ne 0){throw 'Failed to pause writers before migration.'}
            $migrationBackup=Join-Path $taskRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-schema-upgrade')
            & (Join-Path $PSScriptRoot 'backup.ps1') -Destination $migrationBackup
            & (Join-Path $PSScriptRoot 'restore-check.ps1') -BackupDirectory $migrationBackup
            Write-Host "Verified pre-migration backup: $migrationBackup"
        }
    }
    docker compose up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Deployment failed; inspect docker compose logs.' }
    $portMatch = [regex]::Match($config,'(?m)^WEB_PORT=(\d+)'); $port=18083
    if ($portMatch.Success) { $port=[int]$portMatch.Groups[1].Value }
    Write-Host "Admin: http://localhost:$port/admin/"
    Write-Host "H5: http://localhost:$port/app/"
    Write-Host 'Initial demo credentials: .local/demo-credentials.txt (local only).'
    Write-Host 'Uses project-scoped persistent volumes. Before schema upgrades, writers are paused and a backup is independently restored.'
} finally { Pop-Location }
