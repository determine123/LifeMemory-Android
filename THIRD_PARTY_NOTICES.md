# 第三方依赖与参考

本项目原创源码采用 MIT 许可证。依赖与用户自行导入的模型保留各自许可证，MIT 不替代这些条款。

- Vosk API / Android：Apache-2.0，https://github.com/alphacep/vosk-api
- JNA：LGPL-2.1-or-later 或 Apache-2.0 双许可，本项目按 Apache-2.0 使用，https://github.com/java-native-access/jna
- AndroidX Core、ExifInterface、WorkManager、AndroidX Test：Apache-2.0，https://android.googlesource.com/platform/frameworks/support/
- Kotlin 标准库：Apache-2.0，https://github.com/JetBrains/kotlin
- Google ML Kit 中文 OCR：遵循 Google SDK 的适用条款，https://developers.google.com/ml-kit/terms
- Gradle：Apache-2.0，https://github.com/gradle/gradle

Omi、Memex 与 Auday 为设计参考，实际读取范围及复用边界见 [参考记录](reference-notes/SOURCES.md)。上游源码副本、语音模型、私人记录均不上传此仓库。

## sherpa-onnx and ONNX Runtime

sherpa-onnx 1.13.8 is used through the official Android AAR under Apache-2.0. The AAR SHA-256 is `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`. Source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8. Its bundled ONNX Runtime is MIT licensed. License texts are included in `app/src/main/assets/licenses/`. Models are imported separately and retain their own licenses; they are not included in the APK.
