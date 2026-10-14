# LifeMemory Android

## Download

[Download LifeMemory v0.8.0-alpha04 APK](https://github.com/determine123/LifeMemory-Android/releases/download/v0.8.0-alpha04/LifeMemory-v0.8.0-alpha04-debug.apk) · [Release notes](https://github.com/determine123/LifeMemory-Android/releases/tag/v0.8.0-alpha04)

Android 8.0+. Debug beta build; allow installation from your browser when prompted. Install over the existing version to keep your data. Offline speech models are imported separately.


[简体中文](README.zh-CN.md) · [Website](https://determine123.github.io/LifeMemory-Android/) · [中文网页](https://determine123.github.io/LifeMemory-Android/zh.html)

**Keep a little of your day.** A local-first Android life journal inspired by Omi, Memex and Auday, with a processing workspace inspired by Orca. Independently implemented in native Java; not an affiliated product.

## Language

Version **0.8.0-alpha04** opens in **English by default**, including on Chinese-language devices. The language button at the top switches between English and Simplified Chinese and saves your choice. Existing records, transcripts and speaker names keep their original language. New AI answers follow your selected language. Daily recaps still have a maximum of **50 Unicode characters**, including punctuation. Offline speech recognition supports Vosk Chinese and optional multilingual SenseVoice through sherpa-onnx. [Model setup](SHERPA-MODELS.md).

## Recording automation and your phone's recorder

Settings → Automatic recording & imports: opt into resume after reboot/opening the app. Manual Stop disarms auto recording until re-enabled. Android 8–10 attempts boot recording; Android 11+ requires opening the app from the boot notification. Automatic mode has no app-enforced 12-hour cap, but Android or vendor power management may interrupt it.

Use System recorder if the installed recorder supports Android's standard action. Otherwise share an audio file to LifeMemory or use Import audio. Enable Recap automatically after transcription for audio → transcript → local/AI recap → app display. No private recorder data is accessed. See [v0.7 validation and limits](TESTING-v0.7.md).

## DeepSeek and authentication fixes

Settings now includes a DeepSeek chat preset and a saved-configuration `/models` test. HTTP errors distinguish authentication, balance, permissions, missing endpoints and rate limits. Cloud transcription pauses on 401/403; interrupted processing flags recover without deleting records. See [step-by-step troubleshooting](TROUBLESHOOTING.md). DeepSeek chat is paired with on-device transcription; a working chat key does not imply a working audio endpoint.

## Features

- Explicit audio recording with a persistent notification, pause/resume and highlights. Save 1/3/5-minute segments; sessions have a 12-hour limit.
- On-device Chinese transcription with an imported Vosk model. Cloud transcription requires explicit configuration; local failure does not silently upload audio.
- Processing center with progress, errors, filters and individual retries that preserve existing transcripts.
- Timestamped playback, manual speaker labels, seeking and playback speed controls.
- Text, photos, local Chinese OCR and manually entered location/activity/mood on a dated timeline.
- Concise daily recaps, separate tasks, source records and evidence-based memory questions. Without AI, show local record statistics.
- ZIP backup export/import with attachment validation, rollback, record merging and source-ID remapping.
- Record topics with historical browsing and transcript search; daily events/tasks open their source text or audio.
- Optional sherpa-onnx SenseVoice and speaker separation in an isolated process. Known speaker count (2–8) or Auto; offline models imported separately.

## Promotional website — included

The full bilingual landing page is in **[docs/](docs/README.md)**. `docs/index.html` is English; `docs/zh.html` is Chinese. Both have language links, responsive layouts, interactive feature demos and keyboard navigation. Demo records are fictional. No analytics, accounts or backend.

Open the HTML file locally or run `python -m http.server 8767 --directory docs`. For GitHub Pages, select **Settings → Pages → main → /docs**. The promotional website is live at [https://determine123.github.io/LifeMemory-Android/](https://determine123.github.io/LifeMemory-Android/).

## Build and install

Requires JDK **17**, Android SDK Platform **35**, Build Tools **35.0.0**, Gradle **8.9**, and AGP **8.7.3**. Minimum Android: **8.0 / API 26**.

Set `ANDROID_HOME` or an untracked `local.properties` with `sdk.dir`. First run `./fetch-dependencies.ps1` in PowerShell to fetch and verify the pinned sherpa-onnx AAR. Then open in Android Studio, or run:

```sh
./gradlew assembleDebug lintDebug assembleDebugAndroidTest
```

Windows: use `gradlew.bat`. Output: `app/build/outputs/apk/debug/app-debug.apk`. Install over the existing version to keep your data. Built APKs and speech models are excluded from the source repository.

Import a ZIP from [Vosk models](https://alphacephei.com/vosk/models) unchanged in Settings. AI features need your own compatible HTTPS provider and key. Never commit credentials.

## Privacy and limits

Records stay in app-private storage. Offline transcription and OCR run locally. Cloud transcription uploads audio; AI recaps/chat send relevant text; optional image analysis sends the selected photo. Keys use Android Keystore encryption. The database has no additional app-level encryption; exported ZIP backups are unencrypted. Backup ZIP import merges records and remaps source links, preserving existing summaries and tasks.

Speaker labels can be corrected manually. Optional local speaker separation is integrated; Auto can over-separate, and noisy-room accuracy still needs real-device validation. No automatic heart-rate, stress or GPS collection. Real-device battery use, noisy-room accuracy, background restrictions and uninterrupted all-day recording need further testing. Scheduled recaps may be delayed by Android power management.

## Validation and license

Latest report: [v0.7 — 36 device tests](TESTING-v0.7.md). Previous reports: [v0.6](TESTING-v0.6.md), [v0.5](TESTING-v0.5.md), [v0.4](TESTING-v0.4.md), [v0.3](TESTING-v0.3.md). Website checks: `node docs/check.mjs`. Tests use synthetic speech and emulator fixtures, not proof of real-world accuracy.

Original code: [MIT](LICENSE). [Third-party notices](THIRD_PARTY_NOTICES.md) · [Reference scope](reference-notes/SOURCES.md).

## Contact and community

Found a problem? [Open an issue](https://github.com/determine123/LifeMemory-Android/issues) or [contribute a pull request](https://github.com/determine123/LifeMemory-Android/pulls). You can also contact the developer at [determine@sjtu.edu.cn](mailto:determine@sjtu.edu.cn).

Follow [determine on Bilibili](https://space.bilibili.com/1553355526) for project updates and demos.

Douyin: **ltm1524001843** (search this ID in Douyin to follow determine).

## v0.8.0-alpha04 integration

Includes optional local SenseVoice/speaker separation, source-linked daily recaps, topic browsing and backup import. See [model setup](SHERPA-MODELS.md) and [validation report](TESTING-v0.8-alpha03.md). Model ZIPs are not bundled with the APK. Speaker numbers are per recording, not personal identity. Existing recaps can be regenerated to add linked events.

## Four-period timeline

The homepage shows Morning (00–11), Midday (11–14), Afternoon (14–18) and Evening (18–24), each with a recap of up to 50 Unicode characters. Raw minute recordings are kept inside period details. Automatic recaps run after each period ends; Android may delay background work. Update one period or all four manually. Without AI, show record counts rather than fabricated semantic summaries. Derived period caches can be regenerated after backup restore; original records and tasks remain backed up. See [validation](TESTING-v0.8-alpha04.md).
