package com.github.youngrok.mako.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * PSI element that wraps a region of embedded Python (`${...}`, `<% ... %>`,
 * `<%! ... %>`, or a `% ...` control line) and acts as an injection host so the
 * Python language can be injected into the code portion.
 *
 * The element spans the whole construct including delimiters; the injector is
 * responsible for selecting only the code sub-range to inject into.
 */
class MakoInjectionHost(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = codeRange() != null

    override fun updateText(text: String): PsiLanguageInjectionHost {
        // Templates are not typically refactored through injection edits; return
        // self so the platform does not crash on quick-edit attempts.
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<MakoInjectionHost> =
        LiteralTextEscaper.createSimple(this)

    /**
     * The child AST node that holds the Python code, or null if there is none
     * yet (e.g. an empty `${}`).
     */
    fun codeNode(): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (MakoTypes.PYTHON_CODE.contains(child.elementType)) return child
            child = child.treeNext
        }
        return null
    }

    private fun codeRange() = codeNode()
}
