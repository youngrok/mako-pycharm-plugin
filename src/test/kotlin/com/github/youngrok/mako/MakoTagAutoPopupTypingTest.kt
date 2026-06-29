package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.template.lang.core.templateLanguages.TemplatesService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester

/**
 * Reproduces the REAL user flow: typing `<` then `%` in an `.html` Mako template
 * with auto-popup enabled, and inspecting the live lookup. This is what surfaces
 * the "HTML tags appear instead of Mako tags" bug that the static
 * `completeBasic()` tests miss.
 */
class MakoTagAutoPopupTypingTest : BasePlatformTestCase() {

    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    // Auto-popup machinery must run off the EDT.
    override fun runInDispatchThread() = false

    private fun setUpRoot() {
        val anchor = myFixture.addFileToProject("templates/.anchor", "")
        val module = ModuleUtilCore.findModuleForFile(anchor.virtualFile, project)!!
        ApplicationManager.getApplication().invokeAndWait {
            val service = TemplatesService.getInstance(module)!!
            service.setTemplateLanguage(MakoLanguage)
            service.setTemplateFolders(anchor.virtualFile.parent)
        }
    }

    fun testTypingPercentShowsMakoTags() {
        setUpRoot()
        val vf = myFixture.addFileToProject("templates/page.html", "").virtualFile
        myFixture.configureFromExistingVirtualFile(vf)

        tester.runWithAutoPopupEnabled {
            // Type `<` (HTML popup appears) then `%` — forming `<%`, which must
            // switch the popup to Mako tag names only.
            myFixture.type('<')
            tester.joinAutopopup(); tester.joinCompletion()
            myFixture.type('%')
            tester.joinAutopopup(); tester.joinCompletion()

            val strings = tester.lookup?.items?.map { it.lookupString } ?: emptyList()
            assertTrue("Mako tags expected after <%, got $strings",
                strings.containsAll(listOf("def", "include", "inherit")))
            assertFalse("HTML tag 'div' must not appear after <%, got $strings",
                strings.contains("div"))
        }
    }
}
