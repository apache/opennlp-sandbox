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

import opennlp.tools.util.ObjectStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CorefSampleStreamFactoryTest {

  private CorefSampleStreamFactory streamFactory;

  @BeforeAll
  public static void initEnv() {
    CorefSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  public void setUp() {
    streamFactory= new CorefSampleStreamFactory();
  }

  @Test
  void testCreate() {
    String[] args = new String[]{"-data",
            CorefSampleStreamFactoryTest.class.getResource("/models/training/coref/training-test.txt").getPath()};
    ObjectStream<CorefSample> samples = streamFactory.create(args);
    assertNotNull(samples);
  }

  @Test
  void testCreateWithEmptyParameters() {
    assertThrows(IllegalArgumentException.class, () -> streamFactory.create(new String[0]));
  }

  @Test
  void testCreateWithIncorrectParameters() {
    assertThrows(RuntimeException.class, () -> streamFactory.create(new String[]{"-data","Non-Existing.txt"}));
  }
}
