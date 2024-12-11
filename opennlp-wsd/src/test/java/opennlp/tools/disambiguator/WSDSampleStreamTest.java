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

import java.io.IOException;

import opennlp.tools.AbstractTest;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the {@link WSDSampleStream} class.
 */
public class WSDSampleStreamTest extends AbstractTest {

  /**
   * Tests if the {@link WSDSample} correctly created from valid input.
   */
  @Test
  void testReadValid() throws IOException {
    // TargetIndex TargetLemma Token_Tag Token_Tag ...
    String sampleTokens = "1 The_DT day_NN has_VBZ just_RB started_VBN ._.";

    ObjectStream<WSDSample> sampleTokenStream = new WSDSampleStream(
            ObjectStreamUtils.createObjectStream(sampleTokens));

    WSDSample sample = sampleTokenStream.read();
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
  }

  @Test
  void testReadInvalidNoTargetIndex() {
    assertThrows(NumberFormatException.class, () ->
            new WSDSampleStream(ObjectStreamUtils.createObjectStream(
            "The day has just started")).read());
  }

  @Test
  void testReadInvalidNoTagsYieldsNull() throws IOException {
    assertNull(new WSDSampleStream(ObjectStreamUtils.createObjectStream(
              "1 The day has just started")).read());
  }

}
