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
import opennlp.tools.disambiguator.ims.WTDIMS;
import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.lesk.LeskParameters;

import org.junit.Test;

public class LeskEvaluatorTest {

  static SensevalReader seReader = new SensevalReader();

  @Test
  public static void main(String[] args) {
    Constants.print("Evaluation Started");

    Lesk lesk = new Lesk();
    LeskParameters leskParams = new LeskParameters();
    leskParams.setLeskType(LeskParameters.LESK_TYPE.LESK_EXT_EXP_CTXT_WIN);
    lesk.setParams(leskParams);

    ArrayList<String> words = seReader.getSensevalWords();

    for (String word : words) {
      WSDEvaluator evaluator = new WSDEvaluator(lesk);

      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {

        ArrayList<WTDIMS> instances = getTestData(word);

        if (instances != null) {
          Constants.print("------------------" + word + "------------------");
          for (WordToDisambiguate instance : instances) {

            if (instance.getSenseIDs() != null
                && !instance.getSenseIDs().get(0).equals("null")) {
              evaluator.evaluateSample(instance);
            }
          }
          Constants.print(evaluator.toString());
        } else {
          Constants.print("null instances");
        }
      }

    }
  }

  protected static ArrayList<WTDIMS> getTestData(String wordTag) {

    ArrayList<WTDIMS> instances = new ArrayList<WTDIMS>();
    for (WordToDisambiguate wtd : seReader.getSensevalData(wordTag)) {
      WTDIMS wtdims = new WTDIMS(wtd);
      if (wtdims != null) {
        if (wtdims.getSenseIDs().get(0) != null
            && !wtdims.getSenseIDs().get(0).equalsIgnoreCase("U")) {
          instances.add(wtdims);
        }
      }
    }
    return instances;
  }

}
