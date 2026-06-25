package com.github.youngrok.mako.lang

import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class MakoBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(MakoTypes.EXPR_OPEN, MakoTypes.EXPR_CLOSE, false),
            BracePair(MakoTypes.PYBLOCK_OPEN, MakoTypes.PYBLOCK_CLOSE, true),
            BracePair(MakoTypes.PYBLOCK_OPEN_MODULE, MakoTypes.PYBLOCK_CLOSE, true),
            BracePair(MakoTypes.TAG_OPEN, MakoTypes.TAG_CLOSE, true),
        )
    }
}
