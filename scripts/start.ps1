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
    [IO.File]::WriteAllText((Join-Path $taskRoot '.local/demo-credentials.txt'), "Demo only. Username: admin`nInitial password: $demoPassword`nExisting admins keep their existing password; changing .env does not reset it.`n")
    if ($IsWindows) {
        $owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe (Join-Path $taskRoot '.env') /inheritance:r /grant:r "${owner}:F" | Out-Null
        & icacls.exe (Join-Path $taskRoot '.local/demo-credentials.txt') /inheritance:r /grant:r "${owner}:F" | Out-Null
    }
    if (-not $SkipVerify) {
        mvn -B -ntp verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
    }
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
    $retainedTag='rollback-'+(Get-Date -Format 'yyyyMMddHHmmss')
    foreach($repository in @('campuseats-server','campuseats-web')) {
        docker image inspect "${repository}:v1" --format '{{.Id}}' 2>$null | Out-Null
        if($LASTEXITCODE -eq 0) {
            docker image tag "${repository}:v1" "${repository}:$retainedTag"
            if($LASTEXITCODE -ne 0){throw 'Previous image retention failed.'}
            Write-Host "Retained ${repository}:$retainedTag"
        }
    }
    docker compose up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Deployment failed; inspect docker compose logs.' }
    $portMatch = [regex]::Match($config,'(?m)^WEB_PORT=(\d+)'); $port=18083
    if ($portMatch.Success) { $port=[int]$portMatch.Groups[1].Value }
    Write-Host "Admin: http://localhost:$port/admin/"
    Write-Host "H5: http://localhost:$port/app/"
    Write-Host 'Initial demo credentials: .local/demo-credentials.txt (local only).'
    Write-Host 'Uses new campuseats-v1 volumes. Original campuseats volumes are preserved.'
} finally { Pop-Location }
