# 上游参考记录

读取日期：2026-10-13。

## Omi

仓库：https://github.com/BasedHardware/omi

读取提交：`078999b2fc098b1425bcf3367a2f86b86dec223f`。

具体文件：`app/android/app/src/main/kotlin/com/friend/ios/phonemic/PhoneMicForegroundService.kt`。

参考显式前台服务启动、通知渠道、权限失败后的停止处理、START_NOT_STICKY 和通知返回应用的设计。应用内以独立 Java 代码实现，录音使用 Android MediaRecorder。

上游 LICENSE 为 MIT，Copyright (c) 2024 Based Hardware Contributors。

## Memex

仓库：https://github.com/memex-lab/memex

读取提交：`4401c41603dcbf603b8e215ba4d7370b7773c954`。

具体文件：`lib/domain/models/timeline_card_model.dart` 以及根目录 `pubspec.yaml`。

参考时间戳、原始文本、媒体资产、处理状态共同组成时间线卡片，本地数据库与中文 ML Kit OCR 的技术选择。应用内以独立 SQLite 模型和原生 UI 实现；未复用 Dart 实现代码。

上游 LICENSE 为 GPL-3.0。原始源码参考副本不参与本项目编译或交付。

GitHub 部分连接不稳定，未完整下载上游所有模块；以上明确列出实际读取的代码范围。

## Auday 与本机离线语音

参考用户提供的《Auday参考项目.txt》及 https://auday.ai/ 的产品思路：重要时刻、原声回听、本地处理与每日回顾；没有获取或复制 Auday 源码。

离线语音使用 https://github.com/alphacep/vosk-api 的 Vosk Android 0.3.75 与 JNA 5.18.1。模型须自行导入，官方列表：https://alphacephei.com/vosk/models 。官方模型下载曾发生 TLS 连接失败，随后通过社区镜像获取模型完成中文合成语音的实际离线推理测试，详见 TESTING-v0.3.md；不代表真机或多人识别准确率。

