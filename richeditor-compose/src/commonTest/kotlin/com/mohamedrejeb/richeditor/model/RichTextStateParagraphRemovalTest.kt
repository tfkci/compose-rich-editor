package com.mohamedrejeb.richeditor.model

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.paragraph.RichParagraph
import kotlin.test.Test

/**
 * Regression test for a crash in [RichTextState.updateAnnotatedString]:
 *
 *   java.lang.IndexOutOfBoundsException: index: 12, size: 12
 *     at SnapshotStateList.get
 *     at RichTextState.updateAnnotatedString
 *
 * The loop iterated `richParagraphList` via `fastForEachIndexed` (which
 * captures the list size once) and called `richParagraphList.removeAt(i)`
 * inside the body. Once the first paragraph was removed, the loop continued
 * past the new end of the list and threw.
 */
@OptIn(ExperimentalRichTextApi::class)
class RichTextStateParagraphRemovalTest {

    @Test
    fun `shrinking text below paragraph range does not IOOB`() {
        val paragraphCount = 13
        val paragraphs = List(paragraphCount) { i ->
            RichParagraph(key = i + 1).also { p ->
                p.children.add(RichSpan(text = "line$i", paragraph = p))
            }
        }
        val state = RichTextState(initialRichParagraphList = paragraphs)

        // Force updateAnnotatedString down the `index > newText.length` branch
        // on many paragraphs at once. Before the fix this crashed with IOOB
        // because removeAt(i) was called during fastForEachIndexed — the only
        // invariant we care about here is that the call completes without
        // throwing.
        state.onTextFieldValueChange(
            TextFieldValue(text = "", selection = TextRange.Zero)
        )
    }

    @Test
    fun `repeated shrink-grow cycles do not IOOB`() {
        val state = RichTextState(
            initialRichParagraphList = List(20) { i ->
                RichParagraph(key = i + 1).also { p ->
                    p.children.add(RichSpan(text = "paragraph$i", paragraph = p))
                }
            }
        )

        // Shrink hard, grow back, shrink again — exercises the removal path
        // repeatedly across multiple updateAnnotatedString invocations.
        repeat(5) {
            state.onTextFieldValueChange(
                TextFieldValue(text = "a", selection = TextRange(1))
            )
            state.onTextFieldValueChange(
                TextFieldValue(
                    text = "aaaa aaaa aaaa",
                    selection = TextRange(14),
                )
            )
        }
    }
}
