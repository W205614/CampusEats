$ErrorActionPreference = 'Stop'
$scripts = Split-Path $PSScriptRoot -Parent
$root = Split-Path $scripts -Parent
. (Join-Path $scripts 'secure-files.ps1')
$directory = New-SecureDirectory -Path (Join-Path $root ('.local/production-preflight-test-' + [guid]::NewGuid().ToString('N')))
$path = Join-Path $directory 'fixture.env'
$settings = [ordered]@{
  SERVER_IMAGE = ('registry.fixture.invalid/server@sha256:' + ('a' * 64))
  WEB_IMAGE = ('registry.fixture.invalid/web@sha256:' + ('b' * 64))
  INSTANCE_ID = 'fixture-one'
  WEB_BIND_ADDRESS = '10.0.0.10'
  CLB_CIDR = '10.0.1.0/24'
  DB_URL = 'jdbc:mysql://mysql.fixture.invalid:3306/campus?serverTimezone=Asia/Shanghai&sslMode=VERIFY_IDENTITY'
  DB_USER = 'fixture'
  MYSQL_PASSWORD = 'fixture-private-mysql-value'
  REDIS_CONTROL_HOST = 'redis-control.fixture.invalid'
  REDIS_CONTROL_PASSWORD = 'fixture-private-control-value'
  REDIS_CACHE_HOST = 'redis-cache.fixture.invalid'
  REDIS_CACHE_PASSWORD = 'fixture-private-cache-value'
  JWT_ADMIN_SECRET = 'fixture-private-jwt-signing-key-value-at-least-32'
  ALLOWED_ORIGINS = 'https://campus.fixture.invalid'
}
function Write-Fixture {
  Write-SecureTextFile -Path $path -Content (($settings.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }) -join "`n")
}
function Check-Fixture([bool]$Expected, [switch]$Live) {
  Write-Fixture
  $arguments = @('-NoProfile', '-File', (Join-Path $scripts 'production-preflight.ps1'), '-EnvFile', $path)
  if ($Live) { $arguments += '-RequireLiveEvidence' }
  $output = (& (Get-Process -Id $PID).Path @arguments 2>&1) -join "`n"
  if (($LASTEXITCODE -eq 0) -ne $Expected) { throw 'Production preflight fixture returned the wrong result.' }
  if ($output -match 'fixture-private') { throw 'Production preflight leaked a credential.' }
  if ($Expected) {
    $result = $output | ConvertFrom-Json
    if ($result.configuration -cne 'VALIDATED' -or $result.connectivity -cne 'NOT_TESTED' -or $result.livePayments -cne 'NOT_TESTED') { throw 'Configuration checks claimed live service verification.' }
  }
}
Check-Fixture $true
$originalUrl = $settings.DB_URL
$settings.DB_URL = 'jdbc:mysql://mysql.fixture.invalid:3306/campus?serverTimezone=Asia/Shanghai&xsslMode=VERIFY_IDENTITY'
Check-Fixture $false
$settings.DB_URL = $originalUrl + '&sslMode=DISABLED'
Check-Fixture $false
$settings.DB_URL = 'jdbc:mysql://(host=mysql.fixture.invalid,sslMode=DISABLED)/campus?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai'
Check-Fixture $false
$settings.DB_URL = 'jdbc:mysql://mysql.fixture.invalid/campus?SSLMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai'
Check-Fixture $false
$settings.DB_URL = $originalUrl + '&connectionTimeZone=UTC'
Check-Fixture $false
$settings.DB_URL = $originalUrl + '&serverTimezone=Asia/Shanghai'
Check-Fixture $false
$settings.DB_URL = 'jdbc:mysql://mysql.fixture.invalid:3306/campus?serverTimezone=Asia%2FShanghai&sslMode=VERIFY_IDENTITY'
Check-Fixture $true
$settings.DB_URL = $originalUrl + '&connectionTimeZone=Asia/Shanghai'
Check-Fixture $true
$settings.DB_URL = $originalUrl
$settings.REDIS_CACHE_HOST = $settings.REDIS_CONTROL_HOST
Check-Fixture $false
$settings.REDIS_CACHE_HOST = 'redis-cache.fixture.invalid'
$settings.ALLOWED_ORIGINS = '*'
Check-Fixture $false
$settings.ALLOWED_ORIGINS = 'https://campus.fixture.invalid'
Check-Fixture $false -Live
Write-Output 'Production preflight: JDBC parameter boundaries, duplicates, aliases and encoding, Redis isolation, HTTPS origin, restore evidence and secret redaction passed.'
