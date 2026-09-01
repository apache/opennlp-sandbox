Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html
Fetched: 2026-08-28

Terminology used verbatim by the Elasticsearch Term Vectors API: "term vectors",
"term frequency", "term statistics", "field statistics", "offsets", "payloads",
"positions".

Definition quoted from the page: the API is used to "Get information and
statistics about terms in the fields of a particular document."

Term frequency is listed as "term frequency in the field (always returned)".

Relevance: "term vector", "term frequency", and per-term "offsets" are standard
search vocabulary. The workbench's `opennlp:term-vectors` layer and its
`term`/`frequency`/`occurrences` fields line up with this API one for one.
