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

import static junit.framework.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext-dedupe-test.xml" })
@ActiveProfiles("UnitTest")
public class GeneralizationListReducerTest {
  @Autowired
  private GeneralizationListReducer generalizationListReducer;

  @Test
  public void notNull() {
    assertNotNull(generalizationListReducer);
  }

  @Test
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

    Boolean b = ch1.isASubChunk(ch2);
    b = ch2.isASubChunk(ch1);
    b = ch5.isASubChunk(ch4);
    b = ch4.isASubChunk(ch5);

    List<ParseTreeChunk> res = generalizationListReducer
        .applyFilteringBySubsumption(inp);
    System.out.println(res);

  }
}
