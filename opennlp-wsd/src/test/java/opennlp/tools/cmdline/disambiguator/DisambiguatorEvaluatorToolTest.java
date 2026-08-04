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

package opennlp.tools.cmdline.disambiguator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisambiguatorEvaluatorToolTest {

  private DisambiguatorEvaluatorTool tool;

  @BeforeEach
  void setUp() {
    tool = new DisambiguatorEvaluatorTool();
  }

  @Test
  void testGetName() {
    assertEquals("DisambiguatorEvaluator", tool.getName());
  }

  @Test
  void testGetShortDescription() {
    assertEquals("Disambiguator Evaluation Tool",
            tool.getShortDescription());
  }

  /*
   * Note: The order in which the parameters appear in the usage string is derived from
   * Class#getMethods(), which is not specified and differs between JVM implementations.
   * Therefore, only the presence of each parameter is checked here, not their order.
   */
  @Test
  void testGetHelp() {
    final String help = tool.getHelp();
    assertTrue(help.startsWith("Usage: opennlp DisambiguatorEvaluator "));
    for (String param : new String[] {"-data testData", "[-model model]",
        "[-encoding charsetName]", "[-type mfs|lesk|ims]", "-lang language"}) {
      assertTrue(help.contains(param), "Expected usage to contain '" + param + "' but was: " + help);
    }
  }
  
}
