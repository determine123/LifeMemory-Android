# Transcription source update — 2026-10-15

This source update retains the existing SenseVoice/sherpa-onnx pipeline, bounded 30-second chunks, pause preference in the final five seconds, exact-zero skipping and speaker-turn boundaries. No Whisper runtime or neural VAD model is newly integrated.

- SpeechChunks now uses a rolling squared-energy window rather than repeatedly summing overlapping windows.
- SherpaAsr uses integer source bounds for non-diarized whole recordings. Avoiding the float-seconds round trip prevents possible single-sample tail loss on irregular-length recordings.
- Seven JVM tests passed, including nonzero source offsets, choosing the quieter of two pauses, contiguous coverage over multiple pauses, irregular tails, very quiet speech and invalid bounds.

Offline compileDebugJavaWithJavac, compileDebugAndroidTestJavaWithJavac and lintDebug passed. No real-device recognition-accuracy or battery claim is made. Existing APK downloads are not replaced by this source-only update; rebuild to include these follow-up changes.
