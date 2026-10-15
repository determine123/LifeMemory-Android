# v0.8.0-alpha08 — Updated offline transcription APK

2026-10-15. Android debug pre-release, versionCode 15.

Includes pause-aware SenseVoice chunks, rolling energy calculation and exact integer sample bounds for whole recordings. Maximum chunk remains 30 seconds; quiet pauses preferred near the boundary. Exactly zero chunks are skipped without removing quiet voice. Tail samples and speaker turn boundaries are preserved. No new Whisper runtime or neural VAD model. Original audio unchanged; select SenseVoice with imported models and explicitly retranscribe to apply the new logic.

Seven JVM tests passed against production SpeechChunks. assembleDebug, assembleDebugAndroidTest and lintDebug passed. Lint: 0 errors, 16 warnings. apksigner verified the same certificate as alpha06/alpha07; aapt verified versionCode 15 / 0.8.0-alpha08. No connected device/native-model regression in this run; no accuracy or battery improvement percentage is claimed. Offline model ZIPs are imported separately. Previous version artifacts remain available for rollback.
