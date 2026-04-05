package com.mohamedrejeb.richeditor.ui

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.CoroutineScope

/**
 * Basic composable that enables users to edit rich text via hardware or software keyboard, but provides no decorations like hint or placeholder.
 * Whenever the user edits the texe.
 *
 * BasicRichTextEditor is a wrapper around [BasicTextField] and it accepts all the parameters that [BasicTextField] accepts.
 *
 * This composable provides basic rich text editing functionality, however does not include any
 * decorations such as borders, hints/placeholder. A design system based implementation such as
 * Material Design Filled text field is typically what is needed to cover most of the needs. This
 * composable is designed to be used when a custom implementation for different design system is
 * needed.
 *
 * @param state [RichTextState] that holds the state of the [BasicRichTextEditor].
 * @param modifier optional [Modifier] for this text field.
 * @param enabled controls the enabled state of the [BasicRichTextEditor]. When `false`, the text
 * field will be neither editable nor focusable, the input of the text field will not be selectable
 * @param readOnly controls the editable state of the [BasicRichTextEditor]. When `true`, the text
 * field can not be modified, however, a user can focus it and copy text from it. Read-only text
 * fields are usually used to display pre-filled forms that user can not edit
 * @param textStyle Style configuration that applies at character level such as color, font etc.
 * @param keyboardOptions software keyboard options that contains configuration such as
 * [KeyboardType] and [ImeAction].
 * @param keyboardActions when the input service emits an IME action, the corresponding callback
 * is called. Note that this IME action may be different from what you specified in
 * [KeyboardOptions.imeAction].
 * @param singleLine when set to true, this text field becomes a single horizontally scrolling
 * text field instead of wrapping onto multiple lines. The keyboard will be informed to not show
 * the return key as the [ImeAction]. [maxLines] and [minLines] are ignored as both are
 * automatically set to 1.
 * @param maxLines the maximum height in terms of maximum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines]. This parameter is ignored when [singleLine] is true.
 * @param minLines the minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines]. This parameter is ignored when [singleLine] is true.
 * @param maxLength the maximum length of the text field. If the text is longer than this value,
 * it will be ignored. The default value of this parameter is [Int.MAX_VALUE].
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw a cursor or selection around the text.
 * @param interactionSource the [MutableInteractionSource] representing the stream of
 * [Interaction]s for this TextField. You can create and pass in your own remembered
 * [MutableInteractionSource] if you want to observe [Interaction]s and customize the
 * appearance / behavior of this TextField in different [Interaction]s.
 * @param cursorBrush [Brush] to paint cursor with. If [SolidColor] with [Color.Unspecified]
 * provided, there will be no cursor drawn
 * @param decorationBox Composable lambda that allows to add decorations around text field, such
 * as icon, placeholder, helper messages or similar, and automatically increase the hit target area
 * of the text field. To allow you to control the placement of the inner text field relative to your
 * decorations, the text field implementation will pass in a framework-controlled composable
 * parameter "innerTextField" to the decorationBox lambda you provide. You must call
 * innerTextField exactly once.
 *
 */
@Composable
public fun BasicRichTextEditor(
    state: RichTextState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() }
) {
    BasicRichTextEditor(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        maxLength = maxLength,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox,
        contentPadding = PaddingValues()
    )
}

/**
 * Basic composable that enables users to edit rich text via hardware or software keyboard, but provides no decorations like hint or placeholder.
 * Whenever the user edits the texe.
 *
 * BasicRichTextEditor is a wrapper around [BasicTextField] and it accepts all the parameters that [BasicTextField] accepts.
 *
 * This composable provides basic rich text editing functionality, however does not include any
 * decorations such as borders, hints/placeholder. A design system based implementation such as
 * Material Design Filled text field is typically what is needed to cover most of the needs. This
 * composable is designed to be used when a custom implementation for different design system is
 * needed.
 *
 * @param state [RichTextState] that holds the state of the [BasicRichTextEditor].
 * @param modifier optional [Modifier] for this text field.
 * @param enabled controls the enabled state of the [BasicRichTextEditor]. When `false`, the text
 * field will be neither editable nor focusable, the input of the text field will not be selectable
 * @param readOnly controls the editable state of the [BasicRichTextEditor]. When `true`, the text
 * field can not be modified, however, a user can focus it and copy text from it. Read-only text
 * fields are usually used to display pre-filled forms that user can not edit
 * @param textStyle Style configuration that applies at character level such as color, font etc.
 * @param keyboardOptions software keyboard options that contains configuration such as
 * [KeyboardType] and [ImeAction].
 * @param keyboardActions when the input service emits an IME action, the corresponding callback
 * is called. Note that this IME action may be different from what you specified in
 * [KeyboardOptions.imeAction].
 * @param singleLine when set to true, this text field becomes a single horizontally scrolling
 * text field instead of wrapping onto multiple lines. The keyboard will be informed to not show
 * the return key as the [ImeAction]. [maxLines] and [minLines] are ignored as both are
 * automatically set to 1.
 * @param maxLines the maximum height in terms of maximum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines]. This parameter is ignored when [singleLine] is true.
 * @param minLines the minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines]. This parameter is ignored when [singleLine] is true.
 * @param maxLength the maximum length of the text field. If the text is longer than this value,
 * it will be ignored. The default value of this parameter is [Int.MAX_VALUE].
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw a cursor or selection around the text.
 * @param interactionSource the [MutableInteractionSource] representing the stream of
 * [Interaction]s for this TextField. You can create and pass in your own remembered
 * [MutableInteractionSource] if you want to observe [Interaction]s and customize the
 * appearance / behavior of this TextField in different [Interaction]s.
 * @param cursorBrush [Brush] to paint cursor with. If [SolidColor] with [Color.Unspecified]
 * provided, there will be no cursor drawn
 * @param decorationBox Composable lambda that allows to add decorations around text field, such
 * as icon, placeholder, helper messages or similar, and automatically increase the hit target area
 * of the text field. To allow you to control the placement of the inner text field relative to your
 * decorations, the text field implementation will pass in a framework-controlled composable
 * parameter "innerTextField" to the decorationBox lambda you provide. You must call
 * innerTextField exactly once.
 *
 */
@Composable
public fun BasicRichTextEditor(
    state: RichTextState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    singleParagraph: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() },
    contentPadding: PaddingValues
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val clipboardManager = LocalClipboardManager.current
    val pasteHandler = rememberSpannedPasteHandler(state)
    val richClipboardManager = remember(state, pasteHandler) {
        RichTextClipboardManager(
            richTextState = state,
            clipboardManager = clipboardManager,
            spannedPasteHandler = pasteHandler,
        )
    }
    val originalToolbar = LocalTextToolbar.current
    val richTextToolbar = remember(originalToolbar, pasteHandler, state) {
        SpannedPasteTextToolbar(delegate = originalToolbar, pasteHandler = pasteHandler, richTextState = state)
    }

    // Wrap the new Clipboard API (Compose 1.7+) to intercept copy and write HTML.
    // BasicTextField uses LocalClipboard.setClipEntry() for copy, completely bypassing
    // both LocalClipboardManager and TextToolbar callbacks.
    val originalClipboard = LocalClipboard.current
    val richClipboard = remember(originalClipboard, state, pasteHandler) {
        RichTextClipboard(
            delegate = originalClipboard,
            richTextState = state,
            spannedPasteHandler = pasteHandler,
        )
    }

    // Track the last non-collapsed selection so that RichTextClipboard can use it.
    // BasicTextField collapses the selection BEFORE calling Clipboard.setClipEntry(),
    // so by the time setClipEntry runs, richTextState.selection is already collapsed.
    LaunchedEffect(state.selection) {
        if (!state.selection.collapsed) {
            richClipboard.lastNonCollapsedSelection = state.selection
        }
    }

    // Wire the paste handler into state so onTextFieldValueChange can use it for the IME path.
    SideEffect {
        state.spannedPasteHandler = pasteHandler
    }

    LaunchedEffect(singleParagraph) {
        state.singleParagraphMode = singleParagraph
    }

    if (!singleParagraph) {
        // Workaround for Android to fix a bug in BasicTextField where it doesn't select the correct text
        // when the text contains multiple paragraphs.
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    val pressPosition = interaction.pressPosition
                    val topPadding = with(density) { contentPadding.calculateTopPadding().toPx() }
                    val startPadding = with(density) { contentPadding.calculateStartPadding(layoutDirection).toPx() }

                    adjustTextIndicatorOffset(
                        pressPosition = pressPosition,
                        state = state,
                        topPadding = topPadding,
                        startPadding = startPadding,
                    )
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalClipboardManager provides richClipboardManager,
        LocalClipboard provides richClipboard,
        LocalTextToolbar provides richTextToolbar,
    ) {
        BasicTextField(
            value = state.textFieldValue,
            onValueChange = {
                if (readOnly) return@BasicTextField
                if (it.text.length > maxLength) return@BasicTextField

                state.onTextFieldValueChange(it)
            },
            modifier = modifier
                .onPreviewKeyEvent { event ->
                    if (readOnly)
                        return@onPreviewKeyEvent false

                    // Intercept Ctrl+V / Cmd+V to apply span-aware paste on supported platforms.
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.V &&
                        (event.isCtrlPressed || event.isMetaPressed) &&
                        pasteHandler.tryPasteSpanned()
                    ) return@onPreviewKeyEvent true

                    // Intercept Ctrl+C / Cmd+C — handle copy entirely ourselves
                    // so the framework's async clipboard write cannot overwrite our HTML.
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.C &&
                        (event.isCtrlPressed || event.isMetaPressed) &&
                        !state.selection.collapsed
                    ) {
                        val sel = state.selection
                        richTextToolbar.writeHtmlToClipboard(sel)
                        state.selection = TextRange(sel.max)
                        return@onPreviewKeyEvent true // consume — don't let framework also copy
                    }

                    state.onPreviewKeyEvent(event)
                }
                .drawRichSpanStyle(
                    richTextState = state,
                    topPadding = with(density) { contentPadding.calculateTopPadding().toPx() },
                    startPadding = with(density) { contentPadding.calculateStartPadding(layoutDirection).toPx() },
                )
                .then(
                    if (!readOnly)
                        Modifier
                    else
                        Modifier.focusProperties { canFocus = false }
                )
                .then(
                    if (singleParagraph)
                        Modifier
                    else
                        Modifier
                            // Workaround for Desktop to fix a bug in BasicTextField where it doesn't select the correct text
                            // when the text contains multiple paragraphs.
                            .adjustTextIndicatorOffset(
                                state = state,
                                contentPadding = contentPadding,
                                density = density,
                                layoutDirection = layoutDirection,
                                scope = rememberCoroutineScope()
                            )
                ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = state.visualTransformation,
            onTextLayout = {
                state.onTextLayout(
                    textLayoutResult = it,
                    density = density,
                )
                onTextLayout(it)
            },
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = decorationBox,
        )
    }
}

internal expect fun Modifier.adjustTextIndicatorOffset(
    state: RichTextState,
    contentPadding: PaddingValues,
    density: Density,
    layoutDirection: LayoutDirection,
    scope: CoroutineScope,
): Modifier

internal suspend fun adjustTextIndicatorOffset(
    pressPosition: Offset,
    state: RichTextState,
    topPadding: Float,
    startPadding: Float,
) {
    state.adjustSelectionAndRegisterPressPosition(
        pressPosition = Offset(
            x = pressPosition.x - startPadding,
            y = pressPosition.y - topPadding
        ),
    )
}

public typealias RichTextChangedListener = (RichTextState) -> Unit

/**
 * A [TextToolbar] decorator that intercepts the "Paste" context-menu action and routes it
 * through [SpannedPasteHandler.tryPasteSpanned] before falling back to the original handler.
 * This is how styled text (HTML / Android spans) from other apps reaches the editor.
 */
private class SpannedPasteTextToolbar(
    private val delegate: TextToolbar,
    private val pasteHandler: SpannedPasteHandler,
    private val richTextState: RichTextState,
) : TextToolbar {

    override val status: TextToolbarStatus get() = delegate.status
    override fun hide() = delegate.hide()

    // Compose 1.7+ overload that includes onAutofillRequested.
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect = rect,
            onCopyRequested = wrapCopy(onCopyRequested),
            onPasteRequested = wrapPaste(onPasteRequested),
            onCutRequested = wrapCut(onCutRequested),
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = onAutofillRequested,
        )
    }

    // Older 5-parameter overload kept for binary compatibility with Compose < 1.7.
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect = rect,
            onCopyRequested = wrapCopy(onCopyRequested),
            onPasteRequested = wrapPaste(onPasteRequested),
            onCutRequested = wrapCut(onCutRequested),
            onSelectAllRequested = onSelectAllRequested,
        )
    }

    private fun wrapPaste(original: (() -> Unit)?): (() -> Unit)? =
        if (original == null) null
        else ({ if (!pasteHandler.tryPasteSpanned()) original() })

    /**
     * Wraps the COPY action.
     *
     * We do NOT call [original] at all because on newer Compose versions the
     * framework writes to the clipboard asynchronously via the suspend
     * `Clipboard.setClipEntry` API, which can overwrite our HTML **after**
     * we write it.  Instead we write HTML + plain text ourselves and then
     * collapse the selection so the UI behaves as if a normal copy happened.
     */
    private fun wrapCopy(original: (() -> Unit)?): (() -> Unit)? =
        if (original == null) null
        else ({
            val selection = richTextState.selection
            if (!selection.collapsed) {
                writeHtmlToClipboard(selection)
                richTextState.selection = TextRange(selection.max)
            } else {
                original()
            }
        })

    /**
     * Wraps the CUT action.
     *
     * For cut we still need [original] because it deletes the selected text
     * from the text field.  We capture the selection first, write HTML to
     * the clipboard, and then let the framework delete the selection.
     * The framework's async clipboard write will be ignored because
     * the in-memory cache will match on paste.
     */
    private fun wrapCut(original: (() -> Unit)?): (() -> Unit)? =
        if (original == null) null
        else ({
            val selectionBeforeCut = richTextState.selection
            // Write HTML to clipboard BEFORE the framework deletes the text.
            writeHtmlToClipboard(selectionBeforeCut)
            // Let the framework handle the deletion (and its own clipboard write).
            original()
        })

    /**
     * Writes the current rich text state as HTML to the system clipboard.
     * Called after the framework's default copy/cut has already placed
     * plain text / Spanned content on the clipboard.
     *
     * @param selection The text range to export. Must be captured BEFORE
     *   the framework copy runs, because it clears the selection.
     *   Falls back to the current selection if not provided.
     */
    fun writeHtmlToClipboard(selection: TextRange = richTextState.selection) {
        try {
            if (selection.collapsed) return

            val html = richTextState.toHtml(selection)
            // Build plain text with newlines between paragraphs, only for selected content
            val selectedParagraphs = richTextState.getRichParagraphListByTextRange(selection)
            val plainText = buildString {
                selectedParagraphs.forEachIndexed { i, paragraph ->
                    if (i > 0) append('\n')
                    fun appendSpanText(span: com.mohamedrejeb.richeditor.model.RichSpan) {
                        // Clip span text to the selection range
                        val spanStart = span.textRange.start
                        val spanEnd = span.textRange.end
                        if (spanStart < selection.max && spanEnd > selection.min && span.text.isNotEmpty()) {
                            val clipStart = maxOf(selection.min, spanStart) - spanStart
                            val clipEnd = minOf(selection.max, spanEnd) - spanStart
                            append(span.text.substring(
                                clipStart.coerceIn(0, span.text.length),
                                clipEnd.coerceIn(0, span.text.length),
                            ))
                        }
                        span.children.forEach { appendSpanText(it) }
                    }
                    paragraph.children.forEach { appendSpanText(it) }
                }
            }
            // Also capture the internal (space-separated) representation of the selection
            val internalText = richTextState.textFieldValue.text
            val selectedInternalText = internalText.substring(
                selection.min.coerceIn(0, internalText.length),
                selection.max.coerceIn(0, internalText.length),
            )

            if (html.isNotEmpty()) {
                RichTextClipboardCache.store(html, plainText, selectedInternalText)
                pasteHandler.writeHtml(html, plainText)
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Wraps the new Compose [Clipboard] API (1.7+) to intercept copy operations.
 *
 * [BasicTextField] in Compose 1.7+ calls [Clipboard.setClipEntry] directly for copy,
 * bypassing both [LocalClipboardManager] and [TextToolbar] callbacks. This wrapper
 * intercepts [setClipEntry] to:
 * 1. Let the framework write the clip entry (plain text)
 * 2. Immediately overwrite the system clipboard with HTML + plain text via [SpannedPasteHandler]
 * 3. Cache the HTML in [RichTextClipboardCache] for reliable paste matching
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private class RichTextClipboard(
    private val delegate: Clipboard,
    private val richTextState: RichTextState,
    private val spannedPasteHandler: SpannedPasteHandler,
) : Clipboard {

    /**
     * The last non-collapsed selection observed via LaunchedEffect in BasicRichTextEditor.
     * BasicTextField collapses the selection BEFORE calling [setClipEntry], so
     * [richTextState.selection] is already collapsed when we get here.
     * This field preserves what was selected at the time of copy.
     */
    var lastNonCollapsedSelection: TextRange? = null

    override val nativeClipboard: NativeClipboard get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? {
        return delegate.getClipEntry()
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val selection = lastNonCollapsedSelection ?: richTextState.selection

        // Let the framework write the clip entry first.
        delegate.setClipEntry(clipEntry)

        // Now overwrite with HTML if we have a valid selection.
        if (!selection.collapsed) {
            try {
                val html = richTextState.toHtml(selection)
                if (html.isNotEmpty()) {
                    // Build plain text from selected spans
                    val selectedParagraphs = richTextState.getRichParagraphListByTextRange(selection)
                    val plainText = buildString {
                        selectedParagraphs.forEachIndexed { i, paragraph ->
                            if (i > 0) append('\n')
                            fun appendSpanText(span: com.mohamedrejeb.richeditor.model.RichSpan) {
                                val spanStart = span.textRange.start
                                val spanEnd = span.textRange.end
                                if (spanStart < selection.max && spanEnd > selection.min && span.text.isNotEmpty()) {
                                    val clipStart = maxOf(selection.min, spanStart) - spanStart
                                    val clipEnd = minOf(selection.max, spanEnd) - spanStart
                                    append(span.text.substring(
                                        clipStart.coerceIn(0, span.text.length),
                                        clipEnd.coerceIn(0, span.text.length),
                                    ))
                                }
                                span.children.forEach { appendSpanText(it) }
                            }
                            paragraph.children.forEach { appendSpanText(it) }
                        }
                    }
                    val internalText = richTextState.textFieldValue.text
                    val selectedInternalText = internalText.substring(
                        selection.min.coerceIn(0, internalText.length),
                        selection.max.coerceIn(0, internalText.length),
                    )

                    RichTextClipboardCache.store(html, plainText, selectedInternalText)
                    spannedPasteHandler.writeHtml(html, plainText)
                }
            } catch (_: Exception) {
            }
            // Clear the saved selection so it's not reused for unrelated clipboard writes.
            lastNonCollapsedSelection = null
        }
    }
}
