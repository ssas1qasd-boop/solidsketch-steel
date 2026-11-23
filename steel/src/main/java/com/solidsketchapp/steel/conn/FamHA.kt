package com.solidsketchapp.steel.conn

/**
 * HA family (v69 FAM.HA, the live definition chain; see docs/steel/LIVE-FUNCTIONS.md). Not ported yet: [PORTED] false
 * keeps components of this family as the file has them (regen does not touch them) and new ones are "not built".
 */
object FamHA {
    const val PORTED = false
    fun gen(x: FamCtx) { throw UnsupportedOperationException("family HA not ported yet") }
}
