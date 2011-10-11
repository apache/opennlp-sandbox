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

import org.junit.Test;
import org.junit.runner.RunWith;

public class SyntMatcherTest {

  private SyntMatcher syntMatcher;

  private ParseTreeChunk parseTreeChunk = new ParseTreeChunk();

  @Test
  public void notNullTest() {
    syntMatcher = SyntMatcher.getInstance();
    assertNotNull(syntMatcher);
  }

  @Test
  public void matchTest() {
    syntMatcher = SyntMatcher.getInstance();
    List<List<ParseTreeChunk>> matchResult = syntMatcher
        .matchOrigSentencesCache(
            // "Can I get auto focus lens for digital camera",
            // "How can I get short focus zoom lens for digital camera"
            "Pulitzer Prize-Winning Reporter is an Illegal Immigrant",
            "Gay Pulitzer Prize-Winning Reporter Jose Antonio Vargas Comes Out as Undocumented Immigrant Jose Antonio Vargas, a gay journalist who won a Pulitzer Prize for his coverage of the Virginia Tech shootings in the Washington Post");

    System.out.println(matchResult);
    assertEquals(
        matchResult.toString(),
        "[[ [NNP-pulitzer JJ-prize-winning NN-reporter ],  [NNP-* ],  [JJ-* NN-immigrant ]], [ [JJ-* NN-immigrant ]]]");
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        parseTreeChunk.listToString(matchResult),
        " np [ [NNP-pulitzer JJ-prize-winning NN-reporter ],  [NNP-* ],  [JJ-* NN-immigrant ]] vp [ [JJ-* NN-immigrant ]]");

    matchResult = syntMatcher
        .matchOrigSentencesCache(
            "Sounds too good to be true but it actually is, the world's first flying car is finally here. ",
            "While it may seem like something straight out of a sci-fi movie, the  flying  car  might soon become a reality. ");

    System.out.println(matchResult);
    assertEquals(matchResult.toString(),
        "[[ [DT-the NN-* VBG-flying NN-car ]], []]");
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(parseTreeChunk.listToString(matchResult),
        " np [ [DT-the NN-* VBG-flying NN-car ]]");

  }

  @Test
  public void matchTestDigitalCamera() {
    syntMatcher = SyntMatcher.getInstance();
    List<List<ParseTreeChunk>> matchResult = syntMatcher
        .matchOrigSentencesCache(
            "I am curious how to use the digital zoom of this camera for filming insects",
            "How can I get short focus zoom lens for digital camera");

    System.out.println(matchResult);
    assertEquals(
        matchResult.toString(),
        "[[ [NN-zoom ],  [JJ-digital NN-* ],  [NN-camera ]], [ [JJ-digital NN-* ],  [NN-zoom NN-camera ],  [NN-* IN-for ]]]");
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        parseTreeChunk.listToString(matchResult),
        " np [ [NN-zoom ],  [JJ-digital NN-* ],  [NN-camera ]] vp [ [JJ-digital NN-* ],  [NN-zoom NN-camera ],  [NN-* IN-for ]]");

    matchResult = syntMatcher.matchOrigSentencesCache(
        "Can I get auto focus lens for digital camera",
        "How can I get short focus zoom lens for digital camera");

    System.out.println(matchResult);
    assertEquals(
        matchResult.toString(),
        "[[ [NN-focus NN-* ],  [JJ-digital NN-camera ]], [ [VB-get NN-focus NN-* NN-lens IN-for JJ-digital NN-camera ]]]");
    System.out.println(parseTreeChunk.listToString(matchResult));
    assertEquals(
        parseTreeChunk.listToString(matchResult),
        " np [ [NN-focus NN-* ],  [JJ-digital NN-camera ]] vp [ [VB-get NN-focus NN-* NN-lens IN-for JJ-digital NN-camera ]]");

  }

}
