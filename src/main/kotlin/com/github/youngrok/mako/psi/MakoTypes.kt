package com.github.youngrok.mako.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Central registry of all Mako token types produced by the lexer and the
 * element types produced by the parser.
 */
object MakoTypes {
    // --- Raw template content ------------------------------------------------
    /** HTML / plain text between Mako constructs. */
    @JvmField val TEMPLATE_TEXT: IElementType = MakoTokenType("MAKO_TEMPLATE_TEXT")

    /**
     * Marker token for a contiguous run of non-Mako (data-language) text. The
     * template-data machinery replaces these regions with HTML PSI. This is the
     * single token the data lexer sees as "outer content".
     */
    @JvmField val OUTER: IElementType = MakoTokenType("MAKO_OUTER")

    // --- Expression: ${ ... } ------------------------------------------------
    @JvmField val EXPR_OPEN: IElementType = MakoTokenType("MAKO_EXPR_OPEN")          // ${
    @JvmField val EXPR_CLOSE: IElementType = MakoTokenType("MAKO_EXPR_CLOSE")        // }
    /** Python code inside ${ ... }. */
    @JvmField val EXPR_CODE: IElementType = MakoTokenType("MAKO_EXPR_CODE")

    // --- Control line: % if x:  /  % endif  ---------------------------------
    @JvmField val CONTROL_PERCENT: IElementType = MakoTokenType("MAKO_CONTROL_PERCENT") // leading %
    /** Python code on a control line (the `if x:` part). */
    @JvmField val CONTROL_CODE: IElementType = MakoTokenType("MAKO_CONTROL_CODE")
    /** A `% end...` keyword token, highlighted as a keyword. */
    @JvmField val CONTROL_END: IElementType = MakoTokenType("MAKO_CONTROL_END")

    // --- Python blocks: <% ... %>  and  <%! ... %> ---------------------------
    @JvmField val PYBLOCK_OPEN: IElementType = MakoTokenType("MAKO_PYBLOCK_OPEN")     // <%
    @JvmField val PYBLOCK_OPEN_MODULE: IElementType = MakoTokenType("MAKO_PYBLOCK_OPEN_MODULE") // <%!
    @JvmField val PYBLOCK_CLOSE: IElementType = MakoTokenType("MAKO_PYBLOCK_CLOSE")   // %>
    /** Python code inside <% ... %> / <%! ... %>. */
    @JvmField val PYBLOCK_CODE: IElementType = MakoTokenType("MAKO_PYBLOCK_CODE")

    // --- Comments ------------------------------------------------------------
    /** Single-line `## comment`. */
    @JvmField val LINE_COMMENT: IElementType = MakoTokenType("MAKO_LINE_COMMENT")
    /** `<%doc> ... </%doc>` block comment (whole thing, content included). */
    @JvmField val DOC_COMMENT: IElementType = MakoTokenType("MAKO_DOC_COMMENT")

    // --- Tags: <%def ...>, </%def>, <%inherit .../>, etc. --------------------
    @JvmField val TAG_OPEN: IElementType = MakoTokenType("MAKO_TAG_OPEN")             // <%
    @JvmField val TAG_END_OPEN: IElementType = MakoTokenType("MAKO_TAG_END_OPEN")     // </%
    @JvmField val TAG_NAME: IElementType = MakoTokenType("MAKO_TAG_NAME")             // def, inherit, page:something
    @JvmField val TAG_CLOSE: IElementType = MakoTokenType("MAKO_TAG_CLOSE")           // >
    @JvmField val TAG_SELF_CLOSE: IElementType = MakoTokenType("MAKO_TAG_SELF_CLOSE") // />
    @JvmField val ATTR_NAME: IElementType = MakoTokenType("MAKO_ATTR_NAME")
    @JvmField val ATTR_EQ: IElementType = MakoTokenType("MAKO_ATTR_EQ")               // =
    @JvmField val ATTR_VALUE: IElementType = MakoTokenType("MAKO_ATTR_VALUE")         // "..." or '...'

    // --- <%text> raw region --------------------------------------------------
    @JvmField val TEXT_BODY: IElementType = MakoTokenType("MAKO_TEXT_BODY")

    // --- Misc ----------------------------------------------------------------
    @JvmField val WHITE_SPACE: IElementType = MakoTokenType("MAKO_WHITE_SPACE")
    @JvmField val BAD_CHARACTER: IElementType = MakoTokenType("MAKO_BAD_CHARACTER")

    // --- Parser element types ------------------------------------------------
    @JvmField val EXPRESSION: IElementType = MakoElementType("MAKO_EXPRESSION")
    @JvmField val CONTROL_LINE: IElementType = MakoElementType("MAKO_CONTROL_LINE")
    @JvmField val PYTHON_BLOCK: IElementType = MakoElementType("MAKO_PYTHON_BLOCK")
    @JvmField val TAG: IElementType = MakoElementType("MAKO_TAG")
    @JvmField val ATTRIBUTE: IElementType = MakoElementType("MAKO_ATTRIBUTE")

    // --- Token sets ----------------------------------------------------------
    @JvmField val COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, DOC_COMMENT)
    @JvmField val WHITESPACES: TokenSet = TokenSet.create(WHITE_SPACE)
    @JvmField val STRINGS: TokenSet = TokenSet.create(ATTR_VALUE)

    /** Tokens that contain Python code, used by the language injector. */
    @JvmField val PYTHON_CODE: TokenSet =
        TokenSet.create(EXPR_CODE, CONTROL_CODE, PYBLOCK_CODE)
}
