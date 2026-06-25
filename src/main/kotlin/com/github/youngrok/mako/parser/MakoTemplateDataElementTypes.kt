package com.github.youngrok.mako.parser

import com.github.youngrok.mako.lang.MakoLanguage
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-data-language template element types. For a Mako file whose data language
 * is HTML, the platform builds an HTML PSI tree from the [MakoTypes.OUTER]
 * regions while the Mako constructs are represented by [TEMPLATE_FRAGMENT].
 */
object MakoTemplateDataElementTypes {

    /** The file element type of the Mako (outer) tree. */
    val FILE: IFileElementType = IFileElementType("MAKO_TEMPLATE_FILE", MakoLanguage)

    /**
     * Element type standing in for a whole Mako construct inside the data-language
     * tree. The data lexer collapses each Mako region to this single token.
     */
    val TEMPLATE_FRAGMENT: IElementType = object : IElementType("MAKO_TEMPLATE_FRAGMENT", MakoLanguage) {}

    private val cache = ConcurrentHashMap<Language, TemplateDataElementType>()

    /** A [TemplateDataElementType] bound to the given data [language] (e.g. HTML). */
    fun forDataLanguage(language: Language): TemplateDataElementType =
        cache.getOrPut(language) {
            MakoTemplateDataElementType(language)
        }

    private class MakoTemplateDataElementType(language: Language) : TemplateDataElementType(
        "MAKO_TEMPLATE_DATA_${language.id}",
        language,
        MakoTypes.OUTER,
        TEMPLATE_FRAGMENT,
    )
}
