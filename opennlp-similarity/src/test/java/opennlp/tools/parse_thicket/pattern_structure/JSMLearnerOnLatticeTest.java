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

package opennlp.tools.parse_thicket.pattern_structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.junit.jupiter.api.Test;

class JSMLearnerOnLatticeTest {
  private final ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
  private final LinguisticPatternStructure psPos = new LinguisticPatternStructure(0, 0), psNeg = new LinguisticPatternStructure(0, 0);
  private final ParseTreeMatcherDeterministic md = new ParseTreeMatcherDeterministic();

  @Test
  void testJSMLearner() {

    String text1p = "I rent an office space. This office is for my business. I can deduct office rental expense from my business profit to calculate net income. ";
    String text2p = "To run my business, I have to rent an office. The net business profit is calculated as follows. Rental expense needs to be subtracted from revenue. ";
    String text3p = "To store goods for my retail business I rent some space. When I calculate the net income, I take revenue and subtract business expenses such as office rent. ";
    String text4p = "I rent some space for my business. To calculate my net income, I subtract from revenue my rental business expense.";


    String text1n = "I rent out a first floor unit of my house to a travel business. I need to add the rental income to my profit. However, when I repair my house, I can deduct the repair expense from my rental income. ";
    String text2n = "I receive rental income from my office. I have to claim it as a profit in my tax forms. I need to add my rental income to my profits, but subtract rental expenses such as repair from it. ";
    String text3n = "I advertised my property as a business rental. Advertisement and repair expenses can be subtracted from the rental income. Remaining rental income needs to be added to my profit and be reported as taxable profit. ";
    String text4n = "I showed  my property to a business owner to rent. Expenses on my time spent on advertisement are subtracted from the rental income. My rental profits are added to my taxable income.  ";

    List<List<ParseTreeChunk>> chunks1p = chunk_maker.formGroupedPhrasesFromChunksForPara(text1p);
    List<List<ParseTreeChunk>> chunks2p = chunk_maker.formGroupedPhrasesFromChunksForPara(text2p);
    List<List<ParseTreeChunk>> chunks3p = chunk_maker.formGroupedPhrasesFromChunksForPara(text3p);
    List<List<ParseTreeChunk>> chunks4p = chunk_maker.formGroupedPhrasesFromChunksForPara(text4p);
    List<List<ParseTreeChunk>> chunks1n = chunk_maker.formGroupedPhrasesFromChunksForPara(text1n);
    List<List<ParseTreeChunk>> chunks2n = chunk_maker.formGroupedPhrasesFromChunksForPara(text2n);
    List<List<ParseTreeChunk>> chunks3n = chunk_maker.formGroupedPhrasesFromChunksForPara(text3n);
    List<List<ParseTreeChunk>> chunks4n = chunk_maker.formGroupedPhrasesFromChunksForPara(text4n);


    LinkedHashSet<Integer> obj;
    obj = new LinkedHashSet<>();
    obj.add(0);
    psPos.AddIntent(chunks1p, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(1);
    psPos.AddIntent(chunks2p, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(2);
    psPos.AddIntent(chunks3p, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(3);
    psPos.AddIntent(chunks4p, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(0);
    psNeg.AddIntent(chunks1n, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(1);
    psNeg.AddIntent(chunks2n, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(2);
    psNeg.AddIntent(chunks3n, obj, 0);
    obj = new LinkedHashSet<>();
    obj.add(3);
    psNeg.AddIntent(chunks4n, obj, 0);

    String unknown = "I do not want to rent anything to anyone. I just want to rent a space for myself. I neither calculate deduction of individual or business tax. I subtract my tax from my income";
    List<List<ParseTreeChunk>> chunksUnknown = chunk_maker.formGroupedPhrasesFromChunksForPara(unknown);
    List<List<List<ParseTreeChunk>>> posIntersections = new ArrayList<>(),
        negIntersections = new ArrayList<>();
    List<List<ParseTreeChunk>> intersection;
    for (int iConcept = 0; iConcept < psPos.conceptList.size(); iConcept++) {
      if (psPos.conceptList.get(iConcept).intent != null && psPos.conceptList.get(iConcept).intent.size() > 0) {
        intersection = md
            .matchTwoSentencesGroupedChunksDeterministic(psPos.conceptList.get(iConcept).intent, chunksUnknown);
		  if (reduceList(intersection).size() > 0) {
			  posIntersections.add(reduceList(intersection));
		  }
      }
      if (psNeg.conceptList.get(iConcept).intent != null && psNeg.conceptList.get(iConcept).intent.size() > 0) {
        intersection = md
            .matchTwoSentencesGroupedChunksDeterministic(psNeg.conceptList.get(iConcept).intent, chunksUnknown);
		  if (reduceList(intersection).size() > 0) {
			  negIntersections.add(reduceList(intersection));
		  }
      }
    }

    Pair<List<List<List<ParseTreeChunk>>>, List<List<List<ParseTreeChunk>>>> pair =
        removeInconsistenciesFromPosNegIntersections(posIntersections,
            negIntersections);

    posIntersections = pair.getFirst();
    negIntersections = pair.getSecond();

    List<List<List<ParseTreeChunk>>> posIntersectionsUnderNeg = new ArrayList<>(),
        negIntersectionsUnderPos = new ArrayList<>();

    for (int iConcept = 0; iConcept < psNeg.conceptList.size(); iConcept++) {
      for (List<List<ParseTreeChunk>> negIntersection : negIntersections) {
        intersection = md
            .matchTwoSentencesGroupedChunksDeterministic(psNeg.conceptList.get(iConcept).intent, negIntersection);
		  if (reduceList(intersection).size() > 0) {
			  posIntersectionsUnderNeg.add(reduceList(intersection));
		  }
      }
    }

    for (int iConcept = 0; iConcept < psPos.conceptList.size(); iConcept++) {
      for (List<List<ParseTreeChunk>> posIntersection : posIntersections) {
        intersection = md
            .matchTwoSentencesGroupedChunksDeterministic(psPos.conceptList.get(iConcept).intent, posIntersection);
		  if (reduceList(intersection).size() > 0) {
			  negIntersectionsUnderPos.add(reduceList(intersection));
		  }
      }
    }

    List<ParseTreeChunk> posIntersectionsUnderNegLst = flattenParseTreeChunkLst(posIntersectionsUnderNeg);
    List<ParseTreeChunk> negIntersectionsUnderPosLst = flattenParseTreeChunkLst(negIntersectionsUnderPos);

    posIntersectionsUnderNegLst = subtract(posIntersectionsUnderNegLst, negIntersectionsUnderPosLst);
    negIntersectionsUnderPosLst = subtract(negIntersectionsUnderPosLst, posIntersectionsUnderNegLst);

    // System.out.println("Pos - neg inters = "+posIntersectionsUnderNegLst);
    // System.out.println("Neg - pos inters = "+negIntersectionsUnderPosLst);

  }

  private List<List<ParseTreeChunk>> reduceList(List<List<ParseTreeChunk>> list) {
    float minScore = 1.3f;
    List<List<ParseTreeChunk>> newList = new ArrayList<>();


    ParseTreeChunkListScorer scorer = new ParseTreeChunkListScorer();
    for (List<ParseTreeChunk> group : list) {
      List<ParseTreeChunk> newGroup = new ArrayList<>();
      for (ParseTreeChunk ch : group) {
		  if (scorer.getScore(ch) > minScore) {
			  newGroup.add(ch);
		  }
      }
		if (newGroup.size() > 0) {
			newList.add(newGroup);
		}
    }

    return newList;

  }

  private List<List<ParseTreeChunk>> flattenParseTreeChunkListList(List<List<List<ParseTreeChunk>>> listOfLists) {
    List<List<ParseTreeChunk>> newList = new ArrayList<>();

    for (List<List<ParseTreeChunk>> member : listOfLists) {
      Set<ParseTreeChunk> newSet = new HashSet<>();
      for (List<ParseTreeChunk> group : member) {
		  if (group.size() > 0) {
			  newSet.addAll(group);
		  }
      }
      newList.add(new ArrayList<>(newSet));
    }

    return newList;
  }

  private List<ParseTreeChunk> flattenParseTreeChunkLst(List<List<List<ParseTreeChunk>>> listOfLists) {
    Set<ParseTreeChunk> newSetAll = new HashSet<>();
    for (List<List<ParseTreeChunk>> member : listOfLists) {
      Set<ParseTreeChunk> newSet = new HashSet<>();
      for (List<ParseTreeChunk> group : member) {
		  if (group.size() > 0) {
			  newSet.addAll(group);
		  }
      }
      newSetAll.addAll(newSet);
    }

    return removeDuplicates(new ArrayList<>(newSetAll));
  }

  private List<ParseTreeChunk> removeDuplicates(List<ParseTreeChunk> dupes) {
    List<Integer> toDelete = new ArrayList<>();
	  for (int i = 0; i < dupes.size(); i++) {
		  for (int j = i + 1; j < dupes.size(); j++) {
			  if (dupes.get(i).equals(dupes.get(j))) {
				  toDelete.add(j);
			  }
		  }
	  }
    List<ParseTreeChunk> cleaned = new ArrayList<>();
    for (int i = 0; i < dupes.size(); i++) {
		if (!toDelete.contains(i)) {
			cleaned.add(dupes.get(i));
		}
    }
    return cleaned;
  }

  private List<ParseTreeChunk> subtract(List<ParseTreeChunk> main, List<ParseTreeChunk> toSubtract) {
    List<Integer> toDelete = new ArrayList<>();
	  for (int i = 0; i < main.size(); i++) {
		  for (ParseTreeChunk parseTreeChunk : toSubtract) {
			  if (main.get(i).equals(parseTreeChunk)) {
				  toDelete.add(i);
			  }
		  }
	  }
    List<ParseTreeChunk> cleaned = new ArrayList<>();
    for (int i = 0; i < main.size(); i++) {
		if (!toDelete.contains(i)) {
			cleaned.add(main.get(i));
		}
    }
    return cleaned;
  }

  private List<ParseTreeChunk> intersectParseTreeChunkLists(List<ParseTreeChunk> a, List<ParseTreeChunk> b) {
    List<Integer> inters = new ArrayList<>();
	  for (int i = 0; i < a.size(); i++) {
		  for (ParseTreeChunk parseTreeChunk : b) {
			  if (a.get(i).equals(parseTreeChunk)) {
				  inters.add(i);
			  }
		  }
	  }
    List<ParseTreeChunk> cleaned = new ArrayList<>();
    for (int i = 0; i < a.size(); i++) {
		if (inters.contains(i)) {
			cleaned.add(a.get(i));
		}
    }
    return cleaned;
  }

  private Pair<List<List<List<ParseTreeChunk>>>, List<List<List<ParseTreeChunk>>>>
  removeInconsistenciesFromPosNegIntersections(List<List<List<ParseTreeChunk>>> pos,
                                               List<List<List<ParseTreeChunk>>> neg) {

    List<ParseTreeChunk> posIntersectionsFl = flattenParseTreeChunkLst(pos);
    List<ParseTreeChunk> negIntersectionsFl = flattenParseTreeChunkLst(neg);

    List<ParseTreeChunk> intersParseTreeChunkLists = intersectParseTreeChunkLists(posIntersectionsFl, negIntersectionsFl);

    List<List<List<ParseTreeChunk>>> cleanedFromInconsPos = new ArrayList<>(),
        cleanedFromInconsNeg = new ArrayList<>();
		/*
		System.out.println("pos = "+ pos);
		System.out.println("neg = "+ neg);
		System.out.println("pos flat = "+ posIntersectionsFl);
		System.out.println("neg flat = "+ negIntersectionsFl);
		System.out.println("inters = "+  intersParseTreeChunkLists);
		*/

    for (List<List<ParseTreeChunk>> member : pos) {
      List<List<ParseTreeChunk>> memberList = new ArrayList<>();
      for (List<ParseTreeChunk> group : member) {
        List<ParseTreeChunk> newGroup = new ArrayList<>();
        for (ParseTreeChunk ch : group) {
          boolean bSkip = false;
          for (ParseTreeChunk check : intersParseTreeChunkLists) {
			  if (check.equals(ch)) {
				  bSkip = true;
			  }
          }
			if (!bSkip) {
				newGroup.add(ch);
			}
        }
		  if (newGroup.size() > 0) {
			  memberList.add(newGroup);
		  }
      }
		if (memberList.size() > 0) {
			cleanedFromInconsPos.add(memberList);
		}
    }

    for (List<List<ParseTreeChunk>> member : neg) {
      List<List<ParseTreeChunk>> memberList = new ArrayList<>();
      for (List<ParseTreeChunk> group : member) {
        List<ParseTreeChunk> newGroup = new ArrayList<>();
        for (ParseTreeChunk ch : group) {
          boolean bSkip = false;
          for (ParseTreeChunk check : intersParseTreeChunkLists) {
			  if (check.equals(ch)) {
				  bSkip = true;
			  }
          }
			if (!bSkip) {
				newGroup.add(ch);
			}
        }
		  if (newGroup.size() > 0) {
			  memberList.add(newGroup);
		  }
      }
		if (memberList.size() > 0) {
			cleanedFromInconsNeg.add(memberList);
		}
    }

    return new Pair<>(cleanedFromInconsPos, cleanedFromInconsNeg);

  }


}
