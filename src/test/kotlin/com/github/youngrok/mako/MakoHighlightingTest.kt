package com.github.youngrok.mako

import com.github.youngrok.mako.filetype.MakoFileType
import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.psi.MakoFile
import com.github.youngrok.mako.psi.MakoInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration tests that run on the real IntelliJ Platform: they confirm the
 * file type is recognised, the parser builds a [MakoFile] PSI tree without
 * errors, and the Python injection hosts are produced for code regions.
 */
class MakoHighlightingTest : BasePlatformTestCase() {

    fun testFileTypeIsRecognised() {
        val file = myFixture.configureByText("page.mako", "<h1>${'$'}{title}</h1>")
        assertInstanceOf(file, MakoFile::class.java)
        assertEquals(MakoFileType, file.fileType)
        assertEquals(MakoLanguage, file.language)
    }

    fun testMakoAppearsInPythonTemplateLanguages() {
        // The Python Template Languages dropdown is populated by this call.
        val all = com.intellij.template.lang.core.templateLanguages.PythonTemplateLanguage
            .getAllTemplateLanguages()
        assertTrue(
            "Mako must be offered in the Python Template Languages dropdown; got " +
                all.map { it.templateLanguageName },
            all.any { it.templateLanguageName == "Mako" },
        )
    }

    fun testParsesWithoutErrors() {
        val file = myFixture.configureByText(
            "page.mako",
            """
            ## comment
            <%inherit file="base.mako"/>
            <%! import os %>
            <%def name="greet(name)">
                <p>${'$'}{name | h}</p>
            </%def>
            % if items:
                % for x in items:
                    <li>${'$'}{x}</li>
                % endfor
            % endif
            <% y = {'a': 1} %>
            """.trimIndent(),
        )
        // No PsiErrorElement anywhere in the Mako tree.
        val errors = PsiTreeUtil.findChildrenOfType(
            makoRoot(file),
            com.intellij.psi.PsiErrorElement::class.java,
        )
        assertEmpty(errors)
    }

    fun testInjectionHostsExistForCodeRegions() {
        val file = myFixture.configureByText(
            "page.mako",
            "${'$'}{a + b}\n<% c = 1 %>\n% if d:\nx\n% endif",
        )
        val hosts = PsiTreeUtil.findChildrenOfType(makoRoot(file), MakoInjectionHost::class.java)
        // ${...}, <% ... %>, `% if d:` and `% endif` are all wrapped as hosts.
        assertTrue("expected at least 4 host elements, got ${hosts.size}", hosts.size >= 4)
        // Hosts that carry Python code are valid injection hosts; the `% endif`
        // terminator carries no code and is correctly not a valid host.
        val validHosts = hosts.filter { it.isValidHost }
        assertTrue("expected >=3 hosts with code, got ${validHosts.size}", validHosts.size >= 3)
        assertTrue(
            "the `% endif` terminator should not be a valid injection host",
            hosts.any { !it.isValidHost && it.text.contains("endif") },
        )
    }

    /** The Mako (base-language) PSI root of the multi-root template file. */
    private fun makoRoot(file: com.intellij.psi.PsiFile) =
        file.viewProvider.getPsi(MakoLanguage)

    fun testDataTreeIsHtml() {
        val file = myFixture.configureByText("page.mako", "<h1>${'$'}{title}</h1>")
        val htmlRoot = file.viewProvider.getPsi(com.intellij.lang.html.HTMLLanguage.INSTANCE)
        assertNotNull("expected an HTML data tree", htmlRoot)
        assertEquals("HTML", htmlRoot.language.id)
        // The view provider must expose both Mako and HTML roots.
        assertTrue(file.viewProvider.languages.any { it.id == "HTML" })
        assertTrue(file.viewProvider.languages.contains(MakoLanguage))
        // The HTML must actually be parsed: an <h1> tag should be present.
        val tags = PsiTreeUtil.findChildrenOfType(htmlRoot, com.intellij.psi.xml.XmlTag::class.java)
        assertTrue("expected an <h1> tag in the HTML data tree", tags.any { it.name == "h1" })
    }

    fun testPythonIsInjectedIntoExpression() {
        val file = myFixture.configureByText("page.mako", "<h1>${'$'}{user.name}</h1>")
        val host = PsiTreeUtil.findChildrenOfType(makoRoot(file), MakoInjectionHost::class.java)
            .first { it.isValidHost }
        val injected = com.intellij.lang.injection.InjectedLanguageManager
            .getInstance(project)
            .getInjectedPsiFiles(host)
        assertNotNull("expected an injected file inside \${...}", injected)
        assertFalse("expected at least one injected fragment", injected!!.isEmpty())
        val injectedFile = injected.first().first
        assertEquals("Python", injectedFile.language.id)
    }

    fun testEditorHighlighterLayersHtml() {
        val file = myFixture.configureByText("page.mako", "<h1>${'$'}{title}</h1>")
        val highlighter = com.github.youngrok.mako.highlight.MakoEditorHighlighter(
            com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme,
            project,
            file.virtualFile,
        )
        highlighter.setText(file.viewProvider.document!!.immutableCharSequence)
        // Walk tokens; the <h1> region must be coloured by the HTML layer
        // (i.e. carry an HTML tag-name text-attributes key), not left as plain
        // Mako template text.
        val it = highlighter.createIterator(1) // inside "<h1>"
        val keys = it.textAttributesKeys.map { k -> k.externalName }
        assertTrue(
            "expected an HTML highlighting layer over the <h1> region, got $keys",
            keys.any { name -> name.startsWith("HTML") || name.startsWith("XML") },
        )
    }

    fun testHighlightingLexerRunsOverComplexFile() {
        // configureByText + checkHighlighting exercises the real highlighter.
        myFixture.configureByText(
            "complex.mako",
            "<%page args=\"x\"/>\n<h1>${'$'}{x}</h1>\n% for i in x:\n${'$'}{i}\n% endfor\n",
        )
        // Should not throw; we don't assert specific infos here, just that the
        // highlighting pass completes on real PSI.
        myFixture.doHighlighting()
    }
}
