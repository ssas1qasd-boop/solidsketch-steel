# Group 1 time tracking (PARITY.md "Group 1 Tekla parity: the two-video method", time tracking)

Hours = real work only, from picking the videos to "accepted (pending phone OK)". Wait hours = session paused, usage limits, CI queue.

| code | family | agent | start (UTC) | end (UTC) | hours | wait hours | videos found (0/1/2) | loops | blocked by |
|---|---|---|---|---|---|---|---|---|---|
| T144 | End plates (EP) | g1-t144 | 2026-11-27 21:03 | 2026-11-27 22:33 | 0.9 | 0.6 | 2 | v1: 2, v2: 1 | Gemini free quota (full models used up mid-run, the rest read with the lighter model) and the Apify monthly limit (HTTP 403, no frames for video 2). Merged 2026-11-27 23:45 (a575a45), CI on main green 2026-11-28 00:20 (run 46084338482); delivery with the next component |
| T142 | End plates (EP) | g1-t142 | 2026-11-27 23:33 | 2026-11-28 00:46 | 0.9 | 0.3 | 2 | v1: 2, v2: 1 (+3 on the default cases) | Gemini full-model quota and Apify limit gone: lite-model reads only, no frames (video 2's profiles stand-ins); CI wait. Merged 2026-11-28 (5f8378f), CI on main green (run 46091133130); delivery held for the owner's decision on re-reading the videos with the new Gemini key |
