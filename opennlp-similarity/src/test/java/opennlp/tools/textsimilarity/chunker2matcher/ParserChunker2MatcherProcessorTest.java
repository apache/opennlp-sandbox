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
package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.List;

import junit.framework.TestCase;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.TextSimilarityBagOfWords;

public class ParserChunker2MatcherProcessorTest extends TestCase {
  private ParserChunker2MatcherProcessor parser;
  private TextSimilarityBagOfWords parserBOW = new TextSimilarityBagOfWords();
  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();

  public void testGroupedPhrasesFormer() {
    parser = ParserChunker2MatcherProcessor.getInstance();
    String text = "Where do I apply? Go to your town office or city hall. If your town doesn't have an office, ask the town clerk or a Selectman. Tell them that you need a 1040 tax form . I Can 't Pay the Taxes on my House: What Can I Do?. Pine Tree Legal";

    List<List<ParseTreeChunk>> res = parser
        .formGroupedPhrasesFromChunksForPara(text);
    System.out.println(res);
    assertEquals(
        "[[NP [PRP$-your NN-town NN-office CC-or NN-city NN-hall ], NP [PRP$-your NN-town NN-doesn NN-t ], NP [DT-an NN-office ], NP [DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], NP [DT-a NNP-Selectman ], NP [PRP-them IN-that PRP-you ], NP [PRP-you ], NP [DT-a CD-1040 NN-tax NN-form ], NP [PRP-I ], NP [DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], NP [PRP$-my NNP-House WP-What MD-Can PRP-I ], NP [WP-What MD-Can PRP-I ], NP [PRP-I ], NP [NNP-Pine NNP-Tree NNP-Legal ]], [VP [VBP-do RB-I VB-apply ], VP [VB-Go TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], VP [VBP-have DT-an NN-office ], VP [VB-ask DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], VP [VB-Tell PRP-them IN-that PRP-you ], VP [VBP-need DT-a CD-1040 NN-tax NN-form ], VP [MD-Can VB-t VB-Pay DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], VP [VB-Do ]], [PP [TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], PP [IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ]], [], [SENTENCE [WRB-Where VBP-do RB-I VB-apply ], SENTENCE [VB-Go TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], SENTENCE [IN-If PRP$-your NN-town NN-doesn NN-t VBP-have DT-an NN-office VB-ask DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], SENTENCE [VB-Tell PRP-them IN-that PRP-you VBP-need DT-a CD-1040 NN-tax NN-form ], SENTENCE [PRP-I MD-Can VB-t VB-Pay DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I VB-Do ], SENTENCE [NNP-Pine NNP-Tree NNP-Legal ]]]",
        // "[[NP [PRP$-your NN-town NN-office CC-or NN-city NN-hall ], NP [PRP$-your NN-town NN-doesn NN-t ], NP [DT-an NN-office ], NP [DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], NP [DT-a NNP-Selectman ], NP [PRP-them IN-that PRP-you ], NP [PRP-you ], NP [DT-a CD-1040 NN-tax NN-form ], NP [PRP-I ], NP [DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], NP [PRP$-my NNP-House WP-What MD-Can PRP-I ], NP [WP-What MD-Can PRP-I ], NP [PRP-I ], NP [NNP-Pine NNP-Tree NNP-Legal ]], [VP [VBP-do RB-I VB-apply ], VP [VB-Go TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], VP [VBP-have DT-an NN-office ], VP [VB-ask DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], VP [VB-Tell PRP-them IN-that PRP-you ], VP [VBP-need DT-a CD-1040 NN-tax NN-form ], VP [MD-Can VB-t VB-Pay DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], VP [VB-Do NNP-Pine NNP-Tree NNP-Legal ]], [PP [TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], PP [IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ]], [], [SENTENCE [WRB-Where VBP-do RB-I VB-apply ], SENTENCE [VB-Go TO-to PRP$-your NN-town NN-office CC-or NN-city NN-hall ], SENTENCE [IN-If PRP$-your NN-town NN-doesn NN-t VBP-have DT-an NN-office VB-ask DT-the NN-town NN-clerk CC-or DT-a NNP-Selectman ], SENTENCE [VB-Tell PRP-them IN-that PRP-you VBP-need DT-a CD-1040 NN-tax NN-form ], SENTENCE [PRP-I MD-Can VB-t VB-Pay DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I VB-Do NNP-Pine NNP-Tree NNP-Legal ]]]",
        res.toString());

    res = parser
        .formGroupedPhrasesFromChunksForSentence("How can I get short focus zoom lens for digital camera");
    assertEquals(
        "[[NP [PRP-I ], NP [JJ-short NN-focus NN-zoom NN-lens IN-for JJ-digital NN-camera ], NP [JJ-digital NN-camera ]], [VP [VB-get JJ-short NN-focus NN-zoom NN-lens IN-for JJ-digital NN-camera ]], [PP [IN-for JJ-digital NN-camera ]], [], [SENTENCE [WRB-How MD-can PRP-I VB-get JJ-short NN-focus NN-zoom NN-lens IN-for JJ-digital NN-camera ]]]",
        res.toString());

    res = parser
        .formGroupedPhrasesFromChunksForSentence("Its classy design and the Mercedes name make it a very cool vehicle to drive. ");
    assertEquals(
        "[[NP [PRP$-Its JJ-classy NN-design CC-and DT-the NNP-Mercedes NN-name ], NP [DT-the NNP-Mercedes NN-name ], NP [PRP-it DT-a RB-very JJ-cool NN-vehicle TO-to NN-drive ], NP [DT-a RB-very JJ-cool NN-vehicle TO-to NN-drive ], NP [NN-drive ]], [VP [VBP-make PRP-it DT-a RB-very JJ-cool NN-vehicle TO-to NN-drive ]], [PP [TO-to NN-drive ]], [], [SENTENCE [PRP$-Its JJ-classy NN-design CC-and DT-the NNP-Mercedes NN-name VBP-make PRP-it DT-a RB-very JJ-cool NN-vehicle TO-to NN-drive ]]]",
        res.toString());
    res = parser
        .formGroupedPhrasesFromChunksForSentence("Sounds too good to be true but it actually is, the world's first flying car is finally here. ");
    assertEquals(
        "[[NP [PRP-it RB-actually ], NP [DT-the NN-world NNS-s JJ-first NN-flying NN-car ]], [VP [VBZ-Sounds RB-too JJ-good ], VP [TO-to VB-be JJ-true CC-but PRP-it RB-actually ], VP [VBZ-is DT-the NN-world NNS-s JJ-first NN-flying NN-car ], VP [VBZ-is RB-finally RB-here ]], [], [ADJP [RB-too JJ-good ], ADJP [JJ-true CC-but PRP-it RB-actually ]], [SENTENCE [VBZ-Sounds RB-too JJ-good TO-to VB-be JJ-true CC-but PRP-it RB-actually VBZ-is DT-the NN-world NNS-s JJ-first NN-flying NN-car VBZ-is RB-finally RB-here ]]]",
        res.toString());
    res = parser
        .formGroupedPhrasesFromChunksForSentence("UN Ambassador Ron Prosor repeated the Israeli position that the only way the Palestinians will get UN membership and statehood is through direct negotiations with the Israelis on a comprehensive peace agreement");
    assertEquals(
        "[[NP [NNP-UN NNP-Ambassador NNP-Ron NNP-Prosor ], NP [DT-the JJ-Israeli NN-position IN-that DT-the JJ-only NN-way DT-the NNPS-Palestinians ], NP [DT-the JJ-only NN-way DT-the NNPS-Palestinians ], NP [DT-the NNPS-Palestinians ], NP [NN-membership CC-and NN-statehood VBZ-is IN-through JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], NP [JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], NP [DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], NP [DT-a JJ-comprehensive NN-peace NN-agreement ]], [VP [VBD-repeated DT-the JJ-Israeli NN-position IN-that DT-the JJ-only NN-way DT-the NNPS-Palestinians ], VP [MD-will VB-get IN-UN NN-membership CC-and NN-statehood VBZ-is IN-through JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ]], [PP [IN-that DT-the JJ-only NN-way DT-the NNPS-Palestinians ], PP [IN-UN NN-membership CC-and NN-statehood VBZ-is IN-through JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], PP [IN-through JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], PP [IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ], PP [IN-on DT-a JJ-comprehensive NN-peace NN-agreement ]], [], [SENTENCE [NNP-UN NNP-Ambassador NNP-Ron NNP-Prosor VBD-repeated DT-the JJ-Israeli NN-position IN-that DT-the JJ-only NN-way DT-the NNPS-Palestinians MD-will VB-get IN-UN NN-membership CC-and NN-statehood VBZ-is IN-through JJ-direct NNS-negotiations IN-with DT-the NNP-Israelis IN-on DT-a JJ-comprehensive NN-peace NN-agreement ]]]",
        res.toString());
    parser.close();
  }

  public void testPrintParseTree() {
    parser = ParserChunker2MatcherProcessor.getInstance();
    try {
      parser
          .printParseTree("How can I get short focus zoom lens for digital camera");
    } catch (Exception e) {
      // when models does not read
    }
    parser.close();
  }

  public void testRelevanceAssessm() {
    parser = ParserChunker2MatcherProcessor.getInstance();
    String phrase1 = "Its classy design and the Mercedes name make it a very cool vehicle to drive. "
        + "The engine makes it a powerful car. "
        + "The strong engine gives it enough power. "
        + "The strong engine gives the car a lot of power.";
    String phrase2 = "This car has a great engine. "
        + "This car has an amazingly good engine. "
        + "This car provides you a very good mileage.";

    System.out.println(parser.assessRelevance(phrase1, phrase2)
        .getMatchResult());
    parser.close();

  }

  public void testCompareRelevanceAssessmWithBagOfWords() {
    parser = ParserChunker2MatcherProcessor.getInstance();
    // we first demonstrate how similarity expression for DIFFERENT cases have
    // too high score for bagOfWords
    String phrase1 = "How to deduct rental expense from income ";
    String phrase2 = "How to deduct repair expense from rental income.";
    List<List<ParseTreeChunk>> matchResult = parser.assessRelevance(phrase1,
        phrase2).getMatchResult();
    assertEquals(
        matchResult.toString(),
        "[[ [NN-expense IN-from NN-income ],  [JJ-rental NN-* ],  [NN-income ]], [ [TO-to VB-deduct JJ-rental NN-* ],  [VB-deduct NN-expense IN-from NN-income ]]]");
    System.out.println(matchResult);
    double matchScore = parseTreeChunkListScorer
        .getParseTreeChunkListScore(matchResult);
    double bagOfWordsScore = parserBOW.assessRelevanceAndGetScore(phrase1,
        phrase2);
    assertTrue(matchScore + 2 < bagOfWordsScore);
    System.out.println("MatchScore is adequate ( = " + matchScore
        + ") and bagOfWordsScore = " + bagOfWordsScore + " is too high");

    // we now demonstrate how similarity can be captured by POS and cannot be
    // captured by bagOfWords
    phrase1 = "Way to minimize medical expense for my daughter";
    phrase2 = "Means to deduct educational expense for my son";
    matchResult = parser.assessRelevance(phrase1, phrase2).getMatchResult();
    assertEquals(
        matchResult.toString(),
        "[[ [JJ-* NN-expense IN-for PRP$-my NN-* ],  [PRP$-my NN-* ]], [ [TO-to VB-* JJ-* NN-expense IN-for PRP$-my NN-* ]]]");
    System.out.println(matchResult);
    matchScore = parseTreeChunkListScorer
        .getParseTreeChunkListScore(matchResult);
    bagOfWordsScore = parserBOW.assessRelevanceAndGetScore(phrase1, phrase2);
    assertTrue(matchScore > 2 * bagOfWordsScore);
    System.out.println("MatchScore is adequate ( = " + matchScore
        + ") and bagOfWordsScore = " + bagOfWordsScore + " is too low");
    parser.close();

  }
}
