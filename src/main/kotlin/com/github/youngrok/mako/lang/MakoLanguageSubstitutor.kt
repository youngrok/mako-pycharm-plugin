package com.github.youngrok.mako.lang

import com.intellij.lang.Language
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.template.lang.core.templateLanguages.TemplatesService

/**
 * Forces `.html` (and other data files) under a Mako-configured template root to
 * be parsed as Mako.
 *
 * PyCharm's bundled `PyTemplateLanguageSubstitutor` already does this, but it is
 * registered *after* `WebFrameworkHtmlLanguageSubstitutor` (`order="first"`). In
 * a project that PyCharm also recognises as a web-framework (Vue/etc.) context,
 * that web substitutor returns its own dialect first, so the bundled Mako
 * substitutor never runs and the file stays plain HTML — leaving every Mako
 * feature (highlighting, Python injection, render-context completion/navigation)
 * inert.
 *
 * We register this substitutor with `order="first, before web-framework-html"`,
 * and only claim a file when the user has explicitly selected **Mako** as the
 * module's template language and the file lives under a configured template
 * folder. That makes the behaviour identical to the bundled substitutor but wins
 * the precedence race, without affecting genuinely non-Mako files.
 */
class MakoLanguageSubstitutor : LanguageSubstitutor() {

    override fun getLanguage(file: VirtualFile, project: Project): Language? {
        if (file.isDirectory) return null
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        if (module.isDisposed) return null

        val service = TemplatesService.getInstance(module) ?: return null
        val selected = service.templateLanguage ?: return null
        if (selected.id != "Mako") return null

        if (service.templateFileTypes.none { it == file.fileType }) return null
        if (!isUnderTemplateFolder(file, service.templateFolders)) return null

        return MakoLanguage
    }

    private fun isUnderTemplateFolder(file: VirtualFile, folders: List<VirtualFile>): Boolean {
        if (folders.isEmpty()) return false
        var dir: VirtualFile? = file.parent
        while (dir != null) {
            if (folders.any { it == dir }) return true
            dir = dir.parent
        }
        return false
    }
}
