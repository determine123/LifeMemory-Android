param([switch]$Install)
$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME -and (Test-Path 'E:\AndroidTools\java\jdk-17.0.16+8')) { $env:JAVA_HOME='E:\AndroidTools\java\jdk-17.0.16+8' }
$taskTemp=Join-Path $PSScriptRoot '.tool-tmp'
New-Item -ItemType Directory -Force $taskTemp | Out-Null
$previousJavaOptions=$env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=$taskTemp -Djava.io.tmpdir=$taskTemp"
$gradleCommand=if (Test-Path 'E:\AndroidTools\gradle\gradle-8.9\bin\gradle.bat') { 'E:\AndroidTools\gradle\gradle-8.9\bin\gradle.bat' } else { Join-Path $PSScriptRoot 'gradlew.bat' }
Push-Location $PSScriptRoot
try {
 & $gradleCommand --no-daemon assembleDebug lintDebug
 if ($LASTEXITCODE -ne 0) { throw '构建失败' }
 New-Item -ItemType Directory -Force "$PSScriptRoot\dist" | Out-Null
 Copy-Item "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" "$PSScriptRoot\dist\LifeMemory-debug.apk" -Force
 if ($Install) {
  $adbCommand=if (Test-Path 'E:\AndroidTools\sdk\platform-tools\adb.exe') { 'E:\AndroidTools\sdk\platform-tools\adb.exe' } else { 'adb' }
  & $adbCommand install -r "$PSScriptRoot\dist\LifeMemory-debug.apk"
  if ($LASTEXITCODE -ne 0) { throw '安装失败，请检查 USB 调试连接' }
 }
} finally { Pop-Location; $env:JAVA_TOOL_OPTIONS=$previousJavaOptions }