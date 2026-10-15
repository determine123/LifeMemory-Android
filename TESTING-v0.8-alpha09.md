# v0.8.0-alpha09 — Configurable SenseVoice transcription

2026-10-15. Android debug pre-release, versionCode 16.

Adds explicit SenseVoice recognition language: Auto, Mandarin, English, Cantonese, Japanese and Korean, independent of English/Chinese interface selection. Adds inverse text normalization (ITN) toggle. Defaults retain auto + ITN on. Options are snapshotted into the Messenger request and validated again in the isolated speech service before recognizer creation; unsupported values fall back to auto. Existing models and transcripts remain unchanged until explicit retranscription. These controls do not apply to Vosk/cloud and do not translate audio.

Eleven JVM tests passed: seven pause/chunk tests and four options tests (default/invalid values, supported language codes, independent normalization, bilingual labels). assembleDebug, assembleDebugAndroidTest and lintDebug passed. Lint: 0 errors, 16 warnings. apksigner verified the same certificate as alpha06/alpha07; aapt verified versionCode 16 / 0.8.0-alpha09. No connected device/native-model language accuracy regression was performed; no speech accuracy or battery improvement percentage is claimed.

Sources reviewed: https://github.com/QwenAudio/SenseVoice and https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-sense-voice-model-config.cc . Existing sherpa-onnx 1.13.8 API used, no new engine or copied upstream implementation.
