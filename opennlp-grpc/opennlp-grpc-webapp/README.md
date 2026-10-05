<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
-->

# Apache OpenNLP gRPC Web Application

This module packages a standalone HTTP host for `WebUiExtension` providers and a small protobuf
JSON gateway to a separately running OpenNLP gRPC service. The shaded JAR includes the default
TypeScript interface.

Build the application and its dependencies:

```shell
mvn -pl opennlp-grpc/opennlp-grpc-webapp -am verify -Dopennlp.forkCount=1
```

Start the gRPC service first, then run:

```shell
java -jar opennlp-grpc/opennlp-grpc-webapp/target/opennlp-grpc-webapp-3.0.0-SNAPSHOT.jar
```

The defaults are:

- HTTP interface: `http://127.0.0.1:7072/`
- gRPC target: `127.0.0.1:7071`
- per-RPC deadline: 30 seconds
- training and catalog-install deadline: 30 minutes
- maximum JSON request body: 1 MiB

The host exposes:

- `GET /healthz`
- `GET /api/v1/service-info`
- `GET /api/v1/model-bundles`
- `GET /api/v1/ui-extensions`
- `GET /api/v1/search-indexes`
- `GET /api/v1/search-providers`
- `GET /api/v1/model-catalog`
- `GET /api/v1/installed-models`
- `POST /api/v1/analyze`
- `POST /api/v1/analyze-progressive` (NDJSON layer-event stream)
- `POST /api/v1/search`
- `POST /api/v1/install-model` (NDJSON progress stream)
- workspace index, alias, and collection lifecycle endpoints
- dictionary import, vocabulary learning and download, and static-model lifecycle endpoints

The service-info, model-bundle, analysis, and search endpoints use protobuf JSON for the gRPC
message types. Analysis therefore retains the full `OpenNlpDocument` shape and typed annotation
layers. The progressive endpoint writes one `AnalyzeDocumentEvent` per NDJSON line and flushes
each line immediately. Its final event carries the same canonical response shape as unary
analysis. Closing the HTTP response cancels the underlying gRPC stream. Search requests use a
document-shaped query. Responses retain each referenced source
document once, plus compact hits with authoritative spans, indexed text, scores, and index
provenance. The host-specific UI extension
endpoint returns the validated provider ID, title, and mount path for navigation. HTTP error
bodies contain a stable gRPC status code and a message.

Use `--help` for all options. The HTTP listener accepts loopback addresses by default. A
non-loopback bind also requires `--allow-remote`, so deployment behind an authenticated TLS
reverse proxy is an explicit operator choice. The gateway has no authentication or authorization
of its own and exposes state-changing operations, including artifact, model, index, alias, and
collection deletion. Keep it on loopback or place it behind authentication and transport security.
Use `--no-grpc-plaintext` when the gRPC target uses TLS. Process managers and tests can combine
`--http-port 0` with `--bound-port-file PATH`. The
webapp binds and retains the operating-system-assigned socket before it creates `PATH` with the
decimal port and a trailing newline. The path must not already exist. This avoids the
reserve-close-rebind race that occurs when a parent process probes for a free port.

Additional extension JARs can be placed on the application classpath. Each provider is discovered
with Java ServiceLoader and mounted at its typed `WebUiMountPath`. Extensions contribute static
resources only and cannot install handlers into the host. The extension catalog is ordered by
mount path so clients receive stable navigation.
