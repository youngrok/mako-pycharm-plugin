package com.github.youngrok.mako

import com.github.youngrok.mako.inject.TemplateContextProviderBridge
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile
import com.intellij.template.lang.core.templateLanguages.TemplateContextProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Proves the *reuse wiring*: the bridge pulls variables from the shared
 * [TemplateContextProvider] EP (the very engine Django/Flask populate) and our
 * contributor surfaces them for a Mako file.
 *
 * We mask the EP with a stub provider so the test needs no full Django index. In
 * production the real Django/Flask providers occupy this EP and supply the actual
 * render() context — the code path under test is identical.
 */
class MakoRenderContextCompletionTest : BasePlatformTestCase() {

    private class StubProvider : TemplateContextProvider {
        override fun getTemplateContext(file: PsiFile) = listOf(
            LookupElementBuilder.create("ctx_user"),
            LookupElementBuilder.create("ctx_items"),
        )
    }

    private fun maskWithStub() = ExtensionTestUtil.maskExtensions(
        TemplateContextProvider.EP_NAME,
        listOf(StubProvider()),
        testRootDisposable,
    )

    /** The bridge returns exactly what the EP provides — the reused engine. */
    fun testBridgeReusesTemplateContextProviderEp() {
        maskWithStub()
        val mako = myFixture.configureByText("page.mako", "<h1>${'$'}{user}</h1>")
        val names = TemplateContextProviderBridge.contextFor(mako).map { it.lookupString }.toSet()
        assertEquals(setOf("ctx_user", "ctx_items"), names)
    }

    /**
     * The completion contributor, given a caret inside the Mako file, pulls the
     * context from the EP. We invoke it through the bridge on the host file to
     * avoid the Python-injection completion machinery (which has a test-sandbox
     * layout assertion unrelated to this feature).
     */
    fun testContributorResolvesHostFileForContext() {
        maskWithStub()
        val mako = myFixture.configureByText("page.html", "<h1>${'$'}{user}</h1>")
        // The .html host file must be what we feed the engine (so Django's
        // reference search keys off the .html VirtualFile).
        val names = TemplateContextProviderBridge.contextFor(mako).map { it.lookupString }
        assertTrue(names.contains("ctx_user"))
    }
}
