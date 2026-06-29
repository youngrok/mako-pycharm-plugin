package com.github.youngrok.mako.parser

import com.github.youngrok.mako.psi.MakoAttribute
import com.github.youngrok.mako.psi.MakoFile
import com.github.youngrok.mako.psi.MakoInjectionHost
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.ASTWrapperPsiElement

class MakoParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = MakoLexer()

    override fun createParser(project: Project?): PsiParser = MakoParser()

    override fun getFileNodeType(): IFileElementType = MakoTemplateDataElementTypes.FILE

    override fun getCommentTokens(): TokenSet = MakoTypes.COMMENTS

    override fun getWhitespaceTokens(): TokenSet = MakoTypes.WHITESPACES

    override fun getStringLiteralElements(): TokenSet = MakoTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        MakoTypes.EXPRESSION,
        MakoTypes.PYTHON_BLOCK,
        MakoTypes.CONTROL_LINE -> MakoInjectionHost(node)
        MakoTypes.ATTRIBUTE -> MakoAttribute(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = MakoFile(viewProvider)
}
