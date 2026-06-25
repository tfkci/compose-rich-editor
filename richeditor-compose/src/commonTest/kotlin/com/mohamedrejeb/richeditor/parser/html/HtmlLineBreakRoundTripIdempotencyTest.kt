package com.mohamedrejeb.richeditor.parser.html

import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for the "paragraph spacing grows on every save" bug.
 *
 * Content that contains <br> separators not wrapped in their own paragraph
 * (e.g. plain line-break joined text, or legacy entries) used to gain an extra
 * <br> on every setHtml()/toHtml() round-trip, because toHtml() emitted
 * malformed, unbalanced <p> markup for non-empty line-break paragraphs that sit
 * after an empty line-break paragraph. Re-parsing that markup created an extra
 * line-break paragraph each cycle.
 *
 * The round-trip must converge: toHtml(setHtml(x)) applied repeatedly must reach
 * a fixed point and never keep growing.
 */
class HtmlLineBreakRoundTripIdempotencyTest {

    private fun toHtmlAfterSetHtml(html: String): String {
        val state = RichTextState()
        state.setHtml(html)
        return state.toHtml()
    }

    private fun assertRoundTripConverges(start: String) {
        val first = toHtmlAfterSetHtml(start)
        val second = toHtmlAfterSetHtml(first)
        val third = toHtmlAfterSetHtml(second)
        assertEquals(first, second, "round-trip not idempotent (1st vs 2nd) for input [$start]")
        assertEquals(second, third, "round-trip not idempotent (2nd vs 3rd) for input [$start]")
    }

    @Test
    fun brSeparatedTextDoesNotGrow() {
        assertRoundTripConverges("First<br><br>Second")
    }

    @Test
    fun brJoinedBlocksDoNotGrow() {
        // Mirrors Daynote joining blocks with "<br><br>".
        assertRoundTripConverges("<p>First paragraph</p><br><br>Second paragraph")
    }

    @Test
    fun multipleBlankLinesDoNotGrow() {
        assertRoundTripConverges("Line one<br><br><br>Line two")
    }

    @Test
    fun plainParagraphsRemainStable() {
        assertRoundTripConverges("<p>First paragraph</p><p>Second paragraph</p>")
    }

    @Test
    fun blankLineBetweenParagraphsRemainsStable() {
        assertRoundTripConverges("<p>First</p><p><br></p><p>Second</p>")
    }
}
