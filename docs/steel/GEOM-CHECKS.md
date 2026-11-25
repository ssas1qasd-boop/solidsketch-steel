# Geometry checks (standing rule B) on R11 and the C04 case

Generated with `python3 tools/golden/geom_checks.py tests/steel/models/Frame_28x53m_R11.ss --md ...` (rules: docs/steel/PARITY.md, "Standing rule B").
Holes-only groups (holesOnly) get the hole check but no grip check (they have no bolt).

- **C04 workflow case** (default RAFTER set + Generation of purlins + C04): plain v69 26 failures; web v70 and app **2** (the generator's Purlin cleat PL8 overlapping the C04 cleat and stiffener at the same joint: owner question).
- **R11:** tests/steel/models/Frame_28x53m_R11.ss: 3680 connections, 2699 failures; checked: {'overlap pairs': 1286, 'bolts': 18088}

Notes:
- Gusset Plate BOLTS / grip 24 vs 12 / Connection Plate-Gusset OVERLAP: the node gussets proven misplaced in R10 (PARITY.md "Checked with the package, not fixed").
- Purlin grip 60 vs 2 (280): single-part M10 groups in purlins; Tekla's bolt list has no M10 at all: probably the sag-rod holes stored as bolts by the importer. Not changed.
- Cleat Angle / Purlin OVERLAP (5 joints, 1.0-1.8 mm³): faces in designed contact, at the 1 mm³ limit; most likely the tessellation of R11's new cleat holes against Tekla's purlin holes. To be proven.
- STRINGER / BRACKET OVERLAP (10 groups, 1.2-15.5 mm³): after R11 fix 2. Not explained yet; under investigation before fix 2 is called clean.
- FLOATING assembly parts: plates more than 1 mm from every other part of their own assembly and not bolted (e.g. built-up webs / flanges of joists: their flanges are separate assemblies in the import).

| check | connection type | part | other / value | count |
|---|---|---|---|---|
| BOLTS | TKBOLT | grip |  | 899 |
| BOLTS | TKBOLT | Gusset Plate PL12*213 |  | 396 |
| BOLTS | TKBOLT | Gusset Plate PL12*331 |  | 140 |
| BOLTS | TKBOLT | Gusset Plate PL12*214 |  | 120 |
| BOLTS | TKBOLT | Gusset Plate PL12*330 |  | 112 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*280 | 71 |
| BOLTS | TKBOLT | Gusset Plate PL12*318 |  | 70 |
| BOLTS | TKBOLT | Gusset Plate PL12*329 |  | 70 |
| BOLTS | TKBOLT | Gusset Plate PL12*334 |  | 70 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*213 | 66 |
| FLOATING | assembly | Web FLT8*200 |  | 63 |
| BOLTS | TKBOLT | Gusset Plate PL12*210 |  | 60 |
| BOLTS | TKBOLT | Gusset Plate PL12*212 |  | 48 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate FLT12*140 | 48 |
| BOLTS | TKBOLT | Gusset Plate PL12*333 |  | 36 |
| FLOATING | assembly | Bottom flange FLT10*140 |  | 34 |
| FLOATING | assembly | Top flange FLT10*140 |  | 34 |
| FLOATING | assembly | GROUT PL30*260 |  | 32 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*223 | 25 |
| FLOATING | assembly | Top flange FLT8*120 |  | 24 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*331 | 24 |
| OVERLAP | TKBOLT | Top flange FLT10*150 | End plate Post PL12*160 | 24 |
| FLOATING | assembly | Cap Plate FLT8*150 |  | 23 |
| FLOATING | assembly | THREAD PL10*336 |  | 23 |
| FLOATING | assembly | Web FLT8*300 |  | 17 |
| FLOATING | assembly | GROUT FLT30*300 |  | 16 |
| FLOATING | assembly | Top flange FLT10*200 |  | 16 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*330 | 16 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*231 | 12 |
| FLOATING | assembly | Top flange FLT12*250 |  | 10 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*210 | 10 |
| OVERLAP | TKBOLT | STRINGER UPN240 | BRACKET L60*6 | 10 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*212 | 8 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*333 | 8 |
| FLOATING | assembly | Shear Plate FLT10*150 |  | 6 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*284 | 6 |
| OVERLAP | TKBOLT | Cleat Angle L140*60*6 | Purlin CC200-2-20-60 | 5 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*329 | 5 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*334 | 5 |
| BOLTS | TKBOLT | Stair Plate FLT12*150 |  | 4 |
| FLOATING | assembly | GROUT PL30*230 |  | 4 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*350 | 4 |
| FLOATING | assembly | Bottom flange FLT10*120 |  | 3 |
| FLOATING | assembly | Bottom flange FLT12*250 |  | 3 |
| FLOATING | assembly | Top flange FLT10*120 |  | 3 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*234 | 3 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*318 | 3 |
| FLOATING | assembly | Bottom flange FLT6*150 |  | 2 |
| FLOATING | assembly | PLATE FLT6*150 |  | 2 |
| FLOATING | assembly | Top flange FLT10*150 |  | 2 |
| FLOATING | assembly | Web FLT6*200 |  | 2 |
| OVERLAP | TKBOLT | Connection Plate FLT12*140 | Gusset Plate PL12*220 | 2 |
