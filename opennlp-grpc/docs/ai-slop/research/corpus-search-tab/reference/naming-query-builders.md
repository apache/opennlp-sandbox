# Cross-product survey: what the "advanced / boolean query" UI is called

Sources fetched 2026-08-28:

- https://docs.aws.amazon.com/cloudsearch/latest/developerguide/search-tester.html
- https://docs.aws.amazon.com/cloudsearch/latest/developerguide/searching-compound-queries.html
- https://docs.aws.amazon.com/cloudsearch/latest/developerguide/getting-started-search.html
- https://www.elastic.co/docs/explore-analyze/query-filter/filtering
- https://www.elastic.co/guide/en/kibana/current/playground-query.html
- https://solr.apache.org/guide/solr/latest/query-guide/query-screen.html
- https://docs.coveo.com/en/3056/
- https://coveo.github.io/search-ui/classes/querybuilder.html
- https://docs.opensearch.org/latest/dashboards/query-workbench/

## Products that literally say "query builder" or equivalent in the UI

### Coveo: "Coveo Advanced Search"

Component name in Coveo for Sitecore. Doc description: the "Coveo Advanced Search component allows
users to easily build complex queries using intuitive wizard modals".

Coveo also ships a developer-facing `QueryBuilder` class in the JavaScript Search Framework, and a
"Rule Set Editor", described as "a wizard which alleviates the complexity that comes with dealing
with filtering expression syntax".

Takeaway: "Advanced Search" is the end-user label; "Query Builder" is the API/class name.

### Amazon CloudSearch: "Search Tester" and the "structured" query parser

The CloudSearch console screen is called the "search tester". Docs: "The search tester in the Amazon
CloudSearch console enables you to submit sample search requests using any of the supported query
parsers: simple, structured, lucene, or dismax. By default, requests are processed with the simple
query parser."

Flow described: "enter your structured query in the Search field and choose Run".

The structured parser is described as letting you "search specific fields, construct compound
queries using Boolean operators, and use advanced features such as term boosting and proximity
searching". Doc page title: "Constructing Compound Queries in Amazon CloudSearch".

Syntax is prefix boolean: `(and boost=N EXPRESSION1 EXPRESSION2 ...)`, `(or ...)`, `(not ...)`.
Worked example from the docs: `(and title:'star' year:{,2000])`.

Three reusable labels here: "Search Tester" for the screen, "structured query" for the mode,
"compound query" for a multi-clause expression.

### Elastic Playground: "visual query editor"

Doc sentence: "Select the Query tab to open the visual query editor." A rare case of a mainstream
search product describing a query UI as an editor rather than a builder.

### Solr Admin: "Query" screen as a form

The Solr Query screen is a plain form over request parameters with an "Execute Query" button. Solr's
own prose calls what you produce a "structured query" (standard query parser docs: "supports a
robust and fairly intuitive syntax allowing you to create a variety of structured queries").

### Kibana: "Add filter" pills

Kibana never uses the words "query builder". The clause-by-clause UI is a row of filter pills built
with "Add filter" and refined via "Edit filter", with "Edit as Query DSL" as the raw escape hatch.

### OpenSearch: "Query Workbench"

The only mainstream product surveyed whose query screen is named with a workshop metaphor.
Language toggle "SQL" / "PPL", buttons "Run", "Clear", "Explain".

## Summary of the available label vocabulary

Screen-level labels observed in shipping products:

| Label | Product | Meaning in that product |
| --- | --- | --- |
| "Query" | Solr Admin, Weaviate Cloud, Elastic Playground tab | Compose and run a query |
| "Search" | many | Run a search |
| "Discover" | Kibana, OpenSearch Dashboards | Explore data in an index |
| "Console" | Kibana Dev Tools, Qdrant Web UI | Send raw API requests |
| "Query Workbench" | OpenSearch Dashboards | SQL / PPL query editor |
| "Search Tester" | Amazon CloudSearch | Try sample search requests |
| "Search preview" | Meilisearch Cloud, Algolia Editor | Test relevance against a live index |
| "Browse" | Algolia dashboard | Page through records in an index |
| "Explorer" | Weaviate Cloud | Inspect objects, metadata, vectors |
| "Playground" | Elastic (Kibana) | Experiment against your indices |
| "Advanced Search" | Coveo | Build a complex query via a wizard |

Mode / language labels for the structured clause layer:

| Label | Product |
| --- | --- |
| "Query DSL" plus "bool query" (`must` / `should` / `filter` / `must_not`) | Elasticsearch, OpenSearch |
| "KQL" / "Kibana Query Language" | Kibana |
| "Dashboards Query Language (DQL)" | OpenSearch Dashboards |
| "Lucene query syntax" | Kibana language switcher, Solr "lucene" parser |
| "Standard Query Parser", "DisMax", "eDisMax" / "Extended query parser" | Solr |
| "YQL" / "Vespa Query Language" | Vespa |
| "structured query" / "compound query" | Amazon CloudSearch |
| "filter_by" | Typesense |
| "filter expression" | Meilisearch |
| "Filtering" / "filtering clauses" | Qdrant |
| "filters", "facetFilters", "numericFilters" | Algolia |
| GraphQL | Weaviate |

## Observations for a general audience

1. No product in this survey labels a screen "Boolean search". The word "Boolean" appears only in
   prose describing operators (Elasticsearch "boolean combinations of other queries", CloudSearch
   "compound queries using Boolean operators", Solr "Boolean operators ... must appear in all
   uppercase").
2. "Advanced search" survives as an end-user label mainly outside the engine vendors themselves
   (Coveo's component, and general web application convention).
3. "Query builder" is far more common as a library or class name than as a UI label.
4. "Filter" is the single most portable word for the clause layer. It appears in Elasticsearch
   (`filter` clause), Kibana ("Add filter"), Qdrant ("Filtering"), Typesense (`filter_by`),
   Meilisearch ("filter expression"), Algolia ("filters"), and Solr (`fq`, "The filter queries").
