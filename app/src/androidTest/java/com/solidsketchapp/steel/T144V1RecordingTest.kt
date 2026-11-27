package com.solidsketchapp.steel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solidsketchapp.core.api.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T144 video 1 (tests/steel/videos/T144/VIDEO_1.txt: VLQiysAuEr4, the final state at 27:18), rebuilt through the real dialog: HEA360 column,
 * IPE400 beam on its flange; End plate (144) Picture 1 = -80, 2 = -80, gap 0, t 20; Plates End plate b 180, h 560, Fitting plate 1 t 12;
 * Stiffeners created, t 10, positions 1 = 20, 2 / 5 = 40 (up), 3 / 4 = -40 (down); Haunch top / bottom 12 x 80 x 80, chamfers 80 line,
 * both created; Bolts M20, n 5, s 120, 6 = -40 (Top), 3 = 40, 5 blank (= 180 - 2 x 40), Tekla length. Pick parts, orbit, orthographic
 * stills, saved (t144v1/t144_v1.ss); then Plates b 200 (the new field), Modify, orbit again. /sdcard/t144v1_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class T144V1RecordingTest : T144RecordingBase() {
    @Test fun t144v1Recording() {
        dir = "t144v1"; start()
        val j = Vec3(200.0, 0.0, 5800.0); look(j, Vec3(0.9, -1.2, 0.7), 3500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/t144v1_rec.mp4")
        try {
            pause(1200)
            member("COLUMN", """{"prof":"HEA360","rot":"Top","p1":[0,0,0],"p2":[0,0,9000]}"""); pause(800)
            member("BEAM", """{"prof":"IPE400","p1":[0,0,6000],"p2":[6000,0,6000]}"""); pause(1000)
            shot("members")
            openCatalog("144")
            tab("Picture"); field("tp", "20"); field("ext", "-80"); field("extb", "-80"); field("gap", "0"); shot("dialog144_picture")
            tab("Plates"); field("epb", "180"); field("eph", "560"); field("shim", "12"); shot("dialog144_plates")
            tab("Stiffeners"); pick("scr", "Stiffeners are created"); for (k in 1..4) field("stt$k", "10")
            field("sg1", "20"); field("sg2", "40"); field("sg5", "40"); field("sg3", "-40"); field("sg4", "-40"); shot("dialog144_stiffeners")
            tab("Haunch"); field("hpt1", "12"); field("hpb1", "80"); field("hph1", "80"); field("hpt2", "12"); field("hpb2", "80"); field("hph2", "80")
            for (k in 1..4) field("hc$k", "80"); pick("hct", "Line chamfer"); pick("hpc", "Top and bottom haunch plates are created"); shot("dialog144_haunch")
            tab("Bolts"); pick("bolt", "M20"); field("rows", "5"); field("pitches", "120"); field("e", "-40"); field("side", "40"); field("gauge", "")
            pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); shot("dialog144_bolts")
            // picks on the web faces, square to the camera: the column's web above the joint, the beam's web near it (both within the portrait view at 9 m)
            val cam = { look(Vec3(1200.0, 0.0, 6600.0), Vec3(-0.12, -1.0, 0.22), 9000.0) }
            val pairs = listOf(7800.0 to 2200.0, 8200.0 to 2600.0, 7400.0 to 1900.0, 8500.0 to 2400.0).map { (cz, bx) -> Vec3(0.0, -12.0, cz) to Vec3(bx, -12.0, 5800.0) }
            pickParts("T144", cam, pairs)
            assertTrue("144 b ${q("T144", "epb")}", q("T144", "epb")?.toDoubleOrNull() == 180.0)
            assertTrue("144 e ${q("T144", "e")}", q("T144", "e")?.toDoubleOrNull() == 40.0)
            assertTrue("144 sg2 ${q("T144", "sg2")}", q("T144", "sg2")?.toDoubleOrNull() == -40.0)
            val c = endPlateCentre()
            orbit(c, 1500.0, "v1")
            landscape(true)
            orthoShot(Vec3(c.x - 100.0, c.y, c.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front")   // along the frame line: plate, shim, haunch plates, stiffeners
            orthoShot(Vec3(c.x, c.y, c.z), Vec3(1.0, 0.0, 0.0), 900.0, "ortho_end")                // onto the end plate from the beam side: b 180, h 560, 2 x 5 bolts
            orthoShot(Vec3(c.x - 100.0, c.y, c.z), Vec3(0.0, 0.0, 1.0), 1100.0, "ortho_top")
            landscape(false)
            look(c, Vec3(0.9, -1.2, 0.7), 1500.0); pause(600)
            save("t144_v1.ss")
            // the parity option: Plates b 200, Modify
            reopen144("Plates"); tab("Plates"); field("epb", "200"); pause(600); shot("dialog144_b200")
            modifyAndClose("b 200") { q("T144", "epb")?.toDoubleOrNull() == 200.0 }
            orbit(c, 1500.0, "b200")
            landscape(true); orthoShot(Vec3(c.x, c.y, c.z), Vec3(1.0, 0.0, 0.0), 900.0, "ortho_end_b200"); landscape(false)
            pause(800)
        } finally { stopRec(rec) }
    }
}
