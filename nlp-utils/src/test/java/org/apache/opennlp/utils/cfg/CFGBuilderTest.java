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

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Testcase for {@link org.apache.opennlp.utils.cfg.CFGBuilder}
 */
public class CFGBuilderTest {

  @Test
  public void testVoidBuild() throws Exception {
    CFGBuilder builder = CFGBuilder.createCFG();
    assertNotNull(builder);
    try {
      builder.build();
      fail("cannot build a grammar without V, ∑, R and S");
    } catch (AssertionError e) {
      // expected to fail
    }
  }

  @Test
  public void testBuildWithEmptySets() throws Exception {
    CFGBuilder builder = CFGBuilder.createCFG().
            withNonTerminals(Collections.<String>emptyList()).
            withTerminals(Collections.<String>emptyList()).
            withRules(Collections.<Rule>emptyList()).
            withStartSymbol("");
    try {
      assertNotNull(builder.build());
      fail("cannot build a grammar whose start symbol doesn't belong to the non terminals symbols set");
    } catch (AssertionError e) {
      // expected to fail
    }
  }

  @Test
  public void testBuildWithMinimalGrammarSettings() throws Exception {
    CFGBuilder builder = CFGBuilder.createCFG().
            withNonTerminals(Arrays.asList("")).
            withTerminals(Collections.<String>emptyList()).
            withRules(Collections.<Rule>emptyList()).
            withStartSymbol("");
    assertNotNull(builder.build());
  }

  @Test
  public void testBuildWithMinimalGrammarSettingsAndRandomExpansion() throws Exception {
    CFGBuilder builder = CFGBuilder.createCFG().
            withNonTerminals(Arrays.asList("")).
            withTerminals(Collections.<String>emptyList()).
            withRules(Collections.<Rule>emptyList()).
            withRandomExpansion(true).
            withStartSymbol("");
    assertNotNull(builder.build());
  }
}
