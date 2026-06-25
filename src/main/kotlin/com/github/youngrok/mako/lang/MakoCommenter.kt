package com.github.youngrok.mako.lang

import com.intellij.lang.Commenter

/**
 * Ctrl/Cmd+/ produces a Mako `##` line comment; block comments use `<%doc>`.
 */
class MakoCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "## "

    override fun getBlockCommentPrefix(): String = "<%doc>"

    override fun getBlockCommentSuffix(): String = "</%doc>"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
