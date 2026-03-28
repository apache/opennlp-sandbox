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

package org.apache.opennlp.namefinder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FeedDictionaryTest {

  private static WordIndexer indexer;

  @BeforeAll
  static void beforeClass() {
    try (InputStream words = new GZIPInputStream(FeedDictionaryTest.class.getResourceAsStream("/words.txt.gz"));
         InputStream chars = new GZIPInputStream(FeedDictionaryTest.class.getResourceAsStream("/chars.txt.gz"))) {

      indexer = new WordIndexer(words, chars);
    } catch (Exception ex) {
      indexer = null;
    }
    assertNotNull(indexer);
  }

  @Test
  void testToTokenIds() {
    String text1 = "Stormy Cars ' friend says she also plans to sue Michael Cohen .";
    TokenIds oneSentence = indexer.toTokenIds(text1.split("\\s+"));
    assertNotNull(oneSentence);
    assertEquals(13, oneSentence.getWordIds()[0].length, "Expect 13 tokenIds");

    String[] text2 = new String[] {"I wish I was born in Copenhagen Denmark",
        "Donald Trump died on his way to Tivoli Gardens in Denmark ."};
    List<String[]> collect = Arrays.stream(text2).map(s -> s.split("\\s+")).toList();
    TokenIds twoSentences = indexer.toTokenIds(collect.toArray(new String[2][]));
    assertNotNull(twoSentences);
    assertEquals(8, twoSentences.getWordIds()[0].length, "Expect 8 tokenIds");
    assertEquals(12, twoSentences.getWordIds()[1].length, "Expect 12 tokenIds");
  }
}
