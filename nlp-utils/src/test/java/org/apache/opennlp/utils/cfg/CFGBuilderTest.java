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
package org.apache.opennlp.utils.cfg;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testcase for {@link org.apache.opennlp.utils.cfg.CFGBuilder}
 */
class CFGBuilderTest {

  @Test
  void testVoidBuild() {
    CFGBuilder builder = CFGBuilder.createCFG();
    assertNotNull(builder);
    assertThrows(AssertionError.class, builder::build);
  }

  @Test
  void testBuildWithEmptySets() {
    CFGBuilder builder = CFGBuilder.createCFG().
        withNonTerminals(Collections.emptyList()).
        withTerminals(Collections.emptyList()).
        withRules(Collections.emptyList()).
        withStartSymbol("");

    assertThrows(AssertionError.class, builder::build);
  }

  @Test
  void testBuildWithMinimalGrammarSettings() {
    CFGBuilder builder = CFGBuilder.createCFG().
        withNonTerminals(List.of("")).
        withTerminals(Collections.emptyList()).
        withRules(Collections.emptyList()).
        withStartSymbol("");
    assertNotNull(builder.build());
  }

  @Test
  void testBuildWithMinimalGrammarSettingsAndRandomExpansion() {
    CFGBuilder builder = CFGBuilder.createCFG().
        withNonTerminals(List.of("")).
        withTerminals(Collections.emptyList()).
        withRules(Collections.emptyList()).
        withRandomExpansion(true).
        withStartSymbol("");
    assertNotNull(builder.build());
  }
}
