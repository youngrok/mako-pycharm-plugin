package com.github.youngrok.mako.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

/**
 * A Mako tag attribute (`name="value"`). Implementing [ContributedReferenceHost]
 * makes the platform run registered [com.intellij.psi.PsiReferenceContributor]s
 * against this element, so the `file="..."` value can carry file references —
 * plain leaf tokens never consult the reference registry.
 */
class MakoAttribute(node: ASTNode) : ASTWrapperPsiElement(node), ContributedReferenceHost {

    override fun getReferences(): Array<PsiReference> =
        ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS)

    /** The attribute name (`file`, `name`, ...), or null if absent. */
    fun nameToken(): ASTNode? = node.findChildByType(MakoTypes.ATTR_NAME)

    /** The quoted value token, or null if the attribute has no value yet. */
    fun valueToken(): ASTNode? = node.findChildByType(MakoTypes.ATTR_VALUE)
}
