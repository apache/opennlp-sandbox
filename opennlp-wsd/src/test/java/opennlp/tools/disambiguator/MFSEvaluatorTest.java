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

package opennlp.tools.disambiguator;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MFSEvaluatorTest extends AbstractEvaluatorTest {

  private static List<String> words;

  private MFS mfs;

  @BeforeAll
  public static void initResources() {
    words = seReader.getSensevalWords();
    assertNotNull(words);
    assertFalse(words.isEmpty());
  }

  @BeforeEach
  public void setup() {
    mfs = new MFS();
  }

  @Test
  void testEvaluation() {
    WSDHelper.print("Evaluation Started");

    for (String word : words) {
      WSDEvaluator evaluator = new WSDEvaluator(mfs);

      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {

        List<WSDSample> instances = seReader.getSensevalData(word);

        if (instances != null && instances.size() > 1) {
          WSDHelper.print("------------------" + word + "------------------");
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null
                && !instance.getSenseIDs()[0].equals("null")) {
              evaluator.evaluateSample(instance);
            }
          }
          WSDHelper.print(evaluator.toString());
        } else {
          WSDHelper.print("null instances");
        }
      }
    }
  }

}
