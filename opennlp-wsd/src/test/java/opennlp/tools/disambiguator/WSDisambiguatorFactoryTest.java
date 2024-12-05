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

import opennlp.tools.AbstractTest;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is the test class for {@link WSDisambiguatorFactory}.
 */
class WSDisambiguatorFactoryTest extends AbstractTest {

  @Test
  void testCreateValid() throws InvalidFormatException {

    WSDisambiguatorFactory factory = WSDisambiguatorFactory.create(
            WSDisambiguatorFactory.class.getCanonicalName());
    assertInstanceOf(WSDisambiguatorFactory.class, factory);
    assertNotNull(factory.getContextGenerator());
    assertInstanceOf(IMSWSDContextGenerator.class, factory.getContextGenerator());
    assertDoesNotThrow(factory::validateArtifactMap);
  }

  @Test
  void testCreateWithNull() throws InvalidFormatException {
    assertInstanceOf(WSDisambiguatorFactory.class,
            WSDisambiguatorFactory.create(null));
  }

  @Test
  void testCreateWithInvalidName() {
    assertThrows(InvalidFormatException.class, () ->
            WSDisambiguatorFactory.create("X"));
  }

  @Test
  void testCreateWithHierarchy() {
    assertThrows(InvalidFormatException.class, () ->
            WSDisambiguatorFactory.create(Object.class.getCanonicalName()));
  }

}
