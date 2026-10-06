param([string]$BaseUrl = 'http://127.0.0.1:18083')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Checks = 0
function Check([bool]$Condition, [string]$Label) {
    if (-not $Condition) { throw "FAIL $Label" }
    $script:Checks++; Write-Host "PASS $Label"
}
function Request([string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}) {
    $options = @{Uri="$BaseUrl$Path"; Method=$Method; Headers=$Headers; TimeoutSec=20; SkipHttpErrorCheck=$true}
    if ($null -ne $Body) { $options.Body = $Body | ConvertTo-Json -Depth 10; $options.ContentType='application/json; charset=utf-8' }
    $response = Invoke-WebRequest @options
    $json = $null
    try { $json = $response.Content | ConvertFrom-Json } catch {}
    return @{status=[int]$response.StatusCode; json=$json}
}
function Api([string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}) {
    $r = Request $Method $Path $Body $Headers
    if ($r.status -ne 200 -or $r.json.code -ne 1) { throw "$Method $Path failed: HTTP $($r.status) $($r.json.msg)" }
    return $r.json.data
}
function Reject([string]$Method, [string]$Path, $Body, [hashtable]$Headers, [string]$Label) {
    $r = Request $Method $Path $Body $Headers
    Check ($r.status -eq 200 -and $r.json.code -eq 0 -and -not [string]::IsNullOrWhiteSpace($r.json.msg)) $Label
}
$root = Split-Path $PSScriptRoot -Parent
$config = [IO.File]::ReadAllText((Join-Path $root '.env'))
if ($config -notmatch '(?m)^DEMO_ENABLED=true\r?$' -or $config -notmatch '(?m)^MOCK_PAYMENT_ENABLED=true\r?$') { throw 'Functional test requires local demo and mock payment mode.' }
$prefix = 'UX' + (Get-Date -Format 'MMddHHmmss')
$categoryIds = @(); $dishId = $null; $setmealId = $null; $addressId = $null; $employeeId = $null; $orderIds = @()
$admin = Api POST '/api/employee/login' @{username='admin';password='123456'}
$a = @{token=$admin.token}
$user = Api POST '/user/user/login' @{code='local-ux-test'}
$u = @{authentication=$user.token}
$shop = Api GET '/user/shop/status'
$oldAddresses = @(Api GET '/user/addressBook/list' $null $u)
$oldDefault = $oldAddresses | Where-Object isDefault -eq 1 | Select-Object -First 1
$cart = @(Api GET '/user/shoppingCart/list' $null $u)
if ($cart.Count -ne 0) { throw 'Demo cart is not empty. Preserve it and finish/clear it before this fixture test.' }
try {
    Reject POST '/api/employee/login' @{username='admin';password='wrong'} @{} 'wrong password is rejected with message'
    Reject POST '/api/employee/login' @{} @{} 'empty credentials are rejected'
    Check ((Request GET '/api/dish/page?page=1&pageSize=10').status -eq 401) 'missing admin token is rejected'
    foreach ($type in @('employee','dish','setmeal')) { Reject GET "/api/$type/999999999" $null $a "missing $type has friendly response" }
    Reject GET '/api/dish/page?page=0&pageSize=0' $null $a 'invalid pagination rejected'
    Reject POST '/api/dish' @{name='';price=-1} $a 'invalid dish rejected'
    Reject POST '/user/shoppingCart/add' @{} $u 'empty cart selection rejected'
    Reject POST '/user/shoppingCart/add' @{dishId=999999999} $u 'missing dish rejected by cart'
    Reject POST '/user/shoppingCart/add' @{dishId=46;setmealId=1} $u 'ambiguous cart selection rejected'
    foreach ($type in @(1,2)) {
        $null = Api POST '/api/category' @{name="$prefix-C$type";type=$type;sort=99} $a
        $page = Api GET "/api/category/page?page=1&pageSize=100&name=$prefix" $null $a
        $categoryId = ($page.records | Where-Object name -eq "$prefix-C$type").id
        Check ($null -ne $categoryId) "create category type $type"
        $categoryIds += $categoryId
        $null = Api POST "/api/category/status/1?id=$categoryId" $null $a
    }
    $dish = @{name="$prefix-Dish";categoryId=$categoryIds[0];price=12.5;image='/demo-images/dish.svg';description='Disposable functional fixture';flavors=@(@{name='辣度';value='["微辣","中辣"]'})}
    $null = Api POST '/api/dish' $dish $a
    $page = Api GET "/api/dish/page?page=1&pageSize=100&name=$prefix" $null $a
    $dishId = ($page.records | Where-Object name -eq $dish.name).id
    $detail = Api GET "/api/dish/$dishId" $null $a
    Check ($detail.flavors.Count -eq 1 -and $detail.price -eq 12.5) 'dish details include price and flavors'
    $null = Api POST "/api/dish/status/1?id=$dishId" $null $a
    $visible = @(Api GET "/user/dish/list?categoryId=$($categoryIds[0])" $null $u)
    Check ($visible.Count -eq 1) 'new active dish visible to user'
    Reject DELETE "/api/dish?ids=$dishId" $null $a 'active dish cannot be deleted'
    Reject DELETE "/api/category?id=$($categoryIds[0])" $null $a 'referenced category cannot be deleted'
    $dish.id = $dishId; $dish.price = 15
    $null = Api PUT '/api/dish' $dish $a
    $visible = @(Api GET "/user/dish/list?categoryId=$($categoryIds[0])" $null $u)
    Check ($visible[0].price -eq 15) 'dish update invalidates warmed user cache'
    $setmeal = @{name="$prefix-Meal";categoryId=$categoryIds[1];price=14;image='/demo-images/dish.svg';description='Disposable functional fixture';setmealDishes=@(@{dishId=$dishId;name=$dish.name;price=15;copies=1})}
    $null = Api POST '/api/setmeal' $setmeal $a
    $page = Api GET "/api/setmeal/page?page=1&pageSize=100&name=$prefix" $null $a
    $setmealId = ($page.records | Where-Object name -eq $setmeal.name).id
    $null = Api POST "/api/setmeal/status/1?id=$setmealId" $null $a
    $meals = @(Api GET "/user/setmeal/list?categoryId=$($categoryIds[1])" $null $u)
    Check ($meals.Count -eq 1) 'active setmeal visible to user'
    $items = @(Api GET "/user/setmeal/dish/$setmealId" $null $u)
    Check ($items.Count -eq 1) 'setmeal contents displayed'
    $null = Api POST "/api/dish/status/0?id=$dishId" $null $a
    $visible = @(Api GET "/user/dish/list?categoryId=$($categoryIds[0])" $null $u)
    $meals = @(Api GET "/user/setmeal/list?categoryId=$($categoryIds[1])" $null $u)
    Check ($visible.Count -eq 0 -and $meals.Count -eq 0) 'stopping dish invalidates dish and related setmeal caches'
    Reject POST '/user/shoppingCart/add' @{dishId=$dishId} $u 'stopped dish cannot be added'
    Reject POST "/api/setmeal/status/1?id=$setmealId" $null $a 'setmeal with stopped dish cannot be enabled'
    $null = Api POST "/api/dish/status/1?id=$dishId" $null $a
    $null = Api POST '/user/shoppingCart/add' @{dishId=$dishId;dishFlavor='微辣'} $u
    $null = Api POST '/user/shoppingCart/add' @{dishId=$dishId;dishFlavor='微辣'} $u
    $cart = @(Api GET '/user/shoppingCart/list' $null $u)
    Check ($cart.Count -eq 1 -and $cart[0].number -eq 2) 'cart merges same flavor quantities'
    $null = Api POST '/user/shoppingCart/sub' @{dishId=$dishId;dishFlavor='微辣'} $u
    $cart = @(Api GET '/user/shoppingCart/list' $null $u)
    Check ($cart[0].number -eq 1) 'cart quantity decrease'
    $address = @{consignee=$prefix;phone='13800000000';sex='1';provinceName='陕西省';cityName='西安市';districtName='长安区';detail='本地功能测试地址';label='学校'}
    $badAddress = $address.Clone(); $badAddress.phone = '123'
    Reject POST '/user/addressBook' $badAddress $u 'invalid phone has friendly message'
    $null = Api POST '/user/addressBook' $address $u
    $addressId = (@(Api GET '/user/addressBook/list' $null $u) | Where-Object consignee -eq $prefix).id
    $null = Api PUT '/user/addressBook/default' @{id=$addressId} $u
    Check ((Api GET '/user/addressBook/default' $null $u).id -eq $addressId) 'default address can be selected'
    $address.id = $addressId; $address.detail = '本地功能测试地址-修改'
    $null = Api PUT '/user/addressBook' $address $u
    Check ((Api GET "/user/addressBook/$addressId" $null $u).detail -eq $address.detail) 'address edit persists'
    $submit = @{addressBookId=$addressId;payMethod=1;amount=0.01;packAmount=0;tablewareNumber=1;tablewareStatus=1;deliveryStatus=1;estimatedDeliveryTime=(Get-Date).AddMinutes(30).ToString('yyyy-MM-dd HH:mm:ss');remark="$prefix functional fixture / mock payment"}
    $dish.price = 16; $null = Api PUT '/api/dish' $dish $a
    Reject POST '/user/order/submit' $submit $u 'old cart price cannot be submitted after merchant price change'
    $dish.price = 15; $null = Api PUT '/api/dish' $dish $a
    $null = Api POST "/api/dish/status/0?id=$dishId" $null $a
    Reject POST '/user/order/submit' $submit $u 'existing cart cannot submit stopped dish'
    $null = Api POST "/api/dish/status/1?id=$dishId" $null $a
    $null = Api PUT '/api/shop/0' $null $a
    Reject POST '/user/order/submit' $submit $u 'closed shop cannot accept order'
    Check (@(Api GET '/user/shoppingCart/list' $null $u).Count -eq 1) 'failed submit preserves cart'
    $null = Api PUT '/api/shop/1' $null $a
    $first = Api POST '/user/order/submit' $submit $u; $orderIds += $first.id
    Check ($first.orderAmount -eq 22) 'order amount ignores manipulated client total'
    Reject PUT '/api/order/confirm' @{id=$first.id} $a 'unpaid order cannot be confirmed'
    $null = Api PUT '/api/order/cancel' @{id=$first.id;cancelReason='Functional test'} $a
    Check ((Api GET "/user/order/orderDetail/$($first.id)" $null $u).status -eq 6) 'merchant cancels unpaid order successfully'
    $null = Api PUT '/api/order/cancel' @{id=$first.id;cancelReason='Repeat'} $a
    Check ((Api GET "/user/order/orderDetail/$($first.id)" $null $u).status -eq 6) 'repeat cancel is idempotent'
    Reject PUT '/user/order/payment' @{orderNumber=$first.orderNumber;payMethod=1} $u 'cancelled order cannot be paid'
    $null = Api POST '/user/shoppingCart/add' @{dishId=$dishId} $u
    $second = Api POST '/user/order/submit' $submit $u; $orderIds += $second.id
    $null = Api PUT '/user/order/payment' @{orderNumber=$second.orderNumber;payMethod=1} $u
    $null = Api PUT '/api/order/confirm' @{id=$second.id} $a
    Reject PUT "/api/order/complete/$($second.id)" $null $a 'accepted order cannot skip delivery'
    $null = Api PUT "/api/order/delivery/$($second.id)" $null $a
    $null = Api PUT "/api/order/complete/$($second.id)" $null $a
    $completed = Api GET "/user/order/orderDetail/$($second.id)" $null $u
    Check ($completed.status -eq 5 -and $completed.payStatus -eq 1 -and $null -ne $completed.deliveryTime) 'paid order completes with delivery time'
    Reject PUT '/api/order/confirm' @{id=$second.id} $a 'completed order cannot be reopened'
    Reject PUT '/api/order/cancel' @{id=$second.id;cancelReason='Invalid transition'} $a 'completed order cannot be cancelled'
    $employee = @{username=$prefix;name='UX测试员工';phone='13800000000';sex='1';idNumber='110101199001010010'}
    $null = Api POST '/api/employee' $employee $a
    $page = Api GET '/api/employee/page?page=1&pageSize=100' $null $a
    $employeeId = ($page.records | Where-Object username -eq $prefix).id
    Check ($null -ne $employeeId -and -not ($page.records | Where-Object password)) 'employee list does not expose password hashes'
    $tester = Api POST '/api/employee/login' @{username=$prefix;password='123456'}
    $t = @{token=$tester.token}
    Reject PUT '/api/employee/editPassword' @{oldPassword='wrong';newPassword='Ux987654'} $t 'wrong old password rejected'
    Reject PUT '/api/employee/editPassword' @{oldPassword='123456';newPassword='1'} $t 'short new password rejected'
    $null = Api PUT '/api/employee/editPassword' @{oldPassword='123456';newPassword='Ux987654'} $t
    Reject POST '/api/employee/login' @{username=$prefix;password='123456'} @{} 'old password no longer works on test employee'
    $tester = Api POST '/api/employee/login' @{username=$prefix;password='Ux987654'}
    Check (-not [string]::IsNullOrWhiteSpace($tester.token)) 'new password works on test employee'
    $null = Api POST "/api/employee/status/0?id=$employeeId" $null $a
    Check ((Request GET '/api/dish/page?page=1&pageSize=10' $null @{token=$tester.token}).status -eq 401) 'disabled employee existing token rejected'
    Reject POST "/api/employee/status/0?id=$($admin.id)" $null $a 'current admin cannot disable own account'
    $today = Get-Date -Format 'yyyy-MM-dd'
    foreach ($report in @('turnoverStatistics','userStatistics','ordersStatistics','top10')) {
        $r = Api GET "/api/report/${report}?begin=$today&end=$today" $null $a
        Check ($null -ne $r) "$report returns valid data"
    }
    Reject GET '/api/report/turnoverStatistics' $null $a 'missing report dates rejected'
    Reject GET '/api/report/ordersStatistics?begin=2026-10-06&end=2026-10-05' $null $a 'reversed report dates rejected'
    Reject GET '/api/report/top10?begin=2020-01-01&end=2026-10-06' $null $a 'unbounded report date range rejected'
    $reportFile = Join-Path ([IO.Path]::GetTempPath()) "$prefix-report.xlsx"
    try {
        $download = Invoke-WebRequest "$BaseUrl/api/report/export" -Headers $a -OutFile $reportFile -PassThru -TimeoutSec 30
        $bytes = [IO.File]::ReadAllBytes($reportFile)
        Check ($bytes.Length -gt 1000 -and $bytes[0] -eq 80 -and $bytes[1] -eq 75) 'report export produces XLSX'
        Check ([string]$download.Headers.'Content-Disposition' -like '*campuseats-report-*.xlsx*') 'report export supplies descriptive filename'
    } finally { Remove-Item -LiteralPath $reportFile -ErrorAction SilentlyContinue }
    $malformed = Invoke-WebRequest "$BaseUrl/api/dish" -Method Post -Headers $a -ContentType 'application/json' -Body '{' -SkipHttpErrorCheck
    Check (($malformed.Content | ConvertFrom-Json).code -eq 0) 'malformed JSON has friendly response'
    Write-Host "Functional checks: $script:Checks passed. Fixture orders: $($orderIds -join ', ') (cleaned after verification). No real payment."
} finally {
    # Cleanup only fixture records created during this run. Restore shared demo shop/address state.
    if ($addressId) { $null = Api DELETE "/user/addressBook?id=$addressId" $null $u }
    if ($oldDefault) { $null = Api PUT '/user/addressBook/default' @{id=$oldDefault.id} $u }
    if ($setmealId) { $null = Api POST "/api/setmeal/status/0?id=$setmealId" $null $a; $null = Api DELETE "/api/setmeal?ids=$setmealId" $null $a }
    if ($dishId) {
        $null = Api DELETE '/user/shoppingCart/clean' $null $u
        $null = Api POST "/api/dish/status/0?id=$dishId" $null $a
        $null = Api DELETE "/api/dish?ids=$dishId" $null $a
    }
    foreach ($id in $categoryIds) { $null = Api DELETE "/api/category?id=$id" $null $a }
    $null = Api PUT "/api/shop/$shop" $null $a
    if ($employeeId -or $orderIds.Count -gt 0) {
        Push-Location $root
        try {
            # Prefix is generated above from digits only; no user input enters this SQL.
            $cleanupSql = 'START TRANSACTION;'
            if ($employeeId) { $cleanupSql += "DELETE FROM employee WHERE id=$employeeId AND username='$prefix';" }
            if ($orderIds.Count -gt 0) {
                $ids = $orderIds -join ','
                $predicate = "o.id IN ($ids) AND o.remark='$prefix functional fixture / mock payment'"
                $cleanupSql += "DELETE od FROM order_detail od INNER JOIN orders o ON od.order_id=o.id WHERE $predicate; DELETE o FROM orders o WHERE $predicate;"
            }
            $cleanupSql += 'COMMIT;'
            $cleanupSql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u campus sky_take_out'
            if ($LASTEXITCODE -ne 0) { throw 'Fixture database cleanup failed.' }
        } finally { Pop-Location }
    }
}
