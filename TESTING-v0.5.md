# v0.5.0 validation / 双语验证

2026-10-14: English is the default app language. Simplified Chinese can be selected and is saved independently of the device locale. 239 resource pairs cover app controls, dialogs, settings, processing, playback and notifications. Existing records are not translated.

- assembleDebug, assembleDebugAndroidTest and lintDebug succeeded. Lint: 0 errors, 7 warnings. APK signature verification succeeded.
- 31 Android 8 emulator tests passed, including English Activity UI, Chinese preference persistence/resource selection, offline Chinese synthetic-speech transcription, manual speaker labels, playback, processing states and migration.
- Language resources remain bundled for runtime switching (App Bundle language splits disabled).
- New AI chat/recap responses are instructed to use the selected language; recaps retain the 50-Unicode-character limit. Cloud wording quality was not live-tested with a paid provider.
- Website defaults to English with a Chinese counterpart in docs/. Both pages passed anchor, ID, tab and keyboard-navigation checks. The README defaults to English and links the Chinese README and included website.
- Provider/native-library error details and existing user content may retain their original language. Offline ASR still requires a Chinese Vosk model; an English interface does not add English speech recognition.
- Promotional web files are included; public hosting is not enabled. Prior Sites source upload encountered TLS failures. No live public website URL is claimed.

Test output: test-results/instrumentation-v0.5.txt. Real-device performance and noisy-room/multi-speaker accuracy remain unverified.
