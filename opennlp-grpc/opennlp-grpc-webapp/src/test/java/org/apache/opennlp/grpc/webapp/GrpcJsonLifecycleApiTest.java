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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.webapp;

import java.nio.charset.StandardCharsets;

import io.grpc.Status;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lifecycle routes hand the service's precondition failures to the browser intact. */
class GrpcJsonLifecycleApiTest {

  @Test
  void aNonPersistentProviderRefusesToSealWithAPreconditionFailure() {
    final SearchRpc refusing = new EmptySearchRpc() {
      @Override
      public SealIndexResponse seal(SealIndexRequest request) {
        throw Status.FAILED_PRECONDITION.withDescription(
            "Search provider instance 'exact_volatile' is not persistent").asRuntimeException();
      }
    };
    final GrpcJsonApi api = new GrpcJsonApi(new EmptyAnalysisRpc(), refusing,
        new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    final WebHttpResponse response = api.handle("POST", "/api/v1/seal-index",
        "{\"indexId\":\"live-1\"}".getBytes(StandardCharsets.UTF_8));

    assertEquals(412, response.status());
    final String body = new String(response.body(), StandardCharsets.UTF_8);
    assertTrue(body.contains("FAILED_PRECONDITION"), body);
    assertTrue(body.contains("is not persistent"), body);
  }
}
