# v0.8.0-alpha11 — Self-hosted Circle beta

2026-10-16. Android debug pre-release, versionCode 18.

Adds a separate bilingual Circle tab: server configuration, username/password registration with a server invitation code, sign-in/out, username friend requests with explicit acceptance, friend/private posts with up to nine photos, likes, comments, owner deletion, unfriend, account deletion and paginated feed. Journal records are never auto-posted. Shared photos/text use the independent social database. Existing encrypted AI keys remain in local preferences; social sessions are stored separately with Android Keystore encryption.

No public social server has been deployed. All friends need the same independently hosted HTTPS origin. Only exact loopback/emulator HTTP hosts are allowed for local development; remote HTTP is rejected. GitHub Pages hosts the promotional website, not social APIs. Docker/Caddy deployment configuration is supplied but has not been run in this environment. No connected Android device/emulator UI or end-to-end phone test was performed.

Validation: 18 JVM tests passed for AI/provider validation, speech options/chunks and social server addresses. 12 backend tests passed, with one Starlette/httpx test-client deprecation warning. Real loopback HTTP smoke passed registration, friendship approval, feed delivery and comments. assembleDebug, assembleDebugAndroidTest and lintDebug passed. Lint: 0 errors, 20 warnings. apksigner verified the same certificate as alpha06/alpha07; aapt verified versionCode 18 / 0.8.0-alpha11.

Backend fixtures use temporary SQLite databases, synthetic accounts and generated images. Coverage includes hashed credentials/tokens, pending vs accepted requests, friends-of-friends denial, private posts, like/comment/delete permissions, photo permissions after unfriend/deletion, pagination, logout revocation, account deletion cascades, no password reflection in validation errors, signup/login limits, photo orientation/EXIF removal and upload limits.

Limits: beta for small trusted groups, not WeChat integration or end-to-end encryption. All allowed viewers can see comments; no mutual-friend-only comment filtering. No push/video/password recovery. Drafts remain in memory for the current session. Existing journal ZIP backups exclude social-server data; server backups are separate. See social-server/README.md.
