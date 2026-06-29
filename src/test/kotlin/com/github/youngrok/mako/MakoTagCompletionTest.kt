package com.github.youngrok.mako

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tag-name completion after `<%`: the contributor must offer the built-in Mako
 * tags and insert a sensible skeleton for the chosen one.
 */
class MakoTagCompletionTest : BasePlatformTestCase() {

    fun testOffersTagNamesAfterOpener() {
        myFixture.configureByText("page.mako", "<%<caret>")
        val items = myFixture.completeBasic()
        val names = items.map { it.lookupString }.toSet()
        assertTrue("expected def/include/inherit among $names", names.containsAll(listOf("def", "include", "inherit")))
    }

    fun testPrefixFiltersTagNames() {
        myFixture.configureByText("page.mako", "<%in<caret>")
        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("include should match prefix 'in'", names.contains("include"))
        assertTrue("inherit should match prefix 'in'", names.contains("inherit"))
        assertFalse("def should be filtered out by prefix 'in'", names.contains("def"))
    }

    fun testContainerTagInsertsClosingTag() {
        myFixture.configureByText("page.mako", "<%de<caret>")
        // Only one completion ("def") matches "de", so it is auto-inserted.
        myFixture.completeBasic()
        myFixture.checkResult("<%def name=\"<caret>\"></%def>")
    }

    fun testSelfClosingTagInsertsSlashGt() {
        myFixture.configureByText("page.mako", "<%incl<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("<%include file=\"<caret>\"/>")
    }
}
