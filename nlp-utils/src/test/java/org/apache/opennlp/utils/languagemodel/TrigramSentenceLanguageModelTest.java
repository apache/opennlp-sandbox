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
package org.apache.opennlp.utils.languagemodel;

import java.util.Collections;
import org.apache.opennlp.utils.TestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.languagemodel.TrigramSentenceLanguageModel}
 */
public class TrigramSentenceLanguageModelTest {

  @Test
  public void testEmptyVocabularyProbability() throws Exception {
    TrigramSentenceLanguageModel<String> model = new TrigramSentenceLanguageModel<String>();
    assertEquals("probability with an empty vocabulary is always 0", 0d, model.calculateProbability(Collections.<String[]>emptySet(),
            new String[0]), 0d);
    assertEquals("probability with an empty vocabulary is always 0", 0d, model.calculateProbability(Collections.<String[]>emptySet(),
            new String[]{"1", "2", "3"}), 0d);
  }

  @Test
  public void testRandomVocabularyAndSentence() throws Exception {
    TrigramSentenceLanguageModel<String> model = new TrigramSentenceLanguageModel<String>();
    double probability = model.calculateProbability(TestUtils.generateRandomVocabulary(), TestUtils.generateRandomSentence());
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);
  }

}
