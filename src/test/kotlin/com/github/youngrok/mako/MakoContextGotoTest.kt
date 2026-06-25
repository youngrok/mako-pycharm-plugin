package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.template.lang.core.templateLanguages.TemplateContextProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyReferenceExpression

/**
 * Reproduces, with a stub render-context provider (no Django needed), the case
 * where a context variable is used twice in a template — as a `% for` iterable
 * AND in `${...}` — and verifies BOTH `${...}` references resolve to the context
 * target. This pins down the go-to-def-only-works-once bug.
 */
class MakoContextGotoTest : BasePlatformTestCase() {

    /** Provider whose target is a real, navigable Python element in the project. */
    private inner class StubProvider(private val target: PsiElement) : TemplateContextProvider {
        override fun getTemplateContext(file: PsiFile) =
            listOf(LookupElementBuilder.create(target, "items"))
    }

    fun testContextVarResolvesEvenWhenAlsoUsedInLoop() {
        // A real Python target to resolve to.
        val py = myFixture.configureByText(
            "view.py",
            "items = []\n",
        )
        val target = PsiTreeUtil.findChildrenOfType(py, com.jetbrains.python.psi.PyTargetExpression::class.java)
            .first { it.name == "items" }
        ExtensionTestUtil.maskExtensions(
            TemplateContextProvider.EP_NAME, listOf(StubProvider(target)), testRootDisposable,
        )

        val tpl = myFixture.configureByText(
            "page.mako",
            "% for a in items:\n  ${'$'}{a}\n% endfor\n${'$'}{items}",
        )
        val ilm = InjectedLanguageManager.getInstance(project)
        val makoRoot = tpl.viewProvider.getPsi(MakoLanguage)!!

        val refs = LinkedHashSet<PyReferenceExpression>()
        val seenInjected = HashSet<PsiFile>()
        makoRoot.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                ilm.enumerate(element) { injected, _ ->
                    if (seenInjected.add(injected)) {
                        refs += PsiTreeUtil.findChildrenOfType(injected, PyReferenceExpression::class.java)
                            .filter { it.referencedName == "items" }
                    }
                }
                super.visitElement(element)
            }
        })
        val resolvedTargets = refs.map { ref ->
            (ref.reference as? PsiPolyVariantReference)?.multiResolve(false)?.firstOrNull()?.element
        }

        // Two `items` references (for-iterable + ${}). BOTH must resolve to
        // our context target — not one resolving and the other null.
        assertEquals("expected two items references, got ${refs.map { it.text }}", 2, refs.size)
        assertTrue(
            "every items reference must resolve to the context target; got $resolvedTargets",
            resolvedTargets.all { it != null && it === target },
        )

        // And Go-to-Declaration must reach the target from each ${} occurrence,
        // bypassing the Python evaluator's faulty extra hop.
        val handler = com.github.youngrok.mako.inject.MakoContextGotoHandler()
        for (ref in refs) {
            val leaf = ref.firstChild ?: ref
            val targets = handler.getGotoDeclarationTargets(leaf, 0, null)
            assertNotNull("goto handler returned null for '${ref.text}'", targets)
            assertTrue(
                "goto must land on the context target for '${ref.text}'; got ${targets?.toList()}",
                targets!!.any { it === target },
            )
        }
    }

    /**
     * The decisive regression: Go-to-Declaration hands the handler the HOST Mako
     * leaf (not the injected Python ref). The handler must descend into the
     * injection and still resolve the context target. This is the path that was
     * silently broken (handler only matched injected elements).
     */
    fun testGotoFromHostLeafDescendsIntoInjection() {
        val py = myFixture.configureByText("view.py", "items = []\n")
        val target = PsiTreeUtil.findChildrenOfType(py, com.jetbrains.python.psi.PyTargetExpression::class.java)
            .first { it.name == "items" }
        ExtensionTestUtil.maskExtensions(
            TemplateContextProvider.EP_NAME, listOf(StubProvider(target)), testRootDisposable,
        )

        val src = "<p>${'$'}{items}</p>"
        val tpl = myFixture.configureByText("page.mako", src)
        // Host offset inside `items`.
        val hostOffset = src.indexOf("items") + 3
        val hostLeaf = tpl.findElementAt(hostOffset)
        assertNotNull("host leaf at offset", hostLeaf)

        val handler = com.github.youngrok.mako.inject.MakoContextGotoHandler()
        val targets = handler.getGotoDeclarationTargets(hostLeaf, hostOffset, null)
        assertNotNull("goto from host leaf returned null", targets)
        assertTrue(
            "goto from host leaf must reach the context target; got ${targets?.toList()}",
            targets!!.any { it === target },
        )
    }

    /**
     * The real-IDE failure: Go-to-Declaration hands the handler the **HTML data
     * tree** leaf (an XmlToken — `${client}` is opaque there), not the Mako tree
     * element. The handler must still locate the Mako injection at that offset.
     * This reproduces the `src=XmlTokenImpl lang=HTML -> NO REF` case seen in logs.
     */
    fun testGotoFromHtmlDataTreeLeaf() {
        val py = myFixture.configureByText("view.py", "items = []\n")
        val target = PsiTreeUtil.findChildrenOfType(py, com.jetbrains.python.psi.PyTargetExpression::class.java)
            .first { it.name == "items" }
        ExtensionTestUtil.maskExtensions(
            TemplateContextProvider.EP_NAME, listOf(StubProvider(target)), testRootDisposable,
        )

        val src = "<p>${'$'}{items}</p>"
        val tpl = myFixture.configureByText("page.mako", src)
        val offset = src.indexOf("items") + 2

        // Explicitly fetch the leaf from the HTML data tree (not Mako), as the
        // platform does for a caret inside the data region.
        val htmlRoot = tpl.viewProvider.getPsi(com.intellij.lang.html.HTMLLanguage.INSTANCE)
        assertNotNull("expected an HTML data root", htmlRoot)
        val htmlLeaf = htmlRoot!!.findElementAt(offset)
        assertNotNull("expected an HTML-tree leaf at the caret", htmlLeaf)
        // Sanity: this leaf is indeed an HTML/XML token, not Mako.
        assertEquals("HTML", htmlLeaf!!.containingFile.language.id)

        val handler = com.github.youngrok.mako.inject.MakoContextGotoHandler()
        val targets = handler.getGotoDeclarationTargets(htmlLeaf, offset, null)
        assertNotNull("goto from HTML-tree leaf returned null", targets)
        assertTrue(
            "goto from HTML-tree leaf must still reach the context target; got ${targets?.toList()}",
            targets!!.any { it === target },
        )
    }
}
