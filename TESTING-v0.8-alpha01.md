# v0.8.0-alpha01 — Recording reliability, stage 1

Date: 2026-10-14. This is an experimental build; the public website still downloads stable beta v0.7.0.

## Implemented

- Require more than 16 MiB free space before each segment; monitor available space every two seconds and stop before exhausting storage.
- Persist the current recording checkpoint synchronously before preparing the recorder. Do not overwrite an unresolved recovery checkpoint.
- Handle MediaRecorder error and size-limit callbacks. Android 10+ also observes system-reported microphone silencing and stops with an explanatory message; resumption is manual.
- On interruption/reboot, validate private-media paths and inspect media duration. Recover valid audio once; preserve incomplete audio with an error status and explanatory metadata, excluding it from automatic pending transcription.
- Database/storage failures retain the checkpoint for retry. Successful segments remain intact.

## Evidence

- Offline debug/app test builds passed; lint: 0 errors, 12 warnings.
- API 26 emulator instrumentation: **41 tests passed**. Five new tests cover recovery idempotency, playable WAV recovery, incomplete MP4 preservation, private-path rejection, storage threshold, and actual screen-off recording/save (the first two checks share one test).
- Additional process kill (`kill -9`): one recovered record, original audio retained; this file had a readable duration and was pending transcription.
- Actual emulator reboot: BootReceiver recovered one incomplete recording without opening the app; original file retained, status error.
- Emulated incoming GSM call: service did not crash and saved one segment. This only checks control flow, not real call audio routing.
- Raw evidence: `test-results/instrumentation-v0.8-alpha01.txt`. Manual scenario JSON outputs are in ignored `dist/`.

## Pending real-device validation

User device: Xiaomi 13 Ultra; exact Android/HyperOS build will be read over USB. Test screen-off for at least 30 minutes, incoming/answered calls, process termination, reboot, and vendor battery restrictions. Android 10+ microphone-silencing callback requires modern-device validation; API 26 does not execute it. Low-storage tests verify the threshold logic, not filling a real phone's storage. Abruptly terminated MP4 files may remain unplayable; recovery preserves files rather than repairing them.

## Next stages

2. Optional sherpa-onnx engine, diarization and manual corrections, benchmarked with user-authorized real recordings. Not integrated in this build.
3. Daily summary/events/tasks with source navigation. Not integrated in this build.
4. Historical transcript/topic retrieval and safe backup import. Existing search remains; new topic retrieval/import not integrated in this build.
