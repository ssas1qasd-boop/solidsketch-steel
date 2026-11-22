package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/** The owner's phone is Arabic: numbers must stay ASCII and the layout LTR (S2 phone check). */
class LocaleTest {
    private lateinit var saved: Locale

    @Before fun arabic() { saved = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("ar-EG")) }
    @After fun restore() = Locale.setDefault(saved)

    @Test
    fun fmtUsesAsciiDigitsUnderArabicDefault() {
        // Guard: under ar-EG the platform default really gives Arabic-Indic digits, so the test means something.
        val plain = "%.0f".format(37747619.0)
        assumeTrue("JVM has no Arabic digits for ar-EG: $plain", plain.any { it in '٠'..'٩' })
        assertEquals("Cut: 37747619 → 37634521 mm³", Fmt.f("Cut: %.0f → %.0f mm³", 37747619.0, 37634521.0))
        assertEquals("1234.50", Fmt.f("%.2f", 1234.5))
        assertEquals("box 10×20×30 volume = 6000.0", "box 10×20×30 volume = ${6000.0}") // templates are ASCII too
    }

    /** No locale-dependent formatting may come back in app code: everything goes through Fmt. */
    @Test
    fun noLocaleDependentFormatInSources() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val bad = listOf("app", "steel", "core-view", "core-io", "core-api").flatMap { m ->
            File(root, "$m/src/main").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (Regex("""(String\.format\(|"\s*\.format\(|\.format\()""").containsMatchIn(code) && !code.contains("Locale.ROOT")) "${f.relativeTo(root)}:${i + 1}: $line" else null
                }
            }.toList()
        }
        assertTrue("use Fmt.f instead:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun manifestTurnsOffRtl() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:supportsRtl=\"false\""))
        val main = File(root, "app/src/main/java/com/solidsketchapp/steel/MainActivity.kt").readText()
        assertTrue(main.contains("LocalLayoutDirection provides LayoutDirection.Ltr"))
    }
}
