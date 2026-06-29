package com.github.youngrok.mako.parser

import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * A lightweight parser that groups the lexer's tokens into a shallow PSI tree.
 * The goal is not a full Python/HTML AST (those come from language injection)
 * but to provide stable, well-named anchor elements:
 *
 *  - [MakoTypes.EXPRESSION]  wraps `${ ... }`
 *  - [MakoTypes.PYTHON_BLOCK] wraps `<% ... %>` and `<%! ... %>`
 *  - [MakoTypes.CONTROL_LINE] wraps a `% ... ` line
 *  - [MakoTypes.TAG]          wraps `<%def ...>` / `</%def>` / `<%inherit .../>`
 *
 * Everything else (template text, comments) stays as leaf tokens.
 */
class MakoParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            parseElement(builder)
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseElement(builder: PsiBuilder) {
        when (builder.tokenType) {
            MakoTypes.EXPR_OPEN -> parseExpression(builder)
            MakoTypes.PYBLOCK_OPEN, MakoTypes.PYBLOCK_OPEN_MODULE -> parsePyBlock(builder)
            MakoTypes.CONTROL_PERCENT -> parseControlLine(builder)
            MakoTypes.TAG_OPEN, MakoTypes.TAG_END_OPEN -> parseTag(builder)
            else -> builder.advanceLexer() // text, comments, stray tokens
        }
    }

    private fun parseExpression(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // ${
        if (builder.tokenType == MakoTypes.EXPR_CODE) builder.advanceLexer()
        if (builder.tokenType == MakoTypes.EXPR_CLOSE) builder.advanceLexer()
        m.done(MakoTypes.EXPRESSION)
    }

    private fun parsePyBlock(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // <% or <%!
        if (builder.tokenType == MakoTypes.PYBLOCK_CODE) builder.advanceLexer()
        if (builder.tokenType == MakoTypes.PYBLOCK_CLOSE) builder.advanceLexer()
        m.done(MakoTypes.PYTHON_BLOCK)
    }

    private fun parseControlLine(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // %
        if (builder.tokenType == MakoTypes.CONTROL_CODE || builder.tokenType == MakoTypes.CONTROL_END) {
            builder.advanceLexer()
        }
        m.done(MakoTypes.CONTROL_LINE)
    }

    private fun parseTag(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // <% or </%
        while (!builder.eof()) {
            val t = builder.tokenType
            if (t == MakoTypes.TAG_CLOSE || t == MakoTypes.TAG_SELF_CLOSE) {
                builder.advanceLexer()
                break
            }
            // Anything that isn't part of a tag means the tag was unterminated.
            if (t == MakoTypes.EXPR_OPEN || t == MakoTypes.PYBLOCK_OPEN ||
                t == MakoTypes.PYBLOCK_OPEN_MODULE || t == MakoTypes.CONTROL_PERCENT ||
                t == MakoTypes.TAG_OPEN || t == MakoTypes.TAG_END_OPEN ||
                t == MakoTypes.TEMPLATE_TEXT
            ) {
                break
            }
            if (t == MakoTypes.ATTR_NAME) {
                parseAttribute(builder)
            } else {
                builder.advanceLexer()
            }
        }
        m.done(MakoTypes.TAG)
    }

    /** Groups `name = "value"` into an [MakoTypes.ATTRIBUTE], so the attribute
     *  value can host file references (see MakoFileReferenceContributor). */
    private fun parseAttribute(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // ATTR_NAME
        if (builder.tokenType == MakoTypes.ATTR_EQ) {
            builder.advanceLexer()
            if (builder.tokenType == MakoTypes.ATTR_VALUE) builder.advanceLexer()
        }
        m.done(MakoTypes.ATTRIBUTE)
    }
}
