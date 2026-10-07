param([string]$BaseUrl = 'http://localhost:18083', [switch]$VerifyRestart)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
function Api([string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}) {
    $requestOptions = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 20 }
    if ($null -ne $Body) { $requestOptions.Body = $Body | ConvertTo-Json -Depth 10; $requestOptions.ContentType = 'application/json; charset=utf-8' }
    $response = Invoke-RestMethod @requestOptions
    if ($response.code -ne 1) { throw "$Method $Path failed: $($response.msg)" }
    return $response.data
}
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message }; Write-Host "PASS $Message" }
$repoRoot = Split-Path $PSScriptRoot -Parent
$configuration = [IO.File]::ReadAllText((Join-Path $repoRoot '.env'))
if ($configuration -notmatch '(?m)^DEMO_ENABLED=true\r?$' -or $configuration -notmatch '(?m)^MOCK_PAYMENT_ENABLED=true\r?$') {
    throw 'This smoke test is for local demo mode only.'
}
Check ((Invoke-WebRequest "$BaseUrl/" -TimeoutSec 20).StatusCode -eq 200) 'admin page'
Check ((Invoke-WebRequest "$BaseUrl/doc.html" -TimeoutSec 20).StatusCode -eq 200) 'API docs page'
Check ([string](Invoke-WebRequest "$BaseUrl/demo-images/dish.svg" -TimeoutSec 20).Headers.'Content-Type' -like 'image/*') 'local demo image'
$null = Api GET '/user/shop/status'
Write-Host 'PASS shop status'
$admin = Api POST '/api/employee/login' @{username='admin';password='123456'}
$adminHeaders = @{token=$admin.token}
try {
    Invoke-RestMethod "$BaseUrl/api/dish/page?page=1&pageSize=10" -TimeoutSec 20 | Out-Null
    throw 'Missing admin token was accepted.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
    Write-Host 'PASS missing token rejected with HTTP 401'
}
$null = Api PUT '/api/shop/1' $null $adminHeaders
$page = Api GET '/api/dish/page?page=1&pageSize=10' $null $adminHeaders
Check ($page.total -gt 0) 'seed dishes and authenticated admin API'
$user = Api POST '/user/user/login' @{code='local-smoke'}
$userHeaders = @{authentication=$user.token}
$categories = @(Api GET '/user/category/list?type=1' $null $userHeaders)
$dishes = @(Api GET "/user/dish/list?categoryId=$($categories[0].id)" $null $userHeaders)
Check ($dishes.Count -gt 0) 'demo user login and browsing'
$addresses = @(Api GET '/user/addressBook/list' $null $userHeaders)
if ($addresses.Count -eq 0) {
    $null = Api POST '/user/addressBook' @{consignee='Docker Smoke';phone='13800000000';sex='1';provinceName='陕西省';cityName='西安市';districtName='长安区';detail='本地演示地址';label='学校'} $userHeaders
    $addresses = @(Api GET '/user/addressBook/list' $null $userHeaders)
}
$addressId = $addresses[0].id
$null = Api PUT '/user/addressBook/default' @{id=$addressId} $userHeaders
$null = Api DELETE '/user/shoppingCart/clean' $null $userHeaders
function SubmitSampleOrder {
    $null = Api POST '/user/shoppingCart/add' @{dishId=$dishes[0].id} $userHeaders
    $cart = @(Api GET '/user/shoppingCart/list' $null $userHeaders)
    Check ($cart.Count -eq 1) 'cart add and list'
    # The server must ignore manipulated client prices.
    return Api POST '/user/order/submit' @{addressBookId=$addressId;payMethod=1;amount=0.01;packAmount=0;tablewareNumber=1;tablewareStatus=1;deliveryStatus=1;estimatedDeliveryTime=(Get-Date).AddMinutes(30).ToString('yyyy-MM-dd HH:mm:ss');remark='Docker smoke / mock payment'} $userHeaders
}
$first = SubmitSampleOrder
$second = SubmitSampleOrder
Check ([decimal]$first.orderAmount -eq ([decimal]$dishes[0].price + 7)) 'server recalculates price'
if ($VerifyRestart) {
    Push-Location $repoRoot
    try {
        docker compose restart server
        if ($LASTEXITCODE -ne 0) { throw 'Server restart failed.' }
        docker compose up -d --wait --wait-timeout 240
        if ($LASTEXITCODE -ne 0) { throw 'Server did not recover.' }
    } finally { Pop-Location }
    $detail = Api GET "/user/order/orderDetail/$($first.id)" $null $userHeaders
    Check ($detail.number -eq $first.orderNumber) 'unpaid order survives server restart'
}
$socket = [Net.WebSockets.ClientWebSocket]::new()
$cancel = [Threading.CancellationTokenSource]::new(15000)
try {
    $wsUrl = $BaseUrl.Replace('http://','ws://').Replace('https://','wss://') + '/ws/docker-smoke'
    $null = $socket.ConnectAsync([uri]$wsUrl,$cancel.Token).GetAwaiter().GetResult()
    $null = Api PUT '/user/order/payment' @{orderNumber=$first.orderNumber;payMethod=1} $userHeaders
    $buffer = New-Object byte[] 8192
    $message = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$cancel.Token).GetAwaiter().GetResult()
    $event = [Text.Encoding]::UTF8.GetString($buffer,0,$message.Count) | ConvertFrom-Json
    Check ($event.type -eq 1 -and $event.orderId -eq $first.id) 'WebSocket notification for the requested order'
    $paid = Api GET "/user/order/orderDetail/$($first.id)" $null $userHeaders
    $unpaid = Api GET "/user/order/orderDetail/$($second.id)" $null $userHeaders
    Check ($paid.status -eq 2 -and $paid.payStatus -eq 1 -and $unpaid.status -eq 1) 'paying older order leaves newer order unpaid'
    $null = Api PUT '/user/order/payment' @{orderNumber=$first.orderNumber;payMethod=1} $userHeaders
    Write-Host 'PASS duplicate payment returns success'
} finally { $socket.Dispose(); $cancel.Dispose() }
$null = Api PUT '/api/order/confirm' @{id=$first.id} $adminHeaders
$null = Api PUT "/api/order/delivery/$($first.id)" $null $adminHeaders
$null = Api PUT "/api/order/complete/$($first.id)" $null $adminHeaders
$detail = Api GET "/user/order/orderDetail/$($first.id)" $null $userHeaders
Check ($detail.status -eq 5) 'admin confirm, delivery and completion'
$null = Api PUT "/user/order/cancel/$($second.id)" $null $userHeaders
$history = Api GET '/user/order/historyOrders?page=1&pageSize=10' $null $userHeaders
Check ($history.total -ge 2) 'user order history'
$tempImage = Join-Path ([IO.Path]::GetTempPath()) ("campuseats-smoke-" + [guid]::NewGuid().ToString('N') + '.png')
try {
    [IO.File]::WriteAllBytes($tempImage,[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l3cAAAAASUVORK5CYII='))
    $uploaded = Invoke-RestMethod -Uri "$BaseUrl/api/common/upload" -Method Post -Headers $adminHeaders -Form @{file=(Get-Item $tempImage)} -TimeoutSec 20
    Check ($uploaded.code -eq 1 -and $uploaded.data -like '/uploads/*.png') 'local image upload'
    Check ((Invoke-WebRequest "$BaseUrl$($uploaded.data)" -TimeoutSec 20).StatusCode -eq 200) 'uploaded image download'
} finally { Remove-Item -LiteralPath $tempImage -ErrorAction SilentlyContinue }
Write-Host "Smoke complete. Demo orders retained: $($first.id), $($second.id). No real payment."
