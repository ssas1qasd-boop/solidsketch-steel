# Frame 28×53 m: SolidSketch R04 vs Tekla, connection by connection

The Tekla values come from the project's NC files (DSTV), part lists and assembly drawings. The way of working (pick order and tabs) comes from Tekla detailing videos:
- Industrial Shed P1 `qorGh_pRbwQ`
- Maidul A-Z P2 `rsV1MSj-C1w`
- Maidul A-Z P8 `S39uwXiyW1k`

SolidSketch values were measured on `/home/claude/outputs/Frame_28x53m_R04.ss` with build v56h, using `ss_inspect.py`.

| # | Connection (Tekla) | Pick order: Tekla → SolidSketch | Tekla output | SolidSketch R04 output | Match |
|---|---|---|---|---|---|
| 1 | Base plate **1042**: frame columns BI220 | column, base point → column | 2501 FLT20*200 L260, holes 80×80, grout PL30 320×260, cast PL4, ROD20 L677 ×4, PD40*10 ×2 per anchor | BASE PLATE 260×200×20, anchors 80×80 M20 L677, GROUT 320×260×30, CAST PL4, plate washers | ✅ |
| 1b | 1042: BI320 columns | same | 2502 L360×200, holes 100×100, grout 420×260 | 360×200×20, 100×100, grout 420×260 | ✅ |
| 1c | 1042: gable columns | same | 2503 PL20*240 L260, holes 80×100, grout FLT30*300 L320 | 260×240×20, 80×100, grout 320×300 | ✅ |
| 2 | End plate **144**: eave | column → rafter | F3501 FLT20*150 L340 flush on the flange, 8 M20 at 70/150/220/290, gauge 70 | END PLATE 342×150×20 flush, 8 M20, pitches 80 70 70 | ✅ |
| 3 | Apex haunch **106** | left rafter → right rafter | 3501 FLT20*400 L160 ×2, 8 M20 at rows 50/120/260/330, gauge 80; haunch FLT8*130 L2029 + FLT10*150 | 2× FLT20*400 L160, 8 M20 at the same rows; haunch web 2029×130, flange 10×150 | ✅ |
| 4 | Purlin cleat (cold rolled cleat) | rafter → purlin 1 → purlin 2 | 8503 L140*60*6 L120, 8504 PL6 124×44, 4 M12 | CLEAT L140*60*6 L120, stiffener 124×44, 4 M12 | ✅ |
| 5 | Tube gusset **20**: tongue | main → brace | 6508 FLT12*140 L315 (150 inside), caps FLT5*100 L44, 4 M16 | TONGUE FLT12*140 L315, END CAP FLT5*100 L44, 4 M16 | ✅ |
| 6 | Node gusset (20/11, several braces) | main → each brace | One gusset per node (6532 HZ: 12 holes; 6513 VL) | One GUSSET PLATE per node with all the holes (hull outline) | ✅ (outline ≈) |
| 7 | Tube crossing | continuous → half 1 → half 2 | 6534 PL12*330, 2 tongues | CROSSING GUSSET, 2 tongues, 8 M16 | ✅ (caps 32/56 vs 44) |
| 8 | Sag rod | purlin → rod | Ø14 holes, nuts + washers | Ø14 holes, nut + washer each side (not in the assembly BOM) | ✅ |
| 9 | 144: monitor post on rafter | rafter → post | 7501 PL12*160 L270, 8 M16 (30/100/170/240 × 35/125) | END PLATE 270×160×12, 8 M16 | ✅ |
| 10 | 144: monitor rafter to post | post → monitor rafter | F7501 FLT12*120 L230, 6 M16 | END PLATE 230×120×12, 6 M16 | ✅ |
| 11 | Joining plates **14**: monitor apex | part 1 → part 2 | 20 PL12*155 L260 ×2, 4 M16 at rows 30/230, gauge 75 | 2× 260×155×12, 4 M16 | ✅ |
| 12 | Shear plate **146**: eave beam | column → beam | 4511 FLT10*150 L210, 8 M16 | SHEAR TAB 210×150×10, 8 M16 | ✅ |
| 13 | 144: mezzanine girder | column → girder | 4512 PL16*120 L430, 8 M16 at 30/100/330/400 | END PLATE 430×120×16, 8 M16 | ✅ |
| 14 | 146: joist to girder | girder → joist | 4515 FLT10*130 L150, 4 M16 | SHEAR TAB 150×130×10, 4 M16 | ✅ |
| 15 | Cap **39**: mezzanine column | column → girder | 4512 PL16*120 L430 on the column top, 8 M16 | CAP PLATE 430×120×16, 8 M16 | ✅ |
| 16 | Base plate **1004**: landing post | post | 9506 PL16*170 L200, 4 ROD16 L284, grout PL30*230 L260 | 200×170×16, 4 M16 L284, grout 260×230 | ✅ |
| 17 | 146 hanger: gable column top | rafter → gable column | 3001 FLT10*120 L192 under the rafter, 4 M16 slotted | SHEAR TAB 192×120×10 in the rafter assembly, 4 M16 (round holes) | ✅ (slots) |
| 18 | Stairs: grating treads | stringer → stringer | 23 × PL10*336 L990 on L60*6 L140 | 24 treads PL10 on L60*6, one solid per flight | ◐ |
| 19 | Railing welds | rail → post | CHS48*3 rails welded | CHS42.4 rails welded (B's member sizes) | ◐ |

## Model totals (R04 vs Tekla)
| | SolidSketch R04 | Tekla |
|---|---|---|
| M20 8.8 | 576 | 576 |
| Anchors | M20 192 + M16 24 | ROD20 192 + ROD16 16 |
| M16 | 2744 | 2824 |
| M12 | 1100 | 1148 |
| Steel weight | 63.8 t (grout included) | 63.7 t |
| Checks | 2 transport warnings (13.2 m mezzanine girders) | — |

## Assembly marks (numbering)
- Purlins are split by geometry into 14 marks, PU8001–PU8014 (Tekla also has 14). Struts are split into 4 marks and mezzanine girders into 5.
- HZ bracing still has 8 per mark against Tekla's 4: SolidSketch numbering treats mirrored braces as the same assembly.

## Still different (open)
- **Fins and gussets on column C2001:** SolidSketch has more fins than Tekla's 2 plates (6513 + 6515). The struts and braces on the other wall are not merged with them, because their ends are more than 450 mm apart.
- **Rafter R3001:** the centre HZ gusset is 857 long against Tekla 6532 at 549. Tekla trims the hull corners.
  - 6 cleats against Tekla's 4, because the monitor purlins sit on the rafter in B's geometry.
  - Small plate 2504 (FLT10*60 L88) is missing.
- **Column C2001 height:** 7452 against Tekla 7302 overall. This comes from B's eave level.
- **VL6001:** tube 5922 against 5775.
- **Drawings:** SolidSketch sheets have no view titles or sections (A-A, cleat sections) on rafter and brace sheets, and no n*D14 hole tags. Scales are 1:20–1:50 against Tekla's 1:5–1:12.5 with break lines. Part balloons show p-numbers, not Tekla's 4-digit part marks.
- **Gable hanger 3001:** holes are round in SolidSketch, slotted in Tekla.
- **Monitor PR7001:** in Tekla the monitor rafter's flanges are vertical and its ridge purlins are 510 from the apex. B's model has them differently.
