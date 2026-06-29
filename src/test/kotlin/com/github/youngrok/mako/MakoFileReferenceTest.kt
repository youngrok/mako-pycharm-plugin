package com.github.youngrok.mako

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `file="..."` in <%include>/<%inherit> gets file references: they resolve to the
 * target template and offer it as a completion.
 *
 * No template root is configured in the test fixture, so resolution falls back to
 * the including file's own directory (see [MakoFileReferenceContributor]'s
 * default-context fallback) — which is enough to exercise the wiring.
 */
class MakoFileReferenceTest : BasePlatformTestCase() {

    fun testIncludeFileResolvesToTemplate() {
        myFixture.addFileToProject("base.mako", "base body")
        val file = myFixture.configureByText("page.mako", "<%include file=\"base.m<caret>ako\"/>")
        val ref = refAtCaret(file)
        assertNotNull("expected a file reference at the caret", ref)
        val resolved = ref!!.resolve()
        assertNotNull("file=\"base.mako\" should resolve", resolved)
        assertEquals("base.mako", (resolved as? PsiFile)?.name)
    }

    fun testInheritFileResolves() {
        myFixture.addFileToProject("layout.mako", "layout")
        val file = myFixture.configureByText("page.mako", "<%inherit file=\"layout.m<caret>ako\"/>")
        val resolved = refAtCaret(file)?.resolve()
        assertEquals("layout.mako", (resolved as? PsiFile)?.name)
    }

    fun testFileAttributeOffersCompletion() {
        myFixture.addFileToProject("base.mako", "base")
        myFixture.addFileToProject("other.mako", "other")
        myFixture.configureByText("page.mako", "<%include file=\"<caret>\"/>")
        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("base.mako should be suggested, got $names", names.contains("base.mako"))
        assertTrue("other.mako should be suggested, got $names", names.contains("other.mako"))
    }

    fun testNonFileAttributeHasNoFileReference() {
        val file = myFixture.configureByText("page.mako", "<%def name=\"f<caret>oo\"></%def>")
        // `name` is not a path attribute: no resolving file reference expected.
        val resolved = refAtCaret(file)?.resolve()
        assertNull(resolved)
    }

    private fun refAtCaret(file: PsiFile) =
        file.findReferenceAt(myFixture.caretOffset)
}
