const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
let count = 0;
function check(name, action) { action(); console.log('PASS ' + name); count++; }
(async function() {
  let sent; const toasts = []; const state = { token: 'test', shopPhone: '' };
  const uni = { request(o) { sent = o; }, showToast(o) { toasts.push(o.title); }, redirectTo() {} };
  const context = vm.createContext({ uni, _env: { baseUrl: 'http://localhost:18083' },
    _store: { default: { state, commit(name, value) { if (name === 'setToken') state.token = value; } } } });
  // Execute the code actually installed in the compiled module.
  const vendor = fs.readFileSync(path.join(root, 'mp-weixin/common/vendor.js'), 'utf8');
  const start = vendor.indexOf('function normalizeLocalImages(');
  vm.runInContext(vendor.slice(start, vendor.indexOf('/* WEBPACK VAR INJECTION */}.call(this,', start)), context);
  let promise = context.request({ url: '/user/dish/list', params: { categoryId: 1 } });
  sent.success({ statusCode: 200, data: { code: 1, data: [{ image: '/demo-images/1.png' }] } });
  const result = await promise;
  check('successful request normalizes nested image URLs', () => assert.equal(result.data[0].image, 'http://localhost:18083/demo-images/1.png'));
  check('request uses timeout and authenticated header', () => { assert.equal(sent.timeout, 15000); assert.equal(sent.header.authentication, 'test'); });
  for (const [name, response, msg] of [
    ['business failure gives actionable message', { statusCode: 200, data: { code: 0, msg: '菜品已停售' } }, '菜品已停售'],
    ['HTTP 500 gives safe message', { statusCode: 500, data: '<html>error</html>' }, '服务暂时不可用'],
    ['expired login clears token and prompts login', { statusCode: 401 }, '登录已失效']
  ]) {
    promise = context.request({ url: '/test' });
    const rejected = assert.rejects(promise, e => e.msg.includes(msg));
    sent.success(response); await rejected;
    check(name, () => assert.ok(toasts.at(-1).includes(msg)));
  }
  check('expired token removed', () => assert.equal(state.token, ''));
  for (const [errMsg, msg] of [['request:fail timeout', '请求超时'], ['request:fail network', '网络连接失败']]) {
    promise = context.request({ url: '/test' }); const rejected = assert.rejects(promise, e => e.msg.includes(msg));
    sent.fail({ errMsg }); await rejected;
    check(msg + ' is visible', () => assert.ok(toasts.at(-1).includes(msg)));
  }
  let submits = 0;
  context._api = { submitOrderSubmit() { submits++; return Promise.reject(new Error('test failure')); } };
  context._index = { presentFormat() { return '2026-10-06 12:00:00'; } };
  context._defineProperty = (o, k, v) => { o[k] = v; return o; };
  context.console = { log() {} };
  const submitStart = vendor.indexOf('function payOrderHandle()');
  const submit = vm.runInContext('(' + vendor.slice(submitStart, vendor.indexOf('    // 拨打电话', submitStart)).trim().replace(/,$/, '') + ')', context);
  const component = { isHandlePy: false, address: null };
  submit.call(component);
  check('missing address restores submit button', () => assert.equal(component.isHandlePy, false));
  component.address = {}; component.arrivalTime = '立即派送'; component.isHandlePy = true;
  submit.call(component);
  check('repeated submit while pending is suppressed', () => assert.equal(submits, 0));
  component.isHandlePy = false; submit.call(component); await new Promise(setImmediate);
  check('failed submit restores button for retry', () => assert.equal(component.isHandlePy, false));
  let redirects = 0; let paymentCalls = 0; let resolvePayment;
  uni.redirectTo = () => { redirects++; };
  context.clearTimeout = () => {};
  context._api = { paymentOrder() { paymentCalls++; return new Promise(r => { resolvePayment = r; }); } };
  const pay = fs.readFileSync(path.join(root, 'mp-weixin/pages/pay/index.js'), 'utf8');
  const payStart = pay.indexOf('function handleSave()');
  const handleSave = vm.runInContext('(' + pay.slice(payStart, pay.indexOf('    // // 订单倒计时', payStart)).trim().replace(/,$/, '') + ')', context);
  const payment = { paymentPending: false, timeout: false, orderId: 1, orderDataInfo: { orderNumber: 'test' }, activeRadio: 0 };
  handleSave.call(payment); handleSave.call(payment);
  check('repeated payment while pending is suppressed', () => assert.equal(paymentCalls, 1));
  resolvePayment({ code: 1 }); await new Promise(setImmediate);
  check('successful payment redirects once and restores button', () => { assert.equal(redirects, 1); assert.equal(payment.paymentPending, false); });
  context._api.paymentOrder = () => Promise.reject(new Error('test failure'));
  handleSave.call(payment); await new Promise(setImmediate);
  check('failed payment restores button without redirect', () => { assert.equal(redirects, 1); assert.equal(payment.paymentPending, false); });
  check('payment page states simulation explicitly', () => assert.ok(pay.includes('模拟支付（不涉及资金）')));
  const callStart = vendor.indexOf('function call()');
  const call = vm.runInContext('(' + vendor.slice(callStart, vendor.indexOf('    // // 联系商家', callStart)).trim().replace(/,$/, '') + ')', context);
  let dialed = ''; uni.makePhoneCall = o => { dialed = o.phoneNumber; };
  call.call({ $store: { state } });
  check('missing merchant phone prompts rather than dialing sample number', () => { assert.equal(dialed, ''); assert.equal(toasts.at(-1), '商家暂未设置联系电话'); });
  state.shopPhone = '13800000000'; call.call({ $store: { state } });
  check('configured merchant phone is used', () => assert.equal(dialed, '13800000000'));
  console.log('Mini-program logic checks: ' + count + ' passed. WeChat rendering and device APIs still require device testing.');
})().catch(err => { console.error(err); process.exitCode = 1; });
