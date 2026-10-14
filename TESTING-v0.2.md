# 拾光 v0.2.0 录音、转写和说话人标注测试

2026-10-13，Android 8.0 / API 26 模拟器。安装包：`dist/LifeMemory-v0.2.0-debug.apk`。本次完整设备测试 **OK (26 tests)**。

## 已通过

构建 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 成功，APK 签名校验通过。完整设备测试结果 **OK (26 tests)**，原始输出在 `dist/instrumentation-results.txt`。

- 旧版数据库迁移，保留原始记录与每日回顾。
- 录音从 UI 启动；持续通知含暂停、标记、停止三个动作。
- 实际操作「标记 → 暂停 → 继续 → 停止」成功；暂停时显示麦克风关闭，暂停前和继续后的片段分别保存，停止后前台服务退出。
- 保留期限清理普通过期音频；保留转写文字、标记关联的音频和手动设为重要的音频。
- 云端响应中的时间和说话人字段解析、Vosk 词级时间转为段落回听位置、转写重试期间保留旧文本。
- 离线 Vosk 原生库在模拟器上加载成功；模型 ZIP 路径越界被拒绝。
- 实际标注界面保存「我」，第二段原有标签不受影响；再次编辑预填最新名称；改为「同事」后数据库持久化成功。
- 实际点击转写段落启动回听播放器，并验证暂停、继续、停止和返回转写界面。
- 本地 OCR、图片 EXIF 日期与方向、历史检索、待办去重、事务回滚等既有功能回归通过。

标注测试发现并修复了二次编辑时输入框仍使用旧名称的问题。

## 测试边界

界面回听测试使用 12 秒静音 WAV 和明确标记的固定转写数据，测试后删除这些临时记录及附件。它验证播放器与标注操作，不代表真实语音识别准确率。

后续通过社区镜像获得兼容中文模型，修复了 Android 8.0 上部分 ZIP 存储条目使用数据描述符导致导入失败的问题。导入改为临时文件加 ZipFile 解压，保留路径边界、压缩/解压体积限制，并验证各文件 CRC。

新增完整推理测试：Windows Huihui 生成的 14.6 秒中文语音 → Android AAC/M4A 编码 → 应用解码 → 本机 Vosk 识别，成功输出 3 段，时间分别为 0.21～5.43、6.27～10.95、11.85～13.92 秒。识别正文完整保留了样本中的开发计划、测试录音和整理会议记录。原始结果在 `dist/real-asr-result.json`。这验证了一条清晰合成语音，不代表真人、噪声环境、多说话人的通用准确率。

使用已授权的 DeepSeek 密钥发送同格式的合成音频请求，`https://api.deepseek.com/v1/audio/transcriptions` 返回 HTTP 404。此前其文字聊天接口返回 200。这证明当前应用的 DeepSeek 音频接口配置不可用；未测试其他厂商的云端 ASR 或自动说话人分离。密钥未保存到项目或日志。

测试用模型来自 `https://hf-mirror.com/guloooovoooo/vosk-model-small-cn/resolve/main/vosk-model-small-cn.zip`，是社区镜像，未与官方模型做校验值比对。SHA-256：`2b34e3c3eb2c61105b1affc4863407965dbf968c63ceb5cd46685f9bd627434a`。可在设置中导入 `dist/LifeMemory-cn-model-tested.zip`；它不嵌入 APK。

未完成实体手机音质、多人复杂对话、长时耗电或跨片段说话人身份一致性测试。手动标注「我」不等于自动声纹识别。

## 手机上验证真实转写

1. 安装 APK，保留现有应用可覆盖升级，无需先卸载。
2. 在设置选择转写方式。离线方式需先下载并导入官方 `vosk-model-small-cn-0.22.zip`；云端方式需明确启用音频上传并配置支持音频转写的接口。
3. 录一段 20～30 秒中文，包含姓名、数字和待办；停止后等待队列完成。对照原声检查遗漏、错字和时间戳，不要只看是否显示文字。
4. 进入录音的「转写与定位」，点击某段验证回听位置。
5. 长按段落，标注「我」或其他说话人。关闭再打开，检查名称保留；再次编辑，检查输入框显示最新名称。
6. 若验证自动分离，需配置支持 `diarized_json` 的语音模型；默认 Whisper 时间戳不提供自动说话人身份。不同片段的 A/B 标签不能直接视为同一个人。

官方模型与文档：[Vosk 模型](https://alphacephei.com/vosk/models)、[Vosk 源码](https://github.com/alphacep/vosk-api)。

## 复现设备测试

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
.\test-device.ps1 -Serial emulator-5554
```
