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

import opennlp.tools.disambiguator.ims.IMS;
import opennlp.tools.util.Span;

/**
 * This is a typical example of how to call the disambiguation function in the
 * IMS class.
 * <ul>
 * <li>In the 2 first examples, the training data exist, therefore the IMS
 * approach is used.</li>
 * <li>In the 3rd example, the training data for the word to disambiguate are
 * absent, therefore the Most Frequent Sents (MFS) is returend</li>
 * </ul>
 */
public class IMSTester {

  public static void main(String[] args) {
    
    String modelsDir = "src\\test\\resources\\models\\";
    WSDHelper.loadTokenizer(modelsDir+"en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir+"en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir+"en-pos-maxent.bin");

    IMS ims = new IMS();

    
    /**
     * This is how to make the context for one-word-disambiguation using IMS
     */
    String test1 = "We need to discuss important topic, please write to me soon.";
    String[] sentence1 = WSDHelper.getTokenizer().tokenize(test1);
    String[] tags1 = WSDHelper.getTagger().tag(sentence1);
    List<String> tempLemmas1 = new ArrayList<String>();
    for (int i = 0; i < sentence1.length; i++) {
      String lemma = WSDHelper.getLemmatizer().lemmatize(sentence1[i], tags1[i]);
      tempLemmas1.add(lemma);
    }
    String[] lemmas1 = tempLemmas1.toArray(new String[tempLemmas1.size()]);

    // output
    String[] senses1 = ims.disambiguate(sentence1, tags1, lemmas1, 8);
    System.out.print(lemmas1[8] + " :\t");
    WSDHelper.print(senses1);
    WSDHelper.print("*****************************");

    
    /**
     * This is how to make the context for disambiguation of span of words
     */
    String test2 = "The component was highly radioactive to the point that"
        + " it has been activated the second it touched water";
    String[] sentence2 = WSDHelper.getTokenizer().tokenize(test2);
    String[] tags2 = WSDHelper.getTagger().tag(sentence2);
    List<String> tempLemmas2 = new ArrayList<String>();
    for (int i = 0; i < sentence2.length; i++) {
      String lemma = WSDHelper.getLemmatizer().lemmatize(sentence2[i], tags2[i]);
      tempLemmas2.add(lemma);
    }
    String[] lemmas2 = tempLemmas2.toArray(new String[tempLemmas2.size()]);
    Span span = new Span(3, 7);

    // output
    List<String[]> senses2 = ims.disambiguate(sentence2, tags2, lemmas2, span);
    for (int i = span.getStart(); i < span.getEnd() + 1; i++) {
      String[] senses = senses2.get(i-span.getStart());
      System.out.print(lemmas2[i] + " :\t");
      WSDHelper.print(senses);
      WSDHelper.print("----------");
    }

    WSDHelper.print("*****************************");

    
    /**
     * This is how to make the context for all-words-disambiguation
     */
    String test3 = "The summer almost over and I not to the beach even once";
    String[] sentence3 = WSDHelper.getTokenizer().tokenize(test3);
    String[] tags3 = WSDHelper.getTagger().tag(sentence3);
    List<String> tempLemmas3 = new ArrayList<String>();
    for (int i = 0; i < sentence3.length; i++) {
      String lemma = WSDHelper.getLemmatizer().lemmatize(sentence3[i], tags3[i]);
      tempLemmas3.add(lemma);
    }
    String[] lemmas3 = tempLemmas3.toArray(new String[tempLemmas3.size()]);

    // output
    List<String[]> senses3 = ims.disambiguate(sentence3, tags3, lemmas3);
    for (int i = 0; i < sentence3.length; i++) {
      String[] senses = senses3.get(i);
      System.out.print(lemmas3[i] + " :\t");
      WSDHelper.print(senses);
      WSDHelper.print("----------");
    }
  }

}
