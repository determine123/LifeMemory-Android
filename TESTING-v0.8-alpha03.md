# v0.8.0-alpha03 integration validation

Date: 2026-10-14. Native Java Android application; Android 8.0+, debug pre-release.

## Integrated

- Optional sherpa-onnx 1.13.8 SenseVoice transcription and local speaker separation. Vosk and explicitly configured cloud transcription remain available.
- Speaker models can be imported separately. Auto or known speaker count (2–8); per-segment manual corrections persist when identical segments are retranscribed.
- Native speech/model validation runs in a private `:speech` process. This avoids an observed ZIP-inflater failure after loading the native runtime in the UI process, and reports native process termination without deleting audio.
- Decode PCM16 audio, downmix stereo and resample to 16 kHz. Requests are limited to 10 minutes, with chunk/turn timestamp boundaries.
- Daily recap remains <=50 Unicode characters. Structured events and tasks contain validated source IDs and can open original text or timestamped audio. Deleted sources are reported. Context truncation is disclosed.
- Record topics support exact, case-insensitive cross-date browsing and local historical transcript search.
- Backup versions 2/3 merge into local storage with attachment/path/CRC validation and transaction rollback. Source and bookmark IDs are remapped. Existing recaps/tasks remain, and identical archive reimports skip records. Missing bookmark sources do not link to unrelated records. Export version 3 includes linked recaps/tasks.
- Schema 1/2 migration preserves transcripts, pins, summaries and completed tasks. Settings, API keys and speech models are not imported.

## Validation

- 56 emulator instrumentation tests passed; raw result: `test-results/instrumentation-v0.8-alpha03.txt`.
- Real official SenseVoice int8 model recognized the Chinese reference WAV. Real segmentation + embedding models generated four speaker labels when configured for four participants; native integration also imported a compressed backup after inference.
- Auto mode on the same four-person reference produced seven groups in an earlier run: this demonstrates over-separation, not accurate speaker-count detection. Known-count configuration helps constrain groups but does not prove correct identity or word assignment.
- Tests cover backup merge/reimport, missing-media rejection, path traversal, malformed-data rollback, source-ID validation/remapping, manual speaker retention, topic recall, 8 kHz resampling, stereo downmix and database upgrades, plus existing recording/playback/ASR/OCR tests.
- Offline debug/test builds and lint passed (0 errors, 16 warnings). APK signature verified. Website language/link/demo checks passed.

## Limits

Only API 26 emulator validation was performed. Xiaomi 13 Ultra USB testing, noisy rooms, overlap, long-running memory/energy use and modern Android microphone interruption behavior are pending. No cloud recap call was made during this validation; source parsing/validation/storage were tested locally. Recap content still requires checking against its sources.

Automatic speaker labels apply only within a recording, not a person's identity. Timestamps are approximate chunk/turn boundaries. Changed segment boundaries require reviewing manual labels. Native inference has a ten-minute request timeout. Import limits: 512 MB compressed / 1 GB expanded, 4096 ZIP entries, 32 MB manifest and 20000 records. Reimports of a different archive are separate merges; existing records are not reconciled by personal identity. Orphan media from abrupt process death during import may remain, but existing records are never replaced or deleted.

Speech models are not bundled in the APK; see `SHERPA-MODELS.md`. The pinned AAR is fetched with `fetch-dependencies.ps1` and verified against the upstream SHA-256.

APK SHA-256: 10afb384caf6e531bc0cee272df18c25d797b94bcfa5c1b1087e17af9c6ce7bd
