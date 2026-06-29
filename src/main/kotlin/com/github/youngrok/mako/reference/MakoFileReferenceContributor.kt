package com.github.youngrok.mako.reference

import com.github.youngrok.mako.psi.MakoAttribute
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext

/**
 * Provides path completion and navigation for the `file="..."` attribute of Mako
 * tags such as `<%include file="..."/>` and `<%inherit file="..."/>`.
 *
 * Paths are resolved against the configured template root(s) (see
 * [MakoTemplateRoots]) — matching Mako's own `TemplateLookup` semantics — rather
 * than the including file's directory.
 */
class MakoFileReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(MakoAttribute::class.java),
            MakoFileReferenceProvider,
        )
    }

    private object MakoFileReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext,
        ): Array<PsiReference> {
            val attr = element as? MakoAttribute ?: return PsiReference.EMPTY_ARRAY
            if (attr.nameToken()?.text != "file") return PsiReference.EMPTY_ARRAY
            val valueNode = attr.valueToken() ?: return PsiReference.EMPTY_ARRAY

            // Offset of the value token within the attribute element, plus the
            // inner (unquoted) range within that token.
            val valueStartInAttr = valueNode.startOffset - attr.textRange.startOffset
            val raw = valueNode.text
            val inner = unquotedRange(raw) ?: return PsiReference.EMPTY_ARRAY

            var path = raw.substring(inner.startOffset, inner.endOffset)
            var startInElement = valueStartInAttr + inner.startOffset
            // A leading `/` denotes a root-relative path in Mako; our reference
            // contexts already ARE the roots, so drop it (keeping the offset in
            // sync) to avoid an empty leading path segment.
            if (path.startsWith("/")) {
                path = path.substring(1)
                startInElement += 1
            }

            val set = MakoFileReferenceSet(path, attr, startInElement)
            @Suppress("UNCHECKED_CAST")
            return set.allReferences as Array<PsiReference>
        }

        /**
         * Range of the path inside the quoted attribute text, excluding the
         * surrounding quotes. Null if the value is not (yet) quoted.
         */
        private fun unquotedRange(text: String): TextRange? {
            if (text.isEmpty()) return null
            val q = text[0]
            if (q != '"' && q != '\'') return null
            val end = if (text.length > 1 && text.last() == q) text.length - 1 else text.length
            return TextRange(1, end)
        }
    }
}

/**
 * A [FileReferenceSet] rooted at the Mako template folders. Completion and
 * resolution both walk these roots instead of the default (file-relative) ones.
 */
private class MakoFileReferenceSet(
    path: String,
    element: PsiElement,
    startInElement: Int,
) : FileReferenceSet(path, element, startInElement, null, true) {

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
        val roots = MakoTemplateRoots.rootDirsFor(element)
        // Fall back to the platform default (file's own directory) when no
        // template roots are configured, so completion still does something useful.
        return if (roots.isNotEmpty()) roots else super.computeDefaultContexts()
    }

    // Leading `/` in a Mako path means "from the template root", which is exactly
    // what our default contexts already are — so treat absolute-looking paths as
    // root-relative rather than filesystem-absolute.
    override fun isAbsolutePathReference(): Boolean = false
}
