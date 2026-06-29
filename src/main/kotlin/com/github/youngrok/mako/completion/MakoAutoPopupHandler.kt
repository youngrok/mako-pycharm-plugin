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
 *
 * Tag-name auto-popup on `<%` is handled by
 * [MakoTagCompletionContributor.invokeAutoPopup] instead — that path correctly
 * restarts the session when the HTML popup from the preceding `<` is dismissed.
 *
 * The actual completion variants come from
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
        return Result.CONTINUE
    }
}
