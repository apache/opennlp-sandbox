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

package opennlp.tools.disambiguator;

import java.util.List;
import java.util.stream.Stream;

import net.sf.extjwnl.data.Synset;
import opennlp.tools.AbstractTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the {@link WSDSample} class.
 */
public class WSDSampleTest extends AbstractTest {

  private String demoSample;

  @BeforeEach
  void setUp() {
    demoSample = "1 The_DT day_NN has_VBZ just_RB started_VBN ._.";
  }

  @Test
  void testEqualsAndHashCodeWithOneSample() throws InvalidFormatException {
    WSDSample ps1 = WSDSample.parse(demoSample);
    assertNotNull(ps1);
    assertEquals(ps1, ps1);
    assertEquals(ps1.hashCode(), ps1.hashCode());
  }

  @Test
  void testEqualsAndHashCodeWithTwoSamples() throws InvalidFormatException {
    WSDSample ps1 = WSDSample.parse(demoSample);
    WSDSample ps2 = WSDSample.parse(demoSample);
    assertNotNull(ps1);
    assertNotNull(ps2);
    assertEquals(ps1, ps2);
    assertEquals(ps1.hashCode(), ps2.hashCode());
  }

  @Test
  void testEqualsWithAnotherObject() throws InvalidFormatException {
    WSDSample parsed1 = WSDSample.parse(demoSample);
    assertNotNull(parsed1);
    //noinspection AssertBetweenInconvertibleTypes
    assertNotEquals(parsed1, "Whoops"); // Cave: Intended wrong order!
  }

  @Test
  void testEqualsAndHashCodeDiffer() throws InvalidFormatException {
    WSDSample ps1 = WSDSample.parse(demoSample);
    WSDSample ps2 = WSDSample.parse("1 The_DT day_NN has_VBZ just_RB begun_VBN ._.");
    assertNotNull(ps1);
    assertNotNull(ps2);
    assertNotEquals(ps1, ps2);
    assertNotEquals(ps1.hashCode(), ps2.hashCode());
  }

  @Test
  void testToString() throws InvalidFormatException {
    WSDSample ps = WSDSample.parse(demoSample);
    assertEquals("target at: 1 in: " +
                    "0.The_DT 1.day_NN 2.has_VBZ 3.just_RB 4.started_VBN 5.._.", ps.toString());
  }

  /**
   * Tests if the {@link WSDSample} is correctly parsed.
   */
  @Test
  void testParse() throws InvalidFormatException {
    // TargetIndex TargetLemma Token_Tag Token_Tag ...
    String sampleTokens = "1 The_DT day_NN has_VBZ just_RB started_VBN ._.";

    WSDSample sample = WSDSample.parse(sampleTokens);
    assertNotNull(sample);
    String[] tokens = sample.getSentence();
    String[] tags = sample.getTags();
    String[] lemmas = sample.getLemmas();
    assertNotNull(tokens);
    assertNotNull(tags);
    assertNotNull(lemmas);
    assertEquals(6, tokens.length);
    assertEquals(6, tags.length);
    assertEquals(6, lemmas.length);

    assertEquals("The", tokens[0]);
    assertEquals("day", tokens[1]);
    assertEquals("has", tokens[2]);
    assertEquals("just", tokens[3]);
    assertEquals("started", tokens[4]);
    assertEquals(".", tokens[5]);

    int targetPosition = sample.getTargetPosition();
    assertEquals(1, targetPosition);

    String targetWord = sample.getTargetWord();
    assertNotNull(targetWord);
    assertEquals("day", targetWord);

    String targetTagWordTag =  sample.getTargetWordTag();
    assertNotNull(targetTagWordTag);
    assertEquals("day.n", targetTagWordTag);

    List<Synset> synsets = sample.getSynsets();
    assertNotNull(synsets);

  }

  @Test
  void testParseInvalidNoTargetIndex() {
    assertThrows(NumberFormatException.class, () ->
            WSDSample.parse("The day has just started"));
  }

  @Test
  void testParseInvalidNoTags() {
    assertThrows(InvalidFormatException.class, () ->
            WSDSample.parse("1 The day has just started"));
  }

  @ParameterizedTest(name = "Verify \"{1}\"")
  @MethodSource(value = "provideTagWordTags")
  void testGetTargetWordTag (String input, String expTag) throws InvalidFormatException {
    WSDSample sample = WSDSample.parse(input);
    assertNotNull(input);
    String targetTagWordTag =  sample.getTargetWordTag();
    assertNotNull(targetTagWordTag);
    assertEquals(expTag, targetTagWordTag);
  }

  /*
   * Produces a stream of <label|context> pairs for parameterized unit tests.
   */
  private static Stream<Arguments> provideTagWordTags() {
    String baseSent = "The_DT beautiful_JJ day_NN has_VBZ just_RB started_VBN ._.";
    return Stream.of(
      Arguments.of("0 " + baseSent, "the.?"),
      Arguments.of("1 " + baseSent, "beautiful.a"),
      Arguments.of("2 " + baseSent, "day.n"),
      Arguments.of("3 " + baseSent, "have.v"),
      Arguments.of("4 " + baseSent, "just.r"),
      Arguments.of("5 " + baseSent, "start.v"),
      Arguments.of("6 " + baseSent, "..?")
    );
  }
}
