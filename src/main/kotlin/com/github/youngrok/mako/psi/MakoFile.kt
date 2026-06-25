package com.github.youngrok.mako.psi

import com.github.youngrok.mako.filetype.MakoFileType
import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class MakoFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, MakoLanguage) {
    override fun getFileType(): FileType = MakoFileType

    override fun toString(): String = "Mako File"
}
