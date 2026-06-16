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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor.basic;

import java.util.HashSet;
import java.util.Set;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.DocumentAnalytics;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Computes {@link DocumentAnalytics} from an analyzed document backbone.
 */
final class DocumentAnalyticsComputer {

  /** Prevents instantiation. */
  private DocumentAnalyticsComputer() {
  }

  /**
   * Builds document-level statistics when tokenization has run. POS-based densities require
   * {@code PIPELINE_STEP_POS_TAG}; {@code unique_lemma_count} requires lemmatization.
   *
   * @param document The analyzed document. Must not be {@code null}.
   *
   * @return The analytics message, or {@code null} when no tokens are present.
   */
  static DocumentAnalytics compute(OpenNlpDocument document) {
    int totalTokens = 0;
    int nouns = 0;
    int verbs = 0;
    int adjectives = 0;
    int adverbs = 0;
    int contentWords = 0;
    int scoredTokens = 0;
    boolean hasPosTags = false;
    boolean hasLemmas = false;
    final Set<String> uniqueLemmas = new HashSet<>();

    for (AnnotatedSentence sentence : document.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        totalTokens++;
        if (token.hasPosTag() && !token.getPosTag().isBlank()) {
          hasPosTags = true;
          scoredTokens++;
          final String tag = token.getPosTag();
          if (isNoun(tag)) {
            nouns++;
            contentWords++;
          } else if (isVerb(tag)) {
            verbs++;
            contentWords++;
          } else if (isAdjective(tag)) {
            adjectives++;
            contentWords++;
          } else if (isAdverb(tag)) {
            adverbs++;
            contentWords++;
          } else if (!isPunctuation(tag)) {
            // Function or other non-punctuation token; excluded from content-word counts.
          }
        }
        if (token.hasLemma() && !token.getLemma().isBlank() && !"_".equals(token.getLemma())) {
          hasLemmas = true;
          uniqueLemmas.add(token.getLemma());
        }
      }
    }

    if (totalTokens == 0) {
      return null;
    }

    final DocumentAnalytics.Builder analytics = DocumentAnalytics.newBuilder()
        .setTotalTokens(totalTokens)
        .setTotalSentences(document.getSentencesCount());

    if (hasPosTags && scoredTokens > 0) {
      analytics.setNounDensity((float) nouns / scoredTokens);
      analytics.setVerbDensity((float) verbs / scoredTokens);
      analytics.setAdjectiveDensity((float) adjectives / scoredTokens);
      analytics.setAdverbDensity((float) adverbs / scoredTokens);
      analytics.setContentWordRatio((float) contentWords / scoredTokens);
      analytics.setLexicalDensity((float) contentWords / scoredTokens);
    }

    if (hasLemmas) {
      analytics.setUniqueLemmaCount(uniqueLemmas.size());
    }

    return analytics.build();
  }

  private static boolean isNoun(String tag) {
    return tag.equals("NOUN") || tag.equals("PROPN")
        || tag.startsWith("NN") || tag.equals("NNP") || tag.equals("NNPS");
  }

  private static boolean isVerb(String tag) {
    return tag.equals("VERB") || tag.equals("AUX")
        || tag.startsWith("VB") || tag.equals("MD");
  }

  private static boolean isAdjective(String tag) {
    return tag.equals("ADJ") || tag.startsWith("JJ");
  }

  private static boolean isAdverb(String tag) {
    return tag.equals("ADV") || tag.startsWith("RB");
  }

  private static boolean isPunctuation(String tag) {
    return tag.equals("PUNCT") || tag.equals(".") || tag.equals(",") || tag.equals("''")
        || tag.equals("``") || tag.equals(":") || tag.equals("-LRB-") || tag.equals("-RRB-");
  }
}
