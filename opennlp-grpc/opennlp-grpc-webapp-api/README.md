<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
-->

# Apache OpenNLP gRPC Webapp API

This module defines the ServiceLoader contract for browser interface extensions. A provider
supplies validated metadata and static classpath resources. The web application host retains
control of HTTP routing, API endpoints, security headers, request limits, and lifecycle.

Implement `WebUiExtension` and register the implementation in:

```text
META-INF/services/org.apache.opennlp.grpc.webapp.spi.WebUiExtension
```

The provider descriptor uses dedicated value types for the stable extension id, HTTP mount path,
and classpath resource root:

```java
private static final WebUiExtensionDescriptor DESCRIPTOR = new WebUiExtensionDescriptor(
    new WebUiExtensionId("org.example.analysis-ui"),
    "Analysis UI",
    new WebUiMountPath("/analysis"),
    new WebUiClasspathResource("/META-INF/opennlp-grpc-ui/analysis"));
```

The resource root must contain an `index.html`. References from the page should remain below the
extension mount. The host rejects duplicate ids, duplicate mount paths, path traversal, encoded
separators, and mounts in its reserved `/api` namespace.

