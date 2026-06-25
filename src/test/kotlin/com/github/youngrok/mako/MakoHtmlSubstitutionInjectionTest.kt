package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.psi.MakoInjectionHost
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.template.lang.core.templateLanguages.TemplatesService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end check of the substitution path: a `.html` file under a Mako template
 * folder must (1) be parsed as Mako via the substitutor, and (2) have real Python
 * injected into its `${...}` regions. This mirrors a real Django project setup
 * (`.html` templates, Mako selected) rather than a literal `.mako` file.
 */
class MakoHtmlSubstitutionInjectionTest : BasePlatformTestCase() {

    private fun configureMakoHtml(text: String): com.intellij.psi.PsiFile {
        val file = myFixture.addFileToProject("templates/page.html", text)
        val module = ModuleUtilCore.findModuleForFile(file.virtualFile, project)!!
        val service = TemplatesService.getInstance(module)!!
        service.setTemplateLanguage(MakoLanguage)
        service.setTemplateFolders(file.virtualFile.parent)
        // Re-fetch the PSI now that the substitutor applies.
        return myFixture.configureFromExistingVirtualFile(file.virtualFile).let {
            myFixture.file
        }
    }

    fun testHtmlIsParsedAsMako() {
        val file = configureMakoHtml("<h1>${'$'}{user}</h1>")
        assertTrue(
            "the .html view provider should expose Mako; got ${file.viewProvider.languages.map { it.id }}",
            file.viewProvider.languages.contains(MakoLanguage),
        )
    }

    fun testConsecutiveExpressionsInSubstitutedHtml() {
        // typical shape: Jinja {% %} head + consecutive ${} lines, in a .html
        // mapped to Mako.
        val file = configureMakoHtml(
            "{% block main %}\n${'$'}{forms}\n${'$'}{request}\n${'$'}{client}\n${'$'}{form}\n",
        )
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val ilm = InjectedLanguageManager.getInstance(project)
        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .filter { it.isValidHost }
        val perHost = hosts.map { h ->
            h.text.trim() to (ilm.getInjectedPsiFiles(h)?.firstOrNull()?.first?.language?.id)
        }
        val wanted = listOf("forms", "request", "client", "form")
        for (w in wanted) {
            val h = hosts.firstOrNull { it.text.contains(w) }
            assertNotNull("\${$w} must be a host in substituted .html; got ${perHost}", h)
            val lang = ilm.getInjectedPsiFiles(h!!)?.firstOrNull()?.first?.language?.id
            assertEquals("\${$w} must inject Python in substituted .html", "Python", lang)
        }
    }

    fun testPythonInjectedIntoExpressionInHtml() {
        val file = configureMakoHtml("<h1>${'$'}{user.name}</h1>")
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val host = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .firstOrNull { it.isValidHost }
        assertNotNull("expected a Mako injection host in the .html file", host)
        val injected = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host!!)
        assertNotNull("expected injected Python inside \${...} of the .html file", injected)
        assertFalse("injected list should not be empty", injected!!.isEmpty())
        assertEquals("Python", injected.first().first.language.id)
    }

    /**
     * Stronger: the injected fragment must be *real Python* — `user.name` must
     * parse as a Python reference/attribute expression, not opaque text. This is
     * what "${} is handled as Python code" actually means.
     */
    fun testInjectedFragmentIsRealPythonPsi() {
        val file = configureMakoHtml("<h1>${'$'}{user.name}</h1>")
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val host = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .first { it.isValidHost }
        val injectedFile = InjectedLanguageManager.getInstance(project)
            .getInjectedPsiFiles(host)!!.first().first

        // The injected Python must contain a reference expression `user` and an
        // attribute access `user.name`.
        val refs = PsiTreeUtil.findChildrenOfType(
            injectedFile, com.jetbrains.python.psi.PyReferenceExpression::class.java,
        )
        assertTrue(
            "expected Python reference expressions (user / user.name); got ${refs.map { it.text }}",
            refs.any { it.text == "user" } && refs.any { it.referencedName == "name" },
        )
    }

    /** `% for` control lines in a substituted .html also inject Python. */
    fun testControlLineInjectedInHtml() {
        val file = configureMakoHtml("% for item in items:\n  ${'$'}{item}\n% endfor")
        val makoRoot = file.viewProvider.getPsi(MakoLanguage)!!
        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot, MakoInjectionHost::class.java)
            .filter { it.isValidHost }
        val ilm = InjectedLanguageManager.getInstance(project)
        val anyPython = hosts.any { h ->
            ilm.getInjectedPsiFiles(h)?.any { it.first.language.id == "Python" } == true
        }
        assertTrue("control line / expression in .html must inject Python", anyPython)
    }
}
