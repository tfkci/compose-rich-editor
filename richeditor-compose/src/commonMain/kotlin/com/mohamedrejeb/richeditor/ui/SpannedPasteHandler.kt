package com.mohamedrejeb.richeditor.ui

import androidx.compose.runtime.Composable
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Platform-specific handler for span-aware / HTML clipboard content.
 *
 * On Android this reads [ClipData.Item.htmlText] (set by Chrome, Docs, etc.) or converts
 * an [android.text.Spanned] value (set by AppCompatEditText copies) via [android.text.Html].
 * On other platforms both methods are no-ops.
 */
internal interface SpannedPasteHandler {
    /**
     * Returns the HTML string currently on the platform clipboard, or `null` if the clipboard
     * holds only unstyled plain text.  Does **not** modify any state.
     */
    fun readHtml(): String?

    /**
     * Convenience: reads HTML, deletes any active selection, then inserts the styled content at
     * the cursor.  Used for TextToolbar "Paste" and hardware-keyboard Ctrl/Cmd+V intercepts.
     *
     * @return `true` if styled content was applied; `false` to fall back to plain-text paste.
     */
    fun tryPasteSpanned(): Boolean

    /**
     * Called from [RichTextState.onTextFieldValueChange] when text was added without going
     * through [RichTextClipboardManager.getText] (e.g. the soft-keyboard IME paste button, which
     * uses Android's [InputConnection.performContextMenuAction] and reads the system clipboard
     * directly, bypassing [LocalClipboardManager]).
     *
     * Compares [addedText] against the current clipboard plain text.  If they match **and** the
     * clipboard contains HTML or an Android [Spanned], returns the HTML so the caller can apply
     * rich formatting instead of keeping the plain-text insertion.
     *
     * @return HTML string if the added text was a styled paste, `null` otherwise.
     */
    fun getHtmlIfMatch(addedText: String): String?

    /**
     * Writes rich content to the platform clipboard, setting both HTML and plain-text
     * representations so that pasting into rich-text targets preserves formatting while
     * plain-text targets still receive readable content.
     *
     * @return `true` if the platform clipboard was written successfully; `false` if unsupported,
     *         in which case the caller should fall back to [ClipboardManager.setText].
     */
    fun writeHtml(html: String, plainText: String): Boolean
}

@Composable
internal expect fun rememberSpannedPasteHandler(state: RichTextState): SpannedPasteHandler
