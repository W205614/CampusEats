const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const appFile = path.join(root, 'deploy/admin/js/app.d0aa4eb3.js');
let app = fs.readFileSync(appFile, 'utf8');
app = app.replace('timeout:6e5', 'timeout:15000');
app = app.replace('数据导出', '导出近30日数据');
const oldHandler = '(function(e){if(e&&e.response)switch(e.response.status){case 401:b["a"].push("/login");break;case 405:e.message="请求错误"}e.config.url=e.config.url.replace("/api","");var t=u(e.config);return f(t),Promise.reject(e)})';
const newHandler = '(function(e){if(!o.a.isCancel(e)){var message=e&&e.response&&e.response.status===401?"登录已失效，请重新登录":"网络或服务暂时不可用，请稍后重试";window.dispatchEvent(new CustomEvent("campus-api-error",{detail:message}));if(e&&e.response&&e.response.status===401)b["a"].push("/login")}if(e.config){e.config.url=e.config.url.replace("/api","");f(u(e.config))}return Promise.reject(e)})';
const finalHandler = newHandler.replace('window.dispatchEvent(', 'e.message=message;window.dispatchEvent(');
if (app.includes(newHandler)) app = app.replace(newHandler, finalHandler);
if (!app.includes(finalHandler)) {
  if (!app.includes(oldHandler)) throw new Error('Expected administrator request handler missing; check course asset version.');
  app = app.replace(oldHandler, finalHandler);
}
const tableFile = path.join(root, 'deploy/admin/js/shopTable.fe534d8f.js');
const table = fs.readFileSync(tableFile, 'utf8').replace('数据导出', '导出近30日数据')
  .replace('(100*t.orderdata.orderCompletionRate).toFixed(1)', '(100*(t.orderdata.orderCompletionRate||0)).toFixed(1)')
  .replace('t._s(t.orderdata.validOrderCount)', 't._s(t.orderdata.validOrderCount||0)')
  .replace('t._s(t.orderdata.totalOrderCount)', 't._s(t.orderdata.totalOrderCount||0)');
fs.writeFileSync(tableFile, table, 'utf8');
const tableVersion = crypto.createHash('sha256').update(table).digest('hex').slice(0, 8);
fs.writeFileSync(path.join(root, 'deploy/admin/js/shopTable.' + tableVersion + '.js'), table, 'utf8');
app = app.replace(/login:"90288d75",shopTable:"[a-f0-9]+"/, 'login:"90288d75",shopTable:"' + tableVersion + '"');
fs.writeFileSync(appFile, app, 'utf8');
const indexFile = path.join(root, 'deploy/admin/index.html');
let html = fs.readFileSync(indexFile, 'utf8');
const appVersion = crypto.createHash('sha256').update(app).digest('hex').slice(0, 8);
html = html.replace(/js\/app\.d0aa4eb3\.js(?:\?v=[a-f0-9]+)?/g, 'js/app.d0aa4eb3.js?v=' + appVersion);
if (!html.includes('/campus-feedback.js')) html = html.replace('</head>', '<script defer src="/campus-feedback.js"></script></head>');
fs.writeFileSync(indexFile, html, 'utf8');
fs.copyFileSync(path.join(root, 'deploy/admin-feedback.js'), path.join(root, 'deploy/admin/campus-feedback.js'));
console.log('Administrator timeout, transport error feedback and report label patched.');
