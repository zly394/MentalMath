(() => {
  const panel = document.getElementById('offline-status');
  const title = document.getElementById('offline-status-title');
  const detail = document.getElementById('offline-status-detail');
  const progress = document.getElementById('offline-status-progress');
  const retry = document.getElementById('offline-status-retry');
  const close = document.getElementById('offline-status-close');
  let dismissed = false;
  let hideTimer;

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
      title.textContent = '正在准备离线语音';
      detail.textContent = '下载或读取模型并初始化，首次约 42 MiB。准备完成前不扣答题时间，可先用按键作答。';
    } else if (state.status === 'ready') {
      progress.value = 1;
      title.textContent = '离线语音已就绪';
      detail.textContent = '本页再次进入答题无需重新加载模型。';
      hideTimer = setTimeout(() => { panel.hidden = true; }, 3000);
    } else if (state.status === 'error') {
      title.textContent = '离线语音准备失败';
      detail.textContent = (state.message || '模型加载失败') + '。可重试或使用按键作答。';
    }
  });
})();
