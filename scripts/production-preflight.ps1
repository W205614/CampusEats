param(
  [string]$EnvFile = '.env.production',
  [string]$RestoreEvidence,
  [switch]$RequireLiveEvidence
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$path = [IO.Path]::GetFullPath($EnvFile, $root)
if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Production environment file does not exist.' }
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($path)) {
  if ($line -match '^\s*(#|$)') { continue }
  if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Environment file must contain unquoted NAME=value lines.' }
  if ($settings.ContainsKey($Matches[1])) { throw ('Duplicate setting: ' + $Matches[1]) }
  $settings[$Matches[1]] = $Matches[2]
}
$required = @('SERVER_IMAGE', 'WEB_IMAGE', 'INSTANCE_ID', 'WEB_BIND_ADDRESS', 'CLB_CIDR', 'DB_URL', 'DB_USER', 'MYSQL_PASSWORD', 'REDIS_CONTROL_HOST', 'REDIS_CONTROL_PASSWORD', 'REDIS_CACHE_HOST', 'REDIS_CACHE_PASSWORD', 'JWT_ADMIN_SECRET', 'ALLOWED_ORIGINS')
foreach ($name in $required) {
  if (![string]$settings[$name] -or $settings[$name] -match '(?i)replace-|example\.com|<|>') { throw ('Missing or placeholder setting: ' + $name) }
}
foreach ($name in @('SERVER_IMAGE', 'WEB_IMAGE')) {
  if ($settings[$name] -notmatch '@sha256:[0-9a-f]{64}$') { throw ($name + ' must reference an immutable verified digest.') }
}
if ($settings.INSTANCE_ID -notmatch '^[a-zA-Z0-9_.-]{1,80}$') { throw 'INSTANCE_ID format is invalid.' }
if ($settings.JWT_ADMIN_SECRET.Length -lt 32) { throw 'JWT signing key must have at least 32 characters.' }
$jdbcParameters = @{}
if ($settings.DB_URL -cnotmatch '^jdbc:mysql://([^/]+)/') { throw 'DB_URL must use a single MySQL database endpoint.' }
$authority = [Net.WebUtility]::UrlDecode($Matches[1])
if ($authority -notmatch '^(?:[a-zA-Z0-9.-]+|\[[0-9a-fA-F:]+\])(?::[0-9]{1,5})?$') { throw 'DB_URL host-specific properties cannot override global TLS settings.' }
$queryIndex = $settings.DB_URL.IndexOf('?')
if ($queryIndex -ge 0) {
  foreach ($parameter in $settings.DB_URL.Substring($queryIndex + 1).Split('&')) {
    $pair = $parameter.Split('=', 2)
    if ($pair[0] -match '%(?![0-9a-fA-F]{2})') { throw 'DB_URL parameter encoding is invalid.' }
    $decodedName = [Net.WebUtility]::UrlDecode($pair[0])
    $name = $decodedName.ToLowerInvariant()
    if ($name -notin @('sslmode', 'servertimezone', 'connectiontimezone')) { continue }
    $canonical = @{ sslmode='sslMode'; servertimezone='serverTimezone'; connectiontimezone='connectionTimeZone' }[$name]
    if ($decodedName -cne $canonical) { throw 'DB_URL critical property names must use canonical case.' }
    $value = if ($pair.Count -eq 2) { $pair[1] } else { '' }
    if ($value -match '%(?![0-9a-fA-F]{2})') { throw 'DB_URL parameter encoding is invalid.' }
    if (!$jdbcParameters.ContainsKey($name)) { $jdbcParameters[$name] = [Collections.Generic.List[string]]::new() }
    $jdbcParameters[$name].Add([Net.WebUtility]::UrlDecode($value))
  }
}
$ssl = $jdbcParameters['sslmode']
if ($null -eq $ssl -or $ssl.Count -ne 1 -or $ssl[0] -cne 'VERIFY_IDENTITY') { throw 'DB_URL must declare sslMode=VERIFY_IDENTITY exactly once.' }
$zoneCount = 0
foreach ($name in @('servertimezone', 'connectiontimezone')) {
  if (!$jdbcParameters.ContainsKey($name)) { continue }
  $values = $jdbcParameters[$name]
  if ($values.Count -ne 1 -or $values[0] -cne 'Asia/Shanghai') { throw 'DB_URL connection time zones must consistently be Asia/Shanghai.' }
  $zoneCount++
}
if ($zoneCount -eq 0) { throw 'DB_URL must declare the Asia/Shanghai connection time zone.' }
$controlPort = if ($settings.REDIS_CONTROL_PORT) { $settings.REDIS_CONTROL_PORT } else { '6379' }
$cachePort = if ($settings.REDIS_CACHE_PORT) { $settings.REDIS_CACHE_PORT } else { '6379' }
if (($settings.REDIS_CONTROL_HOST + ':' + $controlPort) -eq ($settings.REDIS_CACHE_HOST + ':' + $cachePort)) { throw 'Control Redis and cache Redis must be different instances.' }
foreach ($name in @('REDIS_CONTROL_SSL', 'REDIS_CACHE_SSL')) {
  if ($settings.ContainsKey($name) -and $settings[$name] -cne 'true') { throw ('Production TLS must be enabled: ' + $name) }
}
foreach ($origin in $settings.ALLOWED_ORIGINS.Split(',')) {
  if ($origin -notmatch '^https://[a-zA-Z0-9.-]+(?::[0-9]+)?$') { throw 'Production origins must be explicit HTTPS origins.' }
}
foreach ($name in @('CLB_CIDR', 'WEB_BIND_ADDRESS', 'MONITOR_BIND_ADDRESS')) {
  if ($settings.ContainsKey($name) -and $settings[$name] -match '^(0\.0\.0\.0(?:/0)?|::(?:/0)?)$') { throw ('Do not expose this setting to the entire Internet: ' + $name) }
}
$restore = 'NOT_VERIFIED'
if ($RestoreEvidence) {
  $evidence = Get-Content -LiteralPath ([IO.Path]::GetFullPath($RestoreEvidence, $root)) -Raw | ConvertFrom-Json
  if ($evidence.status -cne 'PASSED' -or !$evidence.isolated -or !$evidence.databaseChecked -or !$evidence.objectsChecked -or !$evidence.channelReconciled) { throw 'Restore evidence must record isolated database, objects and channel reconciliation checks.' }
  $verified = [DateTimeOffset]::Parse($evidence.verifiedAt)
  if ($verified -gt [DateTimeOffset]::UtcNow -or ([DateTimeOffset]::UtcNow - $verified).TotalHours -gt 48) { throw 'Restore evidence is from the future or older than 48 hours.' }
  $restore = 'REPORTED_PASSED'
}
if ($RequireLiveEvidence -and $restore -ne 'REPORTED_PASSED') { throw 'A recent independently verified production restore report is required.' }
[pscustomobject]@{
  scope = 'INFRASTRUCTURE_CONFIGURATION_ONLY'
  configuration = 'VALIDATED'
  connectivity = 'NOT_TESTED'
  livePayments = 'NOT_TESTED'
  restoreEvidence = $restore
  note = 'This read-only check neither contacts cloud services nor proves the supplied report; verify its source independently.'
} | ConvertTo-Json
