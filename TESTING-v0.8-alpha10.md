# v0.8.0-alpha10 — AI configuration and authentication workflow

2026-10-15. Public Android debug pre-release, versionCode 17.

Fixes the DeepSeek preset discarding unsaved typed keys: it now fills form fields without rendering/replacing the form. Save and test validates/saves the current form before the request. Configuration, encrypted key and provider binding use one SharedPreferences commit. An empty key can preserve a readable saved key only for the same provider; switching provider/port requires a replacement key. A 401 focuses the key field rather than claiming retries repair credentials.

The connection check requests /models then performs a bounded synthetic chat generation (64 output tokens; DeepSeek thinking disabled for this check). No private journal/audio is included; small provider charges may apply. Invalid/revoked credentials still require replacement; this release cannot bypass server authentication.

16 JVM tests passed: 5 API validation/provider reuse tests, 7 segmentation tests and 4 speech-options tests. assembleDebug, assembleDebugAndroidTest and lintDebug passed. Lint: 0 errors, 20 warnings. apksigner verified the same certificate as alpha06/alpha07; aapt verified versionCode 17 / 0.8.0-alpha10. Native Android Keystore/form/provisioning tests on the user's phone remain pending.

A fresh owner-provided key returned HTTP 200 for models and a synthetic chat from the development computer, producing a 21-character Chinese recap. This does not establish that a phone has saved the same key or has working network access.

Public APK/source contain no owner key. Optional personalAssetsDir supplies a local owner-only provisioning asset in an untracked directory, imported into Keystore-backed preferences once per deployment. Owner APKs containing such an asset must not be published; embedded credentials remain extractable from those APKs. Public and personal artifacts are separate.
