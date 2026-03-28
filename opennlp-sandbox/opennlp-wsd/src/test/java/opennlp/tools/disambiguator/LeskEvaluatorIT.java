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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LeskEvaluatorIT extends AbstractEvaluatorTest {

  private static final Logger LOG = LoggerFactory.getLogger(LeskEvaluatorIT.class);

  private Lesk lesk;

  @BeforeAll
  public static void initResources() {
    assertNotNull(sampleTestWordMapping);
    assertFalse(sampleTestWordMapping.isEmpty());
  }

  @BeforeEach
  public void setup() {
    LeskParameters leskParams = new LeskParameters();
    boolean[] a = {true, true, true, true, true, false, false, false, false, false};
    leskParams.setFeatures(a);
    leskParams.setType(LeskParameters.LeskType.LESK_EXT_CTXT);
    lesk = new Lesk(leskParams);
  }

  @Test
  @Disabled // TODO OPENNLP-827 enable this and make it execute faster: -> "isStemEquivalent"
  void testEvaluation() {
    sampleTestWordMapping.keySet().forEach(word -> {
      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {
        WSDEvaluator evaluator = new WSDEvaluator(lesk);
        List<WSDSample> instances = sampleTestWordMapping.get(word);
        if (instances != null && instances.size() > 1) {
          StringBuilder sb = new StringBuilder();
          sb.append("------------------").append(word).append("------------------").append('\n');
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null && !instance.getSenseIDs()[0].equals("null")) {
              evaluator.evaluateSample(instance);
            }
          }
          sb.append(evaluator);
          LOG.info(sb.toString());
        } else {
          LOG.debug("null instances");
        }
      }
    });
  }

}
