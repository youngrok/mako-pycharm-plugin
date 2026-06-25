package com.github.youngrok.mako.inject

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyReferenceExpression

/**
 * Makes Go-to-Declaration (Ctrl/Cmd+B) on a render-context variable inside a Mako
 * `${...}` / `% ...` region jump straight to the view's variable definition.
 *
 * Why this is needed: Go-to-Declaration resolves a context variable through
 * `PyTargetElementEvaluator.getElementByReference`, which, for a reference that
 * resolves to a `PyTargetExpression`, performs an extra "follow-through" resolve
 * hop. For a context variable that appears more than once in the view (almost all
 * of them — `locals()` exposes every local, and most are referenced again), that
 * hop resolves ambiguously and navigation silently fails; only a variable that
 * appears exactly once happens to navigate. Go-to-Declaration consults
 * `GotoDeclarationHandler`s *before* that evaluator, so returning the target here
 * bypasses the faulty hop for every context variable.
 *
 * Go-to-Declaration hands us the element from the **host** PSI tree (the Mako leaf
 * at the caret), not the injected Python reference, so we descend into the
 * injected fragment at the caret offset via [InjectedLanguageManager.findInjectedElementAt].
 */
class MakoContextGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val ilm = InjectedLanguageManager.getInstance(element.project)

        val ref = findPythonReference(element, offset, ilm) ?: return null
        if (ref.isQualified) return null
        val name = ref.referencedName ?: return null

        val rawHost = ilm.getTopLevelFile(ref)
            ?.takeIf { it.viewProvider.baseLanguage == MakoLanguage }
            ?: return null
        // Use the physical original file: the bundled Django provider asserts on a
        // non-null VirtualFile, which a non-physical copy lacks.
        val hostFile = rawHost.originalFile.takeIf { it.virtualFile != null } ?: rawHost

        val target = com.intellij.openapi.util.RecursionManager.doPreventingRecursion(
            GotoKey(hostFile, name), false,
        ) {
            TemplateContextProviderBridge.contextFor(hostFile)
                .firstOrNull { it.lookupString == name }
                ?.psiElement
        } ?: return null

        return arrayOf(target)
    }

    /**
     * Resolve the injected Python reference at the caret. The caret element may
     * come from ANY root of the multi-root Mako file (commonly the HTML data tree,
     * where `${...}` is just an XmlToken). We therefore locate the file's **Mako**
     * root via the view provider and look for the injected Python there at the
     * same document offset.
     */
    private fun findPythonReference(
        element: PsiElement,
        offset: Int,
        ilm: InjectedLanguageManager,
    ): PyReferenceExpression? {
        // Already inside injected Python?
        PsiTreeUtil.getParentOfType(element, PyReferenceExpression::class.java, false)
            ?.let { return it }

        val vp = element.containingFile?.viewProvider ?: return null
        // The Mako root of this (possibly multi-root) file. `findInjectedElementAt`
        // needs a host PSI file; the Mako root is the one carrying the injections.
        val makoRoot = vp.getPsi(MakoLanguage) ?: return null

        // `findInjectedElementAt(hostFile, offset)` returns the injected element at
        // the document offset regardless of which Mako host owns the (chained)
        // injection — exactly what we need when the caret came from the HTML tree.
        val injectedLeaf = ilm.findInjectedElementAt(makoRoot, offset)
        return PsiTreeUtil.getParentOfType(injectedLeaf, PyReferenceExpression::class.java, false)
    }

    private data class GotoKey(val file: PsiFile, val name: String)
}
