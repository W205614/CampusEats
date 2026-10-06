param(
    [string]$CourseRoot = 'D:\Java课件\黑马Java课件\3.黑马程序员Java项目《苍穹外卖》企业级开发实战\资料',
    [int]$Port = 18083
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$adminSource = Join-Path $CourseRoot 'day01\前端运行环境\nginx-1.20.2\html\sky'
$sqlSource = Join-Path $CourseRoot 'day01\数据库\sky.sql'
$mpSource = Join-Path $CourseRoot 'day06\微信小程序代码\mp-weixin'
foreach ($source in @($adminSource, $sqlSource, $mpSource)) {
    if (-not (Test-Path -LiteralPath $source)) { throw "Course asset missing: $source" }
}
New-Item -ItemType Directory -Force -Path (Join-Path $repoRoot 'deploy\admin'), (Join-Path $repoRoot 'deploy\mysql'), (Join-Path $repoRoot 'deploy\images') | Out-Null
1..22 | ForEach-Object {
    Copy-Item -LiteralPath (Join-Path $CourseRoot "day03\图片资源\$_.png") -Destination (Join-Path $repoRoot "deploy\images\$_.png") -Force
}
Copy-Item -Path (Join-Path $adminSource '*') -Destination (Join-Path $repoRoot 'deploy\admin') -Recurse -Force
if (-not (Test-Path (Join-Path $repoRoot 'deploy\mysql\01-schema.sql'))) {
    Copy-Item -LiteralPath $sqlSource -Destination (Join-Path $repoRoot 'deploy\mysql\01-schema.sql')
}
if (-not (Test-Path (Join-Path $repoRoot 'mp-weixin\project.config.json'))) {
    Copy-Item -LiteralPath $mpSource -Destination $repoRoot -Recurse
}
$utf8 = [Text.UTF8Encoding]::new($false)
$adminJs = Join-Path $repoRoot 'deploy\admin\js\app.d0aa4eb3.js'
$content = [IO.File]::ReadAllText($adminJs).Replace('"ws://localhost/ws/"', '(window.location.protocol==="https:"?"wss://":"ws://")+window.location.host+"/ws/"')
$content = $content.Replace('数据导出', '导出近30日数据')
[IO.File]::WriteAllText($adminJs, $content, $utf8)
$index = Join-Path $repoRoot 'deploy\admin\index.html'
[IO.File]::WriteAllText($index, [IO.File]::ReadAllText($index).Replace('<title>瑞吉外卖</title>', '<title>CampusEats 管理端</title>'), $utf8)
$vendor = Join-Path $repoRoot 'mp-weixin\common\vendor.js'
$content = [IO.File]::ReadAllText($vendor)
$content = [regex]::Replace($content, "var baseUrl = 'http://localhost:\d+';", "var baseUrl = 'http://localhost:$Port';")
if (-not $content.Contains('function normalizeLocalImages(')) {
    $content = $content.Replace('function request(_ref)', @'
function normalizeLocalImages(value) {
  if (!value || typeof value !== 'object') return;
  Object.keys(value).forEach(function(key) {
    if (key === 'image' && typeof value[key] === 'string' && value[key].charAt(0) === '/') {
      value[key] = _env.baseUrl + value[key];
    } else if (value[key] && typeof value[key] === 'object') {
      normalizeLocalImages(value[key]);
    }
  });
}
function request(_ref)
'@)
    $content = $content.Replace('resolve(res.data);', 'normalizeLocalImages(res.data); resolve(res.data);')
}
$content = $content.Replace("_this9.phoneData = '400-618-4000';", "_this9.phoneData = res.data || '';")
$content = $content.Replace("_this9.setShopPhone('400-618-4000');", "_this9.setShopPhone(res.data || '');")
[IO.File]::WriteAllText($vendor, $content, $utf8)
node (Join-Path $PSScriptRoot 'patch-miniprogram.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Mini-program patch failed.' }
node (Join-Path $PSScriptRoot 'patch-admin.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Administrator patch failed.' }
Write-Host "Prepared admin and mini-program assets for http://localhost:$Port"
