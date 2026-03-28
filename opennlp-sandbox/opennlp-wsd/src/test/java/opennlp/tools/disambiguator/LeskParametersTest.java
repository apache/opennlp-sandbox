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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LeskParametersTest {

  @Test
  void testCreate() {
    LeskParameters params = new LeskParameters();
    assertNotNull(params);
    assertInstanceOf(WSDParameters.class, params);
    assertInstanceOf(LeskParameters.class, params);
    assertTrue(params.areValid());

    assertEquals(LeskParameters.DFLT_LESK_TYPE, params.getType());
    assertEquals(LeskParameters.DFLT_WIN_SIZE, params.getWinBSize());
    assertEquals(LeskParameters.DFLT_WIN_SIZE, params.getWinFSize());
    assertEquals(LeskParameters.DFLT_DEPTH, params.getDepth());
    assertEquals(LeskParameters.DFLT_DEPTH_WEIGHT, params.getDepthWeight());
    assertEquals(LeskParameters.DFLT_SOURCE, params.getSenseSource());
    assertEquals(LeskParameters.DFLT_IEXP, params.getIexp());
    assertEquals(LeskParameters.DFLT_DEXP, params.getDexp());
    boolean[] activeFeatures = params.getFeatures();
    assertNotNull(activeFeatures);
    assertEquals(10, activeFeatures.length);
    for (int i = 0; i < 10; i++) {
      assertTrue(activeFeatures[i]);
    }

  }

  @Test
  void testDeactivateFeatures() {
    LeskParameters params = new LeskParameters();
    assertNotNull(params);
    boolean[] activeFeatures = params.getFeatures();
    assertNotNull(activeFeatures);
    assertEquals(10, activeFeatures.length);
    for (int i = 0; i < 10; i++) {
      assertTrue(activeFeatures[i]);
    }
    // switch of first feature at idx 0.
    activeFeatures[0] = false;
    params.setFeatures(activeFeatures);
    // get and check
    activeFeatures = params.getFeatures();
    assertNotNull(activeFeatures);
    assertEquals(10, activeFeatures.length);
    for (int i = 0; i < 10; i++) {
      if (i == 0) {
        assertFalse(activeFeatures[i]);
      } else {
        assertTrue(activeFeatures[i]);
      }
    }

  }

  @ParameterizedTest
  @EnumSource(LeskParameters.LeskType.class)
  void testCreateByType(LeskParameters.LeskType input) {
    LeskParameters params = new LeskParameters();
    assertNotNull(params);
    params.setType(input);
    assertEquals(input, params.getType());
    assertTrue(params.areValid());
  }

  @ParameterizedTest
  @EnumSource(LeskParameters.LeskType.class)
  void testCreateByTypeWithInvalidParameters(LeskParameters.LeskType t) {
    int INVALID = -1;
    LeskParameters params = new LeskParameters();
    assertNotNull(params);
    params.setType(t);
    assertEquals(t, params.getType());
    params.setWinBSize(INVALID);
    assertFalse(params.areValid());
    params.setWinBSize(LeskParameters.DFLT_WIN_SIZE);
    params.setWinFSize(INVALID);
    assertFalse(params.areValid());
    if (t.equals(LeskParameters.LeskType.LESK_EXT_EXP) ||
        t.equals(LeskParameters.LeskType.LESK_EXT_EXP_CTXT)) {
      params.setWinFSize(LeskParameters.DFLT_WIN_SIZE);
      params.setDepth(INVALID);
      assertFalse(params.areValid());
      params.setDepth(LeskParameters.DFLT_DEPTH);
      params.setDexp(INVALID);
      assertFalse(params.areValid());
      params.setDexp(LeskParameters.DFLT_DEPTH);
      params.setIexp(INVALID);
      assertFalse(params.areValid());
    }
  }
}
