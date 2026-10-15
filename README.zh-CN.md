<div align="center">

# 拾光 LifeMemory

**一天，四次回顾。每一刻，有迹可循。**

本地优先的安卓生活记录：声音、照片和文字，整理为可追溯的简短回顾。

[下载安卓测试版](https://github.com/determine123/LifeMemory-Android/releases/download/v0.8.0-alpha08/LifeMemory-v0.8.0-alpha08-debug.apk) · [中文官网](https://determine123.github.io/LifeMemory-Android/zh.html) · [English](README.md)

Android 8.0+ · 原生 Java · MIT 开源 · 使用自己的 AI 服务

</div>

## 为什么使用拾光

- **四时段回顾。** 早上 00–11、中午 11–14、下午 14–18、晚上 18–24，每段最多五十个 Unicode 字符（含标点）。分钟录音放在时段详情，首页直接看概览。
- **回忆有依据。** 事件和待办可返回原文或原声，支持拖动回听、倍速和手动纠正说话人。
- **本机转写，自选 AI。** 导入 Vosk 中文或可选 sherpa-onnx SenseVoice 与说话人模型；语义回顾使用自己配置的兼容接口。
- **处理进度可见。** 查看等待、进行、失败、已完成记录，按条重试。
- **保留与恢复。** 保护重要录音，搜索历史转写，按主题回看，导入 ZIP 备份并合并记录和附件。

## 三步开始

1. 下载 **v0.8.0-alpha08 调试预发布版**。已有版本请覆盖安装，保留数据。应用默认英文，可切换中文并记住选择。
2. 录音、添加照片或文字。离线转写需[单独导入模型](SHERPA-MODELS.md)，APK 不包含模型。
3. 配置自己的 AI 服务生成语义回顾。DeepSeek 用于对话和总结，对话密钥不代表支持音频转写；参见[配置排查](TROUBLESHOOTING.md)。

每个时段结束后自动整理，安卓可能延迟后台任务；也可手动更新一个时段或全天四段。未配置 AI 时显示真实记录数量。

## 记录 → 转写 → 回顾

前台通知录音，支持暂停、继续、重要标记和 1 / 3 / 5 分钟分段保存。可导入或分享系统录音机导出的音频。自动记录需主动启用，手动停止会关闭自动模式；新版安卓重启后需打开应用恢复，系统后台限制可能中断录音。

可选 sherpa-onnx 在独立语音进程运行。说话人编号只对应当前录音，不是身份识别；自动人数可能过度分离，可指定人数或手动纠正。支持本地中文 OCR，地点、活动与心情为手动填写。

## 数据与隐私

记录和离线模型保存在应用私有目录。云端转写发送音频，AI 回顾与问答发送相关文字，视觉分析发送所选照片；离线失败不会偷偷切换云端。密钥使用 Android Keystore 加密，数据库没有额外应用层加密，导出 ZIP 为明文。时段概览缓存可在恢复后重新生成。

## 验证状态

当前版本 **7 项 JVM 分段测试通过**；原生模型与设备回归待完成，参见[最新验证报告](TESTING-v0.8-alpha08.md)。上一版 alpha04 全套 61 项测试通过。小米 13 Ultra USB 真机、真实噪声准确率、耗电与厂商后台限制仍待验证，不保证全天无间断录音。

## 构建与宣传网页

需要 JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.9、AGP 8.7.3。设置 `ANDROID_HOME` 或未跟踪的 `local.properties`：

```powershell
./fetch-dependencies.ps1
./gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。不要提交密钥或私人录音。

中英文宣传页随项目附在 [docs/](docs/README.md)，默认英文。`python -m http.server 8767 --directory docs` 可预览，`node docs/check.mjs` 检查交互；GitHub Pages 使用 `main /docs`。网页演示记录为虚构。

## 联系与参与

[提交 Issue](https://github.com/determine123/LifeMemory-Android/issues) · [贡献 PR](https://github.com/determine123/LifeMemory-Android/pulls) · [determine@sjtu.edu.cn](mailto:determine@sjtu.edu.cn)

[B 站](https://space.bilibili.com/1553355526) · 抖音号 **ltm1524001843**（搜索关注）。

## 参考与许可

原生 Java 独立实现。生活记录流程参考 Omi、Memex、Auday，处理进度参考 Orca；本次项目介绍参考 [Tether](https://github.com/tt-11-dd/tether-ai) 的产品说明结构和本地/云端选择表述。无关联关系，不包含其编程 Agent 功能。

[MIT](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md) · [参考范围](reference-notes/SOURCES.md)

## 相关项目与检索筛选

[项目筛选与落地说明](reference-notes/PROJECTS-2026-10-14.md)。搜索支持 `type:audio`、`topic:工作`、`pinned:true`、`period:evening`、具体日期与“昨天晚上”等时段表达。

源码已加入 SenseVoice 停顿分段，7 项 JVM 算法测试通过；alpha08 APK 包含本次改动，原生模型与设备回归待完成。 [2026-10-15](reference-notes/ASR-IMPROVEMENTS-2026-10-15.md)
