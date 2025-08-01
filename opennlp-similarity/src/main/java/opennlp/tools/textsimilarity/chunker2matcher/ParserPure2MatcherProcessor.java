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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;

public class ParserPure2MatcherProcessor extends ParserChunker2MatcherProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static ParserPure2MatcherProcessor pInstance;

  public synchronized static ParserPure2MatcherProcessor getInstance() {
    if (pInstance == null)
      pInstance = new ParserPure2MatcherProcessor();

    return pInstance;
  }

  private ParserPure2MatcherProcessor() {
    try {
      initializeTokenizer();
      initializeParser();
    } catch (IOException e) {
      LOG.warn("A model can't be loaded: {}", e.getMessage());
    }
  }

  @Override
  public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForSentence(String sentence) {
    if (sentence == null || sentence.trim().length() < MIN_SENTENCE_LENGTH)
      return null;

    sentence = TextProcessor.removePunctuation(sentence);
    SentenceNode node = parseSentenceNode(sentence);
    if (node == null) {
      LOG.info("Problem parsing sentence '{}'", sentence);
      return null;
    }
    List<ParseTreeChunk> ptcList = node.getParseTreeChunkList();
    List<String> POSlist = node.getOrderedPOSList();
    List<String> TokList = node.getOrderedLemmaList();

    List<List<ParseTreeChunk>> listOfChunks = new ArrayList<>();
    List<ParseTreeChunk> nounPhr = new ArrayList<>(), prepPhr = new ArrayList<>(),
                         verbPhr = new ArrayList<>(), adjPhr = new ArrayList<>(),
    // to store the whole sentence
    wholeSentence = new ArrayList<>();

    wholeSentence.add(new ParseTreeChunk("SENTENCE", TokList, POSlist));
    for (ParseTreeChunk phr : ptcList) {
      String phrType = phr.getMainPOS();
      if (phrType.startsWith(NP)) {
        nounPhr.add(phr);
      } else if (phrType.startsWith(VP)) {
        verbPhr.add(phr);
      } else if (phrType.startsWith(PP)) {
        prepPhr.add(phr);
      } else if (phrType.endsWith(ADJP)) {
        adjPhr.add(phr);
      } else {
        // LOG.info("Unexpected phrase type found :"+ phr);
      }

    }

    listOfChunks.add(nounPhr);
    listOfChunks.add(verbPhr);
    listOfChunks.add(prepPhr);
    listOfChunks.add(adjPhr);
    listOfChunks.add(wholeSentence);

    return listOfChunks;
  }

  @Override
  public SentencePairMatchResult assessRelevance(String para1, String para2) {
    return super.assessRelevance(para1, para2);
  }

  public static void main(String[] args) throws Exception {
    ParserPure2MatcherProcessor parser = ParserPure2MatcherProcessor.getInstance();
    String text = "Its classy design and the Mercedes name make it a very cool vehicle to drive. ";

    List<List<ParseTreeChunk>> res = parser.formGroupedPhrasesFromChunksForPara(text);
    System.out.println(res);

    String phrase1 = "Its classy design and the Mercedes name make it a very cool vehicle to drive. "
        + "The engine makes it a powerful car. "
        + "The strong engine gives it enough power. "
        + "The strong engine gives the car a lot of power.";
    String phrase2 = "This car has a great engine. "
        + "This car has an amazingly good engine. "
        + "This car provides you a very good mileage.";
    String sentence = "Not to worry with the 2cv.";

    System.out.println(parser.assessRelevance(phrase1, phrase2).getMatchResult());

    System.out.println(parser.formGroupedPhrasesFromChunksForSentence(
            "Its classy design and the Mercedes name make it a very cool vehicle to drive. "));
    System.out.println(parser.formGroupedPhrasesFromChunksForSentence(
            "Sounds too good to be true but it actually is, the world's first flying car is finally here. "));
    System.out.println(parser.formGroupedPhrasesFromChunksForSentence(
            "UN Ambassador Ron Prosor repeated the Israeli position that the only way the Palestinians will get " +
            "UN membership and statehood is through direct negotiations with the Israelis on a comprehensive peace agreement"));

  }
}
