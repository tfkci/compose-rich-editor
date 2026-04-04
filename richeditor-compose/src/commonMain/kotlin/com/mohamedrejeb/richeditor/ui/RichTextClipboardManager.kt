package com.mohamedrejeb.richeditor.ui

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpan
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.RichParagraph
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType.Companion.startText
import com.mohamedrejeb.richeditor.parser.html.RichTextStateHtmlParser
import com.mohamedrejeb.richeditor.utils.append
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.max
import kotlin.math.min

/**
 * A [ClipboardManager] that can handle [RichTextState].
 * It will convert the [RichTextState] to [AnnotatedString] and delegate the [ClipboardManager] to handle the rest.
 *
 * On Android, [getText] also inspects the raw [ClipData] for HTML / Spanned content.
 * When rich content is detected it stores it in [RichTextState.pendingHtmlPaste] so that
 * [RichTextState.onTextFieldValueChange] can apply the styles instead of the plain-text
 * fallback.  This covers **all** paste paths, including the IME's own paste button which
 * bypasses both the [TextToolbar] and `onPreviewKeyEvent`.
 *
 * @param richTextState The [RichTextState] to be handled.
 * @param clipboardManager The [ClipboardManager] to delegate the rest of the work to.
 * @param spannedPasteHandler Platform handler that knows how to read HTML from the clipboard.
 */
internal class RichTextClipboardManager(
    private val richTextState: RichTextState,
    private val clipboardManager: ClipboardManager,
    private val spannedPasteHandler: SpannedPasteHandler,
): ClipboardManager {
    override fun getText(): AnnotatedString? {
        pasteLog(PASTE_TAG, "ClipboardManager.getText() called")
        // Ask the platform handler whether the clipboard has HTML / Spanned content.
        val html = spannedPasteHandler.readHtml()
        if (html != null) {
            pasteLog(PASTE_TAG, "getText: HTML found → setting pendingHtmlPaste and returning plain-text fallback")
            // Signal onTextFieldValueChange to apply the HTML instead of plain text.
            richTextState.pendingHtmlPaste = html
            // Return the plain-text version so that BasicTextField / InputConnection
            // still knows how many characters are being inserted (cursor placement, etc.).
            val plain = clipboardManager.getText()
            pasteLog(PASTE_TAG, "getText: plain-text fallback = \"${plain?.text?.take(80)}\"")
            return plain
        }
        pasteLog(PASTE_TAG, "getText: no HTML — plain paste proceeds normally")
        return clipboardManager.getText()
    }

    @OptIn(ExperimentalRichTextApi::class)
    override fun setText(annotatedString: AnnotatedString) {
        val selection = richTextState.selection

        // Build AnnotatedString for plain text extraction (existing logic)
        val richTextAnnotatedString = buildAnnotatedString {
            var index = 0
            richTextState.richParagraphList.fastForEachIndexed { i, richParagraphStyle ->
                withStyle(
                    richParagraphStyle.paragraphStyle.merge(
                        richParagraphStyle.type.getStyle(richTextState.config)
                    )
                ) {
                    if (
                        !selection.collapsed &&
                        selection.min < index + richParagraphStyle.type.startText.length &&
                        selection.max > index
                    ) {
                        val selectedText = richParagraphStyle.type.startText.substring(
                            max(0, selection.min - index),
                            min(selection.max - index, richParagraphStyle.type.startText.length)
                        )
                        append(selectedText)
                    }
                    index += richParagraphStyle.type.startText.length
                    withStyle(RichSpanStyle.DefaultSpanStyle) {
                        index = append(
                            richSpanList = richParagraphStyle.children,
                            startIndex = index,
                            selection = selection,
                            richTextConfig = richTextState.config,
                        )
                        if (!richTextState.singleParagraphMode) {
                            if (i != richTextState.richParagraphList.lastIndex) {
                                if (
                                    !selection.collapsed &&
                                    selection.min < index + 1 &&
                                    selection.max > index
                                ) appendLine()
                                index++
                            }
                        }
                    }
                }
            }
        }

        val plainText = richTextAnnotatedString.text

        // Try to write HTML + plain text to the platform clipboard.
        // This bypasses the lossy AnnotatedString→Spanned→Html.toHtml() round-trip.
        if (!selection.collapsed && plainText.isNotEmpty()) {
            val html = buildSelectedHtml(selection)
            if (html != null && spannedPasteHandler.writeHtml(html, plainText)) {
                return
            }
        }

        // Fallback: write AnnotatedString via Compose clipboard
        clipboardManager.setText(richTextAnnotatedString)
    }

    /**
     * Builds HTML for the selected portion of the rich text state.
     * Deep-copies the paragraphs that overlap the selection and trims
     * edge spans to match the selection boundaries.
     */
    @OptIn(ExperimentalRichTextApi::class)
    private fun buildSelectedHtml(selection: androidx.compose.ui.text.TextRange): String? {
        if (selection.collapsed) return null

        val selectedParagraphs = mutableListOf<RichParagraph>()
        var index = 0

        richTextState.richParagraphList.fastForEachIndexed { i, paragraph ->
            if (i > 0) index++ // paragraph separator

            val paraStart = index
            index += paragraph.type.startText.length
            val contentStart = index

            // Walk spans to find paragraph end
            index = computeSpanTreeEnd(paragraph.children, index)
            val paraEnd = index

            // Check if this paragraph overlaps with the selection
            if (paraEnd > selection.min && paraStart < selection.max) {
                val copy = paragraph.copy()
                // Trim spans to the selection range
                trimSpanTree(copy.children, contentStart, selection)
                copy.removeEmptyChildren()
                selectedParagraphs.add(copy)
            }
        }

        if (selectedParagraphs.isEmpty()) return null

        val tempState = RichTextState(initialRichParagraphList = selectedParagraphs)
        return RichTextStateHtmlParser.decode(tempState)
    }

    /**
     * Computes the text index after all spans in the tree.
     */
    private fun computeSpanTreeEnd(spans: List<RichSpan>, startIndex: Int): Int {
        var index = startIndex
        spans.fastForEach { span ->
            index += span.text.length
            index = computeSpanTreeEnd(span.children, index)
        }
        return index
    }

    /**
     * Trims span text in-place so only the portion within [selection] remains.
     * Spans entirely outside the selection have their text cleared.
     */
    private fun trimSpanTree(
        spans: MutableList<RichSpan>,
        startIndex: Int,
        selection: androidx.compose.ui.text.TextRange,
    ): Int {
        var index = startIndex
        spans.fastForEach { span ->
            val spanStart = index
            val spanEnd = spanStart + span.text.length

            if (spanEnd <= selection.min || spanStart >= selection.max) {
                // Span is entirely outside the selection — clear it
                span.text = ""
            } else {
                // Trim to the selected portion
                val trimStart = max(0, selection.min - spanStart)
                val trimEnd = min(span.text.length, selection.max - spanStart)
                span.text = span.text.substring(trimStart, trimEnd)
            }

            index = spanEnd
            index = trimSpanTree(span.children, index, selection)
        }
        return index
    }
}