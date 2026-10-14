# v0.8.0-alpha02 — Local memory retrieval

Date: 2026-10-14. Product workflow reference: [Memos](https://github.com/usememos/memos); discovery directory: [GitHub Store](https://git.hiwiki.org/zh-CN). This is an independent native Android implementation; no upstream source copied.

## Changes

- Search and memory questions share local date/keyword ranking and search record text plus existing place, activity and self-reported mood metadata.
- Unmatched topics return no records rather than unrelated recent history. When AI is configured, an empty retrieval result prevents a chat request.
- English date questions ignore common question filler. Bare dates continue to return recent records within that day/week.
- Search results open the original text or audio transcript/player. AI citations produce source buttons only when their IDs occur in the locally retrieved evidence set; missing/deleted records are handled explicitly.

## Validation

Offline app/test builds passed; lint 0 errors, 12 warnings. Four new regression cases cover absent topics (Chinese/English), English date questions, place/activity retrieval, and filler-only questions. Full emulator result is saved separately after the suite completes.

No live cloud AI call was made for this iteration. Citation rendering compiles but real model responses need validation. Keyword ranking is not embedding-based semantic search; result/context limit remains 120. Tags, safe backup import and sherpa-onnx integration are not added in this build. Previous recording reliability changes are retained.

Full API 26 emulator suite: **45 tests passed** in 35.436 seconds. Raw result: test-results/instrumentation-v0.8-alpha02.txt.
