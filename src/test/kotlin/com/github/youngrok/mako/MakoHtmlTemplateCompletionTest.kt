package com.github.youngrok.mako

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.intellij.template.lang.core.templateLanguages.TemplatesService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration tests for the REAL scenario the user hits: `.html` files configured
 * as Mako templates (via the Python Template Languages setting + a template root),
 * reaching Mako through [com.github.youngrok.mako.lang.MakoLanguageSubstitutor] —
 * not literal `.mako` files.
 *
 * These exercise:
 *  - tag-name completion after `<%` inside an `.html` Mako template
 *  - file="..." path completion + resolution against the configured template root
 */
class MakoHtmlTemplateCompletionTest : BasePlatformTestCase() {

    /** Configure module so `.html` under templates/ is treated as Mako. */
    private fun setUpMakoTemplateRoot(rootRelDir: String = "templates"): Unit {
        // A throwaway file just to locate the module + create the folder.
        val anchor = myFixture.addFileToProject("$rootRelDir/.anchor", "")
        val module = ModuleUtilCore.findModuleForFile(anchor.virtualFile, project)!!
        val service = TemplatesService.getInstance(module)!!
        service.setTemplateLanguage(MakoLanguage)
        service.setTemplateFolders(anchor.virtualFile.parent)
    }

    private fun baseLang(file: PsiFile) = file.viewProvider.baseLanguage

    /** Create an `.html` file under templates/ with [content] (may hold <caret>),
     *  open it in the fixture and return its (now-Mako) PsiFile. */
    private fun configureTemplate(relPath: String, content: String): PsiFile {
        val caret = content.indexOf("<caret>")
        val clean = content.replace("<caret>", "")
        val vf = myFixture.addFileToProject(relPath, clean).virtualFile
        myFixture.configureFromExistingVirtualFile(vf)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.file
    }

    // ---------------------------------------------------------------- tag completion

    fun testTagCompletionInHtmlTemplate() {
        setUpMakoTemplateRoot()
        val file = configureTemplate("templates/page.html", "<%<caret>")
        // Sanity: the .html file must actually be Mako now.
        assertEquals("file should be substituted to Mako", MakoLanguage, baseLang(file))

        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected def/include/inherit in $names", names.containsAll(listOf("def", "include", "inherit")))
    }

    fun testTagPrefixCompletionInHtmlTemplate() {
        setUpMakoTemplateRoot()
        val file = configureTemplate("templates/page.html", "<%inc<caret>")
        assertEquals(MakoLanguage, baseLang(file))
        myFixture.completeBasic()
        val text = myFixture.file.text
        assertTrue("include should auto-complete, got [$text]", text.startsWith("<%include"))
    }

    /**
     * Regression for the real-file case: `<%` on its own line (followed by a
     * newline) is lexed as a *Python block* opener, so the caret lands in the
     * block's code region. Tag completion must still fire there (and Python's
     * `request`/builtins must NOT be all that shows).
     */
    fun testTagCompletionWhenCaretInPyBlockRegion() {
        setUpMakoTemplateRoot()
        val file = configureTemplate(
            "templates/page.html",
            "<%inherit file=\"layout.html\"/>\n<%<caret>\n<div></div>",
        )
        assertEquals(MakoLanguage, baseLang(file))
        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("Mako tags must appear after a line-leading <%, got $names",
            names.containsAll(listOf("def", "include", "inherit", "block")))
    }

    /** `<%bl` mid-prefix on its own line must filter to `block`. */
    fun testTagPrefixCompletionInPyBlockRegion() {
        setUpMakoTemplateRoot()
        val file = configureTemplate("templates/page.html", "<div></div>\n<%bl<caret>\n")
        assertEquals(MakoLanguage, baseLang(file))
        myFixture.completeBasic()
        // `block` is the only tag matching `bl`, so it auto-completes to a full
        // `<%block ...></%block>` skeleton rather than staying in the popup.
        assertTrue(
            "`<%bl` should complete to a Mako <%block> tag, got [${myFixture.file.text}]",
            myFixture.file.text.contains("<%block"),
        )
    }

    // ------------------------------------------------------------- file references

    fun testFileCompletionAgainstTemplateRoot() {
        setUpMakoTemplateRoot()
        myFixture.addFileToProject("templates/shared/base.html", "base")
        myFixture.addFileToProject("templates/shared/header.html", "header")
        val file = configureTemplate("templates/page.html", "<%include file=\"shared/<caret>\"/>")
        assertEquals(MakoLanguage, baseLang(file))
        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("base.html should be suggested from template root, got $names", names.contains("base.html"))
    }

    fun testFileResolvesAgainstTemplateRootNotOwnDir() {
        setUpMakoTemplateRoot()
        // The included file lives under the template ROOT in a different folder,
        // NOT next to the including file. Mako resolves from the root.
        val base = myFixture.addFileToProject("templates/shared/base.html", "base")
        val file = configureTemplate("templates/sub/page.html", "<%inherit file=\"shared/ba<caret>se.html\"/>")
        assertEquals(MakoLanguage, baseLang(file))
        val ref = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("expected a file reference", ref)
        assertEquals("must resolve against template root", base, ref!!.resolve())
    }

    // --------------------------------------------------------------- auto-popup

    /**
     * Typing the `%` of `<%` (and the quote of `file="`) must request an
     * auto-popup so completion appears without Ctrl+Space, mirroring HTML editing.
     * We assert the handler's decision (Result.STOP = popup requested) for the
     * relevant characters.
     */
    fun testAutoPopupTriggers() {
        setUpMakoTemplateRoot()

        // Tag popup on `<%` is driven by the contributor's invokeAutoPopup: typing
        // `%` right after `<` requests the popup.
        val contributor = com.github.youngrok.mako.completion.MakoTagCompletionContributor()
        val tagFile = configureTemplate("templates/a.html", "<<caret>")
        val leaf = tagFile.viewProvider.findElementAt(0, MakoLanguage)
            ?: tagFile.findElementAt(0)!!
        assertTrue(
            "typing % after < must request tag auto-popup",
            contributor.invokeAutoPopup(leaf, '%'),
        )

        // `file="` popup is driven by the typed handler: the opening quote requests it.
        val handler = com.github.youngrok.mako.completion.MakoAutoPopupHandler()
        val attrFile = configureTemplate("templates/b.html", "<%include file=\"<caret>")
        val r2 = handler.checkAutoPopup('"', project, myFixture.editor, attrFile)
        assertEquals("typing the opening quote requests popup", TypedHandlerDelegate.Result.STOP, r2)
    }

    fun testRootRelativeFileResolves() {
        setUpMakoTemplateRoot()
        val base = myFixture.addFileToProject("templates/layout.html", "layout")
        val file = configureTemplate("templates/sub/page.html", "<%inherit file=\"/lay<caret>out.html\"/>")
        val ref = file.findReferenceAt(myFixture.caretOffset)
        assertEquals("leading-slash path resolves from root", base, ref?.resolve())
    }
}
