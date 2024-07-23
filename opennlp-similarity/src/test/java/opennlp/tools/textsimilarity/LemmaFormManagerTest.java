/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.textsimilarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LemmaFormManagerTest {

  private final LemmaFormManager lemmaFormManager = new LemmaFormManager();

  @BeforeEach
  void setup() {
    assertNotNull(lemmaFormManager);
  }

  @Test
  void testMatches() {
    assertEquals(lemmaFormManager.matchLemmas(null, "loud", "loudness", "NN"),
        "loud");
    assertNull(lemmaFormManager.matchLemmas(null, "24", "12", "CD"));
    assertEquals(lemmaFormManager.matchLemmas(null, "loud", "loudly", "NN"),
        "loud");
    assertEquals(lemmaFormManager.matchLemmas(null, "!upgrade", "upgrade", "NN"),
        "!upgrade");
    assertNull(lemmaFormManager.matchLemmas(null, "!upgrade", "upgrades", "NN"));
    assertNull(lemmaFormManager.matchLemmas(null, "!upgrade", "get", "NN"));
  }

}
