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
 * Completes Mako tag *attribute names* inside an open tag — e.g. inside
 * `<%def …|>` it offers `name`, `args`, `filter`, `cached`, …, and inside
 * `<%include …|>` it offers `file`, `args`, `import`.
 *
 * Like [MakoTagCompletionContributor], this reads the document text directly
 * (rather than relying on the PSI element type at the caret) so it works even
 * when a half-typed tag is lexed oddly, and it is registered for `any` language
 * because the completion position lands in the HTML data tree.
 *
 * Attribute *values* are handled elsewhere: `file="…"` paths by
 * [com.github.youngrok.mako.reference.MakoFileReferenceContributor].
 */
class MakoAttributeCompletionContributor : CompletionContributor() {

    /** Open the popup when typing a letter that begins an attribute name. */
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean {
        if (!typeChar.isLetter()) return false
        val vp = position.containingFile?.viewProvider ?: return false
        if (vp.baseLanguage != MakoLanguage) return false
        val doc = vp.document ?: return false
        val caret = position.textRange.endOffset.coerceIn(0, doc.textLength)
        // Only when the char to the left is whitespace inside an open tag — i.e.
        // the start of a fresh attribute name, not mid-word (which restarts on its
        // own) and not inside a value.
        val ctx = TagContext.at(doc.charsSequence, caret) ?: return false
        return ctx.inAttributeNamePosition && caret >= 1 && doc.charsSequence[caret - 1].isWhitespace()
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val vp = parameters.originalFile.viewProvider
        if (vp.baseLanguage != MakoLanguage) return
        val doc = vp.document ?: return
        val text = doc.charsSequence
        val offset = parameters.offset

        val ctx = TagContext.at(text, offset) ?: return
        if (!ctx.inAttributeNamePosition) return

        val attrs = ATTRS_BY_TAG[ctx.tagName] ?: return
        val sink = result.withPrefixMatcher(ctx.namePrefix)

        for (attr in attrs) {
            if (attr in ctx.usedAttributes) continue
            if (!attr.startsWith(ctx.namePrefix, ignoreCase = true)) continue
            sink.addElement(attributeLookup(attr))
        }
    }

    private fun attributeLookup(name: String): LookupElement =
        LookupElementBuilder.create(name)
            .withIcon(com.intellij.icons.AllIcons.Nodes.Property)
            .withTypeText("Mako attribute")
            .withInsertHandler(AttrInsertHandler)

    /** Inserts `="<caret>"` after the attribute name if it is not already there. */
    private object AttrInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(ctx: InsertionContext, item: LookupElement) {
            val doc = ctx.document
            val tail = ctx.tailOffset
            // Skip if an `=` already follows (possibly after spaces).
            var i = tail
            val text = doc.charsSequence
            while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
            if (i < text.length && text[i] == '=') {
                ctx.editor.caretModel.moveToOffset(tail)
                return
            }
            doc.insertString(tail, "=\"\"")
            ctx.editor.caretModel.moveToOffset(tail + 2) // between the quotes
        }
    }

    /**
     * Describes where the caret sits relative to the enclosing Mako tag, derived
     * purely from text. `null` if the caret is not inside an open `<%tag …` (before
     * its `>` / `/>` / `%>`).
     */
    private class TagContext private constructor(
        val tagName: String,
        val namePrefix: String,
        val inAttributeNamePosition: Boolean,
        val usedAttributes: Set<String>,
    ) {
        companion object {
            fun at(text: CharSequence, caret: Int): TagContext? {
                // 1. Find the nearest `<%` opener to the left, bailing if we hit a
                //    tag terminator or another opener first (caret not in a tag).
                var i = caret - 1
                while (i >= 1) {
                    val c = text[i]
                    if (c == '>') return null                         // previous tag closed
                    if (c == '<') return null                         // some other `<...`
                    if (c == '%' && text[i - 1] == '<') break        // found `<%`
                    i--
                }
                if (i < 1) return null
                // 2. Read the tag name right after `<%`. (A closing `</%name>`
                //    takes no attributes, but `text[i-1]=='<'` excludes the `/`
                //    case already since we matched `<%`, not `/%`.)
                var p = i + 1
                val nameStart = p
                while (p < caret && isNameChar(text[p])) p++
                val tagName = text.subSequence(nameStart, p).toString()
                if (tagName.isEmpty()) return null
                // A tag name must be followed by whitespace before attributes; if
                // the caret is still inside the name, this isn't attribute context.
                if (p >= caret) return null

                // 3. Bail if the tag is already terminated between `<%name` and the
                //    caret (`>`, `/>`, or `%>`) — then we're past the tag.
                var j = p
                while (j < caret) {
                    val c = text[j]
                    if (c == '>') return null
                    if (c == '/' && j + 1 < text.length && text[j + 1] == '>') return null
                    if (c == '%' && j + 1 < text.length && text[j + 1] == '>') return null
                    j++
                }

                // 4. Determine if the caret is in an attribute-NAME position (not
                //    inside a quoted value) and collect already-used attribute names.
                val region = text.subSequence(p, caret).toString()
                val used = mutableSetOf<String>()
                var k = 0
                var inValue = false
                var quote = ' '
                var curName = StringBuilder()
                while (k < region.length) {
                    val c = region[k]
                    if (inValue) {
                        if (c == quote) inValue = false
                        k++
                        continue
                    }
                    when {
                        c == '"' || c == '\'' -> { inValue = true; quote = c }
                        c == '=' -> {
                            if (curName.isNotEmpty()) { used.add(curName.toString()); curName = StringBuilder() }
                        }
                        c.isWhitespace() -> {
                            if (curName.isNotEmpty()) { used.add(curName.toString()); curName = StringBuilder() }
                        }
                        isNameChar(c) -> curName.append(c)
                        else -> curName = StringBuilder()
                    }
                    k++
                }
                if (inValue) {
                    // Caret is inside a quoted value → not an attribute-name position.
                    return TagContext(tagName, "", inAttributeNamePosition = false, usedAttributes = used)
                }
                // The trailing run of name chars is the prefix being typed now.
                val namePrefix = curName.toString()
                used.remove(namePrefix) // the one being typed isn't "used" yet
                return TagContext(tagName, namePrefix, inAttributeNamePosition = true, usedAttributes = used)
            }

            private fun isNameChar(c: Char): Boolean =
                c.isLetterOrDigit() || c == '_' || c == ':'
        }
    }

    private companion object {
        /**
         * Attribute names per Mako tag, from the Mako docs. Order is the suggested
         * display order (most common first).
         */
        val ATTRS_BY_TAG: Map<String, List<String>> = mapOf(
            "def" to listOf("name", "args", "buffered", "cached", "cache_key",
                "cache_timeout", "cache_type", "cache_dir", "cache_url",
                "decorator", "filter"),
            "block" to listOf("name", "args", "filter", "buffered", "cached",
                "cache_key", "cache_timeout", "cache_type"),
            "page" to listOf("args", "expression_filter", "enable_loop",
                "cached", "cache_key", "cache_timeout", "cache_type",
                "cache_dir", "cache_url"),
            "namespace" to listOf("name", "file", "module", "import",
                "inheritable", "args"),
            "inherit" to listOf("file"),
            "include" to listOf("file", "import", "args"),
            "call" to listOf("expr"),
        )
    }
}
