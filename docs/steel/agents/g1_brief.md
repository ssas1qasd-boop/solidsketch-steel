# Brief: Group 1 two-video agent (one agent goes down the Group 1 list, one component at a time)

Read first, in /home/user/solidsketch-steel (the main session's checkout: read only, never edit / build / checkout / reset there):
- CLAUDE.md
- docs/steel/PARITY.md, the sections "Reference model: R13", "Open", "Integration rule for parallel agents", "Group 1 Tekla parity: the two-video method" (your procedure, steps 1-7), "Reverse-engineering method", and the existing section of your component if there is one.
- The shared agent brief: /tmp/claude-0/-home-user-solidsketch-steel/6611724d-ad87-5673-92d6-fdf1e5de3b4b/scratchpad/agent_brief.md (setup, R13 pipelines, goldens, host tests, CI on your branch, recording-test patterns, commit trailer). Where it conflicts with this brief or the integration rule, this brief wins (you do NOT deliver to the owner and you do NOT merge into main).

## Setup
- `cd /home/user/solidsketch-steel && git fetch origin && git worktree add /home/user/wt-g1-<code> -b agent/g1-<code> origin/main`; work only there; push `agent/g1-<code>`.
- Copy reference/solidsketch_v69.html and local.properties from the main checkout into your worktree.

## Order and pace (owner, 2026-11-28; PARITY.md "Group 1 ... Order")
- Family by family; finish a family before the next. End plates: EP: T142, T101, T29, T32, T24, T34, T115, T27s, T65, T97, T111, T112, T119, T48, T50; then CP: T14, T2, T27; then SD: T1002, T1020, T1026, Tcap. Then Shear tabs, then Clip angles (lists in PARITY.md).
- Do ONE component per run: when it is green on its branch, report to the main session and stop. The main session merges it, runs the suite and CI on main, delivers, and then sends you "next" with the next code. Start each component from fresh origin/main in a new worktree / branch agent/g1-<code>.
- BLOCKED: if a component cannot be finished (fewer than 1 usable video and no help-page example, an engine change outside your scope, Gemini / Apify quota gone for the day with no other way, rule B still failing after 5 loops), stop, push what you have, and report it as BLOCKED with the cause. The main session moves it to the end of its family's list.
- The first component of a family (T142 now) sets the pattern for the rest: keep its golden / recording / composer code reusable (as T144RecordingBase did).
- T144 (done, on main) is your worked example: web/v70/t144.js, tools/golden/cases/t144.js (t144_v1 / t144_v2), T144RecordingBase / T144V1RecordingTest / T144V2RecordingTest, parity_png.py t144 composer, tests/steel/videos/T144/, PARITY.md "T144 · End plate (144): the two-video test".

## Video-faithful cases (owner, 2026-11-28; PARITY.md "Video-faithful cases")
- Build each video's example with the video's OWN values, even where they break a rule. If such a case has rule-B findings that come from the video's values, mark the case `videoFaithful: true`: the rule-B gate skips it. Never put it on ruleB_known.txt.
- Give it `videoStatus` (see t144_v1 / t144_v2): `tekla` = what Tekla shows in the video (status bar text + time, symbol colour if you can read it from a frame), `expect` = the warnings our rule checks must give (check / plate / row), `level` only when the video shows the colour, `noWarning` when Tekla shows no warning for a clash. Check the status bar and the symbol on frames, not only Gemini text. `node tools/golden/video_status.js` must pass and S7StatusTest.videoStatusEqualsWeb must pass (app = web).
- A rule-B finding that does NOT come from the video's values is ours: fix it.
- Rule checks are shared code: if ours give a warning Tekla does not (or miss one Tekla shows), report it with the frame; do not change tkStatus / TkCheck yourself.

## Your work (PARITY.md two-video method, steps 1-5)
1. Videos:
   - Tekla support site first (tools/video/find.py <help slug> ...).
   - Then the YouTube Data API (env YOUTUBE_API_KEY; search.list with q = "Tekla <component name> <number>" variants, then videos.list for duration / channel).
   - Pick 2 different channels or examples. Analyse each with Gemini (tools/video/gemini_read.py / gemini_video.py) in two passes, the second at high resolution on the dialog frames. Use Apify only as a fallback.
   - Write tests/steel/videos/<CODE>/VIDEO_1.txt and VIDEO_2.txt exactly as the rule says.
   - If there are fewer than 2 usable videos, use the Tekla help page example as the second and say so. Never invent values.
2. Settings check against video 1, tab by tab: options, defaults, labels, units. Fix every difference in both web v70 and the app.
   - Integration rule: write your web code in its own section headed `// == <CODE> ==` or in `web/v70/<code>.js` (the build appends web/v70/*.js).
   - Shared code changes (core, catalog structure, rule checks, weights) are NOT yours: describe them in your report.
   - Dialog definitions per component (TKD entries, CONN defaults) are yours.
3. Rebuild video 1's example (the same profiles, angles, offsets, values) as a golden case (tools/golden/cases/<code>_v1 in the right cases file; run_cases + c09 dump).
   - Compare part by part with the video, and run rule B (geom_checks.py: contact <= 1 mm, no floating part, overlap >= 0.01 mm fails, every bolt through a hole, grip = stack +-2 mm).
   - Loop fix -> rebuild until it matches. After 5 loops still differing: stop and report the cause.
   - R13: if R13 has instances of your component, they must stay within 0.1 mm (R13 rebuild report rows); say how many R13 instances there are (0 is a valid answer).
4. Video 2: the same; video 1 must stay matched. Both become goldens (also in the app golden test that covers your component, e.g. S7T144Test / CompGoldenTest), next to the std / odd / skew goldens and R13. Everything stays green, including the R13 rebuild (839 / 839 members, no new NEAR or MISSING).
5. Emulator: one recording test per example, building it through the real dialog with the video's values: <CODE>V1RecordingTest and <CODE>V2RecordingTest, 30-60 s each, orbit front / end / top / 3D, orthographic stills, saved .ss.
   - Add them to the notClass list and the adb pull loop in tools/ci/emulator_run.sh (lowercase names <code>v1 / <code>v2).
   - Set REC_CLASSES on your branch to these two.
   - Run CI on your branch until green, and look at the frames.
   - Recordings for delivery are 30-60 s: if the raw recording is longer, also save a sped-up copy (as T144 did) and keep the raw one as *_recording_full.mp4.
   - Add a composer to tools/golden/parity_png.py making <CODE>_v1.png / <CODE>_v2.png: video frame | app, the same view, labelled, with dimensions.

## Finish: report to the main session (do not merge, do not deliver)
- Rebase onto origin/main, regenerate what your change touches, run `bash tools/ci/full_suite.sh` (it must end green; read build/suite/summary.txt), and push agent/g1-<code>.
- Put the evidence in /tmp/claude-0/-home-user-solidsketch-steel/6611724d-ad87-5673-92d6-fdf1e5de3b4b/scratchpad/g1/<CODE>/:
  - the two .ss models the emulator saved, named <code>_v1.ss / <code>_v2.ss;
  - the two recordings <code>_v1_recording.mp4 / <code>_v2_recording.mp4;
  - <code>_v1.png / <code>_v2.png;
  - VIDEO_1.txt / VIDEO_2.txt;
  - REPORT.md: the settings differences found and fixed, the loops per video, rule-B results, golden status, before -> after, and anything not verified.
- Final reply: branch, commit, CI run id + status, what changed (files / sections), the goldens added, and the REPORT.md content.

SECURITY: APIFY_TOKEN, GEMINI_API_KEY and YOUTUBE_API_KEY are set in the environment. Never print them, never write them to a file, a log, a URL you save or a commit.

## Standing owner rules (2026-11-28)
- main is merge-only: never rebase, reset --hard or force push main. Rebase only your own branch. If a git command stops halfway, abort it and say so in your report.
- tools/ci/ruleB_known.txt: never add an entry. If a rule-B finding looks like Tekla's own geometry, report it (case, count, proof that R13 has the same finding); the main session asks the owner.
- Parked, do not work on: the 6 C06 gussets at 0.126 mm (never widen the 0.1 mm tolerance), EB4007, phone checks.

## Time tracking (owner, 2026-11-28; PARITY.md)
Keep a time log while you work: the UTC start (when you start picking videos) and the UTC end (when your branch is green and ready to report). Note every waiting stretch separately: CI queue or CI running while you can do nothing else, usage limits, session paused. These go in "wait hours", not in "hours". In REPORT.md add one line in this exact form:
| <code> | <family> | <agent> | <start UTC> | <end UTC> | <hours> | <wait hours> | <videos found 0/1/2> | <loops> | <blocked by: what cost the most time> |
The main session adds the end of the merge / delivery to it.
