# Tekla package for Frame 28x53m (owner upload, 2026-11-25)

`Frame_28mx53m_Package_1.zip` = Tekla's own deliverables: GA, assembly and single part drawings (PDF, 418), NC files (DSTV profiles/plates, NC_dxf), reports (bolt list, part/assembly lists, material list) and `Tekla Model.db1`.

Reference data only. Never put it in the APK.

Bolt list (Reports/Bolt List (+Weight).xsr):
- 4.6 BOLT 12X30 × 1056 = the C04 purlin cleat bolts (cleat against the purlin web; R10's 58–72 mm air gap would need ~140 mm bolts)
- 8.8 BOLT 12X35 × 92 = stair bracket bolts (R10 has 130 mm air)

## IFC export from Tekla (owner, 2026-11-25)
`Frame_28x53m_tekla_v1.ifc`: exported by the owner from `Tekla Model.db1` in Tekla Structures 2025 SP6. IFC2X3, Coordination View 2.0, surface geometry.
Options: Bolts On, Assemblies **Off**, Welds **Off**, PropertySets On, Export all On.
Contents: 2702 IfcBeam, 1742 IfcPlate, 437 IfcColumn, 132 IfcMember, 108 IfcFooting, 1694 IfcMechanicalFastener (bolt groups; diameter, length), no IfcOpeningElement.

`Frame_28x53m_tekla_v2.ifc` (second export, use this one): same model and settings, plus **Assemblies On** and **Welds On** (ViewColors On).
Contents: the same parts and 1694 bolt groups, plus 1361 IfcElementAssembly (1364 IfcRelAggregates) and 6043 IfcFastener (welds).

## Package 2 and IFC v3 (owner, 2026-11-27): Tekla model error fixed
The owner found that 3 mezzanine girders (C/5-6, D/5-6, E/5-6, +4.00) had no web in the Tekla model (old EB4006 = top flange only, EB4008 = bottom flange only). Fixed in Tekla: webs FLT8*300 added, assemblies joined, full renumbering, all drawings recreated.
- `Frame_28x53m_tekla_v3.ifc`: IFC2X3, same options as v2 (assemblies, bolts, welds on). 10 complete girders (7 EB4006 + 3 EB4007), 1700 bolt groups.
- `Frame_28mx53m_Package_2.zip`: Assembly (160) / Single (243) / GA (16) as PDF and DWG, NC (206 .nc1), reports SingleList.xsr (part list), AssembleyList.xsr, BOLT-LIST.xsr, out.ifc (= v3).
- Part list total: 4896 members, 64,023.4 kg (was 63,711.7; +311.7 = 3 webs × 103.9). Assembly list: 1182 assemblies, 63,995.0 kg.
- Bolt list: M20x70 192, M20x60 384, M16x50 1952, M16x45 816, M16x40 104, M12x35 92, M12x30 1056 (4,596 bolts).
- Renumbering changed part marks: e.g. 4517 = FLT12*250 flange, 4518 = FLT8*300 web now. Compare by geometry, not by old marks.
