package com.github.youngrok.mako.lang

import com.github.youngrok.mako.parser.MakoTemplateDataElementTypes
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.psi.tree.IElementType

/**
 * Splits a Mako file into two coordinated PSI trees:
 *  - the **template** tree (Mako constructs), and
 *  - the **data** tree (HTML by default, or whatever the user configured for
 *    this file via Settings | Languages & Frameworks | Template Data Languages).
 *
 * This is what makes HTML highlighting, completion and tag-closing work between
 * the Mako tags — matching the behaviour of the old bundled Mako support.
 */
class MakoFileViewProvider(
    manager: PsiManager,
    virtualFile: VirtualFile,
    eventSystemEnabled: Boolean,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    private val dataLanguage: Language by lazy { computeDataLanguage(manager, virtualFile) }

    override fun getBaseLanguage(): Language = MakoLanguage

    override fun getTemplateDataLanguage(): Language = dataLanguage

    override fun getLanguages(): Set<Language> = setOf(MakoLanguage, dataLanguage)

    override fun getContentElementType(language: Language): IElementType? = when (language) {
        dataLanguage -> MakoTemplateDataElementTypes.forDataLanguage(dataLanguage)
        MakoLanguage -> MakoTemplateDataElementTypes.FILE
        else -> null
    }

    /**
     * [com.intellij.psi.AbstractFileViewProvider.createFile] only builds the base
     * language file; the data-language (HTML) root must be created here. We build
     * it via the data language's own parser definition — the platform then uses
     * [getContentElementType] to wire in the [com.intellij.psi.templateLanguages.TemplateDataElementType]
     * so the HTML tree is parsed only from the OUTER fragments.
     */
    override fun createFile(language: Language): PsiFile? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return null
        return when (language) {
            templateDataLanguage, baseLanguage -> parserDefinition.createFile(this)
            else -> null
        }
    }

    override fun cloneInner(virtualFile: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        MakoFileViewProvider(manager, virtualFile, false)

    private companion object {
        fun computeDataLanguage(manager: PsiManager, file: VirtualFile): Language {
            val configured = TemplateDataLanguageMappings.getInstance(manager.project)
                ?.getMapping(file)
            return configured ?: HTMLLanguage.INSTANCE
        }
    }
}

class MakoFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = MakoFileViewProvider(manager, file, eventSystemEnabled)
}
