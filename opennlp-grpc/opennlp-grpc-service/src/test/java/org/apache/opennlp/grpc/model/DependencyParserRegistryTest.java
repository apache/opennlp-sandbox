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
package org.apache.opennlp.grpc.model;

import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyParserRegistryTest {

  @Test
  void emptyConfigurationProducesUnavailableRegistry() {
    final DependencyParserRegistry registry = DependencyParserRegistry.create(Map.of());

    assertFalse(registry.isAvailable());
    final AnalysisException error = assertThrows(
        AnalysisException.class, () -> registry.resolveModelId(null));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
    assertTrue(error.getMessage().contains("No dependency parser"));
  }

  @Test
  void dottedModelIdIsRejectedNamingTheKey() {
    final String key = "model.dependency_parser.my.model.path";

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> DependencyParserRegistry.create(Map.of(key, "/tmp/whatever.model")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(key));
  }

  @Test
  void defaultMustNameConfiguredModel() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> DependencyParserRegistry.create(Map.of(
            DependencyParserRegistry.KEY_DEFAULT_ID, "missing")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("unconfigured dependency parser"));
  }
}
