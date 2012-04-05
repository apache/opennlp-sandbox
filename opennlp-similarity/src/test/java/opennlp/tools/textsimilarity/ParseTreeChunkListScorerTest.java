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

import java.util.List;

import opennlp.tools.parser.Parse;

import org.junit.Test;
import org.junit.runner.RunWith;
import junit.framework.TestCase;

public class ParseTreeChunkListScorerTest extends TestCase {
  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  private ParseTreeChunk parseTreeChunk = new ParseTreeChunk();

  public void test() {
    List<List<ParseTreeChunk>> chs = parseTreeChunk
        .obtainParseTreeChunkListByParsingList("[[ [NN-* IN-in NP-israel ],  [NP-* IN-in NP-israel ],  [NP-* IN-* TO-* NN-* ],  [NN-visa IN-* NN-* IN-in ]],"
            + " [ [VB-get NN-visa IN-* NN-* IN-in .-* ],  [VBD-* IN-* NN-* NN-* .-* ],  [VB-* NP-* ]]]");

    double sc = parseTreeChunkListScorer.getParseTreeChunkListScore(chs);
    assertTrue(sc > 1.90);

  }
}
