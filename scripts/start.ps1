param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and wait for the Linux engine.' }
    if (-not (Test-Path '.env')) {
        $text = [IO.File]::ReadAllText((Join-Path $repoRoot '.env.example'))
        foreach ($placeholder in @('root-password','app-password','redis-password','admin-signing-key','user-signing-key')) {
            $bytes = New-Object byte[] 32
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            $secret = -join ($bytes | ForEach-Object { $_.ToString('x2') })
            $text = $text.Replace("replace-with-random-$placeholder", $secret)
        }
        [IO.File]::WriteAllText((Join-Path $repoRoot '.env'), $text, [Text.UTF8Encoding]::new($false))
    }
    $port = 18083
    $portSetting = Select-String -Path '.env' -Pattern '^WEB_PORT=(\d+)$'
    if ($portSetting) { $port = [int]$portSetting.Matches[0].Groups[1].Value }
    if (-not (Test-Path 'deploy/admin/index.html') -or -not (Test-Path 'mp-weixin/project.config.json') -or -not (Test-Path 'deploy/images/1.png')) {
        & "$PSScriptRoot/prepare-assets.ps1" -Port $port
    }
    node (Join-Path $PSScriptRoot 'patch-miniprogram.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Mini-program patch failed. Check Node.js installation and course asset version.' }
    node (Join-Path $PSScriptRoot 'patch-admin.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Administrator patch failed. Check course asset version.' }
    if (-not $SkipBuild) {
        if (Get-Command mvn -ErrorAction SilentlyContinue) {
            mvn -B -ntp clean verify
        } else {
            docker run --rm --mount "type=bind,source=$repoRoot,target=/workspace" --mount 'type=volume,source=campuseats-maven-cache,target=/root/.m2' -w /workspace maven:3.9-eclipse-temurin-21 mvn -B -ntp clean verify
        }
        if ($LASTEXITCODE -ne 0) { throw 'Maven build or tests failed.' }
    }
    if (-not (Test-Path 'sky-server/target/sky-server-1.0-SNAPSHOT.jar')) { throw 'JAR missing. Run without -SkipBuild.' }
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
    docker compose up -d --build --wait --wait-timeout 240
    if ($LASTEXITCODE -ne 0) { throw 'Deployment failed. Inspect: docker compose ps; docker compose logs --tail 100' }
    Write-Host "Admin: http://localhost:$port (admin / 123456)"
    Write-Host "API docs: http://localhost:$port/doc.html"
    Write-Host "Mini-program: $repoRoot\mp-weixin (WeChat Developer Tools)"
    Write-Host 'Default: local demo login and simulated payment; no real money movement.'
} finally { Pop-Location }
