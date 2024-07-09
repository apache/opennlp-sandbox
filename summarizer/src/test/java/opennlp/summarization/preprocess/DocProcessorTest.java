/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization.preprocess;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.summarization.Sentence;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocProcessorTest {

  private static DefaultDocProcessor dp;

  @BeforeAll
  static void initEnv() {
    dp = new DefaultDocProcessor(DocProcessorTest.class.getResourceAsStream("/en-sent.bin"));
  }

  @Test
  void testGetSentencesFromStr() {
    String sent = "This is a sentence, with some punctuations; to test if the sentence breaker can handle it! Is every thing working OK ? Yes.";
    List<Sentence> doc = dp.getSentencesFromStr(sent);
    //dp.docToString(fileName);
    assertEquals(doc.size(), 3);
  }

}
