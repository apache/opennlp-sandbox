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
import opennlp.tools.disambiguator.mfs.MFS;
import opennlp.tools.disambiguator.mfs.MFSParameters;

import org.junit.Test;

public class MFSEvaluatorTest {

  static SensevalReader seReader = new SensevalReader();

  @Test
  public static void main(String[] args) {
    Constants.print("Evaluation Started");

    MFS mfs = new MFS();
    WSDParameters.isStemCompare = true;

    ArrayList<String> words = seReader.getSensevalWords();

    for (String word : words) {
      WSDEvaluator evaluator = new WSDEvaluator(mfs);

      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {

        ArrayList<WSDSample> instances = getTestData(word);

        if (instances != null) {
          Constants.print("------------------" + word + "------------------");
          for (WSDSample instance : instances) {
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

  /**
   * For a specific word, return the Semeval3 corresponding instances in form of
   * {@link WSDSample}
   * 
   * @param wordTag
   *          the word of which the instances are to be collected. wordTag has
   *          to be in the format "word.POS" (e.g., "activate.v", "smart.a",
   *          etc.)
   * @return list of {@link WSDSample} instances of the wordTag
   */
  protected static ArrayList<WSDSample> getTestData(String wordTag) {

    ArrayList<WSDSample> instances = new ArrayList<WSDSample>();
    for (WordToDisambiguate wtd : seReader.getSensevalData(wordTag)) {

      String targetLemma = Loader.getLemmatizer().lemmatize(wtd.getWord(),
          wtd.getPosTag());

      WSDSample sample = new WSDSample(wtd.getSentence(), wtd.getPosTags(),
          wtd.getWordIndex(), targetLemma);
      sample.setSenseIDs(wtd.getSenseIDs());
      
      if (sample != null) {
        if (sample.getSenseIDs().get(0) != null
            && !sample.getSenseIDs().get(0).equalsIgnoreCase("U")) {
          instances.add(sample);
        }
      }

    }

    return instances;
  }

}
