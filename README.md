<div align="center">

# LifeMemory · 拾光

**Your day. Four clear moments.**

A local-first Android journal for audio, photos and notes — with short recaps you can trace back to the source.

[Download Android beta](https://github.com/determine123/LifeMemory-Android/releases/download/v0.8.0-alpha10/LifeMemory-v0.8.0-alpha10-debug.apk) · [Website](https://determine123.github.io/LifeMemory-Android/) · [简体中文](README.zh-CN.md)

Android 8.0+ · Native Java · MIT · Bring your own AI provider

</div>

## Why LifeMemory?

- **Four periods, less scrolling.** Morning (00–11), midday (11–14), afternoon (14–18) and evening (18–24), each with up to 50 Unicode characters. Minute recordings stay inside period details.
- **Memories with evidence.** Open the source text or original audio behind events and tasks. Seek, replay and correct speaker labels.
- **Local speech, your AI.** Import Vosk Chinese or optional sherpa-onnx SenseVoice and speaker models. Choose your own compatible cloud provider for semantic recaps.
- **Visible processing.** Inspect queued, active, failed and completed recordings; retry individual items.
- **Keep and recover.** Protect important recordings, search transcripts, browse topics and merge ZIP backups with attachments.

## Start in three steps

1. Download **v0.8.0-alpha10** above. This is a **debug pre-release**. Install over your existing version to retain data. The app defaults to English and remembers your English/Chinese choice.
2. Record a moment, add a photo or write a note. For offline speech, [import the required models](SHERPA-MODELS.md); they are not bundled with the APK.
3. Configure your own AI provider for semantic recaps. DeepSeek is supported for chat and summaries; its chat key does not provide audio transcription. See [configuration troubleshooting](TROUBLESHOOTING.md).

Automatic recaps run after each period ends; Android may delay jobs. Update one period or all four manually. Without AI, the app shows factual record counts.

## Capture → transcribe → revisit

Recording uses a foreground notification, pause/resume, important marks and 1/3/5-minute saved segments. Import or share standard audio files from your phone’s recorder. Opt-in automatic mode is disarmed by manual Stop; modern Android requires opening the app after reboot. Background restrictions can interrupt recording.

Optional sherpa-onnx runs in an isolated speech process. Speaker numbers are local to each recording, not verified identities. Auto can over-separate; set a known count or correct labels manually. Photos support local Chinese OCR; location, activity and mood are manually entered.

## Data and privacy

Records and offline models stay in app-private storage. Cloud transcription sends audio; AI recaps/chat send relevant text; optional image analysis sends the selected photo. Local failure does not silently upload audio. Keys use Android Keystore encryption. The database has no additional app-level encryption, and exported ZIP backups are unencrypted. Derived period recaps can be regenerated after restore.

## Validation

**16 JVM validation/segmentation tests passed** for v0.8.0-alpha10; native-model/device regression is pending. [Latest validation](TESTING-v0.8-alpha10.md). The previous alpha04 full suite passed 61 tests. Xiaomi 13 Ultra USB tests, real-world speech accuracy, battery use and vendor background behavior remain pending. Test results do not guarantee uninterrupted all-day recording.

## Build and website

Requires JDK 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9 and AGP 8.7.3. Configure `ANDROID_HOME` or untracked `local.properties`, then fetch the pinned speech dependency:

```powershell
./fetch-dependencies.ps1
./gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Never commit credentials or personal recordings.

The bilingual promotional website is included in [docs/](docs/README.md). English is the default page. Preview with `python -m http.server 8767 --directory docs`; check with `node docs/check.mjs`. GitHub Pages serves `main /docs`. Interactive demo records are fictional.

## Contribute and follow

[Open an issue](https://github.com/determine123/LifeMemory-Android/issues) · [Contribute a PR](https://github.com/determine123/LifeMemory-Android/pulls) · [determine@sjtu.edu.cn](mailto:determine@sjtu.edu.cn)

[Bilibili](https://space.bilibili.com/1553355526) · Douyin: **ltm1524001843** (search this ID).

## References and license

Independently implemented in native Java. Omi, Memex and Auday informed the life-journal workflow; Orca informed processing visibility. [Tether](https://github.com/tt-11-dd/tether-ai) informed this presentation’s product-first structure and clear local/cloud choices. These references are not affiliations or bundled coding-agent features.

[MIT license](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Reference scope](reference-notes/SOURCES.md)

## Related projects and search filters

[Reference choices and implementation](reference-notes/PROJECTS-2026-10-14.md). Search supports `type:audio`, `topic:work`, `pinned:true`, `period:evening`, exact dates and dated periods such as `yesterday evening`.

Pause-aware SenseVoice chunking is implemented in source; seven JVM algorithm tests passed. The alpha10 APK includes this change; native-model/device regression is pending. [2026-10-15](reference-notes/ASR-IMPROVEMENTS-2026-10-15.md)

SenseVoice settings now include an independent speech language (Auto, Mandarin, English, Cantonese, Japanese, Korean) and an ITN number-expression normalization toggle. Use Auto for mixed speech; retranscribe to apply. These settings do not change Vosk/cloud transcription or translate audio.

AI setup: the DeepSeek preset keeps typed keys. Save and test saves the current form and checks both /models and a short synthetic chat. Changing provider requires its key; a 401 still requires valid credentials. No owner credential is included in the public APK.
