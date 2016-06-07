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

import java.util.ArrayList;

import opennlp.tools.disambiguator.datareader.SensevalReader;

import org.junit.Test;

public class LeskEvaluatorTest {

  static SensevalReader seReader = new SensevalReader();

  @Test
  public static void main(String[] args) {
    WSDHelper.print("Evaluation Started");
    String modelsDir = "src/test/resources/models/";
    WSDHelper.loadTokenizer(modelsDir + "en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir + "en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir + "en-pos-maxent.bin");
    Lesk lesk = new Lesk();
    LeskParameters leskParams = new LeskParameters();
    boolean a[] = { true, true, true, true, true, false, false, false, false,
        false };
    leskParams.setFeatures(a);
    leskParams.setLeskType(LeskParameters.LESK_TYPE.LESK_EXT_CTXT);
    lesk.setParams(leskParams);

    ArrayList<String> words = seReader.getSensevalWords();

    for (String word : words) {
      WSDEvaluator evaluator = new WSDEvaluator(lesk);

      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {

        ArrayList<WSDSample> instances = seReader.getSensevalData(word);
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
