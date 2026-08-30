Source: https://inception-project.github.io/releases/34.2/docs/user-guide.html
Fetched: 2026-08-28

INCEpTION (Apache-licensed annotation platform, successor of WebAnno, built on
Apache UIMA) uses "layer" as its primary term for a group of annotations.

Quoted from the user guide:

  "There are different 'aspects' or 'categories' you might want to annotate ...
  What we called 'aspects', 'categories' or 'ways to annotate' here, is referred
  to as **layers** in INCEpTION"

  "INCEpTION supports **span layers** in order to annotate a span from one
  character ... relation layers in order to annotate the relation between two
  span annotations and **chain layers** which are normally used to annotate
  coreferences."

Layer kinds named by the guide: span layer, relation layer, chain layer, and
"Document metadata" for document-level annotations.

Relevance: the workbench's "Layers" browser, "annotation layer", and the
document-scoped versus positional distinction all have direct precedent here.
"Span layer" is the standard name for what the workbench calls
`LAYER_SCOPE_POSITIONAL`; "document metadata layer" is the standard name for
what it calls `LAYER_SCOPE_DOCUMENT`.
