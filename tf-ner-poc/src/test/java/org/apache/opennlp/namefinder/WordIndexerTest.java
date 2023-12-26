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

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WordIndexerTest {

  private static WordIndexer indexer;

  @BeforeAll
  static void beforeClass() {
    try (InputStream words = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/words.txt.gz"));
         InputStream chars = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/chars.txt.gz"))) {
      indexer = new WordIndexer(words, chars);
    } catch (Exception ex) {
      indexer = null;
    }
    assertNotNull(indexer);
  }

  @Test
  void testToTokenIdsWithOneSentence() {
    String text = "Stormy Cars ' friend says she also plans to sue Michael Cohen .";

    TokenIds ids = indexer.toTokenIds(text.split("\\s+"));
    assertEquals(13, ids.getWordIds()[0].length, "Expect 13 tokenIds");

    assertArrayEquals(new int[] {7, 30, 34, 80, 42, 3}, ids.getCharIds()[0][0]);
    assertArrayEquals(new int[] {51, 41, 80, 54}, ids.getCharIds()[0][1]);
    assertArrayEquals(new int[] {64}, ids.getCharIds()[0][2]);
    assertArrayEquals(new int[] {47, 80, 82, 83, 31, 23}, ids.getCharIds()[0][3]);
    assertArrayEquals(new int[] {54, 41, 3, 54}, ids.getCharIds()[0][4]);
    assertArrayEquals(new int[] {54, 76, 83}, ids.getCharIds()[0][5]);
    assertArrayEquals(new int[] {41, 55, 54, 34}, ids.getCharIds()[0][6]);
    assertArrayEquals(new int[] {46, 55, 41, 31, 54}, ids.getCharIds()[0][7]);
    assertArrayEquals(new int[] {30, 34}, ids.getCharIds()[0][8]);
    assertArrayEquals(new int[] {54, 50, 83}, ids.getCharIds()[0][9]);
    assertArrayEquals(new int[] {39, 82, 20, 76, 41, 83, 55}, ids.getCharIds()[0][10]);
    assertArrayEquals(new int[] {51, 34, 76, 83, 31}, ids.getCharIds()[0][11]);
    assertArrayEquals(new int[] {65}, ids.getCharIds()[0][12]);

    // TODO investigate why the 3 commented checks are different: Different data / assertions?
    assertEquals(2720, ids.getWordIds()[0][0]);
    // assertEquals(15275,ids.getWordIds()[0][1]);
    assertEquals(3256, ids.getWordIds()[0][2]);
    assertEquals(11348, ids.getWordIds()[0][3]);
    assertEquals(21054, ids.getWordIds()[0][4]);
    assertEquals(18337, ids.getWordIds()[0][5]);
    assertEquals(7885, ids.getWordIds()[0][6]);
    assertEquals(7697, ids.getWordIds()[0][7]);
    assertEquals(16601, ids.getWordIds()[0][8]);
    assertEquals(2720, ids.getWordIds()[0][9]);
    // assertEquals(17408, ids.getWordIds()[0][10]);
    // assertEquals(11541, ids.getWordIds()[0][11]);
    assertEquals(2684, ids.getWordIds()[0][12]);

  }

  @Test
  void testToTokenIdsWithTwoSentences() {

    String[] text = new String[] {"I wish I was born in Copenhagen Denmark",
        "Donald Trump died on his way to Tivoli Gardens in Denmark ."};

    List<String[]> collect = Arrays.stream(text).map(s -> s.split("\\s+")).toList();

    TokenIds ids = indexer.toTokenIds(collect.toArray(new String[2][]));

    assertEquals(8, ids.getWordIds()[0].length);
    assertEquals(12, ids.getWordIds()[1].length);

    assertArrayEquals(new int[] {4}, ids.getCharIds()[0][0]);
    assertArrayEquals(new int[] {6, 82, 54, 76}, ids.getCharIds()[0][1]);
    assertArrayEquals(new int[] {4}, ids.getCharIds()[0][2]);
    assertArrayEquals(new int[] {6, 41, 54}, ids.getCharIds()[0][3]);
    assertArrayEquals(new int[] {59, 34, 80, 31}, ids.getCharIds()[0][4]);
    assertArrayEquals(new int[] {82, 31}, ids.getCharIds()[0][5]);
    assertArrayEquals(new int[] {51, 34, 46, 83, 31, 76, 41, 28, 83, 31}, ids.getCharIds()[0][6]);
    assertArrayEquals(new int[] {36, 83, 31, 42, 41, 80, 49}, ids.getCharIds()[0][7]);

    assertArrayEquals(new int[] {36, 34, 31, 41, 55, 23}, ids.getCharIds()[1][0]);
    assertArrayEquals(new int[] {52, 80, 50, 42, 46}, ids.getCharIds()[1][1]);
    assertArrayEquals(new int[] {23, 82, 83, 23}, ids.getCharIds()[1][2]);
    assertArrayEquals(new int[] {34, 31}, ids.getCharIds()[1][3]);
    assertArrayEquals(new int[] {76, 82, 54}, ids.getCharIds()[1][4]);
    assertArrayEquals(new int[] {6, 41, 3}, ids.getCharIds()[1][5]);
    assertArrayEquals(new int[] {30, 34}, ids.getCharIds()[1][6]);
    assertArrayEquals(new int[] {52, 82, 11, 34, 55, 82}, ids.getCharIds()[1][7]);
    assertArrayEquals(new int[] {74, 41, 80, 23, 83, 31, 54}, ids.getCharIds()[1][8]);
    assertArrayEquals(new int[] {82, 31}, ids.getCharIds()[1][9]);
    assertArrayEquals(new int[] {36, 83, 31, 42, 41, 80, 49}, ids.getCharIds()[1][10]);
    assertArrayEquals(new int[] {65}, ids.getCharIds()[1][11]);

    // TODO investigate why the 6 commented checks are different: Different data / assertions?
    // assertEquals(21931, ids.getWordIds()[0][0]);
    assertEquals(20473, ids.getWordIds()[0][1]);
    // assertEquals(21931, ids.getWordIds()[0][2]);
    assertEquals(5477, ids.getWordIds()[0][3]);
    assertEquals(11538, ids.getWordIds()[0][4]);
    assertEquals(21341, ids.getWordIds()[0][5]);
    // assertEquals(14024, ids.getWordIds()[0][6]);
    // assertEquals(7420, ids.getWordIds()[0][7]);

    // assertEquals(12492, ids.getWordIds()[1][0]);
    assertEquals(2720, ids.getWordIds()[1][1]);
    assertEquals(9476, ids.getWordIds()[1][2]);
    assertEquals(16537, ids.getWordIds()[1][3]);
    assertEquals(18966, ids.getWordIds()[1][4]);
    assertEquals(21088, ids.getWordIds()[1][5]);
    assertEquals(16601, ids.getWordIds()[1][6]);
    assertEquals(2720, ids.getWordIds()[1][7]);
    assertEquals(2720, ids.getWordIds()[1][8]);
    assertEquals(21341, ids.getWordIds()[1][9]);
    // assertEquals(7420, ids.getWordIds()[1][10]);
    assertEquals(2684, ids.getWordIds()[1][11]);
  }

}
