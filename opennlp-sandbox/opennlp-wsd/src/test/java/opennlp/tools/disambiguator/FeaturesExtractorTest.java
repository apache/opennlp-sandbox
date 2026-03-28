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

package opennlp.tools.disambiguator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FeaturesExtractorTest extends AbstractDisambiguatorTest {

  private WTDIMS wtdims;

  // SUT
  private FeaturesExtractor extractor;

  @BeforeEach
  public void setUp() {
    this.extractor = new FeaturesExtractor();
    final WSDSample sample = new WSDSample(sentence1, tags1, lemmas1, 8);
    wtdims = new WTDIMS(sample);
  }

  @Test
  void testExtractFeatures() {
    // test
    extractor.extractIMSFeatures(wtdims, 3, 2);

    String[] surroundingWords = wtdims.getSurroundingWords();
    assertNotNull(surroundingWords);
    assertEquals(4, surroundingWords.length);
    String[] surroundingTags = wtdims.getPosOfSurroundingWords();
    assertNotNull(surroundingTags);
    assertEquals(7, surroundingTags.length);
    String[] localCollocations = wtdims.getLocalCollocations();
    assertNotNull(localCollocations);
    assertEquals(2, localCollocations.length);
  }

  @Test
  void testExtractFeaturesInvalid1() {
    assertThrows(IllegalArgumentException.class,
            () -> extractor.extractIMSFeatures(null, 3, 2));
  }

  @Test
  void testExtractFeaturesInvalid2() {
    assertThrows(IllegalArgumentException.class,
            () -> extractor.extractIMSFeatures(wtdims, 0, 2));
  }

  @Test
  void testExtractFeaturesInvalid3() {
    assertThrows(IllegalArgumentException.class,
            () -> extractor.extractIMSFeatures(wtdims, 3, 0));
  }

  @Test
  void testSerializeFeatures() {
    // prepare
    extractor.extractIMSFeatures(wtdims, 3, 2);
    // test
    extractor.serializeIMSFeatures(wtdims, extractor.extractTrainingSurroundingWords(List.of(wtdims)));
    String[] features = wtdims.getFeatures();
    assertNotNull(features);
    assertEquals(13, features.length);
  }

  @Test
  void testSerializeFeaturesInvalid1() {
    // prepare
    extractor.extractIMSFeatures(wtdims, 3, 2);
    // test
    assertThrows(IllegalArgumentException.class,
            () -> extractor.serializeIMSFeatures(
                    null, extractor.extractTrainingSurroundingWords(List.of(wtdims))));
  }

  @Test
  void testSerializeFeaturesInvalid2() {
    // prepare
    extractor.extractIMSFeatures(wtdims, 3, 2);
    // test
    assertThrows(IllegalArgumentException.class,
            () -> extractor.serializeIMSFeatures(wtdims, null));
  }

  @Test
  void testExtractTrainingSurroundingWords() {
    // prepare
    extractor.extractIMSFeatures(wtdims, 3, 2);
    // test
    List<String> surroundingWords = extractor.extractTrainingSurroundingWords(List.of(wtdims));
    assertNotNull(surroundingWords);
    assertEquals(4, surroundingWords.size());
  }

  @Test
  void testExtractTrainingSurroundingWordsInvalid1() {
    assertThrows(IllegalArgumentException.class,
            () -> extractor.extractTrainingSurroundingWords(null));
  }
}
