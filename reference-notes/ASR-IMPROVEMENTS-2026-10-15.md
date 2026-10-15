# Offline transcription improvements — 2026-10-15

## Upstream references

- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx — Android offline speech, VAD and diarization. Already integrated at pinned version 1.13.8.
- SenseVoice: https://github.com/QwenAudio/SenseVoice — multilingual speech recognition. Existing LifeMemory SenseVoice recognizer remains in use.
- whisper.cpp: https://github.com/ggml-org/whisper.cpp — native ASR, Android examples and configurable segmentation/no-speech handling. Considered as a future benchmark; not newly integrated.

No new upstream source code copied. This is an independently implemented improvement to the existing SenseVoice pipeline, not a neural VAD implementation.

## Implemented

Before: every speaker turn (or entire audio without diarization) was cut at fixed 30-second boundaries.

After: retain a 30-second maximum; search the last five seconds for a 200-ms window with energy at most one tenth of the chunk average. Split inside the quietest qualifying window; otherwise retain the maximum boundary. Process consecutive ranges without overlap or omitted samples, including the final tail. Never cross diarization turn boundaries. Skip recognition only for exactly zero-valued samples; quiet speech is not removed. Start/end offsets remain relative to the original 16-kHz decoded audio; they are chunk timestamps, not word alignment.

Original recordings and stored transcriptions are not rewritten automatically. The change applies when SenseVoice is selected and a recording is transcribed again. Existing 10-minute inference input limit remains. No recognition-accuracy percentage, noise robustness or battery improvement claimed without representative recordings.

## Validation

SpeechChunksTest covers pause selection, uninterrupted speech, exact sample coverage, quiet speech preservation, speaker-turn limits and invalid bounds. Native-model inference and APK build must be reported separately from these algorithm checks.

Executed: JDK 17 + JUnit 4.13.2, **5 tests passed** against the production SpeechChunks class. Android SDK/Gradle/ADB previously located under E:/AndroidTools are unavailable in the current environment, so no new APK, native model regression or real-device accuracy result is claimed. Existing alpha06 downloads remain unchanged.
