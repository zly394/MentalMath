(function () {
  const startedAt = performance.now();
  let completed = false;
  let failed = false;
  let timer;
  let rendering = false;
  const status = document.getElementById('boot-status');
  const title = document.getElementById('boot-title');
  const detail = document.getElementById('boot-detail');
  const retry = document.getElementById('boot-retry');

  const diagLogs = [];
  let diagBox = null;

  const fmtRect = el => {
    if (!el || typeof el.getBoundingClientRect !== 'function') return 'n/a';
    const r = el.getBoundingClientRect();
    return `${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)}`;
  };

  const recordDiag = label => {
    try {
      const t = Math.round(performance.now() - startedAt);
      const iw = window.innerWidth || 0;
      const ih = window.innerHeight || 0;
      const de = document.documentElement;
      const cw = de ? de.clientWidth : 0;
      const ch = de ? de.clientHeight : 0;
      const vv = window.visualViewport;
      const vvw = vv ? `${Math.round(vv.width)}x${Math.round(vv.height)}@${vv.scale?.toFixed?.(2) || 1}(${Math.round(vv.offsetLeft || 0)},${Math.round(vv.offsetTop || 0)})` : 'n/a';
      const scr = window.screen ? `${window.screen.width}x${window.screen.height}@${window.devicePixelRatio || 1}` : 'n/a';
      const sw = navigator?.serviceWorker?.controller ? 'SW:1' : 'SW:0';
      const standalone = window.navigator?.standalone ? 'SA:1' : 'SA:0';
      const appEl = document.getElementById('app');
      const cardEl = document.querySelector ? document.querySelector('.boot-card') : null;
      const line = `[${label} +${t}ms] ${sw} ${standalone} | win:${iw}x${ih} doc:${cw}x${ch} vv:${vvw} scr:${scr} | boot:${fmtRect(status)} card:${fmtRect(cardEl)} app:${fmtRect(appEl)}`;
      diagLogs.push(line);
      if (!diagBox && document.body && typeof document.createElement === 'function') {
        diagBox = document.createElement('pre');
        diagBox.id = 'viewport-diag';
        diagBox.style.cssText = 'position:fixed;top:12px;left:12px;right:12px;z-index:99999;margin:0;padding:10px 12px;background:rgba(15,23,42,0.92);color:#F8FAFC;font:11px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;border-radius:10px;white-space:pre-wrap;word-break:break-all;box-shadow:0 8px 24px rgba(0,0,0,0.25);user-select:text;-webkit-user-select:text;';
        diagBox.title = '点击关闭诊断浮层';
        diagBox.addEventListener('click', () => diagBox.remove());
        document.body.appendChild(diagBox);
      }
      if (diagBox) {
        diagBox.textContent = '【视口诊断（点击此黑框可关闭）】\n' + diagLogs.join('\n');
      }
    } catch (_) {}
  };

  const stage = (message, explanation) => {
    if (completed || failed) return;
    title.textContent = message;
    detail.textContent = explanation;
    console.info('Application startup stage:', message,
      Math.round(performance.now() - startedAt) + 'ms');
  };
  window.showStartupError = error => {
    if (completed || failed) return;
    failed = true;
    clearTimeout(timer);
    console.error('Application startup failed:', error);
    status.setAttribute('role', 'alert');
    title.textContent = '启动未完成';
    detail.textContent = (error?.message || String(error)) +
      '。请检查网络后重试；不会清除已下载的离线模型。';
    retry.hidden = false;
    recordDiag('ERR');
  };
  window.mentalMathStartup = {
    programLoaded() {
      if (rendering) return;
      stage('正在初始化运行引擎……', '启动脚本已加载，正在准备 WASM 和图形运行环境。');
      recordDiag('T1:prog');
    },
    rendering() {
      rendering = true;
      stage('正在显示首页……', '程序已进入应用，正在创建界面。');
      recordDiag('T2:render');
    },
    ready() {
      recordDiag('T3:readyBeforeRemove');
      completed = true;
      clearTimeout(timer);
      status.remove();
      recordDiag('T4:readyAfterRemove');
      setTimeout(() => recordDiag('T5:+300ms'), 300);
      setTimeout(() => recordDiag('T6:+800ms'), 800);
      console.info('Application startup ready:',
        Math.round(performance.now() - startedAt) + 'ms');
    }
  };
  retry.addEventListener('click', () => window.location.reload());
  window.addEventListener('resize', () => recordDiag('ev:resize'));
  window.addEventListener('orientationchange', () => recordDiag('ev:orient'));
  window.addEventListener('pageshow', () => recordDiag('ev:pageshow'));
  window.visualViewport?.addEventListener?.('resize', () => recordDiag('ev:vvResize'));
  window.addEventListener('error', event => showStartupError(event.error || event.message));
  window.addEventListener('unhandledrejection', event => showStartupError(event.reason));
  stage('正在获取启动程序……', '首次启动需要加载程序与运行引擎；已缓存时优先使用本地资源。');
  recordDiag('T0:init');
  timer = setTimeout(() => showStartupError(new Error('启动等待超过 60 秒')), 60000);
})();
