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

package opennlp.tools.cmdline.coref;

import opennlp.tools.coref.CorefSampleStreamFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreferencerTrainerToolTest {

  private CoreferencerTrainerTool tool;

  @BeforeAll
  public static void initEnv() {
    CorefSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    tool = new CoreferencerTrainerTool();
  }

  @Test
  void testGetName() {
    assertEquals("CoreferencerTrainer", tool.getName());
  }

  @Test
  void testGetShortDescription() {
    assertEquals("Trainer for a Learnable Noun Phrase Coreferencer",
            tool.getShortDescription());
  }

  @Test
  void testGetHelp() {
    assertTrue(tool.getHelp().startsWith("Usage: opennlp CoreferencerTrainer"));
  }
}
