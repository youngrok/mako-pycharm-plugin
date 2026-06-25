package com.github.youngrok.mako.highlight

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class MakoColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "Mako"

    override fun getIcon(): Icon = AllIcons.FileTypes.Html

    override fun getHighlighter(): SyntaxHighlighter = MakoSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getDemoText(): String = """
        ## A single-line Mako comment
        <%doc>
            A multi-line doc comment.
        </%doc>
        <%inherit file="base.mako"/>
        <%page args="items, title='Untitled'"/>

        <%! import datetime %>

        <%def name="greeting(name)">
            <p>Hello, ${'$'}{name | h}!</p>
        </%def>

        <html>
        <body>
            <h1>${'$'}{title}</h1>
            % if items:
                <ul>
                % for item in items:
                    <li>${'$'}{item}</li>
                % endfor
                </ul>
            % else:
                <p>No items.</p>
            % endif

            <% now = datetime.datetime.now() %>
            <p>Rendered at ${'$'}{now}</p>
        </body>
        </html>
    """.trimIndent()

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Delimiters (\${}, <% %>, tags)", MakoSyntaxHighlighter.DELIMITER),
            AttributesDescriptor("Expression code", MakoSyntaxHighlighter.EXPRESSION),
            AttributesDescriptor("Control keyword", MakoSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Tag name", MakoSyntaxHighlighter.TAG_NAME),
            AttributesDescriptor("Attribute name", MakoSyntaxHighlighter.ATTR_NAME),
            AttributesDescriptor("Attribute value", MakoSyntaxHighlighter.ATTR_VALUE),
            AttributesDescriptor("Line comment (##)", MakoSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Block comment (<%doc>)", MakoSyntaxHighlighter.BLOCK_COMMENT),
            AttributesDescriptor("Template text", MakoSyntaxHighlighter.TEMPLATE_TEXT),
            AttributesDescriptor("Bad character", MakoSyntaxHighlighter.BAD_CHARACTER),
        )
    }
}
