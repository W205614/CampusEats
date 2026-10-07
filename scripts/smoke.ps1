param([string]$BaseUrl='http://localhost:18083')
$ErrorActionPreference='Stop'
$ready=Invoke-RestMethod "$BaseUrl/actuator/health/readiness"
if($ready.status -ne 'UP'){throw 'Database readiness is not UP'}
$menu=Invoke-RestMethod "$BaseUrl/api/v1/user/menu/items?categoryId=1&type=DISH"
if($menu.code -ne 'OK' -or $menu.data.Count -lt 1){throw 'Menu unavailable'}
foreach($item in $menu.data){if($item.id -isnot [string] -or $item.price -isnot [string]){throw 'Wire types differ from API contract'}}
$blocked=Invoke-WebRequest "$BaseUrl/api/v1/admin/orders" -SkipHttpErrorCheck
if($blocked.StatusCode -ne 401){throw 'Unauthenticated admin access was not rejected'}
Write-Host 'Readiness, source-built menu, string IDs/prices and authentication checks passed.'
