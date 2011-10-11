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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zvents.ce.common.util.ValueSortMap;

@Component
public class ParagraphClassifier {
  @Autowired
  private SyntMatcher processor;

  @Autowired
  private ParseTreeChunkListScorer parseTreeChunkListScorer;

  @Autowired
  private ParseTreeChunk parseTreeChunk;

  @Autowired
  private ParseTreeChunkFactory parseTreeChunkFactory;

  public ParagraphClassifier() {
  }

  // gets two paragraphs, one to be classified (each sentence to be assigned as
  // closest
  // representative out of the sentences of second paragraph)
  public Map<String, List<LemmaPair>> findMappingBetweenSentencesOfAParagraphAndAClassReps(
      String para1, // input
      // paragraph
      // of
      // sentences
      String classStr) { // training dataset of sentences - class
                         // representatives
                         // profile of matches
    List<List<List<ParseTreeChunk>>> matchResultPerSentence = new ArrayList<List<List<ParseTreeChunk>>>();

    ParseTreeChunk matcher = parseTreeChunkFactory.getParseTreeChunk();

    // splitting into sentences
    String[] sents = processor.getSentenceDetectorME().sentDetect(para1);
    String[] classSents = processor.getSentenceDetectorME()
        .sentDetect(classStr);

    List<List<LemmaPair>> parseSentList = new ArrayList<List<LemmaPair>>();
    for (String s : sents) {
      parseSentList.add(processor.getAllPhrasesTWPairs((processor.parseLine(s,
          processor.getParser(), 1)[0])));
    }

    List<List<LemmaPair>> parseClassList = new ArrayList<List<LemmaPair>>();
    for (String s : classSents) {
      parseClassList.add(processor.getAllPhrasesTWPairs((processor.parseLine(s,
          processor.getParser(), 1)[0])));

    }

    Map<Integer, Integer> sentID_classID = new HashMap<Integer, Integer>();
    Map<String, List<LemmaPair>> sentence_bestClassRep = new HashMap<String, List<LemmaPair>>();
    Map<String, List<List<ParseTreeChunk>>> sentence_generalization = new HashMap<String, List<List<ParseTreeChunk>>>();
    int sN = 0;
    for (List<LemmaPair> chunksSent : parseSentList) {
      Double maxScore = -1.0;
      int cN = 0;
      for (List<LemmaPair> chunksClass : parseClassList) {
        List<List<ParseTreeChunk>> matchResult = matcher
            .matchTwoSentencesGivenPairLists(chunksSent, chunksClass);
        Double score = parseTreeChunkListScorer
            .getParseTreeChunkListScore(matchResult);
        if (score > maxScore) {
          maxScore = score;
          sentence_bestClassRep.put(chunksSent.toString(), chunksClass);
          sentence_generalization.put(sents[sN], matchResult);
          sentID_classID.put(sN, cN);
        }
        cN++;
      }
      sN++;
    }

    return sentence_bestClassRep;

  }

  public Double findShortestDistanceToClass(String para1, String trainingSet,
      String className) {
    List<List<List<ParseTreeChunk>>> matchResultPerSentence = new ArrayList<List<List<ParseTreeChunk>>>();

    ParseTreeChunk matcher = new ParseTreeChunk();

    // splitting into sentences
    String[] sents = processor.getSentenceDetectorME().sentDetect(para1);
    String[] classSents = processor.getSentenceDetectorME().sentDetect(
        trainingSet);

    List<List<LemmaPair>> parseSentList = new ArrayList<List<LemmaPair>>();
    for (String s : sents) {
      parseSentList.add(processor.getAllPhrasesTWPairs((processor.parseLine(s,
          processor.getParser(), 1)[0])));
    }

    List<List<LemmaPair>> parseClassList = new ArrayList<List<LemmaPair>>();
    for (String s : classSents) {
      parseClassList.add(processor.getAllPhrasesTWPairs((processor.parseLine(s,
          processor.getParser(), 1)[0])));

    }

    Map<Integer, Integer> sentID_classID = new HashMap<Integer, Integer>();
    Map<String, List<LemmaPair>> sentence_bestClassRep = new HashMap<String, List<LemmaPair>>();
    Map<String, List<List<ParseTreeChunk>>> sentence_generalization = new HashMap<String, List<List<ParseTreeChunk>>>();
    Map<Integer, Double> sentID_score = new HashMap<Integer, Double>();
    int sN = 0;
    for (List<LemmaPair> chunksSent : parseSentList) {
      Double maxScore = -1.0;
      int cN = 0;
      String bestSent = "", bestClass = "";
      List<List<ParseTreeChunk>> bestMatchResult = null;
      for (List<LemmaPair> chunksClass : parseClassList) {
        List<List<ParseTreeChunk>> matchResult = matcher
            .matchTwoSentencesGivenPairLists(chunksSent, chunksClass);
        Double score = parseTreeChunkListScorer
            .getParseTreeChunkListScore(matchResult);
        if (score > maxScore) {
          maxScore = score;
          sentence_bestClassRep.put(chunksSent.toString(), chunksClass);
          sentence_generalization.put(sents[sN], matchResult);
          sentID_classID.put(sN, cN);
          sentID_score.put(sN, score);
          bestSent = sents[sN];
          bestClass = classSents[cN];
          bestMatchResult = matchResult;
        }
        cN++;
      }
      if (maxScore > 1.6) {
        System.out.println("Best match:" + bestSent + " <x> " + bestClass
            + " of class= " + className.toUpperCase() + " score =" + maxScore
            + " " + parseTreeChunk.listToString(bestMatchResult));
        System.out.println("");
      }
      sN++;
    }

    // now get the average of highest three (if exists- matching score to judge
    // if
    // para belongs to a class
    List<Double> scoreValues = new ArrayList<Double>(sentID_score.values());
    Collections.sort(scoreValues, Collections.reverseOrder());
    if (scoreValues.size() > 2)
      scoreValues = scoreValues.subList(0, 3);
    Double sum = 0.0;
    int count = 0;
    for (Double sc : scoreValues) {
      sum += sc;
      count++;
    }
    return scoreValues.get(0); // sum/(1.0*count);
  }

  public List<String> findClassesForPara(String para) {
    List<String> resultantClasses = new ArrayList<String>();
    Map<String, Double> class_score = new HashMap<String, Double>();
    Double classTHRESH = 1.79;
    List<String> classNames = new ArrayList<String>(
        EpistemicStatesTrainingSet.class_setOfSentences.keySet());
    for (String clName : classNames) {
      String trainingSet = EpistemicStatesTrainingSet.class_setOfSentences
          .get(clName);
      if (trainingSet == null)
        System.err.println("Wrong EpistemicStatesTrainingSet for class = "
            + clName);
      Double scoreAvg = findShortestDistanceToClass(para, trainingSet, clName);
      if (scoreAvg > classTHRESH) {
        resultantClasses.add(clName);
        class_score.put(clName, scoreAvg);
      }
    }
    Map sortedMap = ValueSortMap.sortMapByValue(class_score, false);

    System.out.println(sortedMap);
    return resultantClasses;

  }

}

/*
 * 
 * I removed abberation by digital zoom increase by performance limitation of
 * filters of my camera. =[[ [JJ-digital NN-zoom NN-* ], [PRP$-my NN-camera ]],
 * [ [VBD-* JJ-digital NN-zoom NN-* IN-by NP-filters IN-* PRP$-my NN-camera ],
 * [NP-filters TO-* PRP$-my NN-camera ]], [], [ [IN-by NP-filters TO-* PRP$-my
 * NN-camera ], [TO-* PRP$-my NN-camera ]], [], [], [ [NP-I VBD-* JJ-digital
 * NN-zoom NN-* IN-by NP-filters IN-* PRP$-my NN-camera .-. ], [NP-filters TO-*
 * PRP$-my NN-camera ]]],
 * 
 * 
 * Animals run to the tiger zoo. =[[], [ [VB-run IN-* NP-tigers NP-zoo ]], [], [
 * [IN-* NP-zoo ], [IN-* NP-tigers ]], [], [], [ [NP-* VBP-* TO-to NP-tigers
 * NP-zoo ], [VB-run IN-* NP-tigers NP-zoo ]]],
 * 
 * In this digital camera you can turn your ldc screen away from the scene.= [[
 * [NN-* ], [DT-* NN-* ], [DT-* JJ-* NN-* ], [PRP$-your NN-ldc NN-screen ],
 * [DT-the NN-scene ]], [ [MD-can VB-turn PRP$-your NN-ldc NN-screen RB-away
 * IN-from DT-the NN-scene ]], [], [ [IN-* DT-* NN-* ], [IN-from DT-the NN-scene
 * ]], [], [], [ [NP-* MD-can VB-turn PRP$-your NN-ldc NN-screen RB-away IN-from
 * DT-the NN-scene ], [IN-* DT-* JJ-* NN-* ], [NP-* VBZ-* NN-* ]]],
 * 
 * I can easily connect this digital camera to my desktop computer to copy
 * images. =[[ [DT-this JJ-digital NN-camera ], [PRP$-my NN-* NN-computer ]], [
 * [VBD-* DT-this JJ-digital NN-camera TO-to PRP$-my NN-* NN-computer NN-* ]],
 * [], [ [TO-to PRP$-my NN-* NN-computer ]], [], [], [ [NP-I VBD-* DT-this
 * JJ-digital NN-camera TO-to PRP$-my NN-* NN-computer NN-* .-. ]]],
 * 
 * I want this nice radio thing. =[[ [DT-this JJ-nice NN-thing ]], [ [VB-want
 * DT-this JJ-nice NN-thing ]], [], [], [], [], [ [NP-* VB-want DT-this JJ-nice
 * NN-thing .-. ]]],
 * 
 * This digital camera nicely fits in my palm and the body is not heavy. = [[
 * [DT-* JJ-digital NN-camera ], [PRP$-my NN-* ], [PRP$-my NN-palm ]], [ [VBZ-*
 * TO-* PRP$-my NN-palm ], [VBZ-is NN-* ]], [], [ [IN-* PRP$-my NN-* ], [TO-*
 * PRP$-my NN-palm ]], [], [], [ [DT-* JJ-digital NN-camera IN-* PRP$-my NN-*
 * DT-* NN-* ADJP-* .-. ], [DT-* JJ-digital NN-camera VBZ-* IN-* PRP$-my NN-palm
 * ], [DT-* NN-* VBZ-is NN-* ]]],
 * 
 * I told my wife to film me at a speed while on a boat. =[[ [PRP$-* NN-wife ],
 * [DT-a NN-speed ]], [ [VBD-* TO-to VB-film NP-me DT-a NN-speed ], [VB-film
 * NP-me NP-* IN-* DT-a NN-* ], [VBD-* VBG-* IN-at DT-a NN-speed ], [TO-to
 * VB-film NP-me NP-* IN-* DT-a NN-* ], [TO-to VB-film NP-me IN-at DT-a NN-speed
 * ], [VBG-* IN-at DT-a NN-speed ]], [], [ [IN-at DT-a NN-speed ]], [], [], [
 * [PRP$-* NN-wife TO-to VB-film NP-me IN-at DT-a NN-speed ], [TO-to VB-film
 * NP-me NP-* IN-* DT-a NN-* ], [NP-* VBD-* VB-* IN-at DT-a NN-speed ], [VBG-*
 * IN-at DT-a NN-speed ]]],
 * 
 * I have to frequently change batteries in digital camera. = [[ [JJ-digital
 * NN-* ], [NN-camera ]], [ [VBP-* TO-* VB-* NP-* IN-* NN-camera ], [VBG-* NP-*
 * TO-* NN-camera ]], [], [ [IN-* NN-camera ], [TO-* NN-camera ]], [], [], [
 * [NP-I VBP-* TO-* VB-* NP-* IN-* NN-camera .-. ], [VBG-* TO-to NN-camera ],
 * [VBG-* NP-* TO-* NN-camera ]]],
 * 
 * I enjoyed the digital zoom of this camera because I can quickly adjust for
 * shots far away. =[[ [DT-* NN-* ], [DT-the NN-* IN-of DT-this NN-camera ],
 * [DT-* JJ-digital NN-camera ], [JJ-* NN-* NNS-* ], [DT-the JJ-digital NN-* ]],
 * [ [DT-the NN-* IN-of DT-this NN-camera IN-because NP-I MD-* VB-* NN-* ],
 * [VB-* JJ-* NN-* NNS-* ], [VB-* IN-* NP-* ]], [], [IN-of DT-this NN-camera ]],
 * [], [], [ [NP-I VBD-* DT-the NN-* IN-of DT-this NN-camera IN-because NP-I
 * MD-* VB-* .-. ], [IN-because NP-I MD-* VB-* NN-* ], [NP-I VB-* JJ-* NN-*
 * NNS-* ]]],
 * 
 * Nice to hear interesting programs about animals. =[[ [JJ-* NNS-programs IN-*
 * NP-animals ]], [ [TO-to VB-hear JJ-* NNS-programs IN-* NP-animals ]], [], [
 * [IN-* NP-animals ]], [ [JJ-* TO-to VB-hear JJ-* NNS-programs IN-* NP-animals
 * ]], [], [ [TO-to VB-hear JJ-* NNS-programs IN-* NP-animals ]]],
 * 
 * I like it because radio is loud. = [[ [NN-* NN-* ]], [ [VBZ-is NN-* ]], [], [
 * [IN-* NN-* ]], [], [], [ [NN-* IN-* NN-* IN-because NP-* VBZ-is NN-* .-. ],
 * [NP-* VBZ-is NN-* ]]]}
 */