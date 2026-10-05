<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0.
-->

# Experimental GraalVM native image

This directory builds the gRPC server and the JSON gateway as GraalVM native
binaries and packages them on the CUDA runtime base. Verified end to end on an
RTX 4080 SUPER: the hardened container goes from `docker start` to a healthy
gateway in about 2 seconds (the JVM pair needs about 35), runs the classic
English pipeline plus MiniLM embeddings on the ONNX Runtime CUDA execution
provider, and passes the browser end-to-end suite. Total container memory
including the CUDA session is about 440 MiB. Binary sizes: server 103 MB,
gateway 75 MB.

## Build

```bash
mvn clean install -Dgpu                # the CUDA server flavor
bash docker/native/build-native.sh     # gateway ~1 min, server ~45 min
docker build -f docker/native/Dockerfile.native -t opennlp-grpc-demo:native .
```

Run it exactly like the JVM GPU image (same hardened flags, `--gpus all`), but
add the classic model paths described below to `server.properties`.

## What the curated metadata encodes

`config/{server,webapp}/reachability-metadata.json` started as
`native-image-agent` traces of a live workload (startup with the CUDA
embedder, the browser e2e suite, an endpoint sweep, analyze with embeddings)
and were then edited by hand:

- **ONNX Runtime natives stripped from resources.** Embedding them would put
  ~600 MB into each binary. The runtime image unpacks them from the server jar
  instead and the entrypoint passes `-Donnxruntime.native.path=/opt/onnxruntime`.
- **Model resources added.** The bundled `*.bin` models and `opennlp/**` data
  files are classpath-scanned at run time, which the agent cannot see.
- **Every generated protobuf class registered for reflection** (gateway).
  Protobuf JSON printing reflects over message getters, so one untraced field
  would fail at run time with `MissingReflectionRegistrationError`. The server
  speaks binary protobuf and keeps the leaner traced set; extend it the same
  way if an unexercised RPC path ever reports a missing registration.

## Known constraints

- **Backends are linked at build time.** A native image is a closed world:
  the ServiceLoader backends cannot be dropped onto a classpath at run time.
  The server binary is built from the `opennlp-grpc-server-all` assembly, so
  it contains every in-tree add-on including the `cuda`/`onnx` engines; add
  further backend jars to the `-cp` in `build-native.sh` to link others in.
- **Classpath model discovery does not work.** `ModelBundleCache` resolves
  bundled models by scanning the code-source jar, and a native binary has
  none, so the classic English pipeline and the language detector must be
  configured explicitly:

  ```properties
  model.sentence_detector.path=/srv/opennlp/models/classic/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin
  model.tokenizer.path=/srv/opennlp/models/classic/opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin
  model.pos_tagger.path=/srv/opennlp/models/classic/opennlp-en-ud-ewt-pos-1.3-2.5.4.bin
  model.lemmatizer.path=/srv/opennlp/models/classic/opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin
  model.language_detector.path=/srv/opennlp/models/classic/langdetect-183.bin
  ```

  (Extract the files from the shaded server jar root, e.g.
  `unzip opennlp-grpc-server-*.jar '*.bin'`.)
- **Logging is slf4j-simple, not log4j2.** netty's bundled native-image
  metadata initializes its logging at build time, and log4j-core cannot be
  initialized then. `log4j2.xml` is therefore ignored by the binaries;
  configure levels with `-Dorg.slf4j.simpleLogger.defaultLogLevel=...`.
- **The class-initializer simulation limits in `build-native.sh` are
  required.** Without them GraalVM 25 unrolls one enormous static initializer
  in the shaded jar and the points-to analysis appears to hang for hours.
