package com.solidsketchapp.core.api

import java.util.Locale

/**
 * Number formatting for everything the app shows or writes. Always Locale.ROOT: ASCII digits,
 * '.' as decimal point, no grouping surprises, whatever the phone's language (an Arabic phone
 * would otherwise show Arabic-Indic digits). Use this instead of String.format / "...".format.
 */
object Fmt {
    fun f(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)
}
