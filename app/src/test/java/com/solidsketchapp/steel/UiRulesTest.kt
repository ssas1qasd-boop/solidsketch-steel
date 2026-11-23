package com.solidsketchapp.steel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Owner S3 check: "Delete" wrapped to two lines. Every toolbar button goes through ToolButton (one line). */
class UiRulesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun buttonLabelsStayOnOneLine() {
        val dir = File(root, "app/src/main/java/com/solidsketchapp/steel")
        val tool = File(dir, "ToolButton.kt").readText()
        assertTrue(tool.contains("maxLines = 1") && tool.contains("softWrap = false"))
        val raw = dir.walkTopDown().filter { it.extension == "kt" && it.name != "ToolButton.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (Regex("""\b(Outlined|Text|Filled)?Button\(""").containsMatchIn(l.substringBefore("//"))) "${f.name}:${i + 1}: $l" else null }
        }.toList()
        assertTrue("use ToolButton:\n" + raw.joinToString("\n"), raw.isEmpty())
    }

    @Test
    fun steelButtonsStayOnOneLine() {
        // :steel screens (S5) use StBtn (SteelPanel.kt), which keeps its label on one line too.
        val dir = File(root, "steel/src/main/java")
        val panel = dir.walkTopDown().first { it.name == "SteelPanel.kt" }.readText()
        val btn = panel.substringAfter("fun StBtn(").substringBefore("\n}")
        // its label is a CssText (SsChrome.kt: one line at v69's width), which keeps it on one line
        val css = dir.walkTopDown().first { it.name == "SsChrome.kt" }.readText().substringAfter("fun CssText(").substringBefore("\n}")
        assertTrue(btn.contains("maxLines = 1") && btn.contains("softWrap = false") || btn.contains("CssText(") && css.contains("maxLines = 1") && css.contains("softWrap = false"))
        val raw = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (Regex("""\b(Text|Filled)?Button\(""").containsMatchIn(l.substringBefore("//"))) "${f.name}:${i + 1}: $l" else null }
        }.filter { !it.contains("OutlinedButton(onClick = onClick") }.toList()
        assertTrue("use StBtn:\n" + raw.joinToString("\n"), raw.isEmpty())
    }

    /**
     * S9b (owner: dialog buttons and the catalog's Symbols / Close were under the navigation bar). targetSdk 35 draws edge
     * to edge, so every sheet, dialog, panel, bar, menu and toast must pad itself with the system bar insets
     * (windowInsetsPadding / safeDrawingPadding / navigationBarsPadding / statusBarsPadding / imePadding, or the shared
     * TopInsets / BottomInsets / panelInsets() / sheetInsets()), or be built on StSheet / Sheet, which do.
     */
    @Test
    fun sheetsDialogsAndPanelsKeepClearOfTheSystemBars() {
        val files = listOf(File(root, "app/src/main/java"), File(root, "steel/src/main/java")).flatMap { d -> d.walkTopDown().filter { it.extension == "kt" }.toList() }
        val inset = Regex("""windowInsetsPadding|safeDrawingPadding|navigationBarsPadding|statusBarsPadding|imePadding|\bStSheet\(|\bSheet\(""")
        val fn = Regex("""@Composable\s*(?:\n\s*)?(?:private\s+|internal\s+)?fun\s+(\w*(?:Sheet|Dialog\w*|Panel|Bar|Menu|Toast))\s*\(""")
        // pieces drawn inside a sheet (not anchored to a screen edge), so the sheet's insets already apply
        val inner = mapOf("StBar" to "the button row inside a sheet (v69 #st-sheet .bar)")
        val checked = mutableListOf<String>(); val bad = mutableListOf<String>()
        for (f in files) { val t = f.readText()
            for (m in fn.findAll(t)) { val name = m.groupValues[1]; if (name in inner) continue; if (name == "StSheet") { checked += name; if (!t.substring(m.range.first).substringBefore("\n}").contains("windowInsetsPadding")) bad += "${f.name}: $name"; continue }
                val body = t.substring(m.range.last).let { b -> val end = Regex("""\n}\s*\n""").find(b)?.range?.first ?: b.length; b.substring(0, end) }
                checked += name; if (!inset.containsMatchIn(body)) bad += "${f.name}: $name" } }
        // the screens the owner named must be among the checked ones
        for (n in listOf("TopBar", "BottomBar", "MainMenu", "AboutSheet", "SteelPanel", "CatalogSheet", "CompDialogSheet", "GridSheet", "PropSheet", "StSheet", "SsToast"))
            assertTrue("$n not found by the inset rule (renamed?) — checked: $checked", n in checked)
        assertTrue("these ignore the system bar insets:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
