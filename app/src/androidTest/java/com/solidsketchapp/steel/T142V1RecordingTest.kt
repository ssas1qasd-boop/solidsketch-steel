package com.solidsketchapp.steel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solidsketchapp.core.api.Vec3
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T142 video 1 (tests/steel/videos/T142/VIDEO_1.txt: _Au1wpEDNzw, Bangladesh Civil Engineering, 09:15-14:16), rebuilt through the real
 * dialog: HEB300 column, two IPE300 beams on its web (full-model re-read 2026-11-28); Two sided end plate (142) Picture 1-4 blank, 5 = 2
 * and 2; Plates 1 / Plates 2: end plate 16 x 150 x 450; Bolts M16, tolerance 2, Middle, 5 bolts at 90, edge 45, 2 across at 80, edge 35;
 * Haunch: Top plate / Top plate 2 / Bottom plate / Bottom plate 2 6 x 75 x 75, chamfers 1-4 = 75. Pick parts (column, beam, beam), orbit,
 * orthographic stills, saved (t142v1/t142_v1.ss); then Haunch plate creation (secondary 2) = not created, Modify, orbit again.
 * /sdcard/t142v1_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class T142V1RecordingTest : T142RecordingBase() {
    @Test fun t142v1Recording() {
        dir = "t142v1"; start()
        val c = Vec3(0.0, 0.0, 6850.0); look(c, Vec3(0.9, -1.2, 0.7), 4000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/t142v1_rec.mp4")
        try {
            pause(1200)
            member("COLUMN", """{"prof":"HEB300","p1":[0,0,0],"p2":[0,0,7500]}"""); pause(800)
            member("BEAM", """{"prof":"IPE300","p1":[0,0,7000],"p2":[6000,0,7000]}"""); pause(600)
            member("BEAM", """{"prof":"IPE300","p1":[0,0,7000],"p2":[-6000,0,7000]}"""); pause(1000)
            shot("members")
            openCatalog("142")
            tab("Picture"); field("ext", ""); field("extb", ""); field("extb2", ""); field("ext2", ""); field("gap", "2"); field("gap2", "2"); shot("dialog142_picture")
            tab("Plates 1"); field("tp", "16"); field("epb", "150"); field("eph", "450"); shot("dialog142_plates1")
            tab("Plates 2"); field("tp2", "16"); field("epb2", "150"); field("eph2", "450"); shot("dialog142_plates2")
            tab("Haunch"); for (k in 1..4) { field("hpt$k", "6"); field("hpb$k", "75"); field("hph$k", "75") }; for (k in 1..4) field("hc$k", "75"); shot("dialog142_haunch")
            tab("Bolts"); pick("bolt", "M16"); field("tol", "2"); pick("refV", "Middle"); field("posV", ""); field("e", "45"); field("rows", "5"); field("pitches", "90")
            field("side", "35"); field("gauge", "80"); shot("dialog142_bolts")
            // picks: the column's flange face above the joint, each beam's web (as video 2: the beams land on the column web)
            val cam = { look(Vec3(0.0, 0.0, 7000.0), Vec3(-0.05, -1.0, 0.2), 9000.0) }
            val triples = listOf(Triple(7700.0, 1800.0, -1800.0), Triple(8000.0, 2100.0, -2100.0), Triple(7400.0, 1500.0, -1500.0), Triple(8200.0, 1700.0, -1700.0))
                .map { (cz, b1, b2) -> Triple(Vec3(0.0, -160.0, cz), Vec3(b1, -12.0, 6850.0), Vec3(b2, -12.0, 6850.0)) }
            pickParts3("T142", cam, triples)
            assertTrue("142 bgrp ${q("T142", "bgrp")}", q("T142", "bgrp") == "one")
            assertTrue("142 h ${q("T142", "eph")}", q("T142", "eph")?.toDoubleOrNull() == 450.0)
            assertTrue("142 refV ${q("T142", "refV")}", q("T142", "refV") == "Middle")
            val b = boltSt(); assertTrue("bolts ${b["n"]} L ${b["L"]}", (b["n"] as JsonPrimitive).content.toDouble() == 10.0 && (b["L"] as JsonPrimitive).content.toDouble() == 70.0)   // grip 16 + 2 + 11 + 2 + 16 = 47 -> Tekla M16 L70
            assertTrue("end plates ${count("END PLATE")}, haunch plates ${count("HAUNCH_PLATE")}", count("END PLATE") == 2 && count("HAUNCH_PLATE") == 4)
            views(c, "v1", 1600.0)
            save("t142_v1.ss")
            // the parity option: Haunch plate creation (secondary 2) = not created, Modify
            reopen144("Haunch"); tab("Haunch"); pick("hpc2", "Haunch plates are not created"); pause(600); shot("dialog142_hpc2_none")
            modifyAndClose("hpc2 none") { q("T142", "hpc2") == "none" && count("HAUNCH_PLATE") == 2 }
            views(c, "hpc2", 1600.0)
            pause(800)
        } finally { stopRec(rec) }
    }
}
