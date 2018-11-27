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

import static org.junit.Assert.*;

import java.util.List;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

public class ParseTreeChunkTest extends TestCase {
  private ParseTreeMatcherDeterministic parseTreeMatcher = new ParseTreeMatcherDeterministic();
  private ParseTreeChunk parseTreeChunk = new ParseTreeChunk();
  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();

  public void test() {
    ParseTreeChunk ch1, ch2;
    List<List<ParseTreeChunk>> chRes;

    ch1 = parseTreeChunk
        .obtainParseTreeChunkListByParsingList(
            "[[ [NN-* IN-in NP-israel ],  [NP-* IN-in NP-israel ],  [NP-* IN-* TO-* NN-* ],  [NN-visa IN-* NN-* IN-in ]], [ [VB-get NN-visa IN-* NN-* IN-in .-* ],  [VBD-* IN-* NN-* NN-* .-* ],  [VB-* NP-* ]]]")
        .get(0).get(0);
    ;

    // NP [JJ-great JJ-unsecured NN-loan NNS-deals ]
    // NP [JJ-great NN-pizza NNS-deals ]
    ch1 = new ParseTreeChunk("NP", new String[] { "great", "unsecured", "loan",
        "deals" }, new String[] { "JJ", "JJ", "NN", "NNS" });
    ch2 = new ParseTreeChunk("NP", new String[] { "great", "pizza", "deals" },
        new String[] { "JJ", "NN", "NNS" });
    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(), "[ [JJ-great NNS-deals ]]");

    ch1 = new ParseTreeChunk("NP", new String[] { "great", "unsecured", "loan",
        "of", "jambo" }, new String[] { "JJ", "JJ", "NN", "IN", "NN" });

    ch2 = new ParseTreeChunk("NP", new String[] { "great", "jambo", "loan" },
        new String[] { "JJ", "NN", "NN" });
    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(), "[ [JJ-great NN-loan ],  [NN-jambo ]]");

    ch1 = new ParseTreeChunk("NP", new String[] { "I", "love", "to", "run",
        "around", "zoo", "with", "tigers" }, new String[] { "NP", "VBP", "TO",
        "VB", "IN", "NP", "IN", "NP" });

    ch2 = new ParseTreeChunk("NP", new String[] { "I", "like", "it", "because",
        "it", "is", "loud" }, new String[] { "NP", "IN", "NP", "IN", "NP",
        "VBZ", "ADJP" });
    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(), "[ [NP-i ]]");

    ch1 = new ParseTreeChunk("NP", new String[] { "love", "to", "run",
        "around", "zoo", "with", "tigers" }, new String[] { "VBP", "TO", "VB",
        "IN", "NP", "IN", "NP" });

    ch2 = new ParseTreeChunk("VP", new String[] { "run", "to", "the", "tiger",
        "zoo" }, new String[] { "VBP", "TO", "DT", "NN", "NN" });
    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(),
        "[ [VBP-* TO-to ],  [VB-run IN-* NP-zoo ],  [NP-tigers ]]");

    ch1 = new ParseTreeChunk("VP", new String[] { "love", "to", "run",
        "around", "tigers", "zoo" }, new String[] { "VBP", "TO", "VB", "IN",
        "NP", "NP" });
    ch2 = new ParseTreeChunk("VP", new String[] { "run", "to", "the", "tiger",
        "zoo" }, new String[] { "VBP", "TO", "DT", "NN", "NN" });
    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(), "[ [VBP-* TO-to ],  [VB-run IN-* NP-tigers NP-zoo ]]");
    ch1 = new ParseTreeChunk("VP", new String[] { "run", "around", "tigers",
        "zoo" }, new String[] { "VB", "IN", "NP", "NP" });

    ch2 = new ParseTreeChunk("NP", new String[] { "run", "to", "the", "tiger",
        "zoo" }, new String[] { "VBP", "TO", "DT", "NN", "NN" });

    assertEquals(
        parseTreeMatcher.generalizeTwoGroupedPhrasesDeterministic(ch1, ch2)
            .toString(), "[ [VB-run IN-* NP-tigers NP-zoo ]]");

    List<List<ParseTreeChunk>> lch1 = parseTreeChunk
        .obtainParseTreeChunkListByParsingList("[[[DT-all NNS-children WHNP-who VBP-are CD-four NNS-years JJ-old IN-on CC-or IN-before NP-September ]]]");
    List<List<ParseTreeChunk>> lch2 = parseTreeChunk
        .obtainParseTreeChunkListByParsingList("[[[NP-Children CD-four NNS-years JJ-old ]]]");

    chRes = parseTreeMatcher.matchTwoSentencesGroupedChunksDeterministic(lch1,
        lch2);
    System.out.println("generalization result = " + chRes + " score  ="
        + parseTreeChunkListScorer.getParseTreeChunkListScore(chRes));
    assertEquals(chRes.toString(),
        "[[ [NNS-children CD-four NNS-years JJ-old ]]]");
    assertTrue(parseTreeChunkListScorer.getParseTreeChunkListScore(chRes) > 3);

  }

}
