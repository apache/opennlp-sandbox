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

package org.apache.opennlp.tf.guillaumegenthial;

import org.tensorflow.Tensor;

import java.util.Arrays;

public class FeedDictionary {

  static int PAD_VALUE = 0;


  private final float dropout;
  private final int[][][] charIds;
  private final int[][] wordLengths;
  private final int[][] wordIds;
  private final int[] sentenceLengths;
  private final int maxSentenceLength;
  private final int maxCharLength;
  private final int numberOfSentences;

  public float getDropout() {
    return dropout;
  }

  public int[][][] getCharIds() {
    return charIds;
  }

  public int[][] getWordLengths() {
    return wordLengths;
  }

  public int[][] getWordIds() {
    return wordIds;
  }

  public int[] getSentenceLengths() {
    return sentenceLengths;
  }

  public int getMaxSentenceLength() {
    return maxSentenceLength;
  }

  public int getMaxCharLength() {
    return maxCharLength;
  }

  public int getNumberOfSentences() {
    return numberOfSentences;
  }

  public Tensor<Integer> getSentenceLengthsTensor() {
    return Tensor.create(sentenceLengths, Integer.class);
  }

  public Tensor<Float> getDropoutTensor() {
    return Tensor.create(dropout, Float.class);
  }

  public Tensor<Integer> getCharIdsTensor() {
    return Tensor.create(charIds, Integer.class);
  }

  public Tensor<Integer> getWordLengthsTensor() {
    return Tensor.create(wordLengths, Integer.class);
  }

  public Tensor<Integer> getWordIdsTensor() {
    return Tensor.create(wordIds, Integer.class);
  }

  private FeedDictionary(final float dropout,
                         final int[][][] charIds,
                         final int[][] wordLengths,
                         final int[][] wordIds,
                         final int[] sentenceLengths,
                         final int maxSentenceLength,
                         final int maxCharLength,
                         final int numberOfSentences) {

    this.dropout = dropout;
    this.charIds = charIds;
    this.wordLengths = wordLengths;
    this.wordIds = wordIds;
    this.sentenceLengths = sentenceLengths;
    this.maxSentenceLength = maxSentenceLength;
    this.maxCharLength = maxCharLength;
    this.numberOfSentences = numberOfSentences;

  }

  // multi sentences
  public static FeedDictionary create(TokenIds sentences) {

    int numberOfSentences = sentences.getWordIds().length;

    int[][][] charIds = new int[numberOfSentences][][];
    int[][] wordLengths = new int[numberOfSentences][];

    int maxSentenceLength = Arrays.stream(sentences.getWordIds()).map(s -> s.length).reduce(Integer::max).get();
    Padded paddedSentences = padArrays(sentences.getWordIds(), maxSentenceLength);
    int[][] wordIds = paddedSentences.ids;
    int[] sentenceLengths = paddedSentences.lengths;

    int maxCharLength = Arrays.stream(sentences.getCharIds()).flatMap(s -> Arrays.stream(s).map(c -> c.length)).reduce(Integer::max).get();
    for (int i=0; i < numberOfSentences; i++) {
      Padded paddedWords = padArrays(sentences.getCharIds()[i], maxCharLength);
      charIds[i] = paddedWords.ids;
      wordLengths[i] = paddedWords.lengths;
    }

    return new FeedDictionary(1.0f, charIds, wordLengths, wordIds, sentenceLengths, maxSentenceLength, maxCharLength, numberOfSentences);

  }

  private static Padded padArrays(int[][] ids, int length) {

    int[][] paddedIds = new int[ids.length][length];
    int[] lengths = new int[ids.length];

    for (int i = 0; i < ids.length; i++) {
      int[] src = ids[i];
      int[] dest = new int[length];
      System.arraycopy(src, 0, dest, 0, src.length);
      if (src.length < length)
        Arrays.fill(dest, src.length, length, PAD_VALUE);
      paddedIds[i] = dest;
      lengths[i] = src.length;
    }

    return new Padded(paddedIds, lengths);

  }

  private static class Padded {
    Padded(int[][] ids, int[] lengths) {
      this.ids = ids;
      this.lengths = lengths;
    }
    private int[][] ids;
    private int[] lengths;
  }
}
