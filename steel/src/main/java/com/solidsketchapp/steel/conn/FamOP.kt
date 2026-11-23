package com.solidsketchapp.steel.conn

/**
 * OP family (v69 FAM.OP, the live definition chain; see docs/steel/LIVE-FUNCTIONS.md). Not ported yet: [PORTED] false
 * keeps components of this family as the file has them (regen does not touch them) and new ones are "not built".
 */
object FamOP {
    const val PORTED = false
    fun gen(x: FamCtx) { throw UnsupportedOperationException("family OP not ported yet") }
}
