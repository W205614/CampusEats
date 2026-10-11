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
$menu=Invoke-RestMethod "$BaseUrl/api/v1/user/menu/items?categoryId=1&type=DISH"
if($menu.code -ne 'OK' -or $menu.data.Count -lt 1){throw 'Menu unavailable'}
foreach($item in $menu.data){if($item.id -isnot [string] -or $item.price -isnot [string]){throw 'Wire types differ from API contract'}}
$blocked=Invoke-WebRequest "$BaseUrl/api/v1/admin/orders" -SkipHttpErrorCheck
if($blocked.StatusCode -ne 401){throw 'Unauthenticated admin access was not rejected'}
Write-Host 'Root redirect, readiness, source-built menu, string IDs/prices and authentication checks passed.'
