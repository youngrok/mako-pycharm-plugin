package com.github.youngrok.mako.highlight

import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.templateLanguages.TemplateDataHighlighterWrapper
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings

/**
 * Editor highlighter that paints both layers of a Mako file:
 *  - the **Mako** tokens (`${}`, `<% %>`, tags, …) via [MakoSyntaxHighlighter],
 *    and
 *  - the **data language** (HTML by default, or whatever the file is mapped to)
 *    inside the [MakoTypes.OUTER] regions, by registering the data language's own
 *    highlighter as a layer on that token.
 *
 * Without this, OUTER regions render as plain text — this is what makes HTML / JS
 * / CSS highlighting appear between the Mako constructs, like the old bundled
 * support did.
 */
class MakoEditorHighlighter(
    scheme: EditorColorsScheme,
    project: Project?,
    virtualFile: VirtualFile?,
) : LayeredLexerEditorHighlighter(
    SyntaxHighlighterFactory.getSyntaxHighlighter(MakoLanguage, project, virtualFile),
    scheme,
) {
    init {
        val dataLanguage = resolveDataLanguage(project, virtualFile)
        val dataHighlighter = SyntaxHighlighterFactory
            .getSyntaxHighlighter(dataLanguage, project, virtualFile)
        if (dataHighlighter != null) {
            val layer = LayerDescriptor(
                TemplateDataHighlighterWrapper(dataHighlighter),
                "",
            )
            // OUTER carries the data-language text; the data highlighter colours it.
            registerLayer(MakoTypes.OUTER, layer)
            registerLayer(MakoTypes.TEXT_BODY, layer)
        }
    }

    private companion object {
        fun resolveDataLanguage(project: Project?, file: VirtualFile?): Language {
            if (project != null && file != null) {
                TemplateDataLanguageMappings.getInstance(project)?.getMapping(file)
                    ?.let { return it }
            }
            return HTMLLanguage.INSTANCE
        }
    }
}

class MakoEditorHighlighterProvider : com.intellij.openapi.fileTypes.EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: com.intellij.openapi.fileTypes.FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): com.intellij.openapi.editor.highlighter.EditorHighlighter =
        MakoEditorHighlighter(colors, project, virtualFile)
}
