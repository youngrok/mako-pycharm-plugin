package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.lang.MakoLanguageSubstitutor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.template.lang.core.templateLanguages.TemplatesService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the substitutor forces `.html` under a Mako-configured template folder
 * to be parsed as Mako (the precedence fix for projects where a web-framework
 * substitutor otherwise keeps the file as HTML).
 */
class MakoLanguageSubstitutorTest : BasePlatformTestCase() {

    fun testHtmlUnderMakoTemplateFolderBecomesMako() {
        val html = myFixture.addFileToProject("templates/page.html", "<h1>${'$'}{x}</h1>")
        val vf = html.virtualFile
        val module = ModuleUtilCore.findModuleForFile(vf, project)!!

        val service = TemplatesService.getInstance(module)!!
        service.setTemplateLanguage(MakoLanguage)
        service.setTemplateFolders(vf.parent) // templates/

        val lang = MakoLanguageSubstitutor().getLanguage(vf, project)
        assertEquals("html under Mako template folder must substitute to Mako", MakoLanguage, lang)
    }

    fun testHtmlOutsideTemplateFolderUnaffected() {
        val html = myFixture.addFileToProject("static/other.html", "<h1>hi</h1>")
        val vf = html.virtualFile
        val module = ModuleUtilCore.findModuleForFile(vf, project)!!
        val service = TemplatesService.getInstance(module)!!
        service.setTemplateLanguage(MakoLanguage)
        // No template folders set → must NOT claim the file.
        service.setTemplateFolders()

        val lang = MakoLanguageSubstitutor().getLanguage(vf, project)
        assertNull("html outside any template folder must not be substituted", lang)
    }

    fun testNotClaimedWhenTemplateLanguageIsNotMako() {
        val html = myFixture.addFileToProject("templates/page.html", "<h1>hi</h1>")
        val vf = html.virtualFile
        val module = ModuleUtilCore.findModuleForFile(vf, project)!!
        val service = TemplatesService.getInstance(module)!!
        service.setTemplateFolders(vf.parent)
        // Explicitly select a non-Mako template language (Jinja2 is bundled).
        val jinja = com.intellij.template.lang.core.templateLanguages.PythonTemplateLanguage
            .getAllTemplateLanguages().firstOrNull { it !== MakoLanguage }
        if (jinja != null) {
            service.setTemplateLanguage(jinja)
            val lang = MakoLanguageSubstitutor().getLanguage(vf, project)
            assertNull("must not substitute when a non-Mako template language is selected", lang)
        }
    }
}
