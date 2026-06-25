package com.github.youngrok.mako.inject

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiFile
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider

/**
 * Surfaces the render context (the variables passed to `render(request, 'x.html',
 * {...})`, `render_template(...)`, etc.) inside Mako code regions.
 *
 * It does NOT compute the context itself: it calls PyCharm's existing
 * [com.intellij.template.lang.core.templateLanguages.TemplateContextProvider]
 * extensions (Django's and Flask's), exactly the same engine Django/Jinja use,
 * and re-emits whatever variables they return as completions in the Mako file.
 *
 * Because Mako files are commonly authored as `.html` (mapped to Mako via the
 * Python Template Languages setting), Django recognises the file as one of its
 * templates and its provider returns the render context for it.
 */
class MakoRenderContextCompletion : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        // Only act inside a Mako file (the host file, or an injected fragment
        // whose host is Mako).
        val hostFile = hostMakoFile(parameters.originalFile) ?: return

        val context = TemplateContextProviderBridge.contextFor(hostFile)
        if (context.isEmpty()) return

        // Add the render-context variables; other contributors still run normally.
        for (element in context) {
            result.addElement(element)
        }
    }

    private fun hostMakoFile(file: PsiFile): PsiFile? {
        val vp = file.viewProvider
        if (vp.baseLanguage == MakoLanguage || vp is TemplateLanguageFileViewProvider &&
            vp.baseLanguage == MakoLanguage
        ) {
            // Return the data/base host file so Django's reference search (which
            // keys off the .html VirtualFile) resolves correctly.
            return vp.getPsi(vp.baseLanguage) ?: file
        }
        // Injected fragment: walk to the injection host's file.
        val injectionHostFile = com.intellij.lang.injection.InjectedLanguageManager
            .getInstance(file.project)
            .getInjectionHost(file)
            ?.containingFile
        if (injectionHostFile != null) {
            val hostVp = injectionHostFile.viewProvider
            if (hostVp.baseLanguage == MakoLanguage) return injectionHostFile
        }
        return null
    }
}

/**
 * Thin bridge to PyCharm's render-context engine. Isolated so the reflection-free
 * call site is in one place and easy to test.
 */
object TemplateContextProviderBridge {
    fun contextFor(templateFile: PsiFile): List<LookupElement> {
        val ep = com.intellij.template.lang.core.templateLanguages.TemplateContextProvider.EP_NAME
        return ep.extensionList.flatMap { provider ->
            try {
                provider.getTemplateContext(templateFile)?.toList() ?: emptyList()
            } catch (e: Throwable) {
                // Control-flow exceptions (ProcessCanceledException etc.) must always
                // propagate. Everything else — including AssertionError thrown by the
                // bundled Django provider (DjangoTemplateManager.selectLongest) for
                // projects it doesn't fully understand (non-standard template backend,
                // etc.) — must be swallowed: it must not surface as an error dialog nor
                // abort the surrounding Python resolve (which would break type
                // inference). Treat as "no context from this provider".
                if (e is com.intellij.openapi.diagnostic.ControlFlowException) throw e
                emptyList()
            }
        }
    }
}
