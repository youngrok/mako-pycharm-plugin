# Mako Template Support for PyCharm

Brings back first-class [Mako](https://www.makotemplates.org/) template support to
modern PyCharm, after JetBrains removed the bundled Mako support in 2021.3
([PY-51736](https://youtrack.jetbrains.com/issue/PY-51736)).

> ⚠️ **Disclaimer.** This project's code was written entirely by AI and is not
> thoroughly tested. It is provided **as is, with no warranty of any kind** —
> no guarantee of correctness, stability, or fitness for any purpose. Use at your
> own risk.

## Features

- **Syntax highlighting** for the full Mako grammar:
  - `${ ... }` expressions (with `| h`, `| u`, … filters)
  - `<% ... %>` Python blocks and `<%! ... %>` module-level blocks
  - `% if/for/while/try/with:` … `% endif/endfor/…` control structures
  - `##` line comments and `<%doc> … </%doc>` block comments
  - `<%text> … </%text>` raw regions
  - Tags: `<%def>`, `<%block>`, `<%inherit>`, `<%include>`, `<%namespace>`,
    `<%page>`, `<%call>`, custom `<%ns:tag>` tags, attributes and values
- **HTML support** between the Mako constructs — HTML highlighting, completion and
  tag-closing — via a template-language file view provider with HTML as the data
  language (configurable per file under
  *Settings | Languages & Frameworks | Template Data Languages*).
- **Python language injection** into every code region (`${…}`, `<% … %>`,
  `<%! … %>`, `% … :`), so completion, type inference, inspections and navigation
  work inside Mako code.
- **"Python Template Languages" integration**: Mako appears in the
  *Settings | Languages & Frameworks | Python Template Languages* dropdown next to
  Django, Jinja2 and Chameleon — selecting it lets PyCharm treat your template
  folders as Mako, just like the old bundled support did.
- **Render-context variables**: the variables a view passes to `render(...)` become
  real Python names inside the template — completion, type inference and
  go-to-declaration all work on them. This does **not** reimplement context
  extraction; it reuses PyCharm's existing `TemplateContextProvider` engine (the
  same one Django/Flask populate). See [Render context](#render-context) for the
  exact conditions.
- **Editor conveniences**: brace matching for `${}`/`<% %>`/tags, `##` line
  commenting (Cmd/Ctrl+/), `<%doc>` block commenting, and a configurable color
  scheme (*Settings | Editor | Color Scheme | Mako*).

## Not yet supported

Mako-specific authoring aids are still missing — these are planned:

- **Tag name completion** — completing `<%def`, `<%inherit`, `<%namespace`,
  `<%block`, `<%call`, ….
- **Tag attribute completion** — completing each tag's parameters (e.g. `name`,
  `file`, `args`, `import`), and completing their *values* — most importantly
  **file-path completion** for path attributes like `file=` (`<%inherit file="…">`,
  `<%namespace file="…">`, `<%include file="…">`).
- **Closing-tag completion / matching** — auto-inserting or completing `</%def>`,
  `</%block>`, … and highlighting the matching open/close pair.
- **Control terminators** — completing `% endfor` / `% endif` / `% endwhile`
  (and validating that every `% for` / `% if` has a terminator).
- **`<%def>` as a Python namespace** — using a name defined by `<%def name="f()">`
  (and `<%namespace>` imports) as a callable symbol elsewhere in the template, with
  completion, go-to-definition and argument checking.

## Supported files

`.mako` and `.mak` are associated automatically. To use Mako for other extensions
(e.g. `.html`), add the pattern under *Settings | Editor | File Types | Mako*.

## Render context

If a view function/method (under an app's `views.py` or `views/` package) calls
`render(..., 'page.html', ctx)` with a string template name, the context variables
become real Python names in that template's `${...}` / `<% %>` / `%` regions —
with completion, type inference and go-to-declaration.

PyCharm's bundled Django/Flask engine supplies the context (this plugin reuses it,
it does not reimplement it); the plugin's part is parsing the template as Mako (a
`.mako`/`.mak` file, or a `.html` mapped to Mako under *Settings | Languages &
Frameworks | Python Template Languages*).

## Requirements

- **PyCharm Professional** 2024.3+ (built and tested against 2025.1). The
  "Python Template Languages" dropdown integration relies on the Pro-only
  `intellij.template.lang.core` module; the rest (highlighting, HTML split,
  Python injection) is independent of it.
- The bundled Python and XML/HTML support (always present in PyCharm).

## Install

From a release zip:

1. *Settings/Preferences → Plugins*
2. gear icon → **Install Plugin from Disk…**
3. select `build/distributions/mako-template-support-*.zip`
4. restart the IDE.

## Build from source

```bash
./gradlew buildPlugin     # produces build/distributions/*.zip
./gradlew test            # lexer + platform integration tests
./gradlew runIde          # launch a sandbox IDE with the plugin
```

Requires JDK 21 (the IntelliJ Platform 2025.1 toolchain).

## Releases

Every push and PR is built and tested on CI. To cut a release, push a tag:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

CI then builds the plugin and publishes a GitHub Release with the installable
`.zip` attached.

## Architecture

| Concern | Class |
|---|---|
| Language / template language | `lang/MakoLanguage` |
| File type (`.mako`, `.mak`) | `filetype/MakoFileType` |
| Lexer (hand-written state machine) | `parser/MakoLexer` |
| Parser → shallow PSI | `parser/MakoParser`, `parser/MakoParserDefinition` |
| Template/HTML split | `lang/MakoFileViewProvider`, `parser/MakoTemplateDataElementTypes` |
| Token/element types | `psi/MakoTypes` |
| Syntax highlighting | `highlight/MakoSyntaxHighlighter`, `…Factory`, `…ColorSettingsPage` |
| Python injection | `inject/MakoPythonInjector`, `psi/MakoInjectionHost` |
| Brace matching / commenting | `lang/MakoBraceMatcher`, `lang/MakoCommenter` |

The lexer emits a single `OUTER` token for non-Mako spans; the
`TemplateDataElementType` re-parses those spans with the data language (HTML),
while Mako constructs become `OuterLanguageElement`s in the HTML tree. Python is
injected into the code tokens of the Mako tree via a `MultiHostInjector`.

## License

[MIT](LICENSE).
