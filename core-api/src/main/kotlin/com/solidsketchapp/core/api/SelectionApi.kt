package com.solidsketchapp.core.api

/** Picking and selection. */
interface SelectionApi {
    sealed interface Hit {
        val body: BodyId
        val point: Vec3
        data class Body(override val body: BodyId, override val point: Vec3) : Hit
        data class Face(override val body: BodyId, override val point: Vec3, val faceIndex: Int, val normal: Vec3) : Hit
    }

    fun pick(x: Float, y: Float): Hit?
    fun selected(): List<BodyId>
    fun setSelected(ids: Collection<BodyId>)
    fun setHighlighted(ids: Collection<BodyId>)
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    fun interface Listener {
        fun onSelectionChanged(selected: List<BodyId>)
    }
}
