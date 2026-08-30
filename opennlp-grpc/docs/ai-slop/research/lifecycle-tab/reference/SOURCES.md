# Sources

## External pages consulted (all fetched 2026-08-28)

Excerpted in this directory:
- https://www.elastic.co/guide/en/elasticsearch/reference/current/index-lifecycle-management.html
- https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules-blocks.html
- https://www.elastic.co/guide/en/elasticsearch/reference/7.17/freeze-index-api.html
- https://www.elastic.co/guide/en/elasticsearch/reference/current/aliases.html
- https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-flush.html
- https://lucene.apache.org/core/9_11_0/core/org/apache/lucene/index/IndexWriter.html
- https://qdrant.tech/documentation/concepts/collections/
- https://solr.apache.org/guide/solr/latest/deployment-guide/collection-management.html
- https://docs.weaviate.io/weaviate/manage-data/collections
- https://en.wikipedia.org/wiki/Concept_drift

Consulted and found to carry no usable definition, recorded so nobody repeats the attempt:
- https://docs.opensearch.org/latest/api-reference/snapshots/index/ (navigation only)
- https://docs.opensearch.org/latest/tuning-your-cluster/availability-and-recovery/snapshots/index/ (navigation only)

Deliberately not used: Vespa. Vespa content clusters expose no user-facing commit, seal, or alias
operation, so there is no state in this repo's machine to map onto it. Citing it would be padding.

## Live demo instance

Read-only calls against http://127.0.0.1:7172 on 2026-08-28. No write endpoint was called.
- `GET /api/v1/search-indexes`  -> `{}`
- `GET /api/v1/collections`     -> `{}`
- `GET /api/v1/index-aliases`   -> `{}`
- `GET /api/v1/dictionaries`    -> `{}`
- `GET /api/v1/static-models`   -> `{}`
- `GET /api/v1/search-providers` -> flat_float (VECTOR, LIVE); terms (KEYWORD, LIVE);
  turbo_quant (VECTOR, LIVE, BUNDLE, PERSISTENT)
- `GET /api/v1/service-info`

## Repository files relied on

Front end:
- opennlp-grpc-webapp-default/index.html (lines 42-55 tab ids; 746-750 workspace provider select;
  447-453 workflow provider select; 962-1113 the lifecycle section)
- opennlp-grpc-webapp-default/src/lifecycle-workbench.ts
- opennlp-grpc-webapp-default/src/collection-adapter.ts
- opennlp-grpc-webapp-default/src/search-adapter.ts
- opennlp-grpc-webapp-default/src/api.ts
- opennlp-grpc-webapp-default/src/ui-utils.ts
- opennlp-grpc-webapp-default/src/corpus-workflow.ts
- opennlp-grpc-webapp-default/src/semantic-workbench.ts
- opennlp-grpc-webapp-default/src/main.ts
- opennlp-grpc-webapp-default/src/workbench-navigation.ts

Gateway:
- opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java
- opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcHttpStatusMapper.java
- opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcSearchRpc.java
- opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java

Service and SPI:
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceImpl.java
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/SearchCollectionRegistry.java
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/IndexAliasRegistry.java
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/WorkspaceCheckpointStore.java
- opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/FlatFloatSearchIndexProviderFactory.java
- opennlp-grpc-search-turboquant/src/main/java/org/apache/opennlp/grpc/search/turboquant/TurboQuantSearchIndexProviderFactory.java
- opennlp-grpc-store-s3/src/main/java/org/apache/opennlp/grpc/store/s3/S3VocabularyStoreProvider.java

Protos and docs:
- opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_search.proto
- docs/rfc/opennlp-search-query-model.md (lines 121-150, 255-275)
- docs/rfc/opennlp-grpc-design.md (Phase 1; contains no collection or lifecycle material)
- README.md (lines 610-616, 795-825)

Tests inventoried: see findings/links-and-tests.md Part 3.
