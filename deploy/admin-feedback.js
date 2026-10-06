// Feedback for transport errors in the course's compiled administrator UI.
(function () {
  var style = document.createElement('style');
  style.textContent = '.app-wrapper{min-width:1024px!important}';
  document.head.appendChild(style);
  var timer, alert;
  window.addEventListener('campus-api-error', function(event) {
    if (!alert) {
      alert = document.createElement('div');
      alert.setAttribute('role', 'alert');
      alert.style.cssText = 'position:fixed;top:24px;left:50%;transform:translateX(-50%);z-index:99999;padding:14px 22px;border:1px solid #f3d19e;border-radius:6px;background:#fdf6ec;color:#854d0e;box-shadow:0 4px 16px #0002;max-width:90vw;font-size:14px;';
      document.body.appendChild(alert);
    }
    alert.textContent = event.detail || '网络或服务暂时不可用，请稍后重试';
    alert.hidden = false;
    clearTimeout(timer);
    timer = setTimeout(function() { alert.hidden = true; }, 8000);
  });
})();
