package com.github.youngrok.mako.highlight

import com.github.youngrok.mako.parser.MakoLexer
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class MakoSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = MakoLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        KEYS[tokenType] ?: EMPTY

    companion object {
        val DELIMITER = key("MAKO_DELIMITER", DefaultLanguageHighlighterColors.KEYWORD)
        val EXPRESSION = key("MAKO_EXPRESSION_CODE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val KEYWORD = key("MAKO_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val TAG_NAME = key("MAKO_TAG_NAME", DefaultLanguageHighlighterColors.MARKUP_TAG)
        val ATTR_NAME = key("MAKO_ATTR_NAME", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE)
        val ATTR_VALUE = key("MAKO_ATTR_VALUE", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = key("MAKO_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BLOCK_COMMENT = key("MAKO_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val TEMPLATE_TEXT = key("MAKO_TEXT", HighlighterColors.TEXT)
        val BAD_CHARACTER = key("MAKO_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey =
            createTextAttributesKey(name, fallback)

        private val EMPTY = emptyArray<TextAttributesKey>()

        private val KEYS: Map<IElementType, Array<TextAttributesKey>> = buildMap {
            fun put(type: IElementType, vararg keys: TextAttributesKey) = put(type, arrayOf(*keys))

            put(MakoTypes.EXPR_OPEN, DELIMITER)
            put(MakoTypes.EXPR_CLOSE, DELIMITER)
            put(MakoTypes.EXPR_CODE, EXPRESSION)

            put(MakoTypes.CONTROL_PERCENT, DELIMITER)
            put(MakoTypes.CONTROL_CODE, KEYWORD)
            put(MakoTypes.CONTROL_END, KEYWORD)

            put(MakoTypes.PYBLOCK_OPEN, DELIMITER)
            put(MakoTypes.PYBLOCK_OPEN_MODULE, DELIMITER)
            put(MakoTypes.PYBLOCK_CLOSE, DELIMITER)

            put(MakoTypes.TAG_OPEN, DELIMITER)
            put(MakoTypes.TAG_END_OPEN, DELIMITER)
            put(MakoTypes.TAG_CLOSE, DELIMITER)
            put(MakoTypes.TAG_SELF_CLOSE, DELIMITER)
            put(MakoTypes.TAG_NAME, TAG_NAME)
            put(MakoTypes.ATTR_NAME, ATTR_NAME)
            put(MakoTypes.ATTR_EQ, DELIMITER)
            put(MakoTypes.ATTR_VALUE, ATTR_VALUE)

            put(MakoTypes.LINE_COMMENT, COMMENT)
            put(MakoTypes.DOC_COMMENT, BLOCK_COMMENT)

            put(MakoTypes.TEMPLATE_TEXT, TEMPLATE_TEXT)
            put(MakoTypes.OUTER, TEMPLATE_TEXT)
            put(MakoTypes.TEXT_BODY, TEMPLATE_TEXT)
            put(MakoTypes.BAD_CHARACTER, BAD_CHARACTER)
        }
    }
}
