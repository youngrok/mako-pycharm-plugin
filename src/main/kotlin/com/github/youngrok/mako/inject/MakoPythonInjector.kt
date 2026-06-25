package com.github.youngrok.mako.inject

import com.github.youngrok.mako.psi.MakoFile
import com.github.youngrok.mako.psi.MakoInjectionHost
import com.github.youngrok.mako.psi.MakoTypes
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Injects Python into the code regions of Mako constructs (`${...}`, `<% ... %>`,
 * `<%! ... %>`, `% control:` lines) so completion, type inference, inspections and
 * navigation work — like the old bundled Mako support.
 *
 * All code regions of a file are chained into a SINGLE injected Python document,
 * with block structure reconstructed (`% for/if:` open indented blocks). This makes
 * names bound in one region visible in later ones — e.g. `% for x in items:`
 * … `${x}` resolves and types `bf`.
 *
 * The platform calls this injector once per host; we register the whole chained
 * injection on EVERY call (keyed off the file's host list), so `getInjectedPsiFiles`
 * for any host returns the shared chained file. (Registering only on the first host
 * left every other `${}` without a discoverable injection.)
 */
class MakoPythonInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(MakoInjectionHost::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is MakoInjectionHost) return
        val python = PYTHON ?: return
        val file = context.containingFile as? MakoFile ?: return

        val hosts = PsiTreeUtil.findChildrenOfType(file, MakoInjectionHost::class.java)
            .filter { it.codeNode() != null }
        if (hosts.isEmpty() || context !in hosts) return

        var started = false
        var indent = 0

        for (host in hosts) {
            val code = host.codeNode() ?: continue
            val type = code.elementType
            val rangeInHost = TextRange.from(
                code.startOffset - host.textRange.startOffset,
                code.textLength,
            )

            // `% endfor`/`% endif` etc. close a block: dedent, emit nothing.
            if (type == MakoTypes.CONTROL_END) {
                indent = (indent - 1).coerceAtLeast(0)
                continue
            }

            val opensBlock = type == MakoTypes.CONTROL_CODE &&
                isBlockOpener(code.text.trim())
            val midBlock = type == MakoTypes.CONTROL_CODE && isMidBlock(code.text.trim())

            // Indentation this region lives at. Mid-block headers (else/elif/…) sit
            // one level out; everything else sits at the current indent.
            val here = if (midBlock) (indent - 1).coerceAtLeast(0) else indent
            val pad = "    ".repeat(here)
            // Expressions become indented expression statements `(...)`; statements
            // (control lines, <% %>) are emitted as-is at the indent.
            val prefix = if (type == MakoTypes.EXPR_CODE) "\n$pad(" else "\n$pad"
            val suffix = if (type == MakoTypes.EXPR_CODE) ")\n" else "\n"

            if (!started) { registrar.startInjecting(python); started = true }
            registrar.addPlace(prefix, suffix, host, rangeInHost)

            if (opensBlock) indent += 1
        }

        if (started) registrar.doneInjecting()
    }

    private fun isBlockOpener(text: String): Boolean {
        val kw = text.substringBefore(' ').substringBefore('(').trim().trimEnd(':')
        return kw in BLOCK_OPENERS && text.trimEnd().endsWith(":")
    }

    private fun isMidBlock(text: String): Boolean {
        val kw = text.substringBefore(' ').substringBefore(':').trim()
        return kw in MID_BLOCK
    }

    companion object {
        private val PYTHON: Language? = Language.findLanguageByID("Python")
        private val BLOCK_OPENERS = setOf("for", "if", "while", "with", "try", "def", "class")
        private val MID_BLOCK = setOf("else", "elif", "except", "finally")
    }
}
