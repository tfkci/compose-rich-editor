package com.mohamedrejeb.richeditor.model.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.TimeSource

/**
 * Abstraction over `RichTextState` consumed by [RichTextHistory]. Exposes only the
 * minimum surface the controller needs so it can be unit-tested with a fake host.
 */
internal interface RichTextHistoryHost {
    fun captureState(timestampMs: Long): RichTextSnapshot
    fun restoreState(snapshot: RichTextSnapshot)
}

/**
 * Undo/redo controller for `RichTextState`.
 *
 * The controller stores a bounded stack of groups. Each group holds a `before`
 * snapshot (state prior to the group's first commit) and an `after` snapshot
 * (state after the most recent commit in the group). Undo restores `before` and
 * transfers the group to the redo stack; redo restores `after` and transfers it
 * back.
 *
 * Coalescing is delegated to [RichTextHistoryCoalescer].
 */
public class RichTextHistory internal constructor(
    private val host: RichTextHistoryHost,
    limit: Int,
    coalesceWindowMs: Long,
    private val clock: () -> Long = DefaultClock,
) {
    private val undoStack: ArrayDeque<UndoGroup> = ArrayDeque()
    private val redoStack: ArrayDeque<UndoGroup> = ArrayDeque()
    private val coalescer = RichTextHistoryCoalescer(windowMs = coalesceWindowMs)

    private var canUndoState by mutableStateOf(false)
    private var canRedoState by mutableStateOf(false)

    /** Maximum groups retained on the undo stack. `0` disables history. */
    public var limit: Int = limit
        set(value) {
            require(value >= 0) { "limit must be >= 0" }
            field = value
            trimUndoStackToLimit()
            refreshDerivedState()
        }

    /** Idle window for coalescing consecutive typing / deletion into one undo group. */
    public var coalesceWindowMs: Long
        get() = coalescer.windowMs
        set(value) {
            require(value >= 0) { "coalesceWindowMs must be >= 0" }
            coalescer.windowMs = value
        }

    public val canUndo: Boolean get() = canUndoState
    public val canRedo: Boolean get() = canRedoState

    public fun undo(): Boolean {
        val group = undoStack.removeLastOrNull() ?: return false
        // Materialize the redo target lazily: the live state IS this group's
        // `after` — any mutation since the group's last commit would have pushed
        // a newer group above it on the stack, so nothing can be stale here.
        // Capturing at undo time (instead of after every commit) avoids a full
        // document deep copy per keystroke.
        val resolved =
            if (group.after == null) group.copy(after = host.captureState(clock()))
            else group
        host.restoreState(resolved.before)
        redoStack.addLast(resolved)
        coalescer.reset()
        refreshDerivedState()
        return true
    }

    public fun redo(): Boolean {
        val group = redoStack.removeLastOrNull() ?: return false
        // `after` is always materialized by undo() before a group reaches the
        // redo stack; the fallback is defensive.
        host.restoreState(group.after ?: group.before)
        undoStack.addLast(group)
        coalescer.reset()
        refreshDerivedState()
        return true
    }

    public fun clear() {
        undoStack.clear()
        redoStack.clear()
        coalescer.reset()
        refreshDerivedState()
    }

    internal fun onProgrammaticReplace() {
        clear()
    }

    /**
     * Seals the pending coalesced group without pushing a snapshot. Called when an
     * operation logically ends the current typing burst (e.g. a collapsed-selection
     * style toggle that stages styles for future typing) but has no tree change worth
     * recording. The next committed edit starts a fresh group.
     */
    internal fun sealPendingGroup() {
        coalescer.noteSelectionJump()
    }

    /**
     * Called BEFORE a mutation is applied, while the host still reflects the
     * pre-mutation state. The controller decides, based on [trigger] and the
     * coalescer's state, whether this commit opens a new group — and only then
     * captures the `before` snapshot. Commits that coalesce into the pending
     * group capture nothing, so a typing burst costs one deep copy, not one
     * per keystroke.
     */
    internal fun onBeforeCommit(trigger: CommitTrigger, timestampMs: Long) {
        if (trigger == CommitTrigger.SelectionJump) {
            coalescer.noteSelectionJump()
            return
        }
        redoStack.clear()
        if (coalescer.shouldStartNewGroup(trigger, timestampMs)) {
            undoStack.addLast(
                UndoGroup(before = host.captureState(timestampMs), after = null)
            )
            trimUndoStackToLimit()
        }
        refreshDerivedState()
    }

    /**
     * Called AFTER a mutation has been applied. The tail group's `after` snapshot
     * is NOT captured here — undo() materializes it lazily — so this only advances
     * the coalescer.
     */
    internal fun onAfterCommit(trigger: CommitTrigger) {
        if (trigger == CommitTrigger.SelectionJump || trigger == CommitTrigger.Programmatic) return
        coalescer.noteCommit(trigger, clock())
        refreshDerivedState()
    }

    private fun trimUndoStackToLimit() {
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    private fun refreshDerivedState() {
        canUndoState = undoStack.isNotEmpty()
        canRedoState = redoStack.isNotEmpty()
    }

    private data class UndoGroup(
        val before: RichTextSnapshot,
        /** Redo target; `null` until materialized by the first undo of this group. */
        val after: RichTextSnapshot?,
    )

    internal companion object {
        private val Start = TimeSource.Monotonic.markNow()
        val DefaultClock: () -> Long = { Start.elapsedNow().inWholeMilliseconds }
    }
}
