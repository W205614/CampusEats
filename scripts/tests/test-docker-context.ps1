# Inspect an actual BuildKit COPY result using synthetic files and the project's ignore rules.
$ErrorActionPreference = 'Stop'
$scriptsDir = Split-Path $PSScriptRoot -Parent
$root = Split-Path $scriptsDir -Parent
. (Join-Path $scriptsDir 'secure-files.ps1')
$fixture = Join-Path $root ('.local/docker-context-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null
try {
  Copy-Item -LiteralPath (Join-Path $root '.dockerignore') -Destination $fixture
  $excluded = @('.env', '.env.local', '.local/private.txt', '.idea/dataSources.xml', '.git/config',
    'sky-server/.local/uploads/private.txt', 'sky-server/.env', 'sky-server/.env.test',
    'sky-server/target/app.jar', 'frontend/admin/node_modules/package/index.js',
    'frontend/client/dist/app.js', 'deploy/uploads/private.txt', 'deploy/admin/index.html')
  $included = @('README.md', 'LICENSE', '.env.example', 'sky-server/.env.example', 'sky-server/src/main/example.java')
  foreach ($relative in @($excluded) + @($included)) {
    $path = Join-Path $fixture $relative
    [void][IO.Directory]::CreateDirectory((Split-Path $path -Parent))
    [IO.File]::WriteAllText($path, 'Synthetic context fixture; contains no credentials.')
  }
  [IO.File]::WriteAllText((Join-Path $fixture 'Dockerfile'), "FROM scratch`nCOPY . /`n")
  $archive = Join-Path $fixture 'context.tar'
  # The archive lives outside its own build context.
  $context = Join-Path $fixture 'input'
  New-Item -ItemType Directory -Path $context | Out-Null
  Get-ChildItem -LiteralPath $fixture -Force | Where-Object Name -NE 'input' | Move-Item -Destination $context
  & docker build --network=none --progress=plain --output "type=tar,dest=$archive" $context
  if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the real Docker build context.' }
  $entries = @(& tar -tf $archive | ForEach-Object { $_ -replace '^\./', '' })
  if ($LASTEXITCODE -ne 0) { throw 'Cannot read context archive.' }
  foreach ($relative in $excluded) {
    if ($entries -contains $relative) { throw "Private or generated path entered build context: $relative" }
  }
  foreach ($relative in $included) {
    if ($entries -notcontains $relative) { throw "Required source/example missing from build context: $relative" }
  }
  Write-Host 'Real Docker context excludes root/nested secrets, uploads, IDE files and build output; source, licenses and examples remain.'
} finally {
  Move-ProjectFileToRecycleBin -Path $fixture
}
