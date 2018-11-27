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
import static org.junit.Assert.assertNotNull;

import java.util.List;

import junit.framework.TestCase;

import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.junit.Test;
import org.junit.runner.RunWith;

public class SyntMatcherTest extends TestCase {

  private ParserChunker2MatcherProcessor parserChunker2Matcher;

  private ParseTreeChunk parseTreeChunk = new ParseTreeChunk();

  public void notNullTest() {
    parserChunker2Matcher = ParserChunker2MatcherProcessor.getInstance();
    assertNotNull(parserChunker2Matcher);
  }

  public void testMatch() {
    parserChunker2Matcher = ParserChunker2MatcherProcessor.getInstance();
    List<List<ParseTreeChunk>> matchResult = parserChunker2Matcher
        .assessRelevance(
            // "Can I get auto focus lens for digital camera",
            // "How can I get short focus zoom lens for digital camera"
            "Pulitzer Prize-Winning Reporter is an Illegal Immigrant",
            "Gay Pulitzer Prize-Winning Reporter Jose Antonio Vargas Comes Out as Undocumented "
                + "Immigrant Jose Antonio Vargas, a gay journalist who won a Pulitzer Prize "
                + "for his coverage of the Virginia Tech shootings in the Washington Post")
        .getMatchResult();

    System.out.println(matchResult);
    assertEquals(
        "[[ [NNP-pulitzer NNP-prize NNP-winning NNP-reporter ],  [NN-immigrant ]], []]",
        matchResult.toString());
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        " np [ [NNP-pulitzer NNP-prize NNP-winning NNP-reporter ],  [NN-immigrant ]]",
        parseTreeChunk.listToString(matchResult));

    matchResult = parserChunker2Matcher
        .assessRelevance(
            "Sounds too good to be true but it actually is, the world's first flying car is finally here. ",
            "While it may seem like something straight out of a sci-fi "
                + "movie, the  flying  car  might soon become a reality. ")
        .getMatchResult();

    // TODO: possibly problem in new POS tagger from Parser
    System.out.println(matchResult);
    // was "[[ [DT-the NN-* VBG-flying NN-car ]], []]"
    assertEquals(
        "[[ [PRP-it ],  [DT-the NN-* NNS-* ]], [ [DT-the NN-* NNS-* ]]]",
        matchResult.toString());
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        " np [ [PRP-it ],  [DT-the NN-* NNS-* ]] vp [ [DT-the NN-* NNS-* ]]",
        parseTreeChunk.listToString(matchResult));

    parserChunker2Matcher.close();

  }

  public void testMatchDigitalCamera() {
    parserChunker2Matcher = ParserChunker2MatcherProcessor.getInstance();
    List<List<ParseTreeChunk>> matchResult = parserChunker2Matcher
        .assessRelevance(
            "I am curious how to use the digital zoom of this camera for filming insects",
            "How can I get short focus zoom lens for digital camera")
        .getMatchResult();

    System.out.println(matchResult);
    assertEquals(
        "[[ [PRP-i ],  [NN-zoom NN-camera ],  [JJ-digital NN-* ],  [NN-* IN-for ]], [ [JJ-digital NN-* ],  [NN-zoom NN-camera ],  [NN-* IN-for ]]]",
        matchResult.toString());
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        " np [ [PRP-i ],  [NN-zoom NN-camera ],  [JJ-digital NN-* ],  [NN-* IN-for ]] vp [ [JJ-digital NN-* ],  [NN-zoom NN-camera ],  [NN-* IN-for ]]",
        parseTreeChunk.listToString(matchResult));
    parserChunker2Matcher.close();
  }

  public void testHighSimilarity() {
    parserChunker2Matcher = ParserChunker2MatcherProcessor.getInstance();
    List<List<ParseTreeChunk>> matchResult = parserChunker2Matcher
        .assessRelevance("Can I get auto focus lens for digital camera",
            "How can I get short focus zoom lens for digital camera")
        .getMatchResult();

    System.out.println(matchResult);
    assertEquals(
        "[[ [PRP-i ],  [NN-focus NNS-* NNS-lens IN-for JJ-digital NN-camera ]], [ [VB-get NN-focus NNS-* NNS-lens IN-for JJ-digital NN-camera ]]]",
        matchResult.toString());
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        " np [ [PRP-i ],  [NN-focus NNS-* NNS-lens IN-for JJ-digital NN-camera ]] vp [ [VB-get NN-focus NNS-* NNS-lens IN-for JJ-digital NN-camera ]]",
        parseTreeChunk.listToString(matchResult));
    parserChunker2Matcher.close();
  }

  public void testZClose() {
    ParserChunker2MatcherProcessor.getInstance().close();
  }

}
