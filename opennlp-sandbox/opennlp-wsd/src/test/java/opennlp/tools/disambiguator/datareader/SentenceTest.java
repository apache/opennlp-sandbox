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

package opennlp.tools.disambiguator.datareader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SentenceTest {

  @Test
  void testCreate() {
    Sentence s = new Sentence(1, 2);
    assertNotNull(s.getIwords());
    assertEquals(1, s.getPnum());
    assertEquals(2, s.getSnum());
    assertTrue(s.getIwords().isEmpty());
    Word w = new Word(1, 1, 1, Word.Type.WORD, "cats", "cmd", "NN");
    s.addWord(w);
    assertFalse(s.getIwords().isEmpty());
  }

  @Test
  void testToString() {
    Sentence s = new Sentence(1, 2);
    Word w = new Word(1, 1, 1, Word.Type.WORD, "cats", "cmd", "NN");
    s.addWord(w);
    String asString = s.toString();
    assertNotNull(asString);
    assertFalse(asString.isEmpty());
  }
}
