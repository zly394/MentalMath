# MentalMath

10 以内加减法口算训练，支持 Android、原生 iOS 和 PWA。

## 离线语音作答

| 平台 | 识别方式 | 不可用时 |
| --- | --- | --- |
| PWA | Vosk 中文小模型，限制为零到十、两及未知词 | 显式提示，可用按键作答，不自动切换云端识别 |
| Android | 已有内置 Vosk 模型 | 保持原有处理 |
| 原生 iOS | Apple 中文端侧识别，同时检查端侧支持及权限，并强制 `requiresOnDeviceRecognition` | 不支持、缺少系统资源或启动失败时提示使用按键，不回退联网识别 |

PWA 需要 HTTPS（或 localhost）、AudioWorklet 及麦克风权限。首次语音作答下载约 **42 MiB** 模型，准备期间不扣答题时间；模型准备超时会明确报错。模型解压后由 Vosk 保存到当前站点的 IndexedDB，应用和收音脚本由 Service Worker 缓存，避免重复保存模型压缩包。完成首次加载并确保应用缓存就绪后，可以断网刷新并作答。

清除网站数据、更换域名或浏览器、存储被系统回收后，需要重新联网准备。局域网和 GitHub Pages 的缓存互不共享。模型运行仍消耗设备内存与算力；桌面离线验证不代表 iPhone 上的速度和识别率，需实机测试。

语音模式保持整轮 `play-and-record`，不逐题切换音频会话；只在实际收到麦克风音频后开始答题计时。中间结果不会缩短原答题限时。识别在本机执行，不上传录音；`?speechDebug=1` 仅向同源诊断服务发送时序及状态，不发送录音或转写文本。

`?speechEngine=browser` 是显式的浏览器系统识别对照入口，**不保证离线**；默认及其它参数值均使用 Vosk，不会因失败自动选择此入口。

## 构建与本地验证

Windows：

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDistribution :androidApp:assembleDebug
node --test tests\browser-speech.test.cjs tests\offline-speech.test.cjs tests\pwa-cache.test.cjs tests\speech-diagnostics.test.cjs
python -m unittest discover -s tests -p test_serve_pwa.py
```

Web 构建自动从现有 Android 模型生成 `vosk-model-cn.tar.gz`，无需额外下载模型；部署时必须保留完整构建目录及站点相对路径。更新模型时也应更换归档文件名，并同步修改离线加载地址、Service Worker 排除规则及构建清单过滤，避免复用旧 IndexedDB 模型。

用 `serve_pwa.py` 提供本地页面，iPhone 访问局域网须使用其信任的 HTTPS 证书。`tests\pwa-listening-ready.cjs` 验证准备计时、音频会话和识别截止；`tests\pwa-offline-speech.cjs` 使用真实模型、48 kHz 收音及断网刷新验证。后者需设置 `PWA_TEST_AUDIO_PATH` 指向本机合成的中文数字 WAV（零到十，逐字停顿），可用 Windows 中文语音生成；不要使用用户录音。浏览器脚本还需已安装的 `playwright-core` 和 Chrome，可通过 `PLAYWRIGHT_CORE_PATH`、`PWA_TEST_URL` 指定模块和地址。

原生 iOS 必须在 macOS / Xcode 下构建并在支持中文端侧识别的设备上验证，Windows 构建不能验证 iOS。

## 第三方许可

Vosk、vosk-browser 及中文模型使用 Apache 2.0 许可。PWA 随附 `THIRD_PARTY_NOTICES.txt`、`LICENSE-APACHE-2.0.txt` 和构建生成的 JavaScript 许可说明；模型来源及修改说明见上述 notices 文件。
