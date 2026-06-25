package com.github.youngrok.mako.inject

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Resolves bare names inside Mako code regions (`${ user }`, `<% ... %>`) to the
 * render-context variables — so `${user}` is not flagged "unresolved" and
 * Ctrl/Cmd-click navigates to the `render(..., {'user': u})` site.
 *
 * Like [MakoRenderContextCompletion], it reuses PyCharm's existing
 * [com.intellij.template.lang.core.templateLanguages.TemplateContextProvider]
 * engine; it does not compute context itself. The context elements
 * ([com.intellij.codeInsight.lookup.LookupElement]) already carry the Python
 * `PsiElement` of the source variable, which we hand back as the resolve target.
 */
class MakoContextResolveProvider : PyReferenceResolveProvider {

    override fun resolveName(
        element: PyQualifiedExpression,
        context: TypeEvalContext,
    ): List<RatedResolveResult> {
        // Only qualify bare names (not attribute access like a.b).
        if (element.isQualified) return emptyList()
        val name = element.referencedName ?: return emptyList()

        // Only act when this Python lives inside a Mako-hosted template.
        val hostFile = makoHostFile(element) ?: return emptyList()

        // Computing the render context type-evaluates the view's render() call,
        // which can re-enter resolution. Guard against that — but key the guard on
        // (file, name) so resolving the SAME context var at two different places in
        // one template (e.g. `% for x in items:` and `${x}`)
        // is NOT mistaken for re-entry. Keying on the file alone made the second
        // occurrence return null, which silently broke go-to-def for any context
        // variable used more than once in a template.
        val guardKey = RecursionKey(hostFile, name)
        val match = com.intellij.openapi.util.RecursionManager.doPreventingRecursion(
            guardKey, false,
        ) {
            TemplateContextProviderBridge.contextFor(hostFile)
                .firstOrNull { it.lookupString == name }
        } ?: return emptyList()

        val target = match.psiElement ?: return emptyList()
        return listOf(RatedResolveResult(RatedResolveResult.RATE_NORMAL, target))
    }

    private data class RecursionKey(val file: com.intellij.psi.PsiFile, val name: String)

    private fun makoHostFile(element: PyQualifiedExpression): com.intellij.psi.PsiFile? {
        val raw = InjectedLanguageManager.getInstance(element.project)
            .getInjectionHost(element)
            ?.containingFile
            ?.takeIf { it.viewProvider.baseLanguage == MakoLanguage }
            ?: return null
        // During completion/resolve the host file is often a non-physical copy whose
        // getVirtualFile() is null. The bundled Django provider asserts on a non-null
        // VirtualFile (DjangoTemplateManager.selectLongest), so hand it the original,
        // physical file.
        return raw.originalFile.takeIf { it.virtualFile != null } ?: raw
    }
}
