const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const app = fs.readFileSync(path.resolve(__dirname, '../deploy/admin/js/app.d0aa4eb3.js'), 'utf8');
const start = app.indexOf('(function(e){if(!o.a.isCancel(e))');
const end = app.indexOf('));t["a"]=p', start);
assert.ok(start > 0 && end > start, 'compiled error handler found');
const events = [], routes = [];
const handler = vm.runInNewContext(app.slice(start, end) + ')', {
  o: { a: { isCancel: e => !!e.cancelled } }, b: { a: { push: route => routes.push(route) } },
  window: { dispatchEvent: event => events.push(event) },
  CustomEvent: function(type, options) { this.type = type; this.detail = options.detail; },
  u: () => 'key', f: () => {}, Promise
});
(async function() {
  const network = { config: { url: '/api/dish/page' } };
  await assert.rejects(handler(network), e => e.message.includes('网络或服务'));
  assert.equal(events[0].type, 'campus-api-error'); console.log('PASS network failure supplies actionable feedback');
  await assert.rejects(handler({ response: { status: 401 }, config: { url: '/api/dish/page' } }), e => e.message.includes('登录已失效'));
  assert.equal(routes[0], '/login'); console.log('PASS expired login prompts and redirects');
  const before = events.length;
  await assert.rejects(handler({ cancelled: true })); assert.equal(events.length, before); console.log('PASS cancelled duplicate request does not show false error');
  await assert.rejects(handler({}), e => e.message.includes('网络或服务')); console.log('PASS failure without request configuration avoids exception');
  assert.ok(app.includes('timeout:15000')); console.log('PASS admin request has bounded timeout');
  assert.ok(fs.readFileSync(path.resolve(__dirname, '../deploy/admin/js/shopTable.fe534d8f.js'), 'utf8').includes('导出近30日数据'));
  console.log('PASS statistics bundle labels export range');
  console.log('Administrator request checks: 6 passed.');
})().catch(err => { console.error(err); process.exitCode = 1; });
