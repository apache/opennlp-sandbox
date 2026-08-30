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
package org.apache.opennlp.grpc.embedding;

import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory;

/** ServiceLoader test backend that can fail after earlier factories created providers. */
public final class FailingEmbeddingBackendFactory implements EmbeddingBackendFactory {

  static final String KEY_FAIL = "test.embedder.zz-failing.fail";

  @Override
  public String backendId() {
    return "zz-failing";
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    if (Boolean.parseBoolean(configuration.get(KEY_FAIL))) {
      throw AnalysisException.invalidArgument("deliberate test backend failure");
    }
    return new StubEmbeddingProvider("zz-failing", Map.of(), null);
  }
}
