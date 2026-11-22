package com.solidsketchapp.core.io

/** File names for Save. */
object SsNames {
    const val DEFAULT_BASE = "Untitled"
    const val EXT = ".ss"

    /** Name without the `.ss` extension (what v69 writes as the header `name`). */
    fun base(fileName: String): String = fileName.trim().let { if (it.endsWith(EXT, ignoreCase = true)) it.dropLast(EXT.length) else it }.ifBlank { DEFAULT_BASE }

    /**
     * Suggested name in the Save dialog: the file last opened or saved in this session, else the
     * document's header name, else "Untitled.ss". (If that file exists, Android itself adds " (1)".)
     */
    fun suggest(lastFileName: String?, headerName: String?): String =
        base(lastFileName?.takeIf { it.isNotBlank() } ?: headerName?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE) + EXT
}
