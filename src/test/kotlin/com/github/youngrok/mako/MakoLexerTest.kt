package com.github.youngrok.mako

import com.github.youngrok.mako.parser.MakoLexer
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class MakoLexerTest {

    private fun lex(text: String): List<Pair<IElementType?, String>> {
        val lexer = MakoLexer()
        lexer.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<IElementType?, String>>()
        while (lexer.tokenType != null) {
            out += lexer.tokenType to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return out
    }

    /** The lexer must always cover the whole input with no gaps or overlaps. */
    private fun assertCoversInput(text: String) {
        val lexer = MakoLexer()
        lexer.start(text, 0, text.length, 0)
        var pos = 0
        while (lexer.tokenType != null) {
            assertEquals("token starts where previous ended", pos, lexer.tokenStart)
            assert(lexer.tokenEnd > lexer.tokenStart) { "zero-length token at $pos" }
            pos = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals("entire input consumed", text.length, pos)
    }

    @Test
    fun closingTagAfterText() {
        // Regression: `</%def>` must lex as a closing tag, not be swallowed into
        // the preceding template text.
        val tokens = lex("<footer>x</footer>\n</%def>").filter { it.first != MakoTypes.WHITE_SPACE }
        val endOpen = tokens.indexOfFirst { it.first == MakoTypes.TAG_END_OPEN }
        assertEquals("</%", tokens[endOpen].second)
        assertEquals(MakoTypes.TAG_NAME, tokens[endOpen + 1].first)
        assertEquals("def", tokens[endOpen + 1].second)
        assertEquals(MakoTypes.TAG_CLOSE, tokens[endOpen + 2].first)
    }

    @Test
    fun expression() {
        val tokens = lex("a ${'$'}{x + 1} b")
        assertEquals(MakoTypes.OUTER, tokens[0].first)
        assertEquals(MakoTypes.EXPR_OPEN, tokens[1].first)
        assertEquals(MakoTypes.EXPR_CODE, tokens[2].first)
        assertEquals("x + 1", tokens[2].second)
        assertEquals(MakoTypes.EXPR_CLOSE, tokens[3].first)
    }

    @Test
    fun expressionWithNestedBracesAndStrings() {
        val tokens = lex("${'$'}{ {'a': 1}.get('a') }")
        assertEquals(MakoTypes.EXPR_OPEN, tokens[0].first)
        assertEquals(MakoTypes.EXPR_CODE, tokens[1].first)
        assertEquals(" {'a': 1}.get('a') ", tokens[1].second)
        assertEquals(MakoTypes.EXPR_CLOSE, tokens[2].first)
    }

    @Test
    fun controlLineAndEnd() {
        val tokens = lex("% if x:\n% endif\n").filter { it.first != MakoTypes.WHITE_SPACE }
        assertEquals(MakoTypes.CONTROL_PERCENT, tokens[0].first)
        assertEquals(MakoTypes.CONTROL_CODE, tokens[1].first)
        assertEquals("if x:", tokens[1].second)
        assertEquals(MakoTypes.CONTROL_PERCENT, tokens[3].first)
        assertEquals(MakoTypes.CONTROL_END, tokens[4].first)
        assertEquals("endif", tokens[4].second)
    }

    @Test
    fun pythonBlockAndModuleBlock() {
        val inline = lex("<% x = 1 %>")
        assertEquals(MakoTypes.PYBLOCK_OPEN, inline[0].first)
        assertEquals(MakoTypes.PYBLOCK_CODE, inline[1].first)
        assertEquals(MakoTypes.PYBLOCK_CLOSE, inline[2].first)

        val module = lex("<%! import os %>")
        assertEquals(MakoTypes.PYBLOCK_OPEN_MODULE, module[0].first)
    }

    @Test
    fun lineComment() {
        val tokens = lex("## hi\ntext")
        assertEquals(MakoTypes.LINE_COMMENT, tokens[0].first)
        assertEquals("## hi", tokens[0].second)
    }

    @Test
    fun docComment() {
        val tokens = lex("<%doc>multi\nline</%doc>")
        assertEquals(MakoTypes.DOC_COMMENT, tokens[0].first)
    }

    @Test
    fun defTag() {
        val tokens = lex("<%def name=\"f(x)\">").filter { it.first != MakoTypes.WHITE_SPACE }
        assertEquals(MakoTypes.TAG_OPEN, tokens[0].first)
        assertEquals(MakoTypes.TAG_NAME, tokens[1].first)
        assertEquals("def", tokens[1].second)
        assertEquals(MakoTypes.ATTR_NAME, tokens[2].first)
        assertEquals("name", tokens[2].second)
        assertEquals(MakoTypes.ATTR_EQ, tokens[3].first)
        assertEquals(MakoTypes.ATTR_VALUE, tokens[4].first)
        assertEquals(MakoTypes.TAG_CLOSE, tokens[5].first)
    }

    @Test
    fun selfClosingTag() {
        val tokens = lex("<%inherit file=\"base.html\"/>").filter { it.first != MakoTypes.WHITE_SPACE }
        assertEquals(MakoTypes.TAG_OPEN, tokens[0].first)
        assertEquals(MakoTypes.TAG_NAME, tokens[1].first)
        assertEquals(MakoTypes.TAG_SELF_CLOSE, tokens.last().first)
    }

    @Test
    fun closingTag() {
        val tokens = lex("</%def>")
        assertEquals(MakoTypes.TAG_END_OPEN, tokens[0].first)
        assertEquals(MakoTypes.TAG_NAME, tokens[1].first)
        assertEquals(MakoTypes.TAG_CLOSE, tokens[2].first)
    }

    @Test
    fun escapedPercentIsText() {
        val tokens = lex("%% literal")
        assertEquals(MakoTypes.OUTER, tokens[0].first)
    }

    @Test
    fun textRegionSuspendsParsing() {
        val tokens = lex("<%text>${'$'}{not_code}</%text>")
        assertEquals(MakoTypes.TEXT_BODY, tokens[0].first)
        assertEquals("<%text>${'$'}{not_code}</%text>", tokens[0].second)
    }

    @Test
    fun coversComplexInput() {
        assertCoversInput(
            """
            ## comment
            <%inherit file="b.mako"/>
            <%! import os %>
            <h1>${'$'}{title}</h1>
            % for x in items:
              <li>${'$'}{x | h}</li>
            % endfor
            <% y = {'a': 1} %>
            <%doc>doc</%doc>
            """.trimIndent(),
        )
    }

    @Test
    fun unterminatedExpressionDoesNotHang() {
        assertCoversInput("${'$'}{ x + ")
    }

    @Test
    fun unterminatedTagDoesNotHang() {
        assertCoversInput("<%def name=\"f\"")
    }
}
