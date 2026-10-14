$ErrorActionPreference='Stop'
$libraryPath=Join-Path $PSScriptRoot 'app/libs/sherpa-onnx-1.13.8.aar'
$libraryHash='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
New-Item -ItemType Directory -Force (Split-Path $libraryPath) | Out-Null
if (-not (Test-Path -LiteralPath $libraryPath)) {
  Invoke-WebRequest -Uri 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar' -OutFile $libraryPath
}
if ((Get-FileHash -LiteralPath $libraryPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $libraryHash) { throw 'sherpa-onnx AAR checksum mismatch; replace it with the official release asset.' }
Write-Output 'sherpa-onnx 1.13.8 verified.'
