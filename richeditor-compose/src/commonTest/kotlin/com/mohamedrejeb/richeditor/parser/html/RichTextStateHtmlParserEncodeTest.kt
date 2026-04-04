package com.mohamedrejeb.richeditor.parser.html

import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.type.OrderedList
import com.mohamedrejeb.richeditor.paragraph.type.UnorderedList
import com.mohamedrejeb.richeditor.parser.utils.H1SpanStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RichTextStateHtmlParserEncodeTest {
    @Test
    fun testRemoveHtmlTextExtraSpaces() {
        val html = """
            Hello       World!      Welcome to 
             
             Compose Rich Text Editor!
        """.trimIndent()

        assertEquals(
            "Hello World! Welcome to Compose Rich Text Editor!",
            removeHtmlTextExtraSpaces(html)
        )
    }

    @OptIn(ExperimentalRichTextApi::class)
    @Test
    fun testHtmlWithImage() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>

            <h1>The img element</h1>

            <img src="https://picsum.photos/200/300" alt="Girl in a jacket" width="500" height="600">

            </body>
            </html>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        val h1 = richTextState.richParagraphList[0].children.first()
        val image = richTextState.richParagraphList[1].children.first()

        assertEquals(2, richTextState.richParagraphList.size)
        assertEquals(1, richTextState.richParagraphList[0].children.size)
        assertEquals(1, richTextState.richParagraphList[1].children.size)
        assertEquals("The img element", h1.text)
        assertEquals(H1SpanStyle, h1.spanStyle)
        assertIs<RichSpanStyle.Image>(image.richSpanStyle)
    }

    @OptIn(ExperimentalRichTextApi::class)
    @Test
    fun testHtmlWithBrAndImage() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>

            <h1>The img element</h1>
            <br>
            <img src="https://picsum.photos/200/300" alt="Girl in a jacket" width="500" height="600">

            </body>
            </html>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        val h1 = richTextState.richParagraphList[0].children.first()
        val image = richTextState.richParagraphList[2].children.first()

        assertEquals(3, richTextState.richParagraphList.size)
        assertEquals(1, richTextState.richParagraphList[0].children.size)
        assertTrue(richTextState.richParagraphList[1].isBlank())
        // It's only 1, but we have the added rich span for each paragraph with index > 0
        assertEquals(1, richTextState.richParagraphList[2].children.size)
        assertEquals("The img element", h1.text)
        assertEquals(H1SpanStyle, h1.spanStyle)
        assertIs<RichSpanStyle.Image>(image.richSpanStyle)
    }

    @Test
    fun testHtmlWithEmptyBlockElements1() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>

            <p><p>dd  dd<span> second</span></p></p>

            </body>
            </html>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(1, richTextState.richParagraphList.size)
        assertEquals("dd dd second", richTextState.annotatedString.text)

        richTextState.setHtml(
            """
                <!DOCTYPE html>
                <html>
                <body>
    
                <p><p><p> second</p></p></p>
    
                </body>
                </html>
            """.trimIndent()
        )
    }

    @Test
    fun testHtmlWithEmptyBlockElements2() {
        val html =
            """
                <!DOCTYPE html>
                <html>
                <body>
    
                <p><p><p> second</p></p></p>
    
                </body>
                </html>
            """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(1, richTextState.richParagraphList.size)
        assertEquals("second", richTextState.annotatedString.text)
    }

    @Test
    fun testBrEncodeDecode() {
        val html = "<p>ABC</p><br><br><br>"

        val state = RichTextStateHtmlParser.encode(html)

        assertEquals(5, state.richParagraphList.size)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testBrEncodeDecode2() {
        val html = "<br><p>ABC</p><br><br><p>ABC</p><br><br>"

        val state = RichTextStateHtmlParser.encode(html)

        assertEquals(8, state.richParagraphList.size)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testBrInMiddleOrParagraph() {
        val html = """
            <h1>Hello<br>World!</h1>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(2, richTextState.richParagraphList.size)
        assertEquals(1, richTextState.richParagraphList[0].children.size)
        assertEquals(1, richTextState.richParagraphList[1].children.size)

        val firstPart = richTextState.richParagraphList[0].children.first()
        val secondPart = richTextState.richParagraphList[1].children.first()

        assertEquals("Hello", firstPart.text)
        assertEquals("World!", secondPart.text)

        assertEquals(H1SpanStyle, firstPart.spanStyle)
        assertEquals(H1SpanStyle, secondPart.spanStyle)
    }

    // Regression: setHtml → toHtml roundtrip must preserve <br> counts between block elements
    @Test
    fun testSetHtmlToHtmlRoundtripPreservesBrTags() {
        val html = "<p>Eren</p><br><br><p>Tufekci</p><br><p>Yasemin</p><br><br><br><br><p>Tufekci</p>"
        val state = RichTextState()
        state.setHtml(html)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testSetHtmlToHtmlSingleBrBetweenParagraphs() {
        val html = "<p>A</p><br><p>B</p>"
        val state = RichTextState()
        state.setHtml(html)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testSetHtmlToHtmlDoubleBrBetweenParagraphs() {
        val html = "<p>A</p><br><br><p>B</p>"
        val state = RichTextState()
        state.setHtml(html)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testSetHtmlToHtmlDoubleRoundtrip() {
        // The user reports each roundtrip loses one <br> per group.
        // Test that calling setHtml(toHtml()) a second time is still idempotent.
        val html = "<p>Eren</p><br><br><p>Tufekci</p><br><p>Yasemin</p><br><br><br><br><p>Tufekci</p>"
        val state = RichTextState()
        state.setHtml(html)
        val firstRoundtrip = state.toHtml()
        assertEquals(html, firstRoundtrip, "First roundtrip should preserve all <br> tags")

        // Second roundtrip: feed the output back
        state.setHtml(firstRoundtrip)
        val secondRoundtrip = state.toHtml()
        assertEquals(html, secondRoundtrip, "Second roundtrip should still preserve all <br> tags")

        // Third roundtrip for good measure
        state.setHtml(secondRoundtrip)
        val thirdRoundtrip = state.toHtml()
        assertEquals(html, thirdRoundtrip, "Third roundtrip should still preserve all <br> tags")
    }

    @Test
    fun testSetHtmlParagraphCountPreserved() {
        // Verify the internal paragraph model is correct after setHtml
        val html = "<p>A</p><br><br><p>B</p>"
        val state = RichTextState()
        state.setHtml(html)
        // Expected: P("A"), P(empty/br), P(empty/br), P("B") = 4 paragraphs
        // The </p> creates a separator paragraph, first <br> creates another,
        // second <br> creates one that gets reused by <p>B</p>
        // So we should have: P("A"), P(empty), P(empty), P("B")
        val paragraphs = state.richParagraphList
        val contentParagraphs = paragraphs.filter { !it.isEmpty() }
        val emptyParagraphs = paragraphs.filter { it.isEmpty() }
        assertEquals(2, contentParagraphs.size, "Should have 2 content paragraphs")
        assertEquals(2, emptyParagraphs.size, "Should have 2 empty paragraphs (for 2 <br> tags)")
        assertEquals("A", contentParagraphs[0].children.firstOrNull()?.text)
        assertEquals("B", contentParagraphs[1].children.firstOrNull()?.text)
    }

    @Test
    fun testSetHtmlOnPreExistingState() {
        // In the app, setHtml might be called on a state that already has content
        val state = RichTextState()
        state.setHtml("<p>Initial content</p><br><p>More content</p>")
        // Now overwrite with the problematic HTML
        val html = "<p>Eren</p><br><br><p>Tufekci</p><br><p>Yasemin</p><br><br><br><br><p>Tufekci</p>"
        state.setHtml(html)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testSetHtmlThenSimulateRecomposition() {
        // Simulate what happens when Compose triggers updateAnnotatedString after setHtml
        val state = RichTextState()
        val html = "<p>A</p><br><br><p>B</p>"
        state.setHtml(html)

        // Manually call updateAnnotatedString (which Compose would trigger via recomposition)
        // This is the text-parameter path with the Bug Fixes guards
        state.updateAnnotatedString(state.textFieldValue)

        assertEquals(html, state.toHtml())
    }

    @Test
    fun testSetHtmlThenOnTextFieldValueChange() {
        // Simulate BasicTextField calling onValueChange with the same text (sync)
        val state = RichTextState()
        val html = "<p>A</p><br><br><p>B</p>"
        state.setHtml(html)

        // Simulate what BasicTextField might do: call onTextFieldValueChange with same value
        state.onTextFieldValueChange(state.textFieldValue)

        assertEquals(html, state.toHtml())
    }

    @Test
    fun testEncodeDirectRoundtripPreservesBr() {
        // Test encode directly (not through setHtml) to isolate if the issue is in encode vs updateRichParagraphList
        val html = "<p>A</p><br><br><p>B</p>"
        val state = RichTextStateHtmlParser.encode(html)
        assertEquals(html, state.toHtml())
    }

    @Test
    fun testEncodeUnorderedList() {
        val html = """
            <ul>
                <li>Item 1</li>
                <li>Item 2</li>
                <li>Item 3</li>
            </ul>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(3, richTextState.richParagraphList.size)

        val firstItem = richTextState.richParagraphList[0].children[0]
        val secondItem = richTextState.richParagraphList[1].children[0]
        val thirdItem = richTextState.richParagraphList[2].children[0]

        richTextState.richParagraphList.forEach { p ->
            assertIs<UnorderedList>(p.type)
        }

        assertEquals("Item 1", firstItem.text)
        assertEquals("Item 2", secondItem.text)
        assertEquals("Item 3", thirdItem.text)
    }

    @Test
    fun testEncodeUnorderedListWithNestedList() {
        val html = """
            <ul>
                <li>Item1</li>
                <li>Item2
                    <ul>
                        <li>Item2.1</li>
                        <li>Item2.2</li>
                    </ul>
                </li>
                <li>Item3</li>
            </ul>
        """
            .trimIndent()
            .replace("\n", "")
            .replace(" ", "")

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(5, richTextState.richParagraphList.size)

        val firstItem = richTextState.richParagraphList[0].children[0]
        val secondItem = richTextState.richParagraphList[1].children[0]
        val thirdItem = richTextState.richParagraphList[2].children[0]
        val fourthItem = richTextState.richParagraphList[3].children[0]
        val fifthItem = richTextState.richParagraphList[4].children[0]

        richTextState.richParagraphList.forEachIndexed { i, p ->
            val type = p.type
            assertIs<UnorderedList>(type)

            if (
                i == 0 ||
                i == 1 ||
                i == 4
            )
                assertEquals(1, type.level)
            else
                assertEquals(2, type.level)
        }

        assertEquals("Item1", firstItem.text)
        assertEquals("Item2", secondItem.text)
        assertEquals("Item2.1", thirdItem.text)
        assertEquals("Item2.2", fourthItem.text)
        assertEquals("Item3", fifthItem .text)
    }

    @Test
    fun testEncodeOrderedList() {
        val html = """
            <ol>
                <li>Item 1</li>
                <li>Item 2</li>
                <li>Item 3</li>
            </ol>
        """.trimIndent()

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(3, richTextState.richParagraphList.size)

        val firstItem = richTextState.richParagraphList[0].children[0]
        val secondItem = richTextState.richParagraphList[1].children[0]
        val thirdItem = richTextState.richParagraphList[2].children[0]

        richTextState.richParagraphList.forEach { p ->
            assertIs<OrderedList>(p.type)
        }

        assertEquals("Item 1", firstItem.text)
        assertEquals("Item 2", secondItem.text)
        assertEquals("Item 3", thirdItem.text)
    }

    @Test
    fun testEncodeOrderedListWithNestedList() {
        val html = """
            <ol>
                <li>Item1</li>
                <li>Item2
                    <ol>
                        <li>Item2.1</li>
                        <li>Item2.2</li>
                    </ol>
                </li>
                <li>Item3</li>
            </ol>
        """
            .trimIndent()
            .replace("\n", "")
            .replace(" ", "")

        val richTextState = RichTextStateHtmlParser.encode(html)

        assertEquals(5, richTextState.richParagraphList.size)

        val firstItem = richTextState.richParagraphList[0].children[0]
        val secondItem = richTextState.richParagraphList[1].children[0]
        val thirdItem = richTextState.richParagraphList[2].children[0]
        val fourthItem = richTextState.richParagraphList[3].children[0]
        val fifthItem = richTextState.richParagraphList[4].children[0]

        richTextState.richParagraphList.forEachIndexed { i, p ->
            val type = p.type
            assertIs<OrderedList>(type)

            if (
                i == 0 ||
                i == 1 ||
                i == 4
            )
                assertEquals(1, type.level)
            else
                assertEquals(2, type.level)
        }

        assertEquals("Item1", firstItem.text)
        assertEquals("Item2", secondItem.text)
        assertEquals("Item2.1", thirdItem.text)
        assertEquals("Item2.2", fourthItem.text)
        assertEquals("Item3", fifthItem .text)
    }

    @Test
    fun testHtmlRoundTripPreservesParagraphs() {
        // Simulate: user writes two paragraphs, copies, pastes
        val state = RichTextState()
        state.setHtml("<p>Eren</p><p>Tüfekçi</p>")

        // Verify 2 paragraphs were created
        assertEquals(2, state.richParagraphList.size, "setHtml should create 2 paragraphs")

        val firstText = state.richParagraphList[0].children.firstOrNull()?.text ?: ""
        val secondText = state.richParagraphList[1].children.firstOrNull()?.text ?: ""
        assertEquals("Eren", firstText)
        assertEquals("Tüfekçi", secondText)

        // Now generate HTML (simulates what buildSelectedHtml does)
        val html = state.toHtml()
        println("Generated HTML: $html")

        // Parse it back (simulates what paste does)
        val pastedState = RichTextState()
        pastedState.setHtml(html)

        assertEquals(2, pastedState.richParagraphList.size, "Round-trip should preserve 2 paragraphs. HTML was: $html")

        val pastedFirst = pastedState.richParagraphList[0].children.firstOrNull()?.text ?: ""
        val pastedSecond = pastedState.richParagraphList[1].children.firstOrNull()?.text ?: ""
        assertEquals("Eren", pastedFirst)
        assertEquals("Tüfekçi", pastedSecond)
    }

    @Test
    fun testInsertHtmlPreservesParagraphs() {
        // Simulate pasting HTML into an empty editor
        val targetState = RichTextState()
        val html = "<p>Eren</p><p>Tüfekçi</p>"

        targetState.insertHtmlAfterSelection(html)

        println("After insertHtml: ${targetState.richParagraphList.size} paragraphs")
        targetState.richParagraphList.forEachIndexed { i, p ->
            println("  Paragraph $i: children=${p.children.map { it.text }}")
        }

        assertEquals(2, targetState.richParagraphList.size, "insertHtmlAfterSelection should create 2 paragraphs")
    }

}