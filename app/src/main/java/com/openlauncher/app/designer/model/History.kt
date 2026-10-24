package com.openlauncher.app.designer.model

/**
 * Bounded undo/redo stack. The caller owns the "present" value:
 *  - [record] the state *before* a change,
 *  - [undo] / [redo] hand back the state to show and take the current one.
 */
class History<T>(private val limit: Int = 100) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoDepth: Int get() = past.size
    val redoDepth: Int get() = future.size

    fun record(previous: T) {
        if (past.lastOrNull() == previous) return
        past.addLast(previous)
        while (past.size > limit) past.removeFirst()
        future.clear()
    }

    fun undo(current: T): T? {
        val prev = past.removeLastOrNull() ?: return null
        future.addLast(current)
        return prev
    }

    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        return next
    }

    fun clear() {
        past.clear()
        future.clear()
    }
}
