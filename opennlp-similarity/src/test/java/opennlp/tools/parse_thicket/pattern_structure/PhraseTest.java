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
import java.util.LinkedHashSet;
import java.util.List;

import opennlp.tools.fca.ConceptLattice;
import opennlp.tools.fca.FcaWriter;
import opennlp.tools.similarity.apps.BingWebQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class PhraseTest {
	
	
	public static void main(String []args) {
		
/*//TEST 1		
  				String text1 = "Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons."+"UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
				  "A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " + 
				  "Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. ";
				String text2 = "However, several experts familiar with the inspections believe that Iraq could also probably have produced a workable device in as little as 6 to 24 months, had they decided to seize foreign-supplied HEU from under safeguards and focus their efforts on a crash program to produce a device in the shortest possible amount of time.";
				String text3 ="Iraq invested significant resources into uranium enrichment through laser isotope separation (LIS) involving both molecular (MLIS) and atomic vapor (AVLIS) technologies, including a number of activities with respect to laser component manufacture, particularly CO2 lasers and the manufacture of components for use in laser-related experimentation. The Laser Section within the Physics Department of the IAEC at Tuwaitha received an objective in 1981 from the IAEC to work in Laser Isotope Separation. It started in two lines; one which was looking after the molecular and the other the atomic vapor direction.";
				ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
				List<List<ParseTreeChunk>> chunks1 = chunk_maker.formGroupedPhrasesFromChunksForPara(text1);
				List<List<ParseTreeChunk>> chunks2 = chunk_maker.formGroupedPhrasesFromChunksForPara(text2);
				List<List<ParseTreeChunk>> chunks3 = chunk_maker.formGroupedPhrasesFromChunksForPara(text3);
				ArrayList<ParseTreeChunk> lst = new ArrayList<ParseTreeChunk>();
				PhrasePatternStructureExtended ps = new PhrasePatternStructureExtended(0,0);
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
				ps.logStability();				
				ps.printLatticeExtended();
				
				int [][] binaryContext = ps.toContext(3);
				for (int i = 0; i < binaryContext.length; i++ ){
					System.out.println(Arrays.toString(binaryContext[i]));
				}	
				
				ConceptLattice new_cl = new ConceptLattice(binaryContext.length, binaryContext[0].length, binaryContext,true);	
				new_cl.printLatticeStats();
				new_cl.printLatticeFull();
*/
		
/*				// TEST 2 QUERY NEWS
   				ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
				List<List<ParseTreeChunk>> chunks = null;
				BingWebQueryRunner bq = new BingWebQueryRunner();
				
				String q = "";
//				q = "barack obama";
// 				q = "lady gaga";
 				q = "angela merkel";
// 				q = "putin";
				ArrayList <HitBase> hb = (ArrayList<HitBase>) bq.runSearch(q, 10);
				PhrasePatternStructureExtended ps = new PhrasePatternStructureExtended(0,0);
				int cnt = 0;
				for (HitBase news: hb){
					LinkedHashSet<Integer> obj = null;
					obj = new LinkedHashSet<Integer>();
					obj.add(cnt);
					chunks = chunk_maker.formGroupedPhrasesFromChunksForPara(news.getDescription());
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
*/				
				LinguisticPatternStructure ps = new LinguisticPatternStructure(0,0);
				ParserChunker2MatcherProcessor chunk_maker = ParserChunker2MatcherProcessor.getInstance();
				List<List<ParseTreeChunk>> chunks = null;		
				BingWebQueryRunner bqr = new BingWebQueryRunner();
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
