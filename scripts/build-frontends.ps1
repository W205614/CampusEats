$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
 foreach ($client in @('admin','client')) {
  npm ci --prefix "frontend/$client" --include=optional
  if($LASTEXITCODE -ne 0){throw "npm ci failed: $client"}
 }
 npm run typecheck --prefix frontend/client
 if($LASTEXITCODE -ne 0){throw 'Client type check failed'}
 npm run build --prefix frontend/admin
 if($LASTEXITCODE -ne 0){throw 'Admin build failed'}
 npm run build:h5 --prefix frontend/client
 if($LASTEXITCODE -ne 0){throw 'H5 build failed'}
 npm run build:mp-weixin --prefix frontend/client
 if($LASTEXITCODE -ne 0){throw 'Mini-program build failed'}
} finally { Pop-Location }
