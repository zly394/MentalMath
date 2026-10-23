// Run against a built PWA using PWA_TEST_URL and PLAYWRIGHT_CORE_PATH.
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || 'playwright-core');

(async () => {
  const browser = await chromium.launch({
    channel: 'chrome', headless: true, args: ['--ignore-certificate-errors']
  });
  try {
    const context = await browser.newContext({
      ignoreHTTPSErrors: true, viewport: { width: 390, height: 844 }
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await context.addInitScript(() => {
      window.captureRequests = 0;
      const capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = (...args) => {
        window.captureRequests++;
        return capture(...args);
      };
    });
    let releaseDownload;
    const downloadGate = new Promise(resolve => { releaseDownload = resolve; });
    await context.route('**/vosk-model-cn.tar.gz', async route => {
      await downloadGate;
      await route.continue();
    });
    await page.goto(process.env.PWA_TEST_URL || 'https://127.0.0.1:8443/');
    const panel = page.locator('#offline-status');
    await panel.waitFor({ state: 'visible', timeout: 30000 });
    assert.match(await panel.innerText(), /正在准备离线语音/);
    assert.equal(await page.locator('#offline-status-progress').getAttribute('value'), null);
    assert.equal(await page.evaluate(() => captureRequests), 0);
    releaseDownload();
    await page.waitForFunction(() =>
      document.getElementById('offline-status-title').textContent === '离线语音已就绪',
    null, { timeout: 120000 });
    assert.equal(await page.evaluate(() => captureRequests), 0);
    await panel.waitFor({ state: 'hidden' });
    await page.waitForFunction(() => !document.getElementById('boot-status'));

    // Use keypad mode so navigation checks never acquire a microphone.
    await page.mouse.click(280, 525);
    await page.mouse.click(195, 801);
    await page.waitForTimeout(500);
    const back = page.getByRole('button', { name: '返回', exact: true });
    await back.waitFor({ state: 'attached', timeout: 10000 });
    await page.mouse.click(40, 35);
    await back.waitFor({ state: 'detached' });
    await page.mouse.click(195, 163);
    await back.waitFor({ state: 'attached' });
    await page.mouse.click(40, 35);
    await back.waitFor({ state: 'detached' });
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await context.setOffline(true);
    await page.reload();
    await page.waitForFunction(() =>
      document.getElementById('offline-status-title').textContent === '离线语音已就绪',
    null, { timeout: 120000 });
    assert.deepEqual(errors, []);
    console.log('PASS: homepage progress, real model readiness, back navigation and offline reload');
    await context.close();

    const retryContext = await browser.newContext({
      ignoreHTTPSErrors: true, serviceWorkers: 'block'
    });
    const retryPage = await retryContext.newPage();
    let failDownload = true;
    await retryContext.route('**/vosk-model-cn.tar.gz', route =>
      failDownload
        ? route.fulfill({ status: 503, body: 'Temporarily unavailable' })
        : route.continue());
    await retryPage.goto(process.env.PWA_TEST_URL || 'https://127.0.0.1:8443/');
    await retryPage.locator('#offline-status-retry').waitFor({
      state: 'visible', timeout: 120000
    });
    assert.match(await retryPage.locator('#offline-status-title').innerText(), /失败/);
    failDownload = false;
    await retryPage.locator('#offline-status-retry').click();
    await retryPage.waitForFunction(() =>
      document.getElementById('offline-status-title').textContent === '离线语音已就绪',
    null, { timeout: 120000 });
    console.log('PASS: failed real model download exposes retry and recovers');
    await retryContext.close();
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
