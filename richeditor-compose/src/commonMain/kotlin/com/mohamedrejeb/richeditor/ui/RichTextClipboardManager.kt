package com.mohamedrejeb.richeditor.ui

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.type.ParagraphType.Companion.startText
import com.mohamedrejeb.richeditor.utils.append
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.max
import kotlin.math.min

/**
 * In-memory cache of the last HTML copied from a RichTextState editor.
 * Used as a fallback when the platform clipboard doesn't preserve htmlText
 * (e.g., Compose's newer LocalClipboard API bypasses ClipData.newHtmlText).
 *
 * Multiple plain-text representations are stored because the clipboard text
 * may use different separators (spaces from the internal representation,
 * or newlines from the AnnotatedString).
 */
internal object RichTextClipboardCache {
    var cachedHtml: String? = null
    private var cachedPlainTexts: List<String> = emptyList()

    fun store(html: String, vararg plainTextVariants: String) {
        cachedHtml = html
        cachedPlainTexts = plainTextVariants.toList()
        pasteLog(PASTE_TAG, "ClipboardCache.store: html=${html.take(120)}, variants=${plainTextVariants.map { it.take(40) }}")
    }

    /**
     * Returns cached HTML if the given [clipboardPlainText] matches any stored variant.
     */
    fun match(clipboardPlainText: String): String? {
        val html = cachedHtml ?: return null
        val trimmedInput = clipboardPlainText.trimEnd()
        for (variant in cachedPlainTexts) {
            if (variant.trimEnd() == trimmedInput) {
                pasteLog(PASTE_TAG, "ClipboardCache.match: HIT — returning cached HTML (${html.length} chars)")
                return html
            }
        }
        pasteLog(PASTE_TAG, "ClipboardCache.match: MISS — clip=\"${clipboardPlainText.take(40)}\"")
        return null
    }
}

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

        // Always cache the HTML in-memory so it's available on paste even if
        // the platform clipboard doesn't preserve htmlText.
        if (!selection.collapsed && plainText.isNotEmpty()) {
            val html = try {
                richTextState.toHtml()
            } catch (_: Exception) {
                null
            }
            if (html != null) {
                // Cache in-memory for self-paste.
                // Store the internal text (spaces between paragraphs) and
                // the AnnotatedString text (may have newlines) as match variants.
                val internalText = richTextState.textFieldValue.text
                RichTextClipboardCache.store(html, plainText, internalText)

                // Also try to write HTML directly to platform clipboard
                pasteLog(PASTE_TAG, "setText: writing HTML to clipboard (${html.length} chars)")
                if (spannedPasteHandler.writeHtml(html, plainText)) {
                    pasteLog(PASTE_TAG, "setText: writeHtml succeeded — returning")
                    return
                }
            }
        }

        // Fallback: write AnnotatedString via Compose clipboard
        clipboardManager.setText(richTextAnnotatedString)
    }
}