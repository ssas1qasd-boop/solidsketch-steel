package com.solidsketchapp.steel.conn

/**
 * SD family (v69 FAM.SD, the live definition chain; see docs/steel/LIVE-FUNCTIONS.md). Not ported yet: [PORTED] false
 * keeps components of this family as the file has them (regen does not touch them) and new ones are "not built".
 */
object FamSD {
    const val PORTED = false
    fun gen(x: FamCtx) { throw UnsupportedOperationException("family SD not ported yet") }
}
