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

import opennlp.tools.util.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public class WordIndexer {

  private final Map<Character, Integer> char2idx;
  private final Map<String, Integer> word2idx;

  public static final String UNK = "__UNK__";
  public static final String NUM = "__NUM__";

  private boolean lowerCase = false;
  private boolean allowUnk = true;
  private boolean allowNum = false;

  private final Pattern digitPattern = Pattern.compile("\\d+(,\\d+)*(\\.\\d+)?");

  public boolean isLowerCase() {
    return lowerCase;
  }

  public void setLowerCase(boolean lowerCase) {
    this.lowerCase = lowerCase;
  }

  public boolean isAllowUnk() {
    return allowUnk;
  }

  public void setAllowUnk(boolean allowUnk) {
    this.allowUnk = allowUnk;
  }

  public boolean isAllowNum() {
    return allowNum;
  }

  public void setAllowNum(boolean allowNum) {
    this.allowNum = allowNum;
  }

  public Pattern getDigitPattern() {
    return digitPattern;
  }

  public void setDigitPattern(Pattern digitPattern) {
    this.digitPattern = digitPattern;
  }

  public WordIndexer(InputStream config, InputStream vocabWords, InputStream vocabChars) throws IOException {
    this(vocabWords, vocabChars);
    Properties props = new Properties();
    if (config != null) {
      props.load(new InputStreamReader(config, "UTF8"));
      this.setLowerCase(Boolean.valueOf(props.getProperty("lower_case_embeddings")));
      this.setAllowUnk(Boolean.valueOf(props.getProperty("allow_unk")));
      this.setAllowNum(Boolean.valueOf(props.getProperty("allow_num")));
      this.setDigitPattern(Pattern.compile(props.getProperty("digit_pattern")));
    }
  }

  public WordIndexer(boolean lowerCaseTokens, boolean allowUnk, boolean allowNum, InputStream vocabWords, InputStream vocabChars) throws IOException {
    this(vocabWords, vocabChars);
    this.allowUnk = allowUnk;
    this.allowNum = allowNum;
    this.lowerCase = lowerCaseTokens;
  }

  public WordIndexer(InputStream vocabWords, InputStream vocabChars) throws IOException {
    this.word2idx = new HashMap<>();
    this.char2idx = new HashMap<>();

    readVocabWords(vocabWords);
    readVocacChars(vocabChars);
  }

  private void readVocacChars(InputStream vocabChars) throws IOException {
    try(BufferedReader in = new BufferedReader(new InputStreamReader(vocabChars, StandardCharsets.UTF_8))) {
      String ch;
      int idx = 0;
      while ((ch = in.readLine()) != null) {
        char2idx.put(ch.charAt(0), idx);
        idx += 1;
      }
    }
  }

  private void readVocabWords(InputStream vocabWords) throws IOException {
    try(BufferedReader in = new BufferedReader(new InputStreamReader(vocabWords, StandardCharsets.UTF_8))) {
      String word;
      int idx = 0;
      while ((word = in.readLine()) != null) {
        word2idx.put(word, idx);
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

    // get max token length
    int maxTokenLength = 0;
    for (int i = 0; i < sentences.length; i++) {
      if (sentences[i].length > maxTokenLength)
        maxTokenLength = sentences[i].length;
    }

    for (int i = 0; i < sentences.length; i++) {
      String[] tokens = sentences[i];

      int[][] sentcharIds = new int[maxTokenLength][];
      int[] sentwordIds = new int[maxTokenLength];

      for (int j=0; j < maxTokenLength; j++) {
        if (j < tokens.length) {
          Ids ids = apply(tokens[j]);

          sentcharIds[j] = Arrays.copyOf(ids.getChars(), ids.getChars().length);
          sentwordIds[j] = ids.getWord();
        } else {
          // pad
          sentcharIds[j] = new int[] {0};
          sentwordIds[j] = 0;
        }
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

    if (allowNum && digitPattern.matcher(word).find())
      word = NUM;

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

  public static class Ids {

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
