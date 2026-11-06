param(
  [Parameter(Mandatory)][string]$ServerImage,
  [string]$MySqlImage = 'campuseats-mysql:v1',
  [string]$RedisImage = '',
  [string]$ProjectName = ('campuseats-improvement-cluster-' + [guid]::NewGuid().ToString('N').Substring(0, 12)),
  [int]$NodeAPort = 28097,
  [int]$NodeBPort = 28098,
  [int]$WebPort = 28099
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'secure-files.ps1')
if ($ProjectName -notmatch '^campuseats-improvement-cluster-[a-z0-9-]+$') { throw 'Use a fresh isolated cluster project name.' }
if (@($NodeAPort, $NodeBPort, $WebPort | Select-Object -Unique).Count -ne 3 -or @($NodeAPort, $NodeBPort, $WebPort | Where-Object { $_ -eq 18083 -or $_ -lt 1024 -or $_ -gt 65535 }).Count) { throw 'Use distinct non-daily loopback ports.' }
Push-Location $root
try {
  $existing = @(& docker ps -a -q --filter ('label=com.docker.compose.project=' + $ProjectName))
  if ($LASTEXITCODE -ne 0 -or $existing.Count) { throw 'Cluster fixture refuses existing project containers.' }
  $existingVolumes = @(& docker volume ls -q --filter ('label=com.docker.compose.project=' + $ProjectName))
  if ($LASTEXITCODE -ne 0 -or $existingVolumes.Count) { throw 'Cluster fixture refuses existing project volumes.' }
  foreach ($image in @($ServerImage, $MySqlImage)) {
    & docker image inspect --format '{{.Id}}' $image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Build or import the selected server/database images first; this verifier never builds or retags daily images.' }
  }
  foreach ($port in @($NodeAPort, $NodeBPort, $WebPort)) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } finally { $listener.Stop() }
  }
  function Daily-State {
    $ids = @(& docker ps -a -q --filter 'label=com.docker.compose.project=campuseats-v1')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read daily project state.' }
    if (!$ids.Count) { return '[]' }
    $raw = (& docker inspect @ids) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect daily project state.' }
    return @($raw | ConvertFrom-Json | Sort-Object Id | ForEach-Object {
      [ordered]@{ id = $_.Id; image = $_.Image; status = $_.State.Status; mounts = @($_.Mounts | Sort-Object Destination | Select-Object Type, Name, Source, Destination) }
    }) | ConvertTo-Json -Depth 8 -Compress
  }
  $dailyBefore = Daily-State
  $directory = New-SecureDirectory -Path (Join-Path $root ('.local/' + $ProjectName))
  $configurationPath = Join-Path $directory 'fixture.env'
  function New-FixtureSecret {
    return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
  }
  $configuration = [IO.File]::ReadAllText((Join-Path $root '.env.example'))
  foreach ($placeholder in @('root-password', 'app-password', 'redis-password', 'admin-signing-key', 'demo-password')) {
    $configuration = $configuration.Replace("replace-with-random-$placeholder", (New-FixtureSecret))
  }
  $overrides = [ordered]@{ SERVER_IMAGE = $ServerImage; MYSQL_IMAGE = $MySqlImage; APP_MODE = 'demo'; WEB_PORT = $WebPort; ALLOWED_ORIGINS = "http://127.0.0.1:$NodeAPort,http://127.0.0.1:$NodeBPort,http://127.0.0.1:$WebPort" }
  if ($RedisImage) { $overrides.REDIS_IMAGE = $RedisImage }
  foreach ($entry in $overrides.GetEnumerator()) {
    $name = $entry.Key
    $replacement = $entry.Value
    $pattern = '(?m)^' + [regex]::Escape($name) + '=[^\r\n]*'
    if ([regex]::IsMatch($configuration, $pattern)) {
      $configuration = [regex]::Replace($configuration, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($match) "$name=$replacement" })
    } else {
      $configuration += "`n$name=$replacement"
    }
  }
  $configuration += "`nCLUSTER_A_PORT=$NodeAPort`nCLUSTER_B_PORT=$NodeBPort`n"
  Write-SecureTextFile -Path $configurationPath -Content $configuration
  $compose = @('compose', '--env-file', $configurationPath, '-p', $ProjectName, '-f', 'compose.yaml', '-f', 'compose.cluster-test.yaml')
  & docker @compose config --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Cluster Compose configuration is invalid.' }
  $previousEnvironment = @{}
  $testEnvironment = @{ CLUSTER_PROJECT = $ProjectName; CLUSTER_CONFIG = $configurationPath; CLUSTER_NODE_A = "http://127.0.0.1:$NodeAPort/"; CLUSTER_NODE_B = "http://127.0.0.1:$NodeBPort/" }
  foreach ($entry in $testEnvironment.GetEnumerator()) {
    $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
  }
  try {
    & docker @compose up -d --no-build mysql redis server server-b
    if ($LASTEXITCODE -ne 0) { throw 'Isolated cluster startup failed.' }
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(4)
    do {
      $ready = $true
      foreach ($service in @('server', 'server-b')) {
        $id = (& docker @compose ps -q $service) -join ''
        $health = if ($id) { (& docker inspect --format '{{.State.Health.Status}}' $id) -join '' } else { 'missing' }
        if ($health -ne 'healthy') { $ready = $false }
      }
      if ($ready) { break }
      Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    if (!$ready) { throw 'Both cluster nodes did not become ready; inspect isolated container logs.' }
    $result = (& node ./scripts/check-multi-instance.mjs) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cross-node verification failed.' }
    $parsed = $result | ConvertFrom-Json
    Write-SecureTextFile -Path (Join-Path $directory 'result.json') -Content ($parsed | ConvertTo-Json -Depth 8)
    Write-Output $result
  } finally {
    & docker @compose stop -t 60 server server-b mysql redis | Out-Null
    foreach ($entry in $previousEnvironment.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
    if ((Daily-State) -cne $dailyBefore) { throw 'Daily deployment state differs from the pre-test snapshot.' }
  }
} finally { Pop-Location }
