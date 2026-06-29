package com.github.youngrok.mako.completion

import com.github.youngrok.mako.lang.MakoLanguage
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement

/**
 * Completes Mako tag names right after a `<%` opener: typing `<%d` offers `def`,
 * `<%in` offers `include`/`inherit`, and so on.
 *
 * Mako files are template-data files (a Mako tree plus an HTML data tree). At the
 * caret the platform's completion position usually lands in the HTML tree, so a
 * pattern keyed on `language="Mako"` would never fire. Instead this contributor
 * is registered for `any` language and inspects the Mako PSI tree directly to
 * decide whether the caret sits on a tag name.
 */
class MakoTagCompletionContributor : CompletionContributor() {

    /**
     * Make typing the `%` of `<%` open the completion popup automatically (like
     * HTML's `<`), instead of requiring Ctrl+Space. Returning true here is the
     * platform-blessed way for a contributor to request its own auto-popup, and
     * it correctly restarts the session when an HTML popup from the preceding `<`
     * is being dismissed.
     */
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean {
        // Open the popup when the user types a character that starts/extends a
        // Mako tag opener: the `%` of `<%` (or `</%`), or a tag-name letter right
        // after it. invokeAutoPopup runs BEFORE the typed char is in the document.
        val vp = position.containingFile?.viewProvider ?: return false
        if (vp.baseLanguage != MakoLanguage) return false
        val doc = vp.document ?: return false
        val caret = position.textRange.endOffset.coerceIn(0, doc.textLength)
        val text = doc.charsSequence
        return when {
            // typing `%` right after `<` → forms `<%`
            typeChar == '%' && caret >= 1 && text[caret - 1] == '<' -> true
            // typing a tag-name letter right after `<%` / `</%`
            isTagNameChar(typeChar) && endsWithMakoTagOpener(text, caret) -> true
            else -> false
        }
    }

    /** True if the text ending at [end] finishes with a `<%` or `</%` opener. */
    private fun endsWithMakoTagOpener(text: CharSequence, end: Int): Boolean =
        (end >= 2 && text[end - 1] == '%' && text[end - 2] == '<') ||
            (end >= 3 && text[end - 1] == '%' && text[end - 2] == '/' && text[end - 3] == '<')

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val prefix = tagNamePrefixAt(parameters) ?: return

        // Re-key the result set on the already-typed tag-name prefix so the IDE
        // filters `<%in` → include/inherit correctly (the platform's prefix is
        // computed from the HTML tree and would be empty/wrong here). Match by
        // strict case-insensitive prefix: a tag name is a single word, so the
        // platform's fuzzy matcher (which treats `de` as matching `inclu·de·`)
        // would over-offer. We pre-filter to the true prefix matches.
        val sink = result.withPrefixMatcher(prefix)

        for (tag in TAGS) {
            if (tag.name.startsWith(prefix, ignoreCase = true)) {
                sink.addElement(tag.toLookupElement())
            }
        }
    }

    /**
     * If the caret sits where a Mako tag name belongs — right after a `<%`
     * opener, or partway through an existing tag name — returns the already-typed
     * portion of the name (empty for a bare `<%`). Returns null otherwise.
     *
     * We read the Mako tree (not the merged [com.intellij.psi.FileViewProvider.findElementAt],
     * which returns the overlapping HTML data leaf) on the *original* file: a bare
     * `<%` there is lexed as a Python-block opener (no tag name yet), which we
     * treat as an empty prefix.
     */
    private fun tagNamePrefixAt(parameters: CompletionParameters): String? {
        val vp = parameters.originalFile.viewProvider
        if (vp.baseLanguage != MakoLanguage) return null
        val doc = vp.document ?: return null
        val offset = parameters.offset
        return tagPrefixFromText(doc.charsSequence, offset)
    }

    /**
     * Reads the tag-name prefix straight from the document text, independent of
     * how the lexer happened to classify a half-typed `<%` (a bare `<%` is lexed
     * as a *Python block* opener, with the caret landing in its code region — so
     * a PSI-type-based check misses it and Python-injection completion takes over,
     * showing `request` etc. instead of Mako tags).
     *
     * Returns the run of tag-name characters between an immediately-preceding
     * `<%` (or `</%`) opener and the caret, or null if the caret is not directly
     * inside a tag opener. Examples (caret = `|`):
     *   `<%|`        → ""          (offer all tags)
     *   `<%inc|`     → "inc"
     *   `</%|`       → ""          (closing tag)
     *   `<% x|`      → null        (space after `<%` → it's a Python block)
     */
    private fun tagPrefixFromText(text: CharSequence, offset: Int): String? {
        var i = offset
        // Walk left over tag-name characters already typed.
        while (i > 0 && isTagNameChar(text[i - 1])) i--
        val nameStart = i
        // Immediately before the name we must find `<%` or `</%`.
        if (i >= 2 && text[i - 1] == '%' && text[i - 2] == '<') {
            // ok: `<%`
        } else if (i >= 3 && text[i - 1] == '%' && text[i - 2] == '/' && text[i - 3] == '<') {
            // ok: `</%`
        } else {
            return null
        }
        return text.subSequence(nameStart, offset).toString()
    }

    private fun isTagNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == ':'

    /**
     * A Mako tag and how it is typically written. `container` tags get a closing
     * tag inserted; the rest are self-closed.
     */
    private data class MakoTag(
        val name: String,
        val container: Boolean,
        val typedAttr: String? = null,
    ) {
        fun toLookupElement(): LookupElement =
            LookupElementBuilder.create(name)
                .withIcon(com.intellij.icons.AllIcons.Nodes.Tag)
                .withTypeText("Mako tag")
                .withInsertHandler(TagInsertHandler(this))
    }

    /**
     * After the user picks a tag name (which may have replaced a partial name),
     * append the rest of the skeleton: an attribute stub for tags that need one,
     * and either `/>` (self-closing) or `>...</%name>` (container), leaving the
     * caret where the user most likely types next.
     */
    private class TagInsertHandler(private val tag: MakoTag) : InsertHandler<LookupElement> {
        override fun handleInsert(ctx: InsertionContext, item: LookupElement) {
            val editor = ctx.editor
            val doc = ctx.document
            // Don't duplicate a skeleton if the tag is already terminated to the
            // right of the caret (e.g. the user is editing an existing tag name).
            val tail = doc.charsSequence.subSequence(ctx.tailOffset, doc.textLength)
            if (alreadyTerminated(tail)) return

            val sb = StringBuilder()
            tag.typedAttr?.let { sb.append(" ").append(it).append("=\"\"") }
            // Caret column to land on after insertion.
            val attrCaret = if (tag.typedAttr != null) sb.length - 1 else -1

            if (tag.container) {
                sb.append("></%").append(tag.name).append(">")
            } else {
                sb.append("/>")
            }

            // Capture the insertion point BEFORE inserting: InsertionContext's
            // tailOffset auto-advances past text written through it.
            val base = ctx.tailOffset
            doc.insertString(base, sb)
            val caret = when {
                attrCaret >= 0 -> base + attrCaret
                tag.container -> base + sb.indexOf(">") + 1
                else -> base + sb.length
            }
            editor.caretModel.moveToOffset(caret)
        }

        /** True if the text right of the caret already closes this tag opener. */
        private fun alreadyTerminated(tail: CharSequence): Boolean {
            var i = 0
            while (i < tail.length) {
                val c = tail[i]
                if (c == '>' || (c == '/' && i + 1 < tail.length && tail[i + 1] == '>')) return true
                if (c == '<' || c == '\n') return false
                i++
            }
            return false
        }
    }

    private companion object {
        /**
         * Built-in Mako tags. `<%text>` and `<%doc>` are raw regions handled by
         * the lexer and are offered as containers for convenience.
         */
        val TAGS = listOf(
            MakoTag("def", container = true, typedAttr = "name"),
            MakoTag("block", container = true, typedAttr = "name"),
            MakoTag("call", container = true, typedAttr = "expr"),
            MakoTag("namespace", container = true, typedAttr = "name"),
            MakoTag("inherit", container = false, typedAttr = "file"),
            MakoTag("include", container = false, typedAttr = "file"),
            MakoTag("page", container = false),
            MakoTag("text", container = true),
            MakoTag("doc", container = true),
        )
    }
}
