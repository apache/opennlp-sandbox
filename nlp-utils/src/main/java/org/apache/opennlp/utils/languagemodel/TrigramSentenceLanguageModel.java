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
package org.apache.opennlp.utils.languagemodel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.opennlp.utils.ngram.NGramUtils;

/**
 * A simple trigram language model for sentences made of <code>String</code> arrays
 */
public class TrigramSentenceLanguageModel<T> implements LanguageModel<T[]> {

  @Override
  public double calculateProbability(Collection<T[]> vocabulary, T[] sample) {
    double probability = 0d;
    if (!vocabulary.isEmpty()) {
      for (Trigram trigram : getTrigrams(sample)) {
        if (trigram.getX0() != null && trigram.getX1() != null) {
          // default
          probability += Math.log(NGramUtils.calculateTrigramMLProbability(trigram.getX0(), trigram.getX1(), trigram.getX2(), vocabulary));
        } else if (trigram.getX0() == null && trigram.getX1() != null) {
          // bigram
          probability += Math.log(NGramUtils.calculateBigramMLProbability(trigram.getX2(), trigram.getX1(), vocabulary));
        } else if (trigram.getX0() == null) {
          // unigram
          probability += Math.log(NGramUtils.calculateUnigramMLProbability(trigram.getX2(), vocabulary));
        } else {
          throw new RuntimeException("unexpected");
        }
      }
      if (!Double.isNaN(probability)) {
        probability = Math.exp(probability);
      }
    }
    return probability;
  }

  private Set<Trigram> getTrigrams(T[] sample) {
    Set<Trigram> trigrams = new HashSet<Trigram>();
    for (int i = 0; i < sample.length; i++) {
      T x0 = null;
      T x1 = null;
      T x2 = sample[i];
      if (i > 0) {
        x1 = sample[i - 1];
      }
      if (i > 1) {
        x0 = sample[i - 2];
      }
      if (x0 != null && x1 != null && x2 != null) {
        trigrams.add(new Trigram(x0, x1, x2));
      }
    }
    return trigrams;
  }

  private class Trigram {
    private final T x0;
    private final T x1;
    private final T x2;

    private Trigram(T x0, T x1, T x2) {
      this.x0 = x0;
      this.x1 = x1;
      this.x2 = x2;
    }

    public T getX0() {
      return x0;
    }

    public T getX1() {
      return x1;
    }

    public T getX2() {
      return x2;
    }
  }
}
