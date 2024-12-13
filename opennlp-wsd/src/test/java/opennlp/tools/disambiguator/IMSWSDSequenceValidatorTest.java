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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IMSWSDSequenceValidatorTest extends AbstractTest {

  // SUT
  private IMSWSDSequenceValidator validator;

  @BeforeEach
  public void setUp() {
    validator = new IMSWSDSequenceValidator();
  }

  @Test
  void testValidSequence1() {
    assertTrue(validator.validSequence("I-NP", new String[] {"I-NP", "I-NP", "I-NP"}));
  }

  @Test
  void testValidSequence2() {
    assertTrue(validator.validSequence("I-NP", new String[] {"B-NP", "I-NP"}));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"\t", "\n", " "})
  void testValidSequenceInvalid(String sequence) {
    assertFalse(validator.validSequence("I-", new String[] {sequence}));
  }

  @Test
  void testValidSequenceWithPreviousOutside() {
    assertFalse(validator.validSequence("I-NP", new String[] {"O"}));
  }

  @Test
  void testValidSequenceWithDifferentPreviousTag() {
    // Note: VP inside of NP conflicts
    assertFalse(validator.validSequence("I-VP", new String[] {"B-NP"}));
  }
}
