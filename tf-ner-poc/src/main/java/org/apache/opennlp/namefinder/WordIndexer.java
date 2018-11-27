/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.namefinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import opennlp.tools.util.StringUtil;

public class WordIndexer {

  private final Map<Character, Integer> char2idx;
  private final Map<String, Integer> word2idx;

  public static String UNK = "$UNK$";
  public static String NUM = "$NUM$";

  private boolean lowerCase = false;
  private boolean allowUnk = false;

  private Pattern digitPattern = Pattern.compile("\\d+(,\\d+)*(\\.\\d+)?");

  public WordIndexer(InputStream vocabWords, InputStream vocabChars) throws IOException {
    this.word2idx = new HashMap<>();
    try(BufferedReader in = new BufferedReader(new InputStreamReader(vocabWords, "UTF8"))) {
      String word;
      int idx = 0;
      while ((word = in.readLine()) != null) {
        word2idx.put(word, idx);
        idx += 1;
      }
    }

    this.char2idx = new HashMap<>();
    try(BufferedReader in = new BufferedReader(new InputStreamReader(vocabChars, "UTF8"))) {
      String ch;
      int idx = 0;
      while ((ch = in.readLine()) != null) {
        char2idx.put(ch.charAt(0), idx);
        idx += 1;
      }
    }

  }

  public TokenIds toTokenIds(String[] tokens) {
    String[][] sentences = new String[1][];
    sentences[0] = tokens;
    return toTokenIds(sentences);
  }

  public TokenIds toTokenIds(String[][] sentences) {
    int[][][] charIds = new int[sentences.length][][];
    int[][] wordIds = new int[sentences.length][];

    for (int i = 0; i < sentences.length; i++) {
      String[] sentenceWords = sentences[i];

      int[][] sentcharIds = new int[sentenceWords.length][];
      int[] sentwordIds = new int[sentenceWords.length];

      for (int j=0; j < sentenceWords.length; j++) {
        Ids ids = apply(sentenceWords[j]);

        sentcharIds[j] = Arrays.copyOf(ids.getChars(), ids.getChars().length);
        sentwordIds[j] = ids.getWord();
      }

      charIds[i] = sentcharIds;
      wordIds[i] = sentwordIds;
    }

    return new TokenIds(charIds, wordIds);
  }


  private Ids apply(String word) {
    // 0. get chars of words
    int[] charIds = new int[word.length()];
    int skipChars = 0;
    for (int i = 0; i < word.length(); i++) {
      char ch = word.charAt(i);
      // ignore chars out of vocabulary
      if (char2idx.containsKey(ch))
        charIds[i - skipChars] = char2idx.get(ch);
      else
        skipChars += 1;
    }

    // 1. preprocess word
    if (lowerCase) {
      word = StringUtil.toLowerCase(word);
    }

    // if (digitPattern.matcher(word).find())
    //  word = NUM;

    // 2. get id of word
    Integer wordId;
    if (word2idx.containsKey(word)) {
      wordId = word2idx.get(word);
    } else {
      if (allowUnk)
        wordId = word2idx.get(UNK);
      else
        throw new RuntimeException("Unknown word '" + word + "' is not allowed.");
    }

    // 3. return tuple char ids, word id
    Ids tokenIds = new Ids();
    if (skipChars > 0) {
      tokenIds.setChars(Arrays.copyOf(charIds, charIds.length - skipChars));
    } else {
      tokenIds.setChars(charIds);
    }
    tokenIds.setWord(wordId);

    return tokenIds;
  }

  public class Ids {

    private int[] chars;
    private int word;

    public int[] getChars() {
      return chars;
    }

    public void setChars(int[] chars) {
      this.chars = chars;
    }

    public int getWord() {
      return word;
    }

    public void setWord(int word) {
      this.word = word;
    }
  }
}
