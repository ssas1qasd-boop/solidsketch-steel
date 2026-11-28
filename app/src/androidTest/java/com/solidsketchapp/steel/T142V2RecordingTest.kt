package com.solidsketchapp.steel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solidsketchapp.core.api.Vec3
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T142 video 2 (tests/steel/videos/T142/VIDEO_2.txt: J2OINltOa4I, Gani sk, 00:57-04:25, imperial), rebuilt through the real dialog: two
 * beams on a column web (the video never names its profiles: HEB300 / IPE300 stand in); Two sided end plate (142) Plates 1 / Plates 2 end
 * plate t 1/2" = 12.7, b / h blank; Bolts 5/8" -> M16, tolerance 1/16" = 1.5875, Middle, 3 bolts at 5" = 127, edge 3 1/2" = 88.9, 2 across at
 * 3 1/2" = 88.9, edge 2" = 50.8. Pick parts (column, beam, beam), orbit, orthographic stills, saved (t142v2/t142_v2.ss); then Bolts 4 rows,
 * Modify, orbit again. /sdcard/t142v2_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class T142V2RecordingTest : T142RecordingBase() {
    @Test fun t142v2Recording() {
        dir = "t142v2"; start()
        val c = Vec3(0.0, 0.0, 6850.0); look(c, Vec3(0.9, -1.2, 0.7), 4000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/t142v2_rec.mp4")
        try {
            pause(1200)
            member("COLUMN", """{"prof":"HEB300","p1":[0,0,0],"p2":[0,0,7500]}"""); pause(800)
            member("BEAM", """{"prof":"IPE300","p1":[0,0,7000],"p2":[6000,0,7000]}"""); pause(600)
            member("BEAM", """{"prof":"IPE300","p1":[0,0,7000],"p2":[-6000,0,7000]}"""); pause(1000)
            shot("members")
            openCatalog("142")
            tab("Picture"); field("ext", ""); field("extb", ""); shot("dialog142_picture")
            tab("Plates 1"); field("tp", "12.7"); field("epb", ""); field("eph", ""); shot("dialog142_plates1")
            tab("Plates 2"); field("tp2", "12.7"); shot("dialog142_plates2")
            tab("Bolts"); pick("bolt", "M16"); field("tol", "1.5875"); pick("refV", "Middle"); field("posV", ""); field("e", "88.9"); field("rows", "3"); field("pitches", "127")
            field("side", "50.8"); field("gauge", "88.9"); shot("dialog142_bolts")
            // picks: the column's flange face above the joint, each beam's web
            val cam = { look(Vec3(0.0, 0.0, 7000.0), Vec3(-0.05, -1.0, 0.2), 9000.0) }
            val triples = listOf(Triple(7700.0, 1800.0, -1800.0), Triple(8000.0, 2100.0, -2100.0), Triple(7400.0, 1500.0, -1500.0), Triple(8200.0, 1700.0, -1700.0))
                .map { (cz, b1, b2) -> Triple(Vec3(0.0, -160.0, cz), Vec3(b1, -12.0, 6850.0), Vec3(b2, -12.0, 6850.0)) }
            pickParts3("T142", cam, triples)
            assertTrue("142 bgrp ${q("T142", "bgrp")}", q("T142", "bgrp") == "one")
            assertTrue("142 tp ${q("T142", "tp")}", q("T142", "tp")?.toDoubleOrNull() == 12.7)
            assertTrue("142 e ${q("T142", "e")}", q("T142", "e")?.toDoubleOrNull() == 88.9)
            val b = boltSt(); assertTrue("bolts ${b["n"]} L ${b["L"]}", (b["n"] as JsonPrimitive).content.toDouble() == 6.0 && (b["L"] as JsonPrimitive).content.toDouble() == 60.0)
            assertTrue("end plates ${count("END PLATE")}", count("END PLATE") == 2)
            views(c, "v2", 1400.0)
            save("t142_v2.ss")
            // the parity option: Bolts 4 rows, Modify
            reopen144("Bolts"); tab("Bolts"); field("rows", "4"); pause(600); shot("dialog142_rows4")
            modifyAndClose("rows 4") { q("T142", "rows")?.toDoubleOrNull() == 4.0 && (boltSt()["n"] as JsonPrimitive).content.toDouble() == 8.0 }
            views(c, "rows4", 1400.0)
            pause(800)
        } finally { stopRec(rec) }
    }
}
