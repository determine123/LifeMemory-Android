# v0.8.0-alpha04 — Four-period timeline

Date: 2026-10-14. Android debug pre-release.

The homepage presents four period cards: Morning 00–11, Midday 11–14, Afternoon 14–18, Evening 18–24, using device-local calendar time. Each recap is at most 50 Unicode characters. Raw recordings are retained and available through period details, transcript playback and editing. Existing daily summaries remain in the database. Today's tasks remain visible and completed states are preserved.

Automatic organization is enabled by default and uses four WorkManager jobs, initially targeting 11:00, 14:00, 18:00 and midnight. Android may delay execution. Delayed jobs summarize the most recently completed matching period. The previous daily-only job is cancelled. Manual update is available per period or for all four. Automatic post-transcription recaps only refresh finished periods when the queue drains; current minutes no longer trigger a daily recap.

Recaps use validated source IDs and fingerprint/language caches. Changed evidence marks a period stale. Unconfigured AI shows local counts, not semantic claims. Period caches are derived private preferences and are not in ZIP backups; regenerate them from restored source records. Original records and sourced tasks remain backed up.

61 emulator tests passed. New tests cover exact 11/14/18 boundaries, midnight evening attribution, delayed scheduling, separate local caches with edit invalidation, and four homepage cards hiding minute-source text. Existing native speech, database migration, backup, playback and recording tests also passed. Raw result: test-results/instrumentation-v0.8-alpha04.txt.

Offline build and lint passed (0 errors, 16 warnings). No live cloud period-summary call or real-device scheduling/power test was performed. Xiaomi 13 Ultra USB validation remains pending. Background timing is not guaranteed to the minute.

APK SHA-256: 66465413d5bcfd9a9bb36bf6f346647f87dcec9d2a1901c6d6688e78ccffbcc5
