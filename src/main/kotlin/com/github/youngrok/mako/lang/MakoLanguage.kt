package com.github.youngrok.mako.lang

import com.intellij.template.lang.core.templateLanguages.PythonTemplateLanguage

/**
 * The Mako template language.
 *
 * Extending [PythonTemplateLanguage] (rather than a plain `Language`) makes Mako
 * appear in *Settings | Languages & Frameworks | Python Template Languages*
 * alongside Django, Jinja2 and Chameleon. The dropdown is populated by
 * `PythonTemplateLanguage.getAllTemplateLanguages()`, which collects every
 * registered language that is an instance of this class — so simply subclassing
 * it is enough to be offered there, restoring the behaviour of the old bundled
 * Mako support ([PY-51736](https://youtrack.jetbrains.com/issue/PY-51736)).
 *
 * [PythonTemplateLanguage] already implements
 * [com.intellij.psi.templateLanguages.TemplateLanguage], so the HTML data-language
 * split in [MakoFileViewProvider] keeps working.
 */
object MakoLanguage : PythonTemplateLanguage("Mako") {
    private fun readResolve(): Any = MakoLanguage

    override fun getDisplayName(): String = "Mako"

    override fun isCaseSensitive(): Boolean = true

    /** Label shown in the Python Template Languages dropdown. */
    override fun getTemplateLanguageName(): String = "Mako"

    /**
     * Whether opening [fileName] should prompt the user to pick a template
     * language. We let the explicit `.mako`/`.mak` extensions drive selection.
     */
    override fun isFileLeadsToLanguageSelection(fileName: String): Boolean =
        fileName.endsWith(".mako") || fileName.endsWith(".mak")
}
