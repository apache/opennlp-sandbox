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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import junit.framework.TestCase;
import opennlp.tools.fca.ConceptLattice;
import opennlp.tools.fca.FcaWriter;
import opennlp.tools.fca.FormalConcept;
import opennlp.tools.similarity.apps.BingWebQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class PatternStructureTest extends TestCase{
		ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
		LinguisticPatternStructure ps = new LinguisticPatternStructure(0,0);
		BingWebQueryRunner bqr = new BingWebQueryRunner();

	public void test6texts() {

		String text1 = "I rent an office space. This office is for my business. I can deduct office rental expense from my business profit to calculate net income.";
		String text2 = "To run my business, I have to rent an office. The net business profit is calculated as follows. Rental expense needs to be subtracted from revenue.";
		String text3 = "To store goods for my retail business I rent some space. When I calculate the net income, I take revenue and subtract business expenses such as office rent.";
		String text4 = "I rent out a first floor unit of my house to a travel business. I need to add the rental income to my profit. However, when I repair my house, I can deduct the repair expense from my rental income.";
		String text5 = "I receive rental income from my office. I have to claim it as a profit in my tax forms. I need to add my rental income to my profits, but subtract rental expenses such as repair from it.";
		String text6 = "I advertised my property as a business rental. Advertisement and repair expenses can be subtracted from the rental income. Remaining rental income needs to be added to my profit and be reported as taxable profit. ";				
		
		List<List<ParseTreeChunk>> chunks1 = chunk_maker.formGroupedPhrasesFromChunksForPara(text1);
		List<List<ParseTreeChunk>> chunks2 = chunk_maker.formGroupedPhrasesFromChunksForPara(text2);
		List<List<ParseTreeChunk>> chunks3 = chunk_maker.formGroupedPhrasesFromChunksForPara(text3);
		List<List<ParseTreeChunk>> chunks4 = chunk_maker.formGroupedPhrasesFromChunksForPara(text4);
		List<List<ParseTreeChunk>> chunks5 = chunk_maker.formGroupedPhrasesFromChunksForPara(text5);
		List<List<ParseTreeChunk>> chunks6 = chunk_maker.formGroupedPhrasesFromChunksForPara(text6);
		//ArrayList<ParseTreeChunk> lst = new ArrayList<ParseTreeChunk>();
		

		LinkedHashSet<Integer> obj = null;
		obj = new LinkedHashSet<Integer>();
		obj.add(0);
		ps.AddIntent(chunks1, obj, 0);
		obj = new LinkedHashSet<Integer>();
		obj.add(1);
		ps.AddIntent(chunks2, obj, 0);
		obj = new LinkedHashSet<Integer>();
		obj.add(2);
		ps.AddIntent(chunks3, obj, 0);
		obj = new LinkedHashSet<Integer>();
		obj.add(3);
		ps.AddIntent(chunks4, obj, 0);
		obj = new LinkedHashSet<Integer>();
		obj.add(4);
		ps.AddIntent(chunks5, obj, 0);
		obj = new LinkedHashSet<Integer>();
		obj.add(5);
		ps.AddIntent(chunks6, obj, 0);

		ps.logStability();				
		ps.printLatticeExtended();

		int [][] binaryContext = ps.toContext(3);
		for (int i = 0; i < binaryContext.length; i++ ){
			System.out.println(Arrays.toString(binaryContext[i]));
		}	

		ConceptLattice new_cl = new ConceptLattice(binaryContext.length, binaryContext[0].length, binaryContext,true);	
		new_cl.printLatticeStats();
		new_cl.printLatticeFull();
		assertEquals(new_cl.getLattice().size(), 7);
		assertEquals(new_cl.getAttributesCount(), 21);
		assertEquals(new_cl.getObjectCount(), 3);
	}

				// TEST 2 QUERY NEWS
	public void testQueryNews(){
				List<List<ParseTreeChunk>> chunks = null;
				BingWebQueryRunner bq = new BingWebQueryRunner();

				String q = "";
//				q = "barack obama";
// 				q = "lady gaga";
 				q = "angela merkel";
// 				q = "putin";
				ArrayList <HitBase> hb = (ArrayList<HitBase>) bq.runSearch(q, 10);
				int cnt = 0;
				for (HitBase news: hb){
					LinkedHashSet<Integer> obj = null;
					obj = new LinkedHashSet<Integer>();
					obj.add(cnt);
					chunks = chunk_maker.formGroupedPhrasesFromChunksForPara(news.getAbstractText());
					System.out.println(chunks);
					ps.AddIntent(chunks,obj, 0);
					cnt++;
				}

				ps.logStability();
				System.out.println("LATTICE");
				ps.printLatticeExtended();

				int [][] binaryContext = ps.toContext(cnt);
				for (int i = 0; i < binaryContext.length; i++ ){
					System.out.println(Arrays.toString(binaryContext[i]));
				}	

				ConceptLattice new_cl = new ConceptLattice(binaryContext.length, binaryContext[0].length, binaryContext,true);	
				new_cl.printLatticeStats();
				new_cl.printLatticeFull();

				FcaWriter wr = new FcaWriter();
				wr.WriteAsCxt("res.cxt", new_cl);

				System.out.println("Extent PS "+ps.conceptList.size());
				//for (int i = 0; i<ps.conceptList.size();i++){
				//	System.out.println(ps.conceptList.get(i).extent);
				//}
				System.out.println("Extent CL "+new_cl.getLattice().size());
				//for (int i = 0; i<new_cl.getLattice().size();i++){
				//	System.out.println(new_cl.getLattice().get(i).getExtent());
				//}
	}
	public void testNews(){
		List<List<ParseTreeChunk>> chunks = null;

				ArrayList <HitBase>  result = (ArrayList<HitBase>) bqr.runSearch("site:http://news.yahoo.com "  + "merkel", 10);
				System.out.println(" ResultSize  " + result.size());
				int ind = -1;
				String text_result = "";
				for (int i = 0; i < result.size(); i++ ){
					System.out.println(result.get(i).getAbstractText());
					ind = result.get(i).getAbstractText().indexOf(") -");
					if (ind < 0)
						ind = result.get(i)//.getDescription()
								.getAbstractText().indexOf(") �");
					if (ind > 0)
						text_result = result.get(i)//.getDescription()
								.getAbstractText().substring(ind + 3);
					else 
						text_result = result.get(i)//.getDescription()
								.getAbstractText();

					LinkedHashSet<Integer> obj = null;
					obj = new LinkedHashSet<Integer>();
					obj.add(i);
					chunks = chunk_maker.formGroupedPhrasesFromChunksForPara(text_result);
					ps.AddIntent(chunks,obj, 0);
				}

				ps.logStability();
				ps.printLatticeExtended();

				int [][] binaryContext = ps.toContext(result.size());

				ConceptLattice new_cl = new ConceptLattice(binaryContext.length, binaryContext[0].length, binaryContext,true);	

				FcaWriter wt = new FcaWriter();
				wt.WriteStatsToTxt("merkel_stats.txt", new_cl, 0);
				wt.WriteStatsToCvs("merkel_stats.csv", new_cl, ps.conceptList.size());
				wt.WriteAsCxt("merkel_lattice.cxt", new_cl);

				PatternStructureWriter pswt = new PatternStructureWriter();
				pswt.WriteStatsToTxt("ps_res.txt", ps);

				System.out.println("Extent PS "+ps.conceptList.size());
				System.out.println("Extent CL "+new_cl.getLattice().size());
	}




}
