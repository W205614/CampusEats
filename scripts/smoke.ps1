param([string]$BaseUrl='http://localhost:18083')
$ErrorActionPreference='Stop'
$redirect=Invoke-WebRequest "$BaseUrl/" -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
$origin=[uri]($BaseUrl.TrimEnd('/')+'/')
$target=[uri]::new($origin,[string]$redirect.Headers.Location)
if($redirect.StatusCode -ne 302 -or $target.AbsoluteUri -ne ($BaseUrl.TrimEnd('/')+'/admin/')){
 throw 'Root redirect lost its origin or port'
}
$ready=Invoke-RestMethod "$BaseUrl/actuator/health/readiness"
if($ready.status -ne 'UP'){throw 'Database readiness is not UP'}
$stores=Invoke-RestMethod "$BaseUrl/api/v1/user/stores"
if($stores.code -ne 'OK' -or $stores.data.Count -lt 1){throw 'Campus/store discovery unavailable'}
$storeId=$stores.data[0].id
$menu=Invoke-RestMethod "$BaseUrl/api/v1/user/stores/$storeId/products"
if($menu.code -ne 'OK' -or $menu.data.Count -lt 1){throw 'Menu unavailable'}
foreach($item in $menu.data){if($item.id -isnot [string] -or $item.price -isnot [string]){throw 'Wire types differ from API contract'}}
$blocked=Invoke-WebRequest "$BaseUrl/api/v1/merchant/stores/$storeId/orders" -SkipHttpErrorCheck
if($blocked.StatusCode -ne 401){throw 'Unauthenticated admin access was not rejected'}
$retired=Invoke-WebRequest "$BaseUrl/api/v1/user/shop" -SkipHttpErrorCheck
if($retired.StatusCode -ne 410){throw 'Legacy unscoped entrypoint is still available'}
Write-Host 'Root redirect, readiness, scoped menu, string IDs/prices, authentication and retired legacy entrypoint checks passed.'
