# Live function chains (v69 steel code)

In the web build, later chapters wrap earlier functions: `const prev = stConnGeom; stConnGeom = function (c) { …; return prev(c); }`. **Port the behaviour of the whole chain as it stands at the end of the file, not the first definition.** Each step below either saves the current function under another name (`saved-as`) or replaces it (`reassign`). The last `reassign` is the live entry point, and it calls the earlier ones it saved.

Line numbers refer to `reference/solidsketch_v69.html` (DEV copy, 17,999 lines). Regenerate with `python3 tools/inventory/chains.py reference/solidsketch_v69.html`.

| Function | Steps | Chain (step, line, chapter) |
|---|---|---|
| `stConnGeom` | 23 | def L13913 (v34) → saved-as stConnGeom3 L14050 (v35) → reassign L14051 (v35) → saved-as stConnGeom4 L14269 (v38) → reassign L14270 (v38) → reassign L15167 (v40) → saved-as stConnGeom8 L15167 (v40) → saved-as stConnGeom11 L15252 (v42) → reassign L15253 (v42) → saved-as stConnGeom12 L15326 (v43) → reassign L15327 (v43) → saved-as stConnGeom13 L15376 (v44) → reassign L15377 (v44) → reassign L15639 (v49) → saved-as stConnGeom18 L15639 (v49) → reassign L15652 (v49) → saved-as stConnGeomSR L15652 (v49) → reassign L15845 (v54) → saved-as stConnGeom22 L15845 (v54) → saved-as stConnGeom57 L15991 (v58) → reassign L15992 (v58) → saved-as tkPrevGeom L17090 (v64) → reassign L17091 (v64) |
| `stOwner` | 17 | def L14113 (v36) → saved-as stOwner5 L14331 (v38) → reassign L14332 (v38) → reassign L15168 (v40) → saved-as stOwner8 L15168 (v40) → reassign L15267 (v42) → saved-as stOwner11 L15267 (v42) → reassign L15352 (v43) → saved-as stOwner12 L15352 (v43) → reassign L15398 (v44) → saved-as stOwner13 L15398 (v44) → reassign L15531 (v48) → saved-as stOwner17 L15531 (v48) → reassign L15638 (v49) → saved-as stOwner18 L15638 (v49) → reassign L15885 (v54) → saved-as stOwner22 L15885 (v54) |
| `stChecks` | 9 | def L14187 (v37) → saved-as stChecks18 L15682 (v49) → reassign L15683 (v49) → saved-as stChecks19 L15704 (v50) → reassign L15705 (v50) → saved-as chk0 L17140 (v65 (round 7c)) → reassign L17141 (v65 (round 7c)) → saved-as chk65 L17244 (v66 (round 7c)) → reassign L17248 (v66 (round 7c)) |
| `stTap` | 7 | def L13692 (v32) → saved-as stTapPrev L14017 (v34) → reassign L14018 (v34) → saved-as stTap7 L14402 (v38) → reassign L14403 (v38) → saved-as stTap18 L15674 (v49) → reassign L15675 (v49) |
| `stIcon` | 7 | def L14350 (v38) → reassign L15194 (v40) → saved-as stIcon9 L15194 (v40) → reassign L15789 (v53) → saved-as stIcon21 L15789 (v53) → reassign L16400 (v58) → saved-as stIcon58 L16400 (v58) |
| `stPanel` | 5 | def L13754 (v32) → saved-as stPanel7 L14399 (v38) → reassign L14400 (v38) → saved-as panel0 L17237 (v66 (round 7c)) → reassign L17238 (v66 (round 7c)) |
| `stPortalParts` | 5 | def L13831 (v33) → saved-as stPortalParts11 L15268 (v42) → reassign L15269 (v42) → saved-as stPortalParts12 L15353 (v43) → reassign L15354 (v43) |
| `stPortalConns2` | 5 | def L14100 (v35) → saved-as stPortalConns2_11 L15292 (v42) → reassign L15293 (v42) → saved-as stPortalConns2_12 L15365 (v43) → reassign L15366 (v43) |
| `stCompDialog` | 4 | def L14429 (v38) → saved-as stCompDialog18 L15679 (v49) → reassign L15680 (v49) → reassign L16304 (v58) |
| `stProf` | 3 | def L13577 (v32) → saved-as stProf10 L15202 (v41) → reassign L15203 (v41) |
| `stSection` | 3 | def L13591 (v32) → saved-as stSection10 L15216 (v41) → reassign L15217 (v41) |
| `stBuild` | 3 | def L13605 (v32) → saved-as stBuild10 L15227 (v41) → reassign L15228 (v41) |
| `stHint` | 3 | def L13745 (v32) → reassign L14398 (v38) → saved-as stHint7 L14398 (v38) |
| `stListSheet` | 3 | def L13816 (v32) → saved-as stListPrev L14030 (v34) → reassign L14031 (v34) |
| `stBolts` | 3 | def L13904 (v34) → saved-as stBolts57 L15954 (v58) → reassign L15956 (v58) |
| `stWeldsDraw` | 3 | def L13994 (v34) → reassign L15677 (v49) → saved-as stWD18 L15677 (v49) |
| `stSymDraw` | 3 | def L14416 (v38) → reassign L15663 (v49) → reassign L16082 (v58) |
| `dwAssembly` | 3 | def L14584 (v39) → saved-as dwAssembly55 L15904 (v56) → reassign L15905 (v56) |
| `stStairs` | 3 | def L15399 (v44) → saved-as stStairs19 L15726 (v50) → reassign L15727 (v50) |
| `stRailing` | 3 | def L15405 (v44) → saved-as stRailing19 L15722 (v50) → reassign L15723 (v50) |
| `stConnSheet` | 2 | def L14000 (v34) → reassign L14450 (v38) |
| `dwHoles` | 2 | def L14553 (v39) → reassign L15933 (v56) |
| `stSymPos` | 2 | def L15658 (v49) → reassign L16071 (v58) |
| `stSymHit` | 2 | def L15669 (v49) → reassign L16099 (v58) |

## Stale `window.__st` exports (found in S1)
Some exports were captured before the last reassign, so they call an early link of the chain: `__st.connGeom` (L14039, v34 version; live chain ends L17091), `__st.build` (L13827; ends L15228), `__st.portalParts` (L13880; ends L15354), `__st.dwg.holes` (L14852; ends L15933). Do not use them as reference behaviour. The golden harness adds live wrappers as `__st.live.*` (see `tools/golden/README.md`).
