package com.github.youngrok.mako.psi

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.psi.tree.IElementType

class MakoTokenType(debugName: String) : IElementType(debugName, MakoLanguage)

class MakoElementType(debugName: String) : IElementType(debugName, MakoLanguage)
