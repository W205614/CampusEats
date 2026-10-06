const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const file = path.join(root, 'mp-weixin/common/vendor.js');
let vendor = fs.readFileSync(file, 'utf8');
vendor = vendor.replace(/^\/\/ Included in the compiled uni-app request module by patch-miniprogram\.cjs\.\r?\n/gm, '');
const start = vendor.indexOf('function normalizeLocalImages(');
const requestStart = start >= 0 ? start : vendor.indexOf('function request(_ref)');
const end = vendor.indexOf('/* WEBPACK VAR INJECTION */}.call(this,', requestStart);
if (requestStart < 0 || end < requestStart) throw new Error('Request module not found; check course asset version.');
vendor = vendor.slice(0, requestStart) + fs.readFileSync(path.join(__dirname, 'miniprogram-request.js'), 'utf8') + '\n' + vendor.slice(end);
function replaceOnce(before, after) {
  if (vendor.includes(after)) return;
  if (!vendor.includes(before)) throw new Error('Expected mini-program code missing: ' + before.slice(0, 70));
  vendor = vendor.replace(before, after);
}
replaceOnce('this.isHandlePy = true;', 'if (this.isHandlePy) return;\n      this.isHandlePy = true;');
replaceOnce("if (!this.address) {", "if (!this.address) {\n        this.isHandlePy = false;");
const submitStart = vendor.indexOf('(0, _api.submitOrderSubmit)(params).then(');
const submitEnd = vendor.indexOf('    // 拨打电话', submitStart);
let submit = vendor.slice(submitStart, submitEnd);
if (!submit.includes('.catch(')) {
  submit = submit.replace('if (res.code === 1) {', '_this7.isHandlePy = false;\n        if (res.code === 1) {')
    .replace('      });', '      }).catch(function() { _this7.isHandlePy = false; });');
  vendor = vendor.slice(0, submitStart) + submit + vendor.slice(submitEnd);
}
vendor = vendor.replace('var phone = this.shopPhone;', 'var phone = this.$store.state.shopPhone;');
replaceOnce("uni.makePhoneCall({\r\n        phoneNumber: '114' //仅为示例\r\n      });",
  "var phone = this.$store.state.shopPhone;\n      if (!phone) { uni.showToast({ title: '商家暂未设置联系电话', icon: 'none' }); return; }\n      uni.makePhoneCall({ phoneNumber: phone });");
replaceOnce('var call = function call(val) {', "var call = function call(val) {\n  if (!val) { uni.showToast({ title: '商家暂未设置联系电话', icon: 'none' }); return; }");
fs.writeFileSync(file, vendor, 'utf8');
const payFile = path.join(root, 'mp-weixin/pages/pay/index.js');
let pay = fs.readFileSync(payFile, 'utf8');
pay = pay.replace("payMethodList: ['微信支付']", "payMethodList: ['模拟支付（不涉及资金）']");
if (!pay.includes('paymentPending')) {
  pay = pay.replace('timeout: false,', 'timeout: false,\n      paymentPending: false,');
  pay = pay.replace('if (this.timeout) {', 'if (this.paymentPending) return;\n      if (this.timeout) {');
  pay = pay.replace('clearTimeout(this.times);\r\n        var params', 'this.paymentPending = true;\n        var params');
  pay = pay.replace('(0, _api.paymentOrder)(params).then(function (res) {', '(0, _api.paymentOrder)(params).then(function (res) {\n          _this.paymentPending = false;');
  pay = pay.replace('            uni.redirectTo({url:', '            clearTimeout(_this.times);\n            uni.redirectTo({url:');
  pay = pay.replace('        });\r\n      }\r\n\r\n    },', '        }).catch(function() { _this.paymentPending = false; });\n      }\n\n    },');
  pay = pay.replace('(0, _api.cancelOrder)(this.orderId).then(function (res) {\r\n        });', '(0, _api.cancelOrder)(this.orderId).catch(function() {});');
}
fs.writeFileSync(payFile, pay, 'utf8');
console.log('Mini-program request errors, retry controls and demo payment label patched.');
