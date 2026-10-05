Source: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
Fetched: 2026-08-28

PositionEncodingKind, quoted:

- `'utf-8'`: "Character offsets count UTF-8 code units (e.g bytes)."
- `'utf-16'`: "Character offsets count UTF-16 code units. This is the default and
  must always be supported by servers"
- `'utf-32'`: "Character offsets count UTF-32 code units. Implementation note:
  these are the same as Unicode code points, so this `PositionEncodingKind` may
  also be used for an encoding-agnostic representation of character offsets."

Relevance: the workbench's `Offsets` summary values ("UTF-8 bytes", "UTF-16",
"Unicode code points") match this three-way split. LSP calls the third one
"utf-32" and explicitly notes it equals Unicode code points, so the workbench's
label is accurate and arguably clearer.
