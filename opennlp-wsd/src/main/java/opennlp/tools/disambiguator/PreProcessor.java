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
import java.util.HashMap;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import opennlp.tools.util.Span;

public class PreProcessor {

  public PreProcessor() {
    super();
  }

  public static String[] split(String text) {
    return Loader.getSDetector().sentDetect(text);
  }

  public static String[] tokenize(String sentence) {
    return Loader.getTokenizer().tokenize(sentence);
  }

  public static String[] tag(String[] tokenizedSentence) {
    return Loader.getTagger().tag(tokenizedSentence);
  }

  public static String lemmatize(String word, String posTag) {
    return Loader.getLemmatizer().lemmatize(word, posTag);
  }

  public static boolean isName(String word) {
    Span nameSpans[] = Loader.getNameFinder().find(new String[] { word });
    return (nameSpans.length != 0);
  }

  public static ArrayList<WordPOS> getAllRelevantWords(String[] sentence) {

    ArrayList<WordPOS> relevantWords = new ArrayList<WordPOS>();

    String[] tags = tag(sentence);

    for (int i = 0; i < sentence.length; i++) {
      if (!Loader.getStopCache().containsKey(sentence[i])) {
        if (Loader.getRelvCache().containsKey(tags[i])) {
          relevantWords
              .add(new WordPOS(sentence[i], Constants.getPOS(tags[i])));
        }

      }
    }
    return relevantWords;
  }

  public static ArrayList<WordPOS> getAllRelevantWords(WordToDisambiguate word) {
    return getAllRelevantWords(word.getSentence());
  }

  public static ArrayList<WordPOS> getRelevantWords(WordToDisambiguate word,
      int winBackward, int winForward) {

    ArrayList<WordPOS> relevantWords = new ArrayList<WordPOS>();

    String[] sentence = word.getSentence();
    String[] tags = tag(sentence);

    int index = word.getWordIndex();

    for (int i = index - winBackward; i <= index + winForward; i++) {

      if (i >= 0 && i < sentence.length && i != index) {
        if (!Loader.getStopCache().containsKey(sentence[i])) {

          if (Loader.getRelvCache().containsKey(tags[i])) {
            relevantWords.add(new WordPOS(sentence[i], Constants
                .getPOS(tags[i])));
          }

        }
      }
    }
    return relevantWords;
  }

  /**
   * Stem a single word with WordNet dictionnary
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed list of words
   */
  public static List StemWordWithWordNet(WordPOS wordToStem) {
    if (!Loader.isInitialized() || wordToStem == null)
      return null;
    ArrayList<String> stems = new ArrayList();
    try {
      for (Object pos : POS.getAllPOS()) {
        stems.addAll(Loader.getMorph().lookupAllBaseForms((POS) pos,
            wordToStem.getWord()));
      }

      if (stems.size() > 0)
        return stems;
      else {
        return null;
      }

    } catch (JWNLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Stem a single word tries to look up the word in the stemCache HashMap If
   * the word is not found it is stemmed with WordNet and put into stemCache
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed word list, null means the word is incorrect
   */
  public static List Stem(WordPOS wordToStem) {

    // check if we already cached the stem map
    HashMap posMap = (HashMap) Loader.getStemCache().get(
        wordToStem.getPOS().getKey());

    // don't check words with digits in them
    if (containsNumbers(wordToStem.getWord())) {
      return null;
    }

    List stemList = (List) posMap.get(wordToStem.getWord());
    if (stemList != null) { // return it if we already cached it
      return stemList;

    } else { // unCached list try to stem it
      stemList = StemWordWithWordNet(wordToStem);
      if (stemList != null) {
        // word was recognized and stemmed with wordnet:
        // add it to cache and return the stemmed list
        posMap.put(wordToStem.getWord(), stemList);
        Loader.getStemCache().put(wordToStem.getPOS().getKey(), posMap);
        return stemList;
      } else { // could not be stemmed add it anyway (as incorrect with null
               // list)
        posMap.put(wordToStem.getWord(), null);
        Loader.getStemCache().put(wordToStem.getPOS().getKey(), posMap);
        return null;
      }
    }
  }

  public static boolean containsNumbers(String word) {
    // checks if the word is or contains a number
    return word.matches(".*[0-9].*");
  }

}
