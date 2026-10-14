# v0.6.0 API configuration and queue fixes

2026-10-14

- Added a DeepSeek preset (`https://api.deepseek.com`, `deepseek-flash`) paired with on-device transcription. Prevents selecting the previously unsupported DeepSeek audio route as the cloud transcription configuration.
- Added a saved-configuration GET /models test. Sends credentials only to the saved provider; does not send private records/audio or validate billing, actual chat/vision output, or ASR.
- Normalizes base URLs and key prefixes; rejects insecure URLs, embedded URL credentials, query strings and invalid key whitespace. Existing saved keys are also normalized at request time.
- Distinguishes HTTP 401/402/403/404/429 and temporary server errors. Diagnostics omit headers, keys and raw provider response bodies.
- Pauses cloud speech jobs after 401/403; saving corrected settings clears the pause. On-device ASR is unaffected. Recap workers do not back off/retry non-retryable API responses.
- Recovers stale processing flags on startup without deleting existing transcript/audio paths.
- Build and Lint passed: 0 errors, 7 warnings. APK signature verified.
- 34 Android 8 emulator tests passed, including normalization, simulated HTTP failures/disconnect, interrupted-job recovery, local Chinese transcription and speaker/playback regressions. Simulated HTTP tests do not prove that the user's current key is valid.

The user's phone configuration and key were not read. No live credential from the phone was tested. Install the new APK and follow [troubleshooting](TROUBLESHOOTING.md) to verify actual authentication on the device. Raw suite output: test-results/instrumentation-v0.6.txt.
