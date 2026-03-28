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

import opennlp.tools.AbstractTest;
import opennlp.tools.util.InvalidFormatException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IMSWSDContextGeneratorTest extends AbstractTest {

  // SUT
  private IMSWSDContextGenerator cg;

  @BeforeEach
  public void setUp() {
    cg = new IMSWSDContextGenerator();
  }

  @Test
  void testGetContext() throws InvalidFormatException {
    WSDSample sample = WSDSample.parse("1 The_DT day_NN has_VBZ just_RB started_VBN ._.");
    assertNotNull(sample);
    String[] context = cg.getContext(sample, 2, 3, Collections.emptyList());
    assertNotNull(context);
    assertNotEquals(0, context.length);
    assertEquals(8, context.length);
    assertEquals("F3=day", context[3]);
  }
}
