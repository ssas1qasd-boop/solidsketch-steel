# Rule B on R12 (Tekla IFC)

`python3 tools/golden/geom_checks.py tests/steel/models/Frame_28x53m_R12.ss`, rule 3 as amended 2026-11-26: an overlap with a mean depth under 0.01 mm is contact.

tests/steel/models/Frame_28x53m_R12.ss: 1694 connections, 660 failures; checked: {'contacts (overlap < 0.01 mm deep)': 5201, 'overlap pairs': 1288, 'bolts': 9928, 'model overlap pairs': 14782, 'parts without a valid solid': 0}

All remaining failures are features of Tekla's own model (images: `docs/steel/parity/r12/`):

- nut / washer bore 0.16 mm into the anchor rod (384);
- anchor rods through grout that Tekla did not drill (208);
- hand rail / railing tubes running 1.4–2.0 mm deep into the stanchions (52);
- 4 node gussets 2.7 mm off the connection plate, Tekla's bolt grip 26.7 vs a 24 mm stack (16).

| check | connection type | part | other / value | count |
|---|---|---|---|---|
| OVERLAP | model | NUT PD40*10 | Anchor Bolt ROD20 | 192 |
| OVERLAP | model | WASHER PD40*10 | Anchor Bolt ROD20 | 192 |
| OVERLAP | model | Anchor Bolt ROD20 | GROUT PL30*260 | 128 |
| OVERLAP | model | Anchor Bolt ROD20 | GROUT FLT30*300 | 64 |
| OVERLAP | model | Hand Rail CHS48*3 | Stanchion CHS48*3 | 26 |
| OVERLAP | model | RAILING PD50*5 | Stanchion CHS48*3 | 26 |
| BOLTS | TKBOLT | grip |  | 16 |
| OVERLAP | model | Anchor Bolt ROD16 | GROUT PL30*230 | 16 |
