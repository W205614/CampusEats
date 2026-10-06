// Included in the compiled uni-app request module by patch-miniprogram.cjs.
function normalizeLocalImages(value) {
  if (!value || typeof value !== 'object') return;
  Object.keys(value).forEach(function(key) {
    if (key === 'image' && typeof value[key] === 'string' && value[key].charAt(0) === '/') {
      value[key] = _env.baseUrl + value[key];
    } else if (value[key] && typeof value[key] === 'object') normalizeLocalImages(value[key]);
  });
}
function request(options) {
  return new Promise(function(resolve, reject) {
    function fail(message, code) {
      var error = { code: code || 0, msg: message, data: { msg: message } };
      uni.showToast({ title: message, icon: 'none', duration: 2500 });
      reject(error);
    }
    uni.request({
      url: _env.baseUrl + (options.url || ''),
      data: options.params || {},
      method: options.method || 'GET',
      timeout: 15000,
      header: { 'Accept': 'application/json', 'Content-Type': 'application/json',
        'authentication': _store.default.state.token },
      success: function(res) {
        if (res.statusCode === 401) {
          _store.default.commit('setToken', '');
          fail('登录已失效，请返回首页重新登录', 401);
        } else if (res.statusCode >= 200 && res.statusCode < 300 && res.data &&
          (res.data.code === 1 || res.data.code === 200)) {
          normalizeLocalImages(res.data);
          resolve(res.data);
        } else fail((res.data && res.data.msg) || '服务暂时不可用，请稍后重试');
      },
      fail: function(err) {
        fail(err && /timeout/i.test(err.errMsg || '') ? '请求超时，请重试' : '网络连接失败，请检查网络后重试');
      }
    });
  });
}
