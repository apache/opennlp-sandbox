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

package opennlp.tools.coref;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CorefModelTest extends AbstractCorefTest {

  private static CorefModel model;

  @BeforeAll
  static void initEnv() throws IOException {
    final String modelDir = CorefModelTest.class.getResource(MODEL_DIR).getPath();
    assertNotNull(modelDir);
    model = new CorefModel("eng", modelDir);
    assertNotNull(model);
  }

  @Test
  void testThrowsWithNoLanguageCode() {
    final String modelDir = CorefModelTest.class.getResource(MODEL_DIR).getPath();
    assertNotNull(modelDir);
    assertThrows(NullPointerException.class, () -> new CorefModel(null, modelDir));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"\t", "\n"})
  void testThrowsWithInvalidModelDirectory(String modelDirInput) {
    assertThrows(IllegalArgumentException.class, () -> new CorefModel("eng", modelDirInput));
  }

  @Test
  void testEquals() {
    assertEquals(model, model);
  }

  @Test
  void testEqualsInvalid() {
    assertNotEquals(null, model);
  }

  @Test
  void testHashCodeValid() {
    assertEquals(model.hashCode(), model.hashCode());
  }

  @Test
  void testHashCodeInvalid() {
    assertNotEquals(UUID.randomUUID().toString().hashCode(), model.hashCode());
  }

  @Test
  void testSubModelsArePresent() {
    assertNotNull(model.getCommonNounResolverModel());
    assertNotNull(model.getDefiniteNounResolverModel());
    assertNotNull(model.getNumberModel());
    assertNotNull(model.getPluralNounResolverModel());
    assertNotNull(model.getPluralPronounResolverModel());
    assertNotNull(model.getProperNounResolverModel());
    assertNotNull(model.getSingularPronounResolverModel());
    assertNotNull(model.getSpeechPronounResolverModel());
    assertNotNull(model.getSimModel());
    assertNotNull(model.getFemaleNames());
    assertNotNull(model.getMaleNames());
    assertEquals(4121, model.getFemaleNames().size());
    assertEquals(1176, model.getMaleNames().size());
  }

  /**
   * Verifies that serialization of {@link CorefModel} equals trained state.
   * <p>
   * Tests {@link CorefModel#equals(Object)}.
   */
  @Test
  void testModelSerializationAndEquality() throws IOException {

    // Test serializing and de-serializing model
    try (ByteArrayOutputStream outArray = new ByteArrayOutputStream()) {
      model.serialize(outArray);
      outArray.close();

      // TEST: de-serialization and equality
      try (ByteArrayInputStream inArray = new ByteArrayInputStream(outArray.toByteArray())) {
        CorefModel outputModel = new CorefModel(inArray);
        assertNotNull(outputModel);
        assertTrue(outputModel.isLoadedFromSerialized());
        assertEquals(model, outputModel);
      }
    }
  }

}
