# Steel features in web build v69 (inventory)

Made by searching `reference/solidsketch_v69.html` (DEV copy, 20,495,043 bytes). Regenerate with `tools/inventory/feat.py`. Line numbers are in `LIVE-FUNCTIONS.md`.

## Chapters of the steel code

- v32 · Steel: Tekla-style steel modelling
- v33 · Steel: Portal frame generator
- v34 · Steel connections: base plate, eaves haunch, apex haunch
- v35 · purlin cleats, bolted gussets, tube gussets, tie end plates
- v36 · numbering (Tekla style)
- v37 · model checks
- v38 · Applications & components
- v39 · drawings
- v40 · the Tekla component catalog as family generators
- v41 · more profiles: flats, parametric hollows, channels, cold-formed C/Z, built-up and tapered I
- v42 · girts, gable columns, gable girts
- v42 · batch API
- v43 · crane runway
- v44 · stairs and railings
- v45 · brace-end hardware
- v46 · more exact components
- v47 · cold-formed purlin laps and sleeves
- v48 · stub splices
- v49 · connection review fixes + clickable component symbols
- v50
- v53 · distinct connection icons (Agent 2 r4)
- v54 · Tekla project match: base plate 1042, apex 106/14, node gussets
- v56 · vertical column sheets
- v58 · Agent 2's 3D catalog renders (r5)
- v63 · Tekla-style drawings: GA / assembly / single part, Document manager, PDF / DXF / DWG (round 6)
- v63 · Document manager (Tekla style): create / open / print PDF / export DWG-DXF / NC
- v63 · NC / DSTV export (Agent 2, r6)
- v64 · Tekla import connection types TKCUT/TKBOLT (Agent 2, r7)
- v63 · DWG writer: LibreDWG (GPLv3) compiled to WebAssembly, DXF → DWG R2000
- v65 (round 7c) · Tekla-style weights (net / gross, bolts excluded) + checker false alarms
- v66 (round 7c) · Tekla weights: WEIGHT / WEIGHT_NET / WEIGHT_GROSS, bolts excluded

## Profiles (`ST_CAT`, 500 entries)

CHS 162, RHS 125, SHS 123, HEA 24, HEB 24, HEM 24, IPE 18

v41 also adds flats, parametric hollows, channels, cold-formed C/Z, and built-up and tapered I (parametric, so not all of them are in the table).

## Part types (`ST_SETS`)
COLUMN, BEAM, RAFTER, PURLIN, GIRT, BRACING, PLATE (profile, material, class, finish, part and assembly prefix and start number, plane/rotation/depth with offsets).

## Applications (`ST_APPS`)
Portal frame, Numbering, Checks. Steel toolbar: Grid, Portal frame, Column, Beam, Plate, Components, Numbering, and the work levels.

## Components with full Tekla dialogs (`TKD`, 10)
T144, Tcl, Tsag, Ttg, Ttgx, T146, T1042, T1004, T1014, T39

## Tekla component catalog (`TK`, 246 components)

| Group | Count | Family | Components (Tekla number · name) |
|---|---|---|---|
| Bracing | 36 | GEN,GU | 5 · Simple bolt connection; 10 · Welded gusset; 11 · Bolted gusset; 12d · Diag brace connection; 19 · Bracing cross; 20 · Tube gusset; 22 · Tube crossing; 52 · Traction bar; 61 · Wrapped cross; 62 · Gusseted cross; 67 · Std bracing connection; 105 · Portal bracing; 140 · Bent gusset; 169 · Central gusset; 196 · Bolted gusset (196); 56 · Corner tube gusset; 57 · Corner bolted gusset; 58 · Wraparound gusset; 59 · Hollow brace wraparound gusset; 60w · Wraparound gusset cross; 63 · Corner wrapped gusset; 165 · Heavy brace; 7t · Tensioner; 13b · Tensioner brace; 13c · Tensioner brace and compression bar; 18 · Tensioner central gusset; S3 · Turnbuckle bracing; 1w · Windbracing; 16 · Windbracing 2; 110 · Windbrace connection; S55 · Windbracing (roof); S56 · Vertical bracing; S46 · Crushed tube in points; S47 · Gusset tube in points; S48 · Crushed tube in bolts; S49 · Gusset tube in bolts |
| Shear tabs | 32 | MF,ST,TU | 17 · Stiffened shear plate; 32t · Welded tee; 34s · Shear plate (34); 35 · Shear plate simple (35); 36 · Shear plate welded top; 38 · Fitted shear plate; 38t · Shear plate to top special; 39f · Shear plate full depth special; 40s · Shear Pl parallel; 41s · Shear Pl col with stiff; 43 · Shear and flange plates; 43w · Welded shear plate; 46 · Hql connection; 47 · Shear plate to tube column; 64 · H&V Shear plate; 80 · Shear tab plate connection; 81 · Full depth shear plate; 103 · Shear plate (103); 118 · Two sided shear plate; 129 · Beam with stiffener; 131 · Column with shear plate; 146 · Shear plate simple; 147 · Welded to top flange; 149 · Welded to top flange S; 182 · Column with stiffeners W; 184 · Full depth; 185s · Full depth S; 185 · JP Full depth special; 186 · Column with stiffeners; 187 · Column with stiffeners S; 188 · Column with stiffeners (188); 189 · Shear plate tube column |
| End plates | 23 | CP,EP,SD | 2 · Seating cap plate; 14 · Column - 2 beams; 24 · Two sided end plate (24); 27 · Bolted cap plate; 27s · Stiffened end plate; 29 · End plate (29); 32 · Simple end plate; 34 · Two side end plate; 48 · Spacer plate; 50 · Double plate; 65 · Partial stiff end plate; 97 · HQ End plate; 101 · End plate (101); 111 · End plate with compensating flange plates; 112 · Two-sided end plate with compensating flange plates; 115 · Two sided end plate (115); 119 · Stub connection; 142 · Two sided end plate; 144 · End plate; 1002 · End plate detail; 1020 · LProf base detail; 1026 · Connection plate; cap · Cap plate detail |
| Seated connections | 20 | CP,PU,SD,SE | 7 · Bearing plate; 8 · Channel cleat; 12 · Stiffener seating; 28 · Stub (crane beam); 30 · Seating; 36n · Seating with nail; 37 · Seating cap; 39 · Seating; 60 · Seat type 5; 72 · U.S. Seat connection; 73 · U.S. Seat connection 2; 74 · U.S. Seat connection 3; 75 · U.S. Seat connection 4; 100 · Column tube seating; 104 · Tube seat; 170 · Angle profile box; 1013 · Stub plate; 1040 · Angle profile box (detail); 1048 · U.S. Seat detail; 1049 · U.S. Seat detail 2 |
| Welded, fitting and notch | 20 | SD,WF | 4 · Cross; 13 · Fitting; 23 · Round tube; 31w · Welded column; 44w · Weld preparation; 123 · Welded beam to beam; 128 · Welded column with stiffeners; 183 · Beam prep; 1022 · Doubler plate; pd · Pipe doubler plate; rp · Ring plate; 49 · New notch; 76 · Notch; 1006 · Stiffened notch; 9 · Offshore; 192 · Offshore Z; 193 · Offshore U; 194 · Offshore (194); 195 · Offshore 4; 1057 · Angle cut |
| Splices | 19 | GEN,SP | 6 · Tube splice; 14j · Joining plates; 26 · Muffe; 33t · Tab plate; 42 · Column splice; 45 · Box girder splice; 53 · Diagonal splice; 77 · Splice connection; 124 · Round joining plates; 125 · Moose splice; 126 · Turnbuckle connection; 132 · Column splice (132); 133 · Stub; 135 · Beam to beam stub connection; 137 · Column site weld splice; 152 · 2L Splice; 175 · L splice; 176 · Parallel L profiles; 1054 · Dividing part |
| Base plates | 13 | BP | 55 · Simple base plate; 71 · U.S. Base plate connection; 1004 · Base plate; 1014 · Stiffened base plate; 1016 · Web stiffened base plate; 1031 · Simple base plate 2; 1042 · Base plate; 1044 · U.S. Bearing plate; 1047 · U.S. Base plate; 1052 · Circular base plates; 1053 · Base plate (tower leg); 1066 · Box column base plate; 1068 · Tapered column base plate |
| Stiffeners and gussets | 13 | GU,SD,SP,ST | 11j · JP Floor beam gusset; 12j · JP Floor beam gusset A; 171 · Gusset stiffeners; 1003 · Stiffeners; 1017 · Horizontal stiffener; 1030 · Column stiffeners; 1041 · Stiffeners (1041); 1050 · Standard Tee; 1051 · Standard L; 1060 · Web stiffeners; 1064 · Multiple stiffeners; 1065 · Standard gusset; gt · Gusset+ T |
| Other | 13 | CP,OP,SD,TU | 5w · Wind column; 47p · Plate with nail (tubes); 114 · Panel stabilizer; 1010 · Shear stud; 1011 · Stub; 1015 · Wood girder bearing; 1031l · Lifting alignment pieces; 1069 · Cast-in plate; 92 · Create hole around part; 1029 · Duct hole sleeve; 1058 · Penetrating diaphragm; 1059 · Inner diaphragm; rh · Rebar hole |
| Clip angles | 10 | CA | 3 · Angle cleat; 25 · Two sided angle cleat; 31 · Simple clip angle; 33 · Two side clip angle; 44 · Clip angles; 82 · Simple clip angle 2; 116 · Clip angle (116); 117 · Two sided clip angle (117); 141 · Clip angle; 143 · Two sided clip angle |
| Purlins and cold rolled | 10 | GEN,PU | 1c · Cold rolled overlap; 2c · Cold rolled sleeved; 80b · Bridging; 15 · Wooden purlin shoe; 50p · Generation of purlins; 61p · Purlin 1; 70 · Rail connection; 93 · Purlin connections; 113 · Tube rail; pc · Purlin cleat |
| Haunch and moment | 8 | HA,MF,WF | 40 · Haunch; 41 · Cranked beam; 51 · Fit haunch; 102 · Eaves haunch; 106 · Apex haunch; 130 · Moment connection; 134 · Bolted moment connection; 181 · Moment connection (181) |
| Tube connections | 8 | TU | 102t · Squeezed tube bolted; 103t · Squeezed tube; tc · Tube-Chamfer; ts · Tube-CrossingSaddle; tm · Tube-MitreSaddle+Hole; th · Tube-Saddle+Hole; tsl · Tube-SlottedHole; 47n · Plate with nail |
| Joists | 7 | JO,SD | 58j · Joist seat; 160 · Joist to beam, type 1; 161 · Joist to column, type 1; 162 · 2-Sided joist to column; 163 · Joist to column, type 2; 164 · Joist to beam and column; 1067 · Joist bearing plate |
| Stanchions and posts | 7 | CA,SE,ST,WF | 68 · Stringer stanchion L profile; 69 · Stringer stanchion st; 84 · Stanchion curved; 85 · Stanchion weld; 86 · Stanchion side profile; 87 · Stanchion double plate; 170r · Rail joint |
| Generators | 5 | GEN | S78 · Truss; of · Opening Frame; S71 · Stairs; S77 · Railing; pf · Portal frame |
| Bent plates | 2 | BE | 151 · Bent plate; 190 · Bent plate (190) |

## Output and documents
- Steel list (CSV), IFC4 model, delivery package (SVG, DXF, IFC, CSV)
- Document manager: GA, assembly and single part drawings; Create/update, Open, Print → PDF, PDF (one file), Export DWG, Export DXF, NC files (DSTV), Full package, Drawing properties
- NC/DSTV export (`ncExport`, `ncZip`): DSTV_Profiles, DSTV_Plates, NC_dxf
- DWG: in the web build through LibreDWG (GPLv3). **Not ported**: the native app gets its own clean-room writer (see CLAUDE.md, Licensing).

## Steel UI buttons (`stBtn` labels)
Apply · CSV · Cancel · Catalog · Checks · Clear marks · Close · Components · Create · Create / update · DWG · DXF · Delete · Delivery package · Done · Drawing properties · Drawings · Exit Steel · Export DWG · Export DXF · Finish · Fit · Full package · Get · Grid · Help · IFC model · Load · Modify · NC files (DSTV) · Number the model · Numbering · OK · Open · PDF · PDF (one file) · Pick parts · Place · Portal frame · Print · Print / PDF · Print → PDF · Properties · Run checks · SVG · Save · Save as · Save as  · Steel list · Welds… · on/off

Note (S4, from the live page): the catalog sheet (`ST_CATALOG`) has 18 groups and 252 items: the 246 `TK` components (17 groups), plus 4 `CONN` components v69 lists on the sheet itself (`Ttgx`, `Ttg`, `Tsag`, `Tcl`) and 2 applications (`@numbering`, `@checks`) in an 18th group, "Applications".
