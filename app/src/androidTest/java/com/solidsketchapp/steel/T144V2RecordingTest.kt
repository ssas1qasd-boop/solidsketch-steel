package com.solidsketchapp.steel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solidsketchapp.core.api.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T144 video 2 (tests/steel/videos/T144/VIDEO_2.txt: IBVnHuj1F-s, the final state at 24:24), rebuilt through the real dialog: HEA400 column,
 * HEA400 beam on its flange; End plate (144) Picture 1 = 0, 2 = 0, gap 0, t 20; Plates Fitting plate 1 t 20; Stiffeners created, t 10,
 * chamfers 30 x 30 line; Haunch top / bottom 10 x 75 x 75, chamfers 55 line, both created; Bolts M20, n 4, s 120, 6 = 30 (Top), 2 across,
 * 5 = 120, 3 = 30, Tekla length. Pick parts, orbit, orthographic stills, saved (t144v2/t144_v2.ss); then Stiffeners chamfers 15 x 15,
 * Modify, orbit again. /sdcard/t144v2_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class T144V2RecordingTest : T144RecordingBase() {
    @Test fun t144v2Recording() {
        dir = "t144v2"; start()
        val j = Vec3(200.0, 0.0, 4800.0); look(j, Vec3(0.9, -1.2, 0.7), 3500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/t144v2_rec.mp4")
        try {
            pause(1200)
            member("COLUMN", """{"prof":"HEA400","rot":"Top","p1":[0,0,0],"p2":[0,0,7000]}"""); pause(800)
            member("BEAM", """{"prof":"HEA400","p1":[0,0,5000],"p2":[6000,0,5000]}"""); pause(1000)
            shot("members")
            openCatalog("144")
            tab("Picture"); field("tp", "20"); field("ext", "0"); field("extb", "0"); field("gap", "0"); shot("dialog144_picture")
            tab("Plates"); field("epb", ""); field("eph", ""); field("shim", "20"); shot("dialog144_plates")
            tab("Stiffeners"); pick("scr", "Stiffeners are created"); for (k in 1..4) field("stt$k", "10")
            for (k in 1..5) field("sg$k", "0"); field("sc1", "30"); field("sc2", "30"); pick("sct", "Line chamfer"); shot("dialog144_stiffeners")
            tab("Haunch"); field("hpt1", "10"); field("hpb1", "75"); field("hph1", "75"); field("hpt2", "10"); field("hpb2", "75"); field("hph2", "75")
            for (k in 1..4) field("hc$k", "55"); pick("hct", "Line chamfer"); pick("hpc", "Top and bottom haunch plates are created"); shot("dialog144_haunch")
            tab("Bolts"); pick("bolt", "M20"); field("rows", "4"); field("pitches", "120"); field("e", "30"); field("side", "30"); field("gauge", "120")
            pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); shot("dialog144_bolts")
            val cam = { look(Vec3(1200.0, 0.0, 5600.0), Vec3(-0.12, -1.0, 0.22), 9000.0) }
            val pairs = listOf(6300.0 to 2200.0, 6600.0 to 2600.0, 6000.0 to 1900.0, 6800.0 to 2400.0).map { (cz, bx) -> Vec3(0.0, -12.0, cz) to Vec3(bx, -12.0, 4805.0) }
            pickParts("T144", cam, pairs)
            assertTrue("144 e ${q("T144", "e")}", q("T144", "e")?.toDoubleOrNull() == 30.0)
            assertTrue("144 gauge ${q("T144", "gauge")}", q("T144", "gauge")?.toDoubleOrNull() == 120.0)
            assertTrue("144 sc1 ${q("T144", "sc1")}", q("T144", "sc1")?.toDoubleOrNull() == 30.0)
            val c = endPlateCentre()
            orbit(c, 1500.0, "v2")
            landscape(true)
            orthoShot(Vec3(c.x - 100.0, c.y, c.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front")
            orthoShot(Vec3(c.x, c.y, c.z), Vec3(1.0, 0.0, 0.0), 900.0, "ortho_end")
            orthoShot(Vec3(c.x - 100.0, c.y, c.z), Vec3(0.0, 0.0, 1.0), 1100.0, "ortho_top")
            landscape(false)
            look(c, Vec3(0.9, -1.2, 0.7), 1500.0); pause(600)
            save("t144_v2.ss")
            // one option changed: the stiffener chamfers 15 x 15, Modify
            reopen144("Stiffeners"); tab("Stiffeners"); field("sc1", "15"); field("sc2", "15"); pause(600); shot("dialog144_ch15")
            modifyAndClose("chamfer 15") { q("T144", "sc1")?.toDoubleOrNull() == 15.0 }
            orbit(Vec3(0.0, c.y, c.z), 1500.0, "ch15")
            landscape(true); orthoShot(Vec3(-100.0, c.y, c.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front_ch15"); landscape(false)
            pause(800)
        } finally { stopRec(rec) }
    }
}
