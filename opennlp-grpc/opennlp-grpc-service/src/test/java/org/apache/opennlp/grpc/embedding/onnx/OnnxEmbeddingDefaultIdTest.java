/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.embedding.onnx;

import java.util.Map;

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * model.embedder.default_id is shared across all embedding engines. An id that another
 * engine serves must not fail this engine's startup: the provider simply has no default
 * of its own, and the composite validates the id against the union of engines.
 */
class OnnxEmbeddingDefaultIdTest {

    @Test
    void foreignDefaultIdIsIgnoredNotRejected() {
        final EmbeddingProvider provider = assertDoesNotThrow(() ->
                new OnnxEmbeddingBackendFactory().create(
                        Map.of("model.embedder.default_id", "served-by-another-engine")));

        assertFalse(provider.isAvailable());
        assertNull(provider.resolveModelId(null));
    }
}
