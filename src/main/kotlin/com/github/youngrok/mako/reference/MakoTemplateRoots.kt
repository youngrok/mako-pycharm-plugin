package com.github.youngrok.mako.reference

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.template.lang.core.templateLanguages.TemplatesService

/**
 * Resolves the template root folders a Mako `file="..."` reference should be
 * looked up against.
 *
 * Mako resolves `<%include file="x"/>` and `<%inherit file="x"/>` relative to the
 * configured template lookup directories, not to the including file's directory.
 * PyCharm records those directories in [TemplatesService.templateFolders] (the
 * same ones the [com.github.youngrok.mako.lang.MakoLanguageSubstitutor] uses), so
 * we reuse them as the search roots.
 */
object MakoTemplateRoots {

    /**
     * Template root directories for the file containing [context], most relevant
     * first. Returns an empty list if none are configured (the caller then has no
     * roots to complete against).
     */
    fun rootsFor(context: PsiElement): List<VirtualFile> {
        val vFile = context.containingFile?.originalFile?.virtualFile ?: return emptyList()
        val module = ModuleUtilCore.findModuleForFile(vFile, context.project) ?: return emptyList()
        if (module.isDisposed) return emptyList()
        val service = TemplatesService.getInstance(module) ?: return emptyList()
        return service.templateFolders.filter { it.isValid && it.isDirectory }
    }

    /** [rootsFor] mapped to PSI directories, for use as [com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet] defaults. */
    fun rootDirsFor(context: PsiElement) =
        rootsFor(context).mapNotNull { PsiManager.getInstance(context.project).findDirectory(it) }
}
