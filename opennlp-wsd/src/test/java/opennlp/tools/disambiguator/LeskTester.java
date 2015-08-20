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
import java.util.List;

import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.lesk.LeskParameters;
import opennlp.tools.disambiguator.lesk.LeskParameters.LESK_TYPE;

import org.junit.Test;

public class LeskTester {
  @Test
  public static void main(String[] args) {

    Lesk lesk = new Lesk();
    LeskParameters params = new LeskParameters();
    params.setLeskType(LESK_TYPE.LESK_EXT);
    boolean a[] = { true, true, true, true, true, true, true, true, true, true };
    params.setFeatures(a);
    lesk.setParams(params);
    String modelsDir = "src\\test\\resources\\models\\";
    WSDHelper.loadTokenizer(modelsDir + "en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir + "en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir + "en-pos-maxent.bin");

    String test1 = "I went to the bank to deposit money.";
    String[] sentence1 = WSDHelper.getTokenizer().tokenize(test1);
    int targetWordIndex1 = 5;
    String[] tags1 = WSDHelper.getTagger().tag(sentence1);
    List<String> tempLemmas1 = new ArrayList<String>();
    for (int i = 0; i < sentence1.length; i++) {
      String lemma = WSDHelper.getLemmatizer()
          .lemmatize(sentence1[i], tags1[i]);
      tempLemmas1.add(lemma);
    }
    String[] lemmas1 = tempLemmas1.toArray(new String[tempLemmas1.size()]);
    String[] results1 = lesk.disambiguate(sentence1, tags1, lemmas1,
        targetWordIndex1);
    WSDHelper.print(results1);
    WSDHelper.printResults(lesk, results1);

    WSDHelper.print("----------------------------------------");

    String test2 = "it was a strong argument that his hypothesis was true";
    String[] sentence2 = WSDHelper.getTokenizer().tokenize(test2);
    int targetWordIndex2 = 4;
    String[] tags2 = WSDHelper.getTagger().tag(sentence2);
    List<String> tempLemmas2 = new ArrayList<String>();
    for (int i = 0; i < sentence1.length; i++) {
      String lemma = WSDHelper.getLemmatizer()
          .lemmatize(sentence2[i], tags2[i]);
      tempLemmas2.add(lemma);
    }
    String[] lemmas2 = tempLemmas2.toArray(new String[tempLemmas2.size()]);
    String[] results2 = lesk.disambiguate(sentence2, tags2, lemmas2,
        targetWordIndex2);
    WSDHelper.print(results2);
    WSDHelper.printResults(lesk, results2);
    WSDHelper.print("----------------------------------------");

    String test3 = "the component was highly radioactive to the point that it has been activated the second it touched water";
    String[] sentence3 = WSDHelper.getTokenizer().tokenize(test3);
    int targetWordIndex3 = 3;
    String[] tags3 = WSDHelper.getTagger().tag(sentence3);
    List<String> tempLemmas3 = new ArrayList<String>();
    for (int i = 0; i < sentence3.length; i++) {
      String lemma = WSDHelper.getLemmatizer()
          .lemmatize(sentence3[i], tags3[i]);
      tempLemmas3.add(lemma);
    }
    String[] lemmas3 = tempLemmas3.toArray(new String[tempLemmas3.size()]);
    String[] results3 = lesk.disambiguate(sentence3, tags3, lemmas3,
        targetWordIndex3);
    WSDHelper.print(results3);
    WSDHelper.printResults(lesk, results3);
    WSDHelper.print("----------------------------------------");
  }

}