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

import opennlp.tools.coref.mention.Parse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CorefSampleTest {

  private static final String example =
          "(TOP (S (NP-SBJ (DT The) (NN test) )(VP (MD may) (VP (VB come) (NP-TMP (NN today) )))(. .) ))";

  @Test
  void testGetParses() {
    CorefSample cs = CorefSample.parse(example);
    assertNotNull(cs);
    List<Parse> parses = cs.getParses();
    assertNotNull(parses);
    assertEquals(1, parses.size());
    Parse p = parses.get(0);
    assertNotNull(p);
    assertEquals("The test may come today . ", p.toString());
  }

  @Test
  void testToString() {
    CorefSample cs = CorefSample.parse(example);
    assertNotNull(cs);
    String s = cs.toString();
    assertNotNull(s);
    assertFalse(s.isEmpty());
  }

}
