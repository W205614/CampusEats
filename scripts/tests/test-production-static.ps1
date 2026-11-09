param([string]$WebImage = 'campuseats-web:v1')
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $root 'scripts/secure-files.ps1')
$fixture = Join-Path $root ('.local/production-static-' + [guid]::NewGuid().ToString('N'))
$name = 'campuseats-static-test-' + [guid]::NewGuid().ToString('N')
New-Item -ItemType Directory -Path $fixture | Out-Null
$started = $false
try {
  # Trust only this isolated fixture's bridge; no production configuration is changed.
  $config = (Get-Content (Join-Path $root 'deploy/production/nginx.conf.template') -Raw).Replace('${CLB_CIDR}', '0.0.0.0/0')
  [IO.File]::WriteAllText((Join-Path $fixture 'default.conf'), $config)
  & docker run --detach --rm --name $name --add-host server:127.0.0.1 -p '127.0.0.1::80' --mount "type=bind,source=$fixture/default.conf,target=/etc/nginx/conf.d/default.conf,readonly" $WebImage | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated static-route fixture.' }
  $started = $true
  $binding = (& docker port $name '80/tcp') -join ''
  if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:[0-9]+$') { throw 'Cannot resolve isolated fixture port.' }
  $origin = 'http://' + $binding
  $ready = $false
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    try {
      $ready = (Invoke-WebRequest ($origin + '/LICENSE') -TimeoutSec 1).StatusCode -eq 200
      break
    } catch { Start-Sleep -Milliseconds 250 }
  }
  if (!$ready) { throw 'Isolated Nginx did not become ready.' }
  foreach ($path in @('/LICENSE', '/admin/THIRD_PARTY_LICENSES.md', '/app/THIRD_PARTY_LICENSES.md', '/demo-images/dish.svg')) {
    $response = Invoke-WebRequest ($origin + $path) -TimeoutSec 10
    if ($response.StatusCode -ne 200) { throw "Static route failed: $path" }
    if ($path -eq '/demo-images/dish.svg') {
      if ([string]$response.Content -cne (Get-Content (Join-Path $root 'deploy/dish.svg') -Raw)) { throw 'Demo image content differs from source.' }
    } elseif ($response.Headers['Content-Type'] -notmatch 'text/plain') { throw "License route is not plain text: $path" }
  }
  foreach ($path in @('/demo-images/missing.svg', '/actuator/health', '/v3/api-docs')) {
    $response = Invoke-WebRequest ($origin + $path) -SkipHttpErrorCheck -TimeoutSec 10
    if ($response.StatusCode -ne 404) { throw "Missing or private path is exposed: $path" }
  }
  Write-Host 'Production template serves image and license files; missing images and private endpoints return 404.'
} finally {
  if ($started) {
    & docker stop $name | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot stop isolated fixture: $name" }
  }
  Move-ProjectFileToRecycleBin -Path $fixture
}
