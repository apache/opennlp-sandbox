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
package org.apache.opennlp.grpc.processor.basic;

import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the additive ranked language detection contract: the request opts in through
 * {@code AnalysisOptions.ranked_language_count} and the response carries the ranked
 * predictions, best first, as {@code OpenNlpDocument.ranked_languages}.
 */
class RankedLanguageWireContractTest {

  @Test
  void analysisOptionsCarryTheRankedLanguageCount() {
    final FieldDescriptor field =
        AnalysisOptions.getDescriptor().findFieldByName("ranked_language_count");
    assertNotNull(field, "AnalysisOptions.ranked_language_count is missing");
    assertEquals(11, field.getNumber());
    assertEquals(FieldDescriptor.Type.UINT32, field.getType());
  }

  @Test
  void documentsCarryRankedLanguagePredictions() {
    final FieldDescriptor field =
        OpenNlpDocument.getDescriptor().findFieldByName("ranked_languages");
    assertNotNull(field, "OpenNlpDocument.ranked_languages is missing");
    assertEquals(15, field.getNumber());
    assertTrue(field.isRepeated());
    assertEquals("LanguageScore", field.getMessageType().getName());
    assertNotNull(field.getMessageType().findFieldByName("language"));
    assertNotNull(field.getMessageType().findFieldByName("confidence"));
  }
}
