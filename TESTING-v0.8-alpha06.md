# v0.8.0-alpha06 — Source-preserving memory filters

2026-10-14. Android debug pre-release, versionCode 13.

Adds type, exact topic, pinned and period filters, combined with existing dates and keyword matching. Dated natural-language periods support yesterday evening / 昨天晚上. Fixes exact-date removal before keyword tokenization. Existing result IDs and source navigation retained. No SQLite schema change or cloud request required for search.

15 targeted emulator instrumentation tests passed (10 MemorySearchTest, 5 DayPeriodsTest), including exact dates, 18:00 boundary, Chinese/English periods, exact case-insensitive topics and pinned/type combinations. Offline build and lint passed. Tests ran on the same search/UI sources before the version-only increment; final packaged version is alpha06. Prior 61-test alpha04 suite is historical, not a full-suite alpha06 run. Xiaomi 13 Ultra USB evaluation remains pending.

Reference selection: reference-notes/PROJECTS-2026-10-14.md. No newly reviewed project source code copied.
