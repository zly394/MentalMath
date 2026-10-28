(function () {
  const startedAt = performance.now();
  let completed = false;
  let failed = false;
  let timer;
  let rendering = false;
  const app = document.getElementById('app');
  const status = document.getElementById('boot-status');
  const title = document.getElementById('boot-title');
  const detail = document.getElementById('boot-detail');
  const retry = document.getElementById('boot-retry');

  const syncViewport = () => {
    const vv = window.visualViewport;
    const width = Math.round(vv?.width || window.innerWidth || document.documentElement?.clientWidth || 0);
    const height = Math.round(vv?.height || window.innerHeight || document.documentElement?.clientHeight || 0);
    const left = Math.round(vv?.offsetLeft || 0);
    const top = Math.round(vv?.offsetTop || 0);
    if (width > 0 && height > 0) {
      if (!completed && status?.style) {
        status.style.left = left + 'px';
        status.style.top = top + 'px';
        status.style.width = width + 'px';
        status.style.height = height + 'px';
      }
      if (app?.style) {
        app.style.width = width + 'px';
        app.style.height = height + 'px';
      }
      if (typeof window.scrollTo === 'function') {
        window.scrollTo(0, 0);
      }
    }
  };

  const stage = (message, explanation) => {
    if (completed || failed) return;
    syncViewport();
    title.textContent = message;
    detail.textContent = explanation;
    console.info('Application startup stage:', message,
      Math.round(performance.now() - startedAt) + 'ms');
  };
  window.showStartupError = error => {
    if (completed || failed) return;
    failed = true;
    clearTimeout(timer);
    syncViewport();
    console.error('Application startup failed:', error);
    status.setAttribute('role', 'alert');
    title.textContent = '启动未完成';
    detail.textContent = (error?.message || String(error)) +
      '。请检查网络后重试；不会清除已下载的离线模型。';
    retry.hidden = false;
  };
  window.mentalMathStartup = {
    programLoaded() {
      if (rendering) return;
      stage('正在初始化运行引擎……', '启动脚本已加载，正在准备 WASM 和图形运行环境。');
    },
    rendering() {
      rendering = true;
      stage('正在显示首页……', '程序已进入应用，正在创建界面。');
    },
    ready() {
      syncViewport();
      completed = true;
      clearTimeout(timer);
      status.remove();
      console.info('Application startup ready:',
        Math.round(performance.now() - startedAt) + 'ms');
    }
  };
  retry.addEventListener('click', () => window.location.reload());
  window.addEventListener('resize', syncViewport);
  window.addEventListener('orientationchange', syncViewport);
  window.addEventListener('load', syncViewport);
  window.addEventListener('pageshow', syncViewport);
  window.visualViewport?.addEventListener?.('resize', syncViewport);
  if (typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(syncViewport);
  }
  window.addEventListener('error', event => showStartupError(event.error || event.message));
  window.addEventListener('unhandledrejection', event => showStartupError(event.reason));
  stage('正在获取启动程序……', '首次启动需要加载程序与运行引擎；已缓存时优先使用本地资源。');
  timer = setTimeout(() => showStartupError(new Error('启动等待超过 60 秒')), 60000);
})();
