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
}
