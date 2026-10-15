# Offline SenseVoice and speaker separation

The APK includes sherpa-onnx 1.13.8, not speech models. Vosk remains available. Audio stays local when either offline engine is selected.

1. On a PC, run `python scripts/prepare-sherpa-models.py`. It downloads models from the official sherpa-onnx GitHub releases and creates ZIP bundles under `dist/sherpa-models/`. It never extracts executable scripts from the model archives.
2. Transfer `LifeMemory-SenseVoice.zip` and `LifeMemory-SpeakerModels.zip` to the phone. In Settings, import each ZIP using the matching button.
3. Select **sherpa-onnx · SenseVoice** as the transcription method. Enable **Automatic speaker separation** if desired. For a known group, set the speaker count (2–8); Auto can over-separate speakers.
4. Transcribe a saved or imported recording. Tap a transcript segment to replay it; long-press to correct its speaker. Matching segments retain manual corrections on retranscription. Changed segmentation requires reviewing labels again.

SenseVoice ZIP contains `model.int8.onnx` (or `model.onnx`) and `tokens.txt`. Speaker ZIP contains `segmentation.onnx` and `embedding.onnx`. Models can be nested inside a directory. Generated bundles use stored ZIP entries for compatibility; model sizes are roughly 240 MB and 46 MB. Model import temporarily requires space for the archive and extracted files.

Audio is decoded to 16 kHz mono. Supported input is PCM16-decoded audio with a sample rate of at least 8 kHz; stereo is downmixed. Recordings are limited to 10 minutes per inference request; split longer files. Timestamps are chunk/turn boundaries, not guaranteed word alignment. Speaker numbers apply only to the current recording and do not identify real people. Overlap/noise can reduce accuracy. The speech runtime runs in a private process; crashes leave the source audio available for retry.

Official references:
- https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- https://github.com/k2-fsa/sherpa-onnx/blob/master/java-api-examples/OfflineSpeakerDiarizationDemo.java
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-segmentation-models
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models

Models are downloaded separately and subject to their respective upstream licenses. The helper preserves the SenseVoice archive LICENSE; do not assume the application's MIT license applies to models.

## SenseVoice recognition options (alpha09)

Settings → Speech language selects Auto, Mandarin, English, Cantonese, Japanese or Korean. This is independent of the interface language. Choose Auto for mixed-language recordings. The ITN checkbox controls number-expression normalization; default is on. Both are passed to the isolated speech process for each request and apply only to new SenseVoice transcription/retranscription. Existing text is not rewritten. Neither setting performs translation or changes Vosk/cloud recognition. Accuracy for each language still requires recordings for evaluation.
