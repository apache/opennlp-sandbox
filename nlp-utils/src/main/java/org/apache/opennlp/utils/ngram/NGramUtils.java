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
package org.apache.opennlp.utils.ngram;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * utility class for calculating probabilities of tri/bi/uni-grams
 */
public class NGramUtils {

  private static <T> Double count(T x0, T x1, T x2, Collection<T[]> sentences) {
    Double count = 0d;
    for (T[] sentence : sentences) {
      int idx0 = contains(sentence, x0);
      if (idx0 >= 0) {
        if (idx0 + 2 < sentence.length && x1.equals(sentence[idx0 + 1]) && x2.equals(sentence[idx0 + 2])) {
          count++;
        }
      }
    }
    return count;
  }

  private static <T> int contains(T[] sentence, T word) {
    for (int i = 0; i < sentence.length; i++) {
      if (word.equals(sentence[i])) {
        return i;
      }
    }
    return -1;
  }

  private static <T> Double count(T sequentWord, T precedingWord, Collection<T[]> set) {
    Double result = 0d;
    boolean foundPreceding = false;
    for (T[] sentence : set) {
      for (T w : sentence) {
        if (precedingWord.equals(w)) {
          foundPreceding = true;
          continue;
        }
        if (foundPreceding && sequentWord.equals(w)) {
          foundPreceding = false;
          result++;
        } else
          foundPreceding = false;
      }
    }
    return result;
  }

  private static <T> Double count(T word, Collection<T[]> set) {
    Double result = 0d;
    for (T[] sentence : set) {
      for (T w : sentence) {
        if (word.equals(w))
          result++;
      }
    }
    return result;
  }

  public static <T> Double calculateLaplaceSmoothingProbability(T sequentWord, T precedingWord, Collection<T[]> set, Double k) {
    return (count(sequentWord, precedingWord, set) + k) / (count(precedingWord, set) + k * set.size());
  }

  public static <T> Double calculateBigramMLProbability(T sequentWord, T precedingWord, Collection<T[]> set) {
    return count(sequentWord, precedingWord, set) / count(precedingWord, set);
  }

  public static <T> Double calculateTrigramMLProbability(T x0, T x1, T x2, Collection<T[]> sentences) {
    return count(x0, x1, x2, sentences) / count(x1, x0, sentences);
  }

  public static Double calculateBigramPriorSmoothingProbability(String sequentWord, String precedingWord, Collection<String[]> set, Double k) {
    return (count(sequentWord, precedingWord, set) + k * calculateUnigramMLProbability(sequentWord, set)) / (count(precedingWord, set) + k * set.size());
  }

  public static <T> Double calculateUnigramMLProbability(T word, Collection<T[]> set) {
    double vocSize = 0d;
    for (T[] s : set) {
      vocSize += s.length;
    }
    return count(word, set) / vocSize;
  }

  public static <T> Double calculateLinearInterpolationProbability(T x0, T x1, T x2, Collection<T[]> sentences,
                                                                   Double lambda1, Double lambda2, Double lambda3) {
    assert lambda1 + lambda2 + lambda3 == 1 : "lambdas sum should be equals to 1";
    assert lambda1 > 0 && lambda2 > 0 && lambda3 > 0 : "lambdas should all be greater than 0";

    return lambda1 * calculateTrigramMLProbability(x0, x1, x2, sentences) +
            lambda2 * calculateBigramMLProbability(x2, x1, sentences) +
            lambda3 * calculateUnigramMLProbability(x2, sentences);

  }

  private static <T> Collection<T> flatSet(Collection<T[]> set) {
    Collection<T> flatSet = new HashSet<T>();
    for (T[] sentence : set) {
      flatSet.addAll(Arrays.asList(sentence));
    }
    return flatSet;
  }

  public static <T> Double calculateMissingBigramProbabilityMass(T x1, Double discount, Collection<T[]> set) {
    Double missingMass = 0d;
    Double countWord = count(x1, set);
    for (T word : flatSet(set)) {
      missingMass += (count(word, x1, set) - discount) / countWord;
    }
    return 1 - missingMass;
  }

}
