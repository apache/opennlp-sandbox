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

package opennlp.tools.textsimilarity;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class GeneralizationListReducerTest extends TestCase {
  private GeneralizationListReducer generalizationListReducer = new GeneralizationListReducer();

  public void notNull() {
    assertNotNull(generalizationListReducer);
  }

  public void test() {
    ParseTreeChunk ch1 = new ParseTreeChunk("VP", new String[] { "run",
        "around", "tigers", "zoo" }, new String[] { "VB", "IN", "NP", "NP" });

    ParseTreeChunk ch2 = new ParseTreeChunk("NP", new String[] { "run",
        "around", "tigers" }, new String[] { "VB", "IN", "NP", });

    ParseTreeChunk ch3 = new ParseTreeChunk("NP", new String[] { "the",
        "tigers" }, new String[] { "DT", "NP", });

    ParseTreeChunk ch4 = new ParseTreeChunk("NP", new String[] { "the", "*",
        "flying", "car" }, new String[] { "DT", "NN", "VBG", "NN" });

    ParseTreeChunk ch5 = new ParseTreeChunk("NP", new String[] { "the", "*" },
        new String[] { "DT", "NN", });

    // [DT-the NN-* VBG-flying NN-car ], [], [], [DT-the NN-* ]]

    List<ParseTreeChunk> inp = new ArrayList<ParseTreeChunk>();
    inp.add(ch1);
    inp.add(ch2);
    inp.add(ch5);
    inp.add(ch3);
    inp.add(ch2);
    inp.add(ch2);
    inp.add(ch3);
    inp.add(ch4);

    assertTrue(ch1.isASubChunk(ch2));
    assertFalse(ch2.isASubChunk(ch1));
    assertFalse(ch5.isASubChunk(ch4));
    assertTrue(ch4.isASubChunk(ch5));

    assertFalse(ch2.isASubChunk(ch3));
    assertFalse(ch3.isASubChunk(ch2));

    assertFalse(ch5.isASubChunk(ch3));
    assertFalse(ch3.isASubChunk(ch5));

    List<ParseTreeChunk> res = generalizationListReducer
        .applyFilteringBySubsumption(inp);
    assertEquals(
        res.toString(),
        "[VP [VB-run IN-around NP-tigers NP-zoo ], NP [DT-the NP-tigers ], NP [DT-the NN-* VBG-flying NN-car ]]");
    System.out.println(res);

  }
}
