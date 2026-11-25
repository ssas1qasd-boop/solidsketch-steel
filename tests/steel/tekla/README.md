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
