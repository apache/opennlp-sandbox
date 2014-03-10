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
public class TrigramSentenceLanguageModel implements LanguageModel<String[]> {
  @Override
  public double calculateProbability(Collection<String[]> vocabulary, String[] sample) {
    double probability = 1d;
    for (Trigram trigram : getTrigrams(sample)) {
      if (trigram.getX0() != null && trigram.getX1() != null) {
        // default
        probability *= NGramUtils.calculateTrigramMLProbability(trigram.getX0(), trigram.getX1(), trigram.getX2(), vocabulary);
      } else if (trigram.getX0() == null && trigram.getX1() != null) {
        // bigram
        probability *= NGramUtils.calculateBigramMLProbability(trigram.getX2(), trigram.getX1(), vocabulary);
      } else if (trigram.getX0() == null && trigram.getX1() == null) {
        // unigram
        probability *= NGramUtils.calculateUnigramMLProbability(trigram.getX2(), vocabulary);
      } else {
        // unexpected
      }
    }
    return probability;
  }

  private Set<Trigram> getTrigrams(String[] sample) {
    Set<Trigram> trigrams = new HashSet<Trigram>();
    for (int i = 0; i < sample.length - 2; i++) {
      String x0 = null;
      String x1 = null;
      String x2 = sample[i];
      if (i > 1) {
        x1 = sample[i - 1];
      }
      if (i > 2) {
        x0 = sample[i - 2];
      }
      trigrams.add(new Trigram(x0, x1, x2));
    }
    return trigrams;
  }

  private class Trigram {
    private final String x0;
    private final String x1;
    private final String x2;

    private Trigram(String x0, String x1, String x2) {
      this.x0 = x0;
      this.x1 = x1;
      this.x2 = x2;
    }

    public String getX0() {
      return x0;
    }

    public String getX1() {
      return x1;
    }

    public String getX2() {
      return x2;
    }
  }
}
