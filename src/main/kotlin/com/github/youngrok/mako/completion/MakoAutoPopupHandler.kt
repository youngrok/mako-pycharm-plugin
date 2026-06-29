package com.github.youngrok.mako.completion

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Pops up completion automatically as the user types — the way HTML/XML editing
 * does — so the user does not have to press Ctrl+Space.
 *
 *  - typing the opening quote of `file="` (and inside the path) → offer files
 *  - typing an attribute-name letter inside an open `<%tag …` → offer attributes
 *
 * Tag-name auto-popup on `<%` is handled by
 * [MakoTagCompletionContributor.invokeAutoPopup] instead — that path correctly
 * restarts the session when the HTML popup from the preceding `<` is dismissed.
 *
 * The actual completion variants come from
 * [MakoAttributeCompletionContributor] and
 * [com.github.youngrok.mako.reference.MakoFileReferenceContributor]; this handler
 * only schedules the popup, reusing the platform's [AutoPopupController].
 */
class MakoAutoPopupHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (file.viewProvider.baseLanguage != MakoLanguage) return Result.CONTINUE

        // Entered/extending an attribute value or a path segment → file refs.
        if (charTyped == '"' || charTyped == '\'' || charTyped == '/') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            return Result.STOP
        }
        // Attribute-name letter inside an open Mako tag → attribute completion.
        // (Plain letters don't trigger contributor.invokeAutoPopup, so we schedule
        // it here; the contributor still decides whether to actually offer items.)
        if (charTyped.isLetter()) {
            val caret = editor.caretModel.offset
            if (insideOpenMakoTag(editor.document.charsSequence, caret)) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                return Result.STOP
            }
        }
        return Result.CONTINUE
    }

    /**
     * Cheap text-only check: is [caret] inside an open `<%tag …` (after the tag
     * name, before the tag is closed by `>` / `/>` / `%>`)? A precise decision is
     * left to [MakoAttributeCompletionContributor]; this just gates the popup.
     */
    private fun insideOpenMakoTag(text: CharSequence, caret: Int): Boolean {
        var i = caret - 1
        while (i >= 1) {
            val c = text[i]
            // `>` closes a tag/element (covers `>`, `/>` and the `>` of `%>`).
            if (c == '>') return false
            if (c == '<') return false                       // a new `<...` started
            if (c == '%' && text[i - 1] == '<') return true  // `<%` opener found
            i--
        }
        return false
    }
}
