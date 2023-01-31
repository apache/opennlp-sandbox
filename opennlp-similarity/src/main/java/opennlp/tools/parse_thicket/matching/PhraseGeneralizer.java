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

package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;






import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.textsimilarity.GeneralizationListReducer;
import opennlp.tools.textsimilarity.LemmaFormManager;
import opennlp.tools.textsimilarity.POSManager;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class PhraseGeneralizer implements IGeneralizer<ParseTreeChunk> {

	private GeneralizationListReducer generalizationListReducer = new GeneralizationListReducer();
	protected LemmaGeneralizer lemmaFormManager = new LemmaGeneralizer();
	//protected LemmaFormManager lemmaFormManager = new LemmaFormManager();
	
	
	protected PartOfSpeechGeneralizer posManager = new PartOfSpeechGeneralizer();
	
	protected PStemmer ps = new PStemmer();
	protected ParseTreeNodeGeneralizer nodeGen = new ParseTreeNodeGeneralizer();

	/**
	 * key matching function which takes two phrases, aligns them and finds a set
	 * of maximum common sub-phrase
	 * 
	 * @param chunk1
	 * @param chunk2
	 * @return
	 */
	@Override
	public List<ParseTreeChunk> generalize(
			Object chunk1o, Object chunk2o) {

		ParseTreeChunk chunk1 = (ParseTreeChunk)chunk1o, chunk2 = (ParseTreeChunk)chunk2o;

		List<ParseTreeChunk> resultChunks = new ArrayList<ParseTreeChunk>();


		List<String> pos1 = chunk1.getPOSs();
		List<String> pos2 = chunk2.getPOSs();
		List<String> lem1 = chunk1.getLemmas();
		List<String> lem2 = chunk2.getLemmas();

		List<String> lem1stem = new ArrayList<String>();
		List<String> lem2stem = new ArrayList<String>();


		for (String word : lem1) {
			try {
				lem1stem.add(ps.stem(word.toLowerCase()).toString());
			} catch (Exception e) {
				// e.printStackTrace();

				if (word.length() > 2)
					System.err.println("Unable to stem: " + word);
			}
		}
		try {
			for (String word : lem2) {
				lem2stem.add(ps.stem(word.toLowerCase()).toString());
			}
		} catch (Exception e) {
			System.err.println("problem processing word " + lem2.toString());
		}

		List<String> overlap = new ArrayList<String>(lem1stem);
		overlap.retainAll(lem2stem);

		if (overlap == null || overlap.size() < 1)
			return null;

		// to accumulate starts of alignments
		List<Integer> occur1 = new ArrayList<Integer>(), occur2 = new ArrayList<Integer>();

		// for verbs find alignment even if no same verb lemmas, just any pair of verbs. Usually should be 0,0
		if (chunk1.getMainPOS().startsWith("VP") && chunk2.getMainPOS().startsWith("VP")) {
			Integer i1 = null, i2 = null;
			for(int i=0; i< pos1.size(); i++){
				if (pos1.get(i).startsWith("VB")){
					i1 = i;
					break;
				}
			}

			for(int i=0; i< pos2.size(); i++){
				if (pos2.get(i).startsWith("VB")){
					i2 = i;
					break;
				}
			}
			if (i1!=null)
				occur1.add(i1);
			if (i2!=null)
				occur2.add(i2);
		}


		for (String word : overlap) {
			Integer i1 = lem1stem.indexOf(word);
			Integer i2 = lem2stem.indexOf(word);
			occur1.add(i1);
			occur2.add(i2);
		}


		// now we search for plausible sublists of overlaps
		// if at some position correspondence is inverse (one of two position
		// decreases instead of increases)
		// then we terminate current alignment accum and start a new one
		List<List<int[]>> overlapsPlaus = new ArrayList<List<int[]>>();
		// starts from 1, not 0
		List<int[]> accum = new ArrayList<int[]>();
		accum.add(new int[] { occur1.get(0), occur2.get(0) });
		for (int i = 1; i < occur1.size() && i< occur2.size(); i++) {

			if (occur1.get(i) > occur1.get(i - 1)
					&& occur2.get(i) > occur2.get(i - 1))
				accum.add(new int[] { occur1.get(i), occur2.get(i) });
			else {
				overlapsPlaus.add(accum);
				accum = new ArrayList<int[]>();
				accum.add(new int[] { occur1.get(i), occur2.get(i) });
			}
		}
		if (accum.size() > 0) {
			overlapsPlaus.add(accum);
		}


		for (List<int[]> occur : overlapsPlaus) {
			List<ParseTreeNode> results = new ArrayList<ParseTreeNode>();
			List<Integer> occr1 = new ArrayList<Integer>(), occr2 = new ArrayList<Integer>();
			for (int[] column : occur) {
				occr1.add(column[0]);
				occr2.add(column[1]);
			}

			int ov1 = 0, ov2 = 0; // iterators over common words;
			List<String> commonPOS = new ArrayList<String>(), commonLemmas = new ArrayList<String>();
			// we start two words before first word
			int k1 = occr1.get(ov1) - 2, k2 = occr2.get(ov2) - 2;
			// if (k1<0) k1=0; if (k2<0) k2=0;
			Boolean bReachedCommonWord = false;
			while (k1 < 0 || k2 < 0) {
				k1++;
				k2++;
			}
			int k1max = pos1.size() - 1, k2max = pos2.size() - 1;
			while (k1 <= k1max && k2 <= k2max) {
				// first check if the same POS
				String sim = null;
				List<String> sims = posManager.//similarPOS(pos1.get(k1), pos2.get(k2));
						generalize(pos1.get(k1), pos2.get(k2));
				if (!sims.isEmpty())
					sim = sims.get(0);
				
				String lemmaMatch = null;		
				List<String> lemmaMatchs = lemmaFormManager.//matchLemmas(ps, 
						generalize(lem1.get(k1),
						lem2.get(k2));
				if (!lemmaMatchs.isEmpty())
					lemmaMatch = lemmaMatchs.get(0);
				

				if ((sim != null)
						&& (lemmaMatch == null || (lemmaMatch != null ))) {
					commonPOS.add(pos1.get(k1));


					// doing parse tree node generalization
					List<ParseTreeNode> genRes =  nodeGen.generalize(chunk1.getParseTreeNodes().get(k1), chunk2.getParseTreeNodes().get(k2)); 
					if (genRes.size()==1)
						results.add(genRes.get(0));

					if (lemmaMatch != null) {
						commonLemmas.add(lemmaMatch);
						// System.out.println("Added "+lemmaMatch);
						if (k1 == occr1.get(ov1) && k2 == occr2.get(ov2))
							bReachedCommonWord = true; // now we can have different increment
						// opera
						else {
							if (occr1.size() > ov1 + 1 && occr2.size() > ov2 + 1
									&& k1 == occr1.get(ov1 + 1) && k2 == occr2.get(ov2 + 1)) {
								ov1++;
								ov2++;
								bReachedCommonWord = true;
							}
						}
					} else {
						commonLemmas.add("*");
					} // the same parts of speech, proceed to the next word in both
					// expressions
					k1++;
					k2++;

				} else if (!bReachedCommonWord) {
					k1++;
					k2++;
				} // still searching
				else {
					// different parts of speech, jump to the next identified common word
					ov1++;
					ov2++;
					if (ov1 > occr1.size() - 1 || ov2 > occr2.size() - 1)
						break;
					// now trying to find
					int kk1 = occr1.get(ov1) - 2, // new positions of iterators
							kk2 = occr2.get(ov2) - 2;
					int countMove = 0;
					while ((kk1 < k1 + 1 || kk2 < k2 + 1) && countMove < 2) { // if it is
						// behind
						// current
						// position,
						// synchroneously
						// move
						// towards
						// right
						kk1++;
						kk2++;
						countMove++;
					}
					k1 = kk1;
					k2 = kk2;

					if (k1 > k1max)
						k1 = k1max;
					if (k2 > k2max)
						k2 = k2max;
					bReachedCommonWord = false;
				}
			}
			ParseTreeChunk currResult = new ParseTreeChunk(results);
			//currResultOld = new ParseTreeChunk(commonLemmas, commonPOS, 0, 0);


			resultChunks.add(currResult);
		}

		return resultChunks;
	}

}
