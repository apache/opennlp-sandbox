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

package opennlp.tools.jsmlearning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import opennlp.tools.parse_thicket.pattern_structure.LinguisticPatternStructure;
import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class JSMLearnerOnLatticeBase {
	ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
	LinguisticPatternStructure psPos = new LinguisticPatternStructure(0,0), psNeg = new LinguisticPatternStructure(0,0);
	ParseTreeMatcherDeterministic md = new ParseTreeMatcherDeterministic(); 
	



	public JSMDecision buildLearningModel(List<String> posTexts, List<String> negTexts, 
			String unknown, String[] separationKeywords){
		psPos = new LinguisticPatternStructure(0,0); psNeg = new LinguisticPatternStructure(0,0);

		if (separationKeywords!=null){ // re-sort by occurrence of separation keyword
			Pair<List<String>, List<String>> pair = reGroupByOccurrenceOfSeparationKeyword(posTexts, negTexts, separationKeywords );
			posTexts = pair.getFirst(); negTexts = 	pair.getSecond();
		}

		List<List<List<ParseTreeChunk>>> lingRepsPos = new ArrayList<List<List<ParseTreeChunk>>>(),
				lingRepsNeg = new ArrayList<List<List<ParseTreeChunk>>>();
		for(String text: posTexts)
			lingRepsPos.add(chunk_maker.formGroupedPhrasesFromChunksForPara(text));

		for(String text: negTexts)
			lingRepsNeg.add(chunk_maker.formGroupedPhrasesFromChunksForPara(text));

		LinkedHashSet<Integer> obj = null;
		int i=0;
		for(List<List<ParseTreeChunk>> chunk: lingRepsPos){
			obj = new LinkedHashSet<Integer>();
			obj.add(i);
			psPos.AddIntent(chunk, obj, 0);
			i++;
		}
		i=0;
		for(List<List<ParseTreeChunk>> chunk: lingRepsNeg){
			obj = new LinkedHashSet<Integer>();
			obj.add(i);
			psNeg.AddIntent(chunk, obj, 0);
			i++;
		}



		List<List<ParseTreeChunk>> chunksUnknown = chunk_maker.formGroupedPhrasesFromChunksForPara(unknown);
		List<List<List<ParseTreeChunk>>> posIntersections = new ArrayList<List<List<ParseTreeChunk>>>(), 
				negIntersections = new ArrayList<List<List<ParseTreeChunk>>>();
		List<List<ParseTreeChunk>> intersection = null;
		for(int iConcept = 0; iConcept<psPos.conceptList.size(); iConcept++){
			if (psPos.conceptList.get(iConcept).intent!=null && psPos.conceptList.get(iConcept).intent.size()>0){
				intersection = computeIntersectionWithIntentExtendedByDeduction(psPos, iConcept, chunksUnknown);
				if (reduceList(intersection).size()>0)
					posIntersections.add(reduceList(intersection));
			}
			if (psNeg.conceptList.get(iConcept).intent!=null && psNeg.conceptList.get(iConcept).intent.size()>0){				
				intersection = md
						.matchTwoSentencesGroupedChunksDeterministic(psNeg.conceptList.get(iConcept).intent, chunksUnknown);
				if (reduceList(intersection).size()>0)
					negIntersections.add(reduceList(intersection));
			}
		}

		Pair<List<List<List<ParseTreeChunk>>>, List<List<List<ParseTreeChunk>>>> pair = 
				removeInconsistenciesFromPosNegIntersections( posIntersections, 
						negIntersections);

		posIntersections = pair.getFirst();
		negIntersections = pair.getSecond();

		List<List<List<ParseTreeChunk>>> posIntersectionsUnderNeg = new ArrayList<List<List<ParseTreeChunk>>>(), 
				negIntersectionsUnderPos = new ArrayList<List<List<ParseTreeChunk>>>();

		for(int iConcept = 0; iConcept<psNeg.conceptList.size(); iConcept++){
			for(int iConceptJ = 0; iConceptJ<negIntersections.size(); iConceptJ++){
				intersection = md
						.matchTwoSentencesGroupedChunksDeterministic(psNeg.conceptList.get(iConcept).intent, negIntersections.get(iConceptJ));
				if (reduceList(intersection).size()>0)
					posIntersectionsUnderNeg.add(reduceList(intersection));
			}
		}

		for(int iConcept = 0; iConcept<psPos.conceptList.size(); iConcept++){
			for(int iConceptJ = 0; iConceptJ<posIntersections.size(); iConceptJ++){
				intersection = md
						.matchTwoSentencesGroupedChunksDeterministic(psPos.conceptList.get(iConcept).intent, posIntersections.get(iConceptJ));
				if (reduceList(intersection).size()>0)
					negIntersectionsUnderPos.add(reduceList(intersection));
			}
		}

		List<ParseTreeChunk>posIntersectionsUnderNegLst = flattenParseTreeChunkLst(posIntersectionsUnderNeg);
		List<ParseTreeChunk>negIntersectionsUnderPosLst=flattenParseTreeChunkLst(negIntersectionsUnderPos);

		posIntersectionsUnderNegLst = subtract(posIntersectionsUnderNegLst, negIntersectionsUnderPosLst);
		negIntersectionsUnderPosLst= subtract(negIntersectionsUnderPosLst, posIntersectionsUnderNegLst);

		System.out.println("Pos - neg inters = "+posIntersectionsUnderNegLst);
		System.out.println("Neg - pos inters = "+negIntersectionsUnderPosLst);

		Boolean bPositiveClass = (float)posIntersectionsUnderNegLst.size()/(float)negIntersectionsUnderPosLst.size() > 1f;

		JSMDecision decision = new JSMDecision("keywordClassName", bPositiveClass, 
				posIntersections , negIntersections, 
				posIntersectionsUnderNeg,
				negIntersectionsUnderPos, separationKeywords);


		return decision;

	}

	private List<List<ParseTreeChunk>> computeIntersectionWithIntentExtendedByDeduction(
			LinguisticPatternStructure psPos, int iConcept,
			List<List<ParseTreeChunk>> chunksUnknown) {
		
		 return md
			.matchTwoSentencesGroupedChunksDeterministic(psPos.conceptList.get(iConcept).intent, chunksUnknown);
		
	}

	public Pair<List<String>, List<String>>  reGroupByOccurrenceOfSeparationKeyword(List<String> posTexts, List<String> negTexts, String[] keywords){
		// do nothing in base class

		return new Pair<List<String>, List<String>>(posTexts, negTexts);
	}

	public List<List<ParseTreeChunk>> reduceList(List<List<ParseTreeChunk>> list){
		float minScore = 1.3f;
		List<List<ParseTreeChunk>> newList = new ArrayList<List<ParseTreeChunk>>();


		ParseTreeChunkListScorer scorer = new ParseTreeChunkListScorer();
		for(  List<ParseTreeChunk> group: list){
			List<ParseTreeChunk> newGroup = new ArrayList<ParseTreeChunk>();
			for(ParseTreeChunk ch: group){
				if (scorer.getScore(ch) > minScore)
					newGroup.add(ch);
			}
			if (newGroup.size()>0)
				newList.add(newGroup);
		} 

		return newList;

	}

	public List<List<ParseTreeChunk>> flattenParseTreeChunkListList(List<List<List<ParseTreeChunk>>> listOfLists){
		List<List<ParseTreeChunk>> newList = new ArrayList<List<ParseTreeChunk>>();

		for(  List<List<ParseTreeChunk>> member: listOfLists){
			Set<ParseTreeChunk> newSet= new HashSet<ParseTreeChunk>();
			for(  List<ParseTreeChunk> group: member){
				if (group.size()>0)
					newSet.addAll(group);
			}
			newList.add(new ArrayList<ParseTreeChunk>(newSet));
		}

		return newList;  
	}

	public List<ParseTreeChunk> flattenParseTreeChunkLst(List<List<List<ParseTreeChunk>>> listOfLists){
		List<ParseTreeChunk> newList = new ArrayList<ParseTreeChunk>();
		Set<ParseTreeChunk> newSetAll = new HashSet<ParseTreeChunk>();


		for(  List<List<ParseTreeChunk>> member: listOfLists){
			Set<ParseTreeChunk> newSet= new HashSet<ParseTreeChunk>();
			for(  List<ParseTreeChunk> group: member){
				if (group.size()>0)
					newSet.addAll(group);
			}
			newSetAll.addAll(newSet);
		}

		return removeDuplicates(new ArrayList<ParseTreeChunk>(newSetAll));  
	}

	public List<ParseTreeChunk> removeDuplicates(List<ParseTreeChunk> dupes){
		List<Integer> toDelete = new ArrayList<Integer>();
		for(int i=0; i<dupes.size(); i++)
			for(int j=i+1; j<dupes.size(); j++){
				if (dupes.get(i).equals(dupes.get(j))){
					toDelete.add(j);
				}
			}
		List<ParseTreeChunk> cleaned = new ArrayList<ParseTreeChunk>();
		for(int i=0; i<dupes.size(); i++){
			if (!toDelete.contains(i))
				cleaned.add(dupes.get(i));
		}
		return cleaned;
	}

	public List<ParseTreeChunk> subtract(List<ParseTreeChunk> main, List<ParseTreeChunk> toSubtract){
		List<Integer> toDelete = new ArrayList<Integer>();
		for(int i=0; i<main.size(); i++)
			for(int j=0; j<toSubtract.size(); j++){
				if (main.get(i).equals(toSubtract.get(j))){
					toDelete.add(i);
				}
			}
		List<ParseTreeChunk> cleaned = new ArrayList<ParseTreeChunk>();
		for(int i=0; i<main.size(); i++){
			if (!toDelete.contains(i))
				cleaned.add(main.get(i));
		}
		return cleaned;
	}
	public List<ParseTreeChunk> intesectParseTreeChunkLists(List<ParseTreeChunk> a, List<ParseTreeChunk> b){
		List<Integer> inters = new ArrayList<Integer>();
		for(int i=0; i<a.size(); i++)
			for(int j=0; j<b.size(); j++){
				if (a.get(i).equals(b.get(j))){
					inters.add(i);
				}
			}
		List<ParseTreeChunk> cleaned = new ArrayList<ParseTreeChunk>();
		for(int i=0; i<a.size(); i++){
			if (inters.contains(i))
				cleaned.add(a.get(i));
		}
		return cleaned;
	}

	public Pair<List<List<List<ParseTreeChunk>>>, List<List<List<ParseTreeChunk>>>>
	removeInconsistenciesFromPosNegIntersections(List<List<List<ParseTreeChunk>>> pos, 
			List<List<List<ParseTreeChunk>>> neg ){

		List<ParseTreeChunk> posIntersectionsFl = flattenParseTreeChunkLst(pos);
		List<ParseTreeChunk> negIntersectionsFl = flattenParseTreeChunkLst(neg);

		List<ParseTreeChunk> intersParseTreeChunkLists = intesectParseTreeChunkLists(posIntersectionsFl, negIntersectionsFl);

		List<List<List<ParseTreeChunk>>> cleanedFromInconsPos = new ArrayList<List<List<ParseTreeChunk>>>(), 
				cleanedFromInconsNeg = new ArrayList<List<List<ParseTreeChunk>>>();
		/*
		System.out.println("pos = "+ pos);
		System.out.println("neg = "+ neg);
		System.out.println("pos flat = "+ posIntersectionsFl);
		System.out.println("neg flat = "+ negIntersectionsFl);
		System.out.println("inters = "+  intersParseTreeChunkLists);
		 */

		for(  List<List<ParseTreeChunk>> member: pos){
			List<List<ParseTreeChunk>> memberList = new ArrayList<List<ParseTreeChunk>>();
			for( List<ParseTreeChunk> group: member){
				List<ParseTreeChunk> newGroup = new ArrayList<ParseTreeChunk>();
				for(ParseTreeChunk ch: group){
					boolean bSkip = false;	 
					for(ParseTreeChunk check: intersParseTreeChunkLists){
						if (check.equals(ch))
							bSkip=true;
					}
					if (!bSkip)
						newGroup.add(ch);
				}
				if (newGroup.size()>0)
					memberList.add(newGroup);
			} 
			if (memberList.size()>0)
				cleanedFromInconsPos.add(memberList);
		}

		for(  List<List<ParseTreeChunk>> member: neg){
			List<List<ParseTreeChunk>> memberList = new ArrayList<List<ParseTreeChunk>>();
			for( List<ParseTreeChunk> group: member){
				List<ParseTreeChunk> newGroup = new ArrayList<ParseTreeChunk>();
				for(ParseTreeChunk ch: group){
					boolean bSkip = false;	 
					for(ParseTreeChunk check: intersParseTreeChunkLists){
						if (check.equals(ch))
							bSkip=true;
					}
					if (!bSkip)
						newGroup.add(ch);
				}
				if (newGroup.size()>0)
					memberList.add(newGroup);
			} 
			if (memberList.size()>0)
				cleanedFromInconsNeg.add(memberList);
		}
		return  new Pair<List<List<List<ParseTreeChunk>>>, List<List<List<ParseTreeChunk>>>>(cleanedFromInconsPos, cleanedFromInconsNeg);
	}


	public static void main (String[] args) {

		String[] posArr = new String[] {"I rent an office space. This office is for my business. I can deduct office rental expense from my business profit to calculate net income. ",
				"To run my business, I have to rent an office. The net business profit is calculated as follows. Rental expense needs to be subtracted from revenue. ",
				"To store goods for my retail business I rent some space. When I calculate the net income, I take revenue and subtract business expenses such as office rent. ",
		"I rent some space for my business. To calculate my net income, I subtract from revenue my rental business expense."};

		String[] negArr = new String[] {"I rent out a first floor unit of my house to a travel business. I need to add the rental income to my profit. However, when I repair my house, I can deduct the repair expense from my rental income. ",
				"I receive rental income from my office. I have to claim it as a profit in my tax forms. I need to add my rental income to my profits, but subtract rental expenses such as repair from it. ",
				"I advertised my property as a business rental. Advertisement and repair expenses can be subtracted from the rental income. Remaining rental income needs to be added to my profit and be reported as taxable profit. ",			
		"I showed  my property to a business owner to rent. Expenses on my time spent on advertisement are subtracted from the rental income. My rental profits are added to my taxable income.  "};	

		String unknown = "I do not want to rent anything to anyone. I just want to rent a space for myself. I neither calculate deduction of individual or business tax. I subtract my tax from my income";

		JSMDecision dec = new JSMLearnerOnLatticeBase().
				buildLearningModel(Arrays.asList(posArr), Arrays.asList(negArr), unknown, null);


	}
}
