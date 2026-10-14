# v0.7.0 recording automation and system-recorder integration

2026-10-14

- Added explicit opt-in automatic recording. Manual Stop (app or recording notification) disarms future automatic recovery until re-enabled. Auto mode removes the app's 12-hour timer; manual mode retains its existing limit. OS interruption and vendor power management can still stop recording.
- Added BOOT_COMPLETED handling: Android 8–10 attempts a microphone foreground service after opt-in and permission grant. Android 11+ posts a notification instead; opening the visible app resumes recording. No attempt to bypass modern background microphone restrictions.
- Added a standard RECORD_SOUND_ACTION launcher and single audio/* sharing/document import. Unsupported vendor recorders fall back to sharing/exporting an audio file. Private recorder transcripts are not scraped or accessed.
- Imported audio is copied to app-private storage, validated for a positive playable duration, limited to 256 MB, and saved as a pending audio event. Invalid import files are removed. Transcription starts when a supported model/provider is available.
- Optional automatic recap after transcription uses existing local/AI recap logic and stores results in the app. Cloud AI sends text only; failed recaps do not overwrite or remove transcripts. Recaps currently execute on the transcription queue and can delay subsequent jobs.
- Build/Lint and APK signature checked. 36 Android 8 emulator tests pass, including durable manual-stop policy, actual WAV file import/metadata, offline ASR and previous playback/speaker/auth regressions.
- Actual reboot recording on physical devices, Android 14/15 boot notifications, vendor recorder result/share behavior and a live cloud summary of imported audio remain unverified. Notification/microphone permission and vendor autostart settings must be granted on the phone. Force-stopped apps may not receive boot broadcasts.

Official references: [microphone foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/service-types) and [system recorder action](https://developer.android.com/reference/android/provider/MediaStore.Audio.Media#RECORD_SOUND_ACTION).
