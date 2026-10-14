# API authentication and transcription / 鉴权与转写排查

## DeepSeek setup / DeepSeek配置

1. Install v0.6.0 over the existing app; do not uninstall or clear storage.
2. Settings → Use DeepSeek preset. This saves `https://api.deepseek.com`, chat/vision model `deepseek-flash`, and on-device transcription. It does not generate a key or validate an existing key.
3. Enter your own current DeepSeek key → Save settings. Paste only the key; a leading `Bearer` is tolerated and removed. Keys for another provider will not authenticate with DeepSeek.
4. Test saved settings. Only a GET request to `/models` is made, with no audio or personal records. HTTP 200 proves access to the model list, not balance, chat generation, vision support or speech transcription.
5. Import a Chinese Vosk model ZIP → choose on-device transcription. The previous DeepSeek `/audio/transcriptions` test returned 404. A chat key/model is not an offline speech model.
6. Processing center → Retry segment, or the Pending button for queued recordings. Transcript & playback only opens existing timestamped transcripts; it does not retry transcription.

中文操作：覆盖安装新版 → 设置 → 使用DeepSeek配置 → 填写自己的有效DeepSeek密钥 → 保存 → 测试已保存的配置 → 导入离线中文模型 → 本机转写 → 处理中心重试。不要卸载、清除数据或删除62条记录来排查鉴权问题。

## What each error means / 错误区分

- **401**: credentials rejected. Check provider/key pairing, replace an expired or revoked key, save and test. Waiting, switching Wi-Fi or adding balance cannot reliably repair a wrong key.
- **402**: payment or balance issue. Check the provider account.
- **403**: permissions or access restrictions. Check provider policy and account permissions.
- **404**: endpoint or model unavailable. Check the base URL/model and whether that provider supports the requested capability.
- **429**: rate or quota limit. Check quota and wait before retrying.
- Network timeout / TLS failure: connection problem; distinguish it from HTTP authentication errors before changing network settings.

The app pauses cloud transcription after 401/403. Correct and save settings to release the pause. On-device transcription does not require an AI key. Interrupted processing flags recover on app launch; existing audio and transcripts are retained. Non-retryable API errors do not trigger recap backoff retries.

## Safe diagnostics / 安全诊断

Errors show status, provider host and endpoint. They never print the Authorization header, API key or provider response body. This app has no developer-hosted AI backend or OAuth token refresh. Keys are entered in the app and encrypted with Android Keystore. Do not share keys in screenshots, logs, issues or messages.

Official references, checked 2026-10-14: [DeepSeek setup](https://api-docs.deepseek.com/) and [Models endpoint](https://api-docs.deepseek.com/api/list-models/). `deepseek-flash` is the currently documented model; availability and pricing remain provider-controlled.
