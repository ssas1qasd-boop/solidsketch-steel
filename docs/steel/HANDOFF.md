# Handoff (session of 2026-11-27 / 28 → next session)

Read first: `CLAUDE.md`, `docs/steel/PARITY.md` (reference R13; integration rule 1–7; parked items; Group 1 two-video method, order, video-faithful cases, time tracking), this file. Agent briefs: `docs/steel/agents/g1_brief.md` (Group 1 agent) and `docs/steel/agents/agent_brief.md` (setup, pipelines, CI, recording-test patterns). They point to the old session's scratchpad for evidence folders: use the new session's scratchpad instead.

## Where things are
- **main:** merge-only (no rebase, reset --hard or force push, by anyone). Head at handoff: the merge of `session/handoff-2026-11-28` (this file, the delivery report, the R13-REPORT status section, the agent briefs) on top of `e9a243c`.
- **Delivered code commit:** `f4db04a` (APK `0.26.0-parity`, versionCode 37; CI run 46091133130 green).
- **Agent branches** (all merged, kept for history): `agent/c19`, `agent/g1-t144`, `agent/g1-t142`. No agent is running.
- **Full suite** (`bash tools/ci/full_suite.sh`, `build/suite/summary.txt`): every step ok except the parked gable-node checks: `ruleB_cases` (`t20g_r13_gable_vl_lead`, 2 bolts) and `ruleB_r13_app` (4 gable-node bolts with 2.3 mm air, not on the baseline). 0 depth drifts.
- **R13 rebuild:** 839 / 839 members, details 3,701 matched / 6 near / 0 missing, web 64,014.62 kg; 160 assemblies / 416 drawings / 4,596 bolts.

## Delivered (all 🟡, waiting for the owner's phone OK)
| Delivery | Commit | What |
|---|---|---|
| `docs/steel/parity/DELIVERY_2026-11-28.md` | 1ebb96d | C08, C10, C13, C12/C20/C14 at the fixed girders, C18, C05, C06 (6 gussets open), C07, shared weight rules |
| `docs/steel/parity/DELIVERY_2026-11-28_2.md` | fd71ed7 | C19 railing stanchions (26/26 = R13; 26 Hand Rail / Stanchion baseline lines with R13's depth, approved) |
| `docs/steel/parity/DELIVERY_2026-11-28_3.md` | f4db04a | Group 1: T144 (accepted, pending phone OK) and **T142: video evidence provisional (read without frames, quota-limited); no ✓ until re-read** |

Status tables: `docs/steel/R12-REPORT.md` (connections C01–C20, X01–X02) and `docs/steel/R13-REPORT.md` (Group 1), both in their `<!-- owner-status -->` sections (kept through regeneration). Time rows: `docs/steel/parity/TIMES.md`.

## Gemini key
The owner replaced `GEMINI_API_KEY`; the old session still had the old one. The old key's hash: sha256 first 12 hex = `0af73a6b3408`, length 53. In the new session, check without printing the key:
`printf %s "$GEMINI_API_KEY" | sha256sum | cut -c1-12` must differ from `0af73a6b3408`, and a one-word `generateContent` call to `gemini-flash-latest` (header `x-goog-api-key`) must return 200, not 429. Never print or store the key.
Apify: monthly limit used up (USD 5.41 of 5) as of 2026-11-28. YouTube refuses yt-dlp and the watch page from this environment; only `i.ytimg.com` stills (maxres1/2/3, maxresdefault) can be fetched.

## Next steps, in this order
### 1. Re-read T142 with the full Gemini model (owner, 2026-11-28)
Both videos, two passes (pass 2 at high resolution, 1 fps, 60–90 s pieces on the dialog), the full model (`gemini-flash-latest` or newer full model; record which). Get frames if any way works (Apify after its monthly reset, or Gemini's own frame times + stills); otherwise say so again.
- **Video 1** `_Au1wpEDNzw` (Bangladesh Civil Engineering, Tekla 2017, mm). The 142 part 09:11–14:16. Check:
  - profiles: column HEB300, two IPE300 on the column's flanges, in line (09:18, 10:50; 12:10–12:17); levels / spans if readable;
  - Picture 1–4 blank and 5 = 2.00 / 2.00 (09:49 / 09:55; one lite read put 2.00 in 1–4 too);
  - Plates 1 / 2: t 16, b 150, h 450 (10:01–10:51);
  - Bolts: M16, tolerance 2, vertical reference Top → Middle, edge 45, 5 rows at 90 (11:02–11:32); horizontal Middle, edge 35, 2 at 80 (11:55–12:05); the bolt standard;
  - Haunch: 4 plates t 6 × 75 × 75, chamfers 1–4 = 75 (12:30–13:54); the creation lists (not read so far);
  - the status bar after Modify (12:05: "Information: polygon depth value put as zero", no warning) and the component symbol's colour;
  - whether the bolts really run across the whole column (the golden's 10 rule-B findings, video-faithful: 263.8 mm of air on each shank).
- **Video 2** `J2OINltOa4I` (Gani sk, imperial). The 142 part 00:57–04:25. Check:
  - the profiles (00:05–00:50; never named so far; HEB300 / IPE300 stand in, marked not from the video). If the video shows W shapes, say which; they are not in the catalog;
  - Plates 1 / 2: t 1/2" (01:50, 02:05), b / h blank;
  - Bolts: 5/8" (→ M16), tolerance 1/16", Site, round, references Middle / Middle (02:22–02:52); final vertical edge 3 1/2", 3 bolts at 5"; horizontal edge 2", 2 bolts at 3 1/2" (Modify 04:11);
  - the status bar and symbol after 04:25.
- Write the results into `tests/steel/videos/T142/VIDEO_1.txt` / `VIDEO_2.txt` (mark what changed from the lite reads).
- **T144's open video points, in the same pass:**
  - T144 video 2 `IBVnHuj1F-s`: save the 19:38 frame with the status bar "Bolt edge distance of the bolt … is too small" and the symbol (the owner says yellow); store it under `tests/steel/videos/T144/` and add it to `t144_v2`'s `videoStatus.tekla`. Video 2's dialog values were also read by the lighter model only (VIDEO_2.txt): re-check them.
  - T144 video 1 `VLQiysAuEr4`: the symbol colour at the final state (the frame f_1490 at 24:50 shows the selected cone drawn green; ours is yellow). If Tekla's is green while the status bar warns, report it to the owner before changing anything.

### 2. Fix T142 (and T144) if the re-read differs
Same method as before (agent on `agent/g1-t142` from fresh main, or the main session): fix in `web/v70/t142.js` + `FamT142.kt`, regenerate `t142_v1` / `t142_v2` (and `video_status.json`), all goldens green, recordings, CI, then a combined delivery. When T142 matches with full-model reads, the owner can give the ✓; change its R13-REPORT row from "provisional" to "accepted (pending phone OK)".

### 3. T101, then the rest of the EP family
Order (PARITY.md): EP: T101, T29, T32, T24, T34, T115, T27s, T65, T97, T111, T112, T119, T48, T50; then CP: T14, T2, T27; then SD: T1002, T1020, T1026, Tcap; then Shear tabs, then Clip angles. One agent, one component per run (`docs/steel/agents/g1_brief.md`); BLOCKED moves to the end of its family. Combined delivery at ≥ 2 components or once a day. After the first 5 accepted components the delivery reports carry the forecast.
- For the EP family, reuse: `T142RecordingBase` (three-part pick) / `T144RecordingBase`, the `twovideo` composer in `tools/golden/parity_png.py`, `videoFaithful` / `videoStatus` in the cases files.
- Web files sort by name: a component file that sorts before `t144.js` is wrapped by T144's wrapper (T142 writes its plates itself for that reason).

## Open items
- **Parked (after Group 1, one task "gable nodes"):** the 6 C06 gussets at 0.126 mm (never widen the 0.1 mm tolerance); the gable-node bolt air (4 bolts in the R13 rebuild, `t20g_r13_gable_vl_lead`); the hand rail depth bias (ours 0.007–0.099 mm deeper than R13 on all 26 Hand Rail / Stanchion overlaps; check the CHS hand rail mesh facets / orientation and the last-stanchion end; report the cause before changing anything).
- **Parked:** EB4007 (Tekla's own error, R14 later); phone checks.
- **Rule checks (shared, open):** any hole near a bolt line still counts as that bolt's own (T25 / T33 / T117 odd profile: 10 mm reported, 4.4 mm true). See PARITY.md "Video-faithful cases".
- **Shared items from T144, not changed:** Picture gap default 2 (Tekla 0) for T144; default bolt length still the v69 rule (the video goldens use Tekla's); bolt standards 6914 / A325M / A325N and material St 52 not in the catalogs; T144 video 2's four extra Haunch boxes (= 10) unread; T186 probably needs the same stiffener-position sign fix.
- **From T142:** default gap 0 / 0 for T142 only; only components saved after v70 (`bgrp: 'one'`) take the new path.
- `tools/ci/ruleB_known.txt` is empty and stays R13-only; no entry without the owner's OK. `tools/ci/ruleB_r13_baseline.txt`: 68 approved lines; new lines only with the owner's OK and R13 proof.
- CLAUDE.md owner decisions still open: applicationId / license variants; Tekla `.db1` import.
- CI: `tools/ci/emulator_run.sh` `REC_CLASSES` on main = T144 v1/v2 + T142 v1/v2; set it to the components being delivered.
