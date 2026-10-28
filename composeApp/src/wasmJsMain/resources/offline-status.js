(() => {
  const panel = document.getElementById('offline-status');
  const title = document.getElementById('offline-status-title');
  const detail = document.getElementById('offline-status-detail');
  const progress = document.getElementById('offline-status-progress');
  const retry = document.getElementById('offline-status-retry');
  const close = document.getElementById('offline-status-close');
  const iconWrap = document.getElementById('offline-status-icon-wrap');
  const spinner = document.getElementById('offline-status-spinner');
  let dismissed = false;
  let hideTimer;

  const ICONS = {
    mic: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2" width="6" height="12" rx="3"></rect><path d="M5 10a7 7 0 0 0 14 0"></path><line x1="12" y1="19" x2="12" y2="22"></line><line x1="8" y1="22" x2="16" y2="22"></line></svg>',
    ready: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#16A34A" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"></path></svg>',
    error: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#DC2626" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>'
  };

  close.onclick = () => {
    dismissed = true;
    panel.hidden = true;
  };
  retry.onclick = () => {
    return globalThis.MentalMathOfflineRecognition.preload().catch(error => {
      console.error('Offline model retry failed:', error);
    });
  };

  globalThis.MentalMathOfflineRecognition.observePreparation(state => {
    clearTimeout(hideTimer);
    if (state.status === 'loading') dismissed = false;
    panel.hidden = state.status === 'idle' || dismissed;
    progress.hidden = state.status !== 'loading';
    retry.hidden = state.status !== 'error';

    if (state.status === 'loading') {
      progress.removeAttribute('value');
      iconWrap.innerHTML = ICONS.mic;
      spinner.hidden = false;
      title.textContent = '正在准备离线语音';
      detail.textContent = '下载或读取模型并初始化，首次约 42 MiB。准备完成前不扣答题时间，可先用按键作答。';
    } else if (state.status === 'ready') {
      progress.value = 1;
      iconWrap.innerHTML = ICONS.ready;
      spinner.hidden = true;
      title.textContent = '离线语音已就绪';
      detail.textContent = '本页再次进入答题无需重新加载模型。';
      hideTimer = setTimeout(() => { panel.hidden = true; }, 3000);
    } else if (state.status === 'error') {
      iconWrap.innerHTML = ICONS.error;
      spinner.hidden = true;
      title.textContent = '离线语音准备失败';
      detail.textContent = (state.message || '模型加载失败') + '。可重试或使用按键作答。';
    }
  });
})();
