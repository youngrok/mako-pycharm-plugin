package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguageSubstitutor
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.LanguageSubstitutors
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The whole fix hinges on our substitutor running BEFORE the web-framework one.
 * `LanguageSubstitutors.substituteLanguage` iterates substitutors in registration
 * order and takes the first non-null result, so our substitutor must appear in the
 * ordered list for HTML and be positioned ahead of any web-framework substitutor.
 *
 * This proves the precedence at runtime without a GUI.
 */
class MakoSubstitutorOrderTest : BasePlatformTestCase() {

    fun testMakoSubstitutorRunsBeforeWebFrameworkForHtml() {
        val all = LanguageSubstitutors.getInstance().allForLanguage(HTMLLanguage.INSTANCE)
        val names = all.map { it.javaClass.name }

        val makoIdx = all.indexOfFirst { it is MakoLanguageSubstitutor }
        assertTrue("our MakoLanguageSubstitutor must be registered for HTML; got $names", makoIdx >= 0)

        val webIdx = names.indexOfFirst { it.contains("WebFramework", ignoreCase = true) }
        if (webIdx >= 0) {
            assertTrue(
                "MakoLanguageSubstitutor (idx=$makoIdx) must run BEFORE the web-framework " +
                    "substitutor (idx=$webIdx); order=$names",
                makoIdx < webIdx,
            )
        }
        // Also ensure the bundled Python template substitutor is present (sanity).
        assertTrue(
            "expected the Python template substitutor in the chain too; got $names",
            names.any { it.contains("PyTemplateLanguageSubstitutor") },
        )
    }
}
