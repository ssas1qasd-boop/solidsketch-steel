package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.SelectionApi

/** Selection state. Picking is delegated to [picker]; [onChanged] asks for a redraw. */
class CoreSelection(
    private val picker: (Float, Float) -> SelectionApi.Hit?,
    private val onChanged: () -> Unit,
) : SelectionApi {
    private var sel: List<BodyId> = emptyList()
    var highlighted: Set<BodyId> = emptySet()
        private set
    private val listeners = mutableListOf<SelectionApi.Listener>()

    override fun pick(x: Float, y: Float): SelectionApi.Hit? = picker(x, y)
    override fun selected(): List<BodyId> = sel

    override fun setSelected(ids: Collection<BodyId>) {
        val n = ids.distinct()
        if (n == sel) return
        sel = n
        onChanged()
        listeners.toList().forEach { it.onSelectionChanged(sel) }
    }

    override fun setHighlighted(ids: Collection<BodyId>) {
        highlighted = ids.toSet(); onChanged()
    }

    /** Drops ids that are no longer in the document. */
    fun retain(alive: Set<BodyId>) {
        if (sel.any { it !in alive }) setSelected(sel.filter { it in alive })
        if (highlighted.any { it !in alive }) setHighlighted(highlighted.filter { it in alive })
    }

    override fun addListener(listener: SelectionApi.Listener) { listeners += listener }
    override fun removeListener(listener: SelectionApi.Listener) { listeners -= listener }
}
