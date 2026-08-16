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
- maximum JSON request body: 1 MiB

The host exposes:

- `GET /healthz`
- `GET /api/v1/service-info`
- `GET /api/v1/model-bundles`
- `POST /api/v1/analyze`

The three API responses and the analysis request use protobuf JSON for the existing gRPC message
types. Analysis therefore retains the full `OpenNlpDocument` shape and typed annotation layers.
HTTP error bodies contain a stable gRPC status code and a message.

Use `--help` for all options. The HTTP listener accepts loopback addresses by default. A
non-loopback bind also requires `--allow-remote`, so deployment behind an authenticated TLS
reverse proxy is an explicit operator choice. Use `--no-grpc-plaintext` when the gRPC target uses
TLS.

Additional extension JARs can be placed on the application classpath. Each provider is discovered
with Java ServiceLoader and mounted at its typed `WebUiMountPath`. Extensions contribute static
resources only and cannot install handlers into the host.

