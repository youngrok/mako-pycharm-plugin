package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.psi.MakoInjectionHost
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reproduces a common template head: Jinja `{% %}` lines with multibyte
 * (Korean) text, then several consecutive `${var}` lines. The real IDE showed the
 * 4th `${...}` parsing as XML_DATA_CHARACTERS (not a Mako expression) and carrying
 * no Python injection. This test pins down whether every consecutive `${}` becomes
 * an injection host with Python injected.
 */
class MakoConsecutiveExprTest : BasePlatformTestCase() {

    fun testForLoopVarVisibleAndConsecutiveInjected() {
        // accounts.html shape: consecutive ${} then a % for ... ${bf} block.
        val file = myFixture.configureByText(
            "p.mako",
            "${'$'}{forms}\n${'$'}{request}\n% for bf in forms:\n  ${'$'}{bf}\n% endfor",
        )
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val ilm = InjectedLanguageManager.getInstance(project)
        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .filter { it.isValidHost }

        // (1) every ${} / control host injects Python (discoverable per-host).
        val perHost = hosts.map { h ->
            h.text.trim() to (ilm.getInjectedPsiFiles(h)?.firstOrNull()?.first?.language?.id)
        }
        assertTrue("all hosts must inject Python; got $perHost",
            perHost.all { it.second == "Python" })

        // (2) the injected fragment for ${bf} must see `bf` as a resolvable name
        // (i.e. the chained doc contains the `for bf in forms` binding).
        val bfHost = hosts.first { it.text.trim() == "\${bf}" || it.text.contains("bf") && it.text.startsWith("\${") }
        val injectedForBf = ilm.getInjectedPsiFiles(bfHost)?.firstOrNull()?.first
        assertNotNull("expected injected file for \${bf}", injectedForBf)
        val docText = injectedForBf!!.text
        assertTrue(
            "chained injection for \${bf} must contain the loop binding; got: $docText",
            docText.contains("for bf in forms"),
        )
    }

    fun testTwoBareExpressions() {
        val file = myFixture.configureByText("p.mako", "${'$'}{aaa}\n${'$'}{bbb}")
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .filter { it.isValidHost }
        val ilm = InjectedLanguageManager.getInstance(project)
        val perHost = hosts.map { h ->
            h.text.trim() to (ilm.getInjectedPsiFiles(h)?.map { it.first.language.id } ?: emptyList())
        }
        assertTrue("both ${'$'}{} must inject Python; got $perHost",
            perHost.all { it.second.contains("Python") })
    }

    fun testConsecutiveExpressionsAllInjected() {
        val text = """
            {% extends 'agent/layout.html' %}
            {% with page_title='수집계정 관리', static_company=client %}{% endwith %}

            ${'$'}{forms}
            ${'$'}{request}
            ${'$'}{client}
            ${'$'}{form}

            <div>x</div>
        """.trimIndent()
        val file = myFixture.configureByText("page.mako", text)
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!

        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .filter { it.isValidHost }
        val hostTexts = hosts.map { it.text }

        // Every one of the four ${...} must be a host with Python injected.
        val wanted = listOf("forms", "request", "client", "form")
        for (w in wanted) {
            assertTrue(
                "\${$w} must be an injection host; hosts=$hostTexts",
                hosts.any { it.text.contains(w) },
            )
        }
    }
}
