package com.mohamedrejeb.richeditor.model.history

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpan
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.paragraph.RichParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalRichTextApi::class)
class RichTextHistoryTest {

    private class FakeHost : RichTextHistoryHost {
        var paragraphs: MutableList<RichParagraph> = mutableListOf(RichParagraph())
        var selection: TextRange = TextRange(0)
        var toAddSpanStyle: SpanStyle = SpanStyle()
        var toAddRichSpanStyle: RichSpanStyle = RichSpanStyle.Default
        var captureCount: Int = 0

        override fun captureState(timestampMs: Long): RichTextSnapshot {
            captureCount++
            return RichTextSnapshot.capture(
                paragraphs = paragraphs,
                selection = selection,
                composition = null,
                toAddSpanStyle = toAddSpanStyle,
                toAddRichSpanStyle = toAddRichSpanStyle,
                timestampMs = timestampMs,
            )
        }

        override fun restoreState(snapshot: RichTextSnapshot) {
            paragraphs = snapshot.paragraphs.map { it.deepCopy() }.toMutableList()
            selection = snapshot.selection
            toAddSpanStyle = snapshot.toAddSpanStyle
            toAddRichSpanStyle = snapshot.toAddRichSpanStyle
        }
    }

    private fun makeHistory(
        host: FakeHost = FakeHost(),
        limit: Int = 100,
        windowMs: Long = 500L,
        clock: () -> Long = { 0L },
    ): Pair<RichTextHistory, FakeHost> =
        RichTextHistory(host = host, limit = limit, coalesceWindowMs = windowMs, clock = clock) to host

    @Test
    fun emptyHistoryCannotUndoOrRedo() {
        val (h, _) = makeHistory()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertFalse(h.undo())
        assertFalse(h.redo())
    }

    @Test
    fun commitThenUndoReturnsToBeforeState() {
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "x"))
        now = 100L
        h.onAfterCommit(CommitTrigger.Formatting)

        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertTrue(h.undo())
        assertEquals(0, host.paragraphs[0].children.size)
        assertTrue(h.canRedo)
    }

    @Test
    fun redoRestoresAfterState() {
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "x"))
        now = 100L
        h.onAfterCommit(CommitTrigger.Formatting)

        h.undo()
        assertTrue(h.redo())
        assertEquals(1, host.paragraphs[0].children.size)
        assertEquals("x", host.paragraphs[0].children[0].text)
    }

    @Test
    fun typingWithinWindowCollapsesIntoOneGroup() {
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        // First commit: typing "a"
        h.onBeforeCommit(CommitTrigger.Typing("a", 1), timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        host.selection = TextRange(1)
        now = 100L
        h.onAfterCommit(CommitTrigger.Typing("a", 1))

        // Second commit within window: typing "b"
        h.onBeforeCommit(CommitTrigger.Typing("b", 2), timestampMs = now)
        host.paragraphs[0].children[0].text = "ab"
        host.selection = TextRange(2)
        now = 150L
        h.onAfterCommit(CommitTrigger.Typing("b", 2))

        // One undo should clear both chars because they coalesced.
        assertTrue(h.undo())
        assertTrue(
            host.paragraphs[0].children.isEmpty() ||
                host.paragraphs[0].children[0].text.isEmpty()
        )
        assertFalse(h.canUndo)
    }

    @Test
    fun coalescedTypingCapturesOneSnapshotAndUndoMaterializesRedoTarget() {
        // Regression pin for the lazy `after` capture: a coalesced typing burst
        // must cost exactly one deep copy (the group's `before`); the redo
        // target is captured only when undo is pressed.
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        h.onBeforeCommit(CommitTrigger.Typing("a", 1), timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        host.selection = TextRange(1)
        h.onAfterCommit(CommitTrigger.Typing("a", 1))

        now = 100L
        h.onBeforeCommit(CommitTrigger.Typing("b", 2), timestampMs = now)
        host.paragraphs[0].children[0].text = "ab"
        host.selection = TextRange(2)
        h.onAfterCommit(CommitTrigger.Typing("b", 2))

        assertEquals(1, host.captureCount, "coalesced burst must capture only the group's before snapshot")

        assertTrue(h.undo())
        assertEquals(2, host.captureCount, "undo must materialize the redo target")

        assertTrue(h.redo())
        assertEquals(2, host.captureCount, "redo must reuse the materialized snapshot")
        assertEquals("ab", host.paragraphs[0].children[0].text)
    }

    @Test
    fun undoUndoRedoRedoRoundTripsAcrossGroups() {
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        now += 1000L
        h.onAfterCommit(CommitTrigger.Formatting)

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "b"))
        now += 1000L
        h.onAfterCommit(CommitTrigger.Formatting)

        assertTrue(h.undo())
        assertEquals(1, host.paragraphs[0].children.size)
        assertTrue(h.undo())
        assertEquals(0, host.paragraphs[0].children.size)

        assertTrue(h.redo())
        assertEquals(1, host.paragraphs[0].children.size)
        assertEquals("a", host.paragraphs[0].children[0].text)
        assertTrue(h.redo())
        assertEquals(2, host.paragraphs[0].children.size)
        assertEquals("b", host.paragraphs[0].children[1].text)
    }

    @Test
    fun limitCapsUndoStack() {
        var now = 0L
        val (h, host) = makeHistory(limit = 2, clock = { now })

        repeat(5) { i ->
            h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
            host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "$i"))
            now += 1000L
            h.onAfterCommit(CommitTrigger.Formatting)
        }
        assertTrue(h.undo())
        assertTrue(h.undo())
        assertFalse(h.undo())
    }

    @Test
    fun commitAfterUndoClearsRedo() {
        var now = 0L
        val (h, host) = makeHistory(clock = { now })

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        now = 100L
        h.onAfterCommit(CommitTrigger.Formatting)

        h.undo()
        assertTrue(h.canRedo)

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = now)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "b"))
        now = 200L
        h.onAfterCommit(CommitTrigger.Formatting)

        assertFalse(h.canRedo)
    }

    @Test
    fun onProgrammaticReplaceClearsBothStacks() {
        val (h, host) = makeHistory()

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = 0L)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        h.onAfterCommit(CommitTrigger.Formatting)

        h.onProgrammaticReplace()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun clearEmptiesBothStacks() {
        val (h, host) = makeHistory()

        h.onBeforeCommit(CommitTrigger.Formatting, timestampMs = 0L)
        host.paragraphs[0].children.add(RichSpan(paragraph = host.paragraphs[0], text = "a"))
        h.onAfterCommit(CommitTrigger.Formatting)
        h.undo()

        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }
}
