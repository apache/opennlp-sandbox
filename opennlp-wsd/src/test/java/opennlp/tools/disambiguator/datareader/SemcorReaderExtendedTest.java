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

package opennlp.tools.disambiguator.datareader;

import opennlp.tools.AbstractTest;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.util.ObjectStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SemcorReaderExtendedTest extends AbstractTest {

  @Test
  void testCreateAndValidate() throws IOException {
    SemcorReaderExtended scReader = new SemcorReaderExtended(Paths.get(SEMCOR_DIR));
    List<WSDSample> samples = scReader.getSemcorData("please.v");
    assertNotNull(samples);
    assertFalse(samples.isEmpty());
    assertEquals(25, samples.size());
    ObjectStream<WSDSample> stream = scReader.getSemcorDataStream("please.v");
    assertNotNull(stream);
    stream.close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"\t", "\n", " "})
  void testInitWithNullOrEmpty(String input) {
    assertThrows(IllegalArgumentException.class, () -> new SensevalReader(input));
  }

  @Test
  void testInitWithInvalid() {
    assertThrows(RuntimeException.class, () -> new SensevalReader("x"));
  }

}
