# 验证记录

日期：2026-10-13。

## 构建与静态检查

- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 成功。
- `build.ps1` 运行成功。
- Android Lint：0 errors，3 warnings。两项提示是已固定依赖存在新版本；另一项是通用图标资源的 monochrome 提示，API 33 专用资源已经提供 monochrome 图标。
- APK 签名通过 `apksigner verify`（v2）。包名 `com.lifememory.app`，版本 `0.1.0` / 1，minSdk 26，targetSdk 35。
- APK 包含 arm64-v8a、armeabi-v7a、x86、x86_64 原生库。构建采用 JDK 17、Gradle 8.9、AGP 8.7.3 和 SDK 35。

## Android 8.0 / API 26 模拟器

设备集成测试：**OK (12 tests)**，原始输出在 `dist/instrumentation-results.txt`。

- SQLite：本地日期边界、跨日期搜索与源记录保留、待办去重且保留完成状态、失败事务回滚、孤立总结/待办仍进入备份日期集合、媒体导出路径可迁移。
- 日期和关键词检索：昨天的事件不会被今天大量录音挤出，旧的中英文主题优先于无关新事件，上周按上一自然周筛选。
- 图片：EXIF 方向与缩放、使用原始拍摄日期。
- ML Kit：在实际模拟器中识别测试图片中的 OpenClaw 会议文字，未调用云端接口。

测试发现并修复了旧版 Android 不支持 `IsHan` 正则属性的问题；修复后完整 12 项回归通过。

## 实际操作检查

- Activity 启动成功；首页截图见 `dist/preview.png`，已经视觉检查布局。
- 在 UI 中创建 `OpenClaw meeting plan` 测试文字，保存成功。
- 未配置 AI 时，点击「整理这一天」生成本地回顾。
- 从 UI 明确启动录音；系统服务状态显示 `isForeground=true`，有持续通知及停止动作。
- 录音跨过两次一分钟分段，保存三个 M4A 文件：247,533、246,748、28,828 字节。点击「停止记录」后服务列表为空，最后一个片段也被保存。
- 从设置页经系统文件选择器保存备份；应用显示导出成功。导出 ZIP 实际包含 4 条事件、当天总结、3 个音频附件，已打开并检查 JSON 与 ZIP 条目。
- 这些测试数据仅存在于测试模拟器中，不会预装进交付 APK。

## 尚未验证

- 模拟器关闭宿主音频输入/输出，验证了录音编码、分段、前台服务生命周期和文件保存；真实麦克风音质、12 小时连续运行、耗电及厂商后台限制仍需实体手机测试。
- 云端转写、视觉理解、每日 AI 总结和 AI 问答已接入，未使用真实 API Key 调用，因此尚未验证服务兼容性与生成效果。
- API 35 完成编译与静态检查，未完成 Android 15 的运行验证。
- 这是可安装的开发测试版，未执行应用商店发布、正式签名或多设备兼容性验收。

## 复现

```powershell
.\build.ps1
# 先编译设备测试包：
.\gradlew.bat assembleDebugAndroidTest
# 在已启动的测试模拟器运行：
.\test-device.ps1 -Serial emulator-5554
```
