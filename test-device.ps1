param([string]$Serial='emulator-5554')
$ErrorActionPreference='Stop'
$adbPath='E:\AndroidTools\sdk\platform-tools\adb.exe'
if (-not (Test-Path $adbPath)) { $adbPath='adb' }
Push-Location $PSScriptRoot
try {
 & $adbPath -s $Serial install -r 'app\build\outputs\apk\debug\app-debug.apk'
 if ($LASTEXITCODE -ne 0) { throw '安装应用失败' }
 & $adbPath -s $Serial install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
 if ($LASTEXITCODE -ne 0) { throw '安装测试包失败' }
 New-Item -ItemType Directory -Force 'dist' | Out-Null
 $testResult=& $adbPath -s $Serial shell am instrument -w 'com.lifememory.app.test/androidx.test.runner.AndroidJUnitRunner'
 $testResult | Set-Content 'dist\instrumentation-results.txt' -Encoding utf8
 $testResult
 if (($testResult -join "`n") -notmatch 'OK \(\d+ tests\)') { throw '设备集成测试未通过' }
} finally { Pop-Location }
