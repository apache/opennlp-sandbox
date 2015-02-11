/*
 	* Licensed to the Apache Software Foundation (ASF) under one or more
 	* contributor license agreements. See the NOTICE file distributed with
 	* this work for additional information regarding copyright ownership.
 	* The ASF licenses this file to You under the Apache License, Version 2.0
 	* (the "License"); you may not use this file except in compliance with
 	* the License. You may obtain a copy of the License at
 	*
 	* http://www.apache.org/licenses/LICENSE-2.0
 	*
 	* Unless required by applicable law or agreed to in writing, software
 	* distributed under the License is distributed on an "AS IS" BASIS,
 	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 	* See the License for the specific language governing permissions and
 	* limitations under the License.
*/

package unittests;

import static org.junit.Assert.*;
import opennlp.summarization.Sentence;
import opennlp.summarization.lexicalchaining.LexicalChainingSummarizer;
import opennlp.summarization.lexicalchaining.LexicalChain;
import opennlp.summarization.lexicalchaining.*;
import opennlp.summarization.lexicalchaining.Word;
import opennlp.summarization.lexicalchaining.WordRelation;
import opennlp.summarization.lexicalchaining.WordRelationshipDetermination;
import opennlp.summarization.lexicalchaining.WordnetWord;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.POS;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

public class LexChainTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}


	@Test
	public void testBuildLexicalChains() {
		try {
			/*
			String article = "US President Barack Obama has welcomed an agreement between the US and Russia under which Syria's chemical weapons must be destroyed or removed by mid-2014 as an \"important step\"."
					+ "But a White House statement cautioned that the US expected Syria to live up to its public commitments. "
					+ "The US-Russian framework document stipulates that Syria must provide details of its stockpile within a week. "
					+ "If Syria fails to comply, the deal could be enforced by a UN resolution. "
					+ " China, France, the UK, the UN and Nato have all expressed satisfaction at the agreement. "
					+ " In Beijing, Foreign Minister Wang Yi said on Sunday that China welcomes the general agreement between the US and Russia.";
*/
			String sentFragModel = "resources/en-sent.bin";
			DefaultDocProcessor dp =new DefaultDocProcessor(sentFragModel);
			String article = dp.docToString("/Users/ram/dev/summarizer/test/forram/technology/output/summary/9.txt");
			LexicalChainingSummarizer lcs;
			lcs = new LexicalChainingSummarizer(dp,"resources/en-pos-maxent.bin");

			long strt = System.currentTimeMillis();

			List<Sentence> sent = dp.getSentencesFromStr(article);
			List<LexicalChain> vh = lcs.buildLexicalChains(article, sent);
			Collections.sort(vh);
			
			List<Sentence> s = dp.getSentencesFromStr(article);
			Hashtable<String, Boolean> comp = new Hashtable<String, Boolean>(); 
			System.out.println(vh.size());
			POSTagger t = new OpenNLPPOSTagger(dp,"resources/en-pos-maxent.bin");
			System.out.println(t.getTaggedString(article));
			for(int i=vh.size()-1;i>=Math.max(vh.size()-50, 0);i--)
			{
				LexicalChain lc = vh.get(i);
				
				if(! (comp.containsKey(lc.getWord().get(0).getLexicon())))
				{
					comp.put(lc.getWord().get(0).getLexicon(), new Boolean(true));
					for(int j=0;j<lc.getWord().size();j++)
						System.out.print(lc.getWord().get(j) + "-- ");
					System.out.println(lc.score());
					for(Sentence sid : lc.getSentences())
					{
						//if(sid>=0 && sid<s.size())
						System.out.println(sid);
					}
				}
				System.out.println("--------");
			}
			System.out.println((System.currentTimeMillis() - strt)/1000);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Test
	public void testGetRelation() {
		try {
			
			WordRelationshipDetermination lcs = new WordRelationshipDetermination();
			LexicalChain l = new LexicalChain();
			List<Word> words = lcs.getWordSenses("music");
			
			l.addWord(words.get(0));
//			int rel = lcs.getRelation(l, "nation");
			WordRelation rel2 = lcs.getRelation(l, "tune", true);
			WordRelation rel3 = lcs.getRelation(l, "vocal", true);
			System.out.println(rel2.relation);
			System.out.println(rel3.relation);
	//		assertEquals(rel, LexicalChainingSummarizer.STRONG_RELATION);
			assertEquals( WordRelation.MED_RELATION, rel2.relation);
			assertEquals( WordRelation.MED_RELATION, rel3.relation);
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

}
