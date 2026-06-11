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
package org.apache.opennlp.grpc.embedding;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests {@link BertTokenizer}.
 *
 * <p>All expected token sequences were generated with the HuggingFace
 * {@code tokenizers} reference implementation ({@code BertWordPieceTokenizer})
 * using the same vocabulary, so they are verified to be identical to the
 * reference BERT tokenization.</p>
 */
class BertTokenizerTest {

  private static final Set<String> VOCABULARY = Set.of(
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
      "em", "##bed", "##ding", "##s",
      "wurttemberg",
      "don", "t", "wait", "what", ".", "?", "!", "'",
      "\u6211", "\u7231",  // CJK: 我 爱
      "natural", "language", "processing");

  private static BertTokenizer uncased(Set<String> vocabulary) {
    return new BertTokenizer(vocabulary, true, "[CLS]", "[SEP]", "[UNK]");
  }

  @Test
  void lowerCasesCapitalizedWords() {
    final String[] tokens =
        uncased(VOCABULARY).tokenize("The quick brown fox jumps over the lazy dog.");

    assertArrayEquals(new String[] {"[CLS]", "the", "quick", "brown", "fox", "jumps",
        "over", "the", "lazy", "dog", ".", "[SEP]"}, tokens);
  }

  @Test
  void lowerCasesBeforeWordpieceSplitting() {
    final String[] tokens = uncased(VOCABULARY).tokenize("Embeddings");

    assertArrayEquals(new String[] {"[CLS]", "em", "##bed", "##ding", "##s", "[SEP]"}, tokens);
  }

  @Test
  void stripsAccentsButKeepsNonCombiningCharacters() {
    // ü decomposes to u + combining diaeresis and the mark is stripped;
    // ß is not a combining mark and must survive, leaving an OOV token.
    final String[] tokens = uncased(VOCABULARY).tokenize("W\u00fcrttemberg Stra\u00dfe");

    assertArrayEquals(new String[] {"[CLS]", "wurttemberg", "[UNK]", "[SEP]"}, tokens);
  }

  @Test
  void splitsPunctuationRunsIntoSingleCharacters() {
    final String[] tokens = uncased(VOCABULARY).tokenize("Wait... what?!");

    assertArrayEquals(new String[] {"[CLS]", "wait", ".", ".", ".", "what", "?", "!",
        "[SEP]"}, tokens);
  }

  @Test
  void isolatesCjkIdeographs() {
    final String[] tokens = uncased(VOCABULARY).tokenize("\u6211\u7231natural language processing");

    assertArrayEquals(new String[] {"[CLS]", "\u6211", "\u7231", "natural", "language",
        "processing", "[SEP]"}, tokens);
  }

  @Test
  void cleansControlCharactersAndNormalizesWhitespace() {
    // Tab and no-break space are whitespace; the NUL character is removed,
    // joining "brown" and "fox" into one out-of-vocabulary token.
    final String[] tokens = uncased(VOCABULARY).tokenize("the\tquick\u00a0brown\u0000fox");

    assertArrayEquals(new String[] {"[CLS]", "the", "quick", "[UNK]", "[SEP]"}, tokens);
  }

  @Test
  void removesPrivateUseAndUnassignedCharacters() {
    // The reference implementation treats all C* categories as control
    // characters: private use (U+E000, Co) and noncharacters (U+FDD0, Cn)
    // are removed, joining the surrounding text into one OOV token.
    final String[] tokens = uncased(VOCABULARY).tokenize("fox\ue000jumps the fox\ufdd0jumps");

    assertArrayEquals(new String[] {"[CLS]", "[UNK]", "the", "[UNK]", "[SEP]"}, tokens);
  }

  @Test
  void partiallyMatchedWordBecomesSingleUnknownToken() {
    // "brownfox" starts with the vocabulary piece "brown", but the remainder has
    // no matching piece; the whole word must become one unknown token.
    final String[] tokens = uncased(VOCABULARY).tokenize("the brownfox jumps");

    assertArrayEquals(new String[] {"[CLS]", "the", "[UNK]", "jumps", "[SEP]"}, tokens);
  }

  @Test
  void casedModeKeepsCaseAndAccents() {
    final BertTokenizer tokenizer = new BertTokenizer(
        Set.of("The", "W\u00fcrttemberg", "fox"), false, "[CLS]", "[SEP]", "[UNK]");
    final String[] tokens = tokenizer.tokenize("The W\u00fcrttemberg fox");

    assertArrayEquals(new String[] {"[CLS]", "The", "W\u00fcrttemberg", "fox", "[SEP]"}, tokens);
  }

  @Test
  void supportsCustomSpecialTokens() {
    final BertTokenizer tokenizer = new BertTokenizer(
        Set.of("the", "fox"), true, "<s>", "</s>", "<unk>");
    final String[] tokens = tokenizer.tokenize("The unknown fox");

    assertArrayEquals(new String[] {"<s>", "the", "<unk>", "fox", "</s>"}, tokens);
  }

}
