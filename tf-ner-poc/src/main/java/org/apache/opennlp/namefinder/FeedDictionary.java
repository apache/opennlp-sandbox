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

import java.util.Arrays;

import org.tensorflow.Tensor;

public class FeedDictionary implements AutoCloseable  {

  static int PAD_VALUE = 0;


  private final Tensor<Float> dropoutTensor;
  private final Tensor<Integer> charIdsTensor;
  private final Tensor<Integer> wordLengthsTensor;
  private final Tensor<Integer> wordIdsTensor;
  private final int[] sentenceLengths;
  private final Tensor<Integer> sentenceLengthsTensor;
  private final int maxSentenceLength;
  private final int maxCharLength;
  private final int numberOfSentences;

  public int[] getSentenceLengths() {
    return sentenceLengths;
  }

  public int getMaxSentenceLength() {
    return maxSentenceLength;
  }

  public int getNumberOfSentences() {
    return numberOfSentences;
  }

  public Tensor<Float> getDropoutTensor() {
    return dropoutTensor;
  }

  public Tensor<Integer> getCharIdsTensor() {
    return charIdsTensor;
  }

  public Tensor<Integer> getSentenceLengthsTensor() {
    return sentenceLengthsTensor;
  }


  public Tensor<Integer> getWordLengthsTensor() {
    return wordLengthsTensor;
  }

  public Tensor<Integer> getWordIdsTensor() {
    return wordIdsTensor;
  }

  private FeedDictionary(final float dropout,
                         final int[][][] charIds,
                         final int[][] wordLengths,
                         final int[][] wordIds,
                         final int[] sentenceLengths,
                         final int maxSentenceLength,
                         final int maxCharLength,
                         final int numberOfSentences) {

    dropoutTensor = Tensor.create(dropout, Float.class);
    charIdsTensor = Tensor.create(charIds, Integer.class);
    wordLengthsTensor = Tensor.create(wordLengths, Integer.class);
    wordIdsTensor = Tensor.create(wordIds, Integer.class);
    this.sentenceLengths = sentenceLengths;
    sentenceLengthsTensor = Tensor.create(sentenceLengths, Integer.class);
    this.maxSentenceLength = maxSentenceLength;
    this.maxCharLength = maxCharLength;
    this.numberOfSentences = numberOfSentences;

  }

  public void close() {
    dropoutTensor.close();
    charIdsTensor.close();
    wordLengthsTensor.close();
    wordIdsTensor.close();
    sentenceLengthsTensor.close();
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
