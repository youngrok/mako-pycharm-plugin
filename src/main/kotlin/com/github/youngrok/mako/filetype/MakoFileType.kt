package com.github.youngrok.mako.filetype

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * The `.mako` / `.mak` file type. Templates can also live in `.html` files, but
 * we only auto-associate the unambiguous Mako extensions; users can map other
 * extensions to this file type via Settings | Editor | File Types.
 */
object MakoFileType : LanguageFileType(MakoLanguage) {
    override fun getName(): String = "Mako"

    override fun getDescription(): String = "Mako template"

    override fun getDefaultExtension(): String = "mako"

    override fun getIcon(): Icon = AllIcons.FileTypes.Html
}
