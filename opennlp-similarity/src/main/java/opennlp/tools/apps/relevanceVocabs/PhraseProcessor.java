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

package opennlp.tools.apps.relevanceVocabs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.parser.Parse;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;
import opennlp.tools.util.Span;

public class PhraseProcessor {
	
	private ParserChunker2MatcherProcessor nlProc = ParserChunker2MatcherProcessor.getInstance() ;
	
	public static boolean allChildNodesArePOSTags(Parse p)
	{
		Parse[] subParses = p.getChildren();
		for (int pi = 0; pi < subParses.length; pi++)
			if (!((Parse) subParses[pi]).isPosTag())
				return false;
		return true;
	}
	
	public ArrayList<String> getNounPhrases(Parse p)
	{
		ArrayList<String> nounphrases = new ArrayList<String>();

		Parse[] subparses = p.getChildren();
		for (int pi = 0; pi < subparses.length; pi++)
		{

			if (subparses[pi].getType().equals("NP") && allChildNodesArePOSTags(subparses[pi]))
			{
				Span _span = subparses[pi].getSpan();
				nounphrases.add(p.getText().substring(_span.getStart(), _span.getEnd()));
			}
			else if (!((Parse) subparses[pi]).isPosTag())
				nounphrases.addAll(getNounPhrases(subparses[pi]));
		}

		return nounphrases;
	}
	
	public ArrayList<String> getVerbPhrases(Parse p)
	{
		ArrayList<String> verbPhrases = new ArrayList<String>();

		Parse[] subparses = p.getChildren();
		for (int pi = 0; pi < subparses.length; pi++)
		{

			if (subparses[pi].getType().startsWith("VB") && allChildNodesArePOSTags(subparses[pi]))
			{
				Span _span = subparses[pi].getSpan();
				verbPhrases.add(p.getText().substring(_span.getStart(), _span.getEnd()));
			}
			else if (!((Parse) subparses[pi]).isPosTag())
				verbPhrases.addAll(getNounPhrases(subparses[pi]));
		}

		return verbPhrases;
	}
	
	// forms phrases from text which are candidate expressions for events lookup
			public List<ParseTreeChunk> getVerbPhrases(String sentence) {
				if (sentence==null)
					return null;
				if (sentence.split(" ").length ==1) { // this is a word, return empty
					//queryArrayStr.add( sentence);
					return null;
				}
				if (sentence.length()>100)
					return null ; // too long of a sentence to parse
				
				System.out.println("About to parse: "+sentence);
				List<List<ParseTreeChunk>> groupedChunks = nlProc.formGroupedPhrasesFromChunksForPara(sentence); 
				if (groupedChunks.size()<1)
					return null;

				List<ParseTreeChunk> vPhrases = groupedChunks.get(1);
				
				return vPhrases;
			}

			public List<List<ParseTreeChunk>> getPhrasesOfAllTypes(String sentence) {
				if (sentence==null)
					return null;
				if (sentence.split(" ").length ==1) { // this is a word, return empty
					//queryArrayStr.add( sentence);
					return null;
				}
				if (sentence.length()>200)
					return null ; // too long of a sentence to parse
				
				System.out.println("About to parse: "+sentence);
				List<List<ParseTreeChunk>> groupedChunks = nlProc.formGroupedPhrasesFromChunksForPara(sentence); 
				if (groupedChunks.size()<1)
					return null;

				return groupedChunks;
			}
	
	// forms phrases from text which are candidate expressions for events lookup
		public List<String> extractNounPhraseProductNameCandidate(String sentence) {
			
			List<String> queryArrayStr = new ArrayList<String>();
			
			if (sentence.split(" ").length ==1) { // this is a word, return empty
				//queryArrayStr.add( sentence);
				return queryArrayStr;
			}
			String quoted1 = StringUtils.substringBetween(sentence, "\"", "\"");
			String quoted2 = StringUtils.substringBetween(sentence, "\'", "\'");
			List<List<ParseTreeChunk>> groupedChunks = nlProc.formGroupedPhrasesFromChunksForPara(sentence); 
			if (groupedChunks.size()<1)
				return queryArrayStr;

			List<ParseTreeChunk> nPhrases = groupedChunks.get(0);

			for (ParseTreeChunk ch : nPhrases) {
				String query = "";
				int size = ch.getLemmas().size();
				boolean phraseBeingFormed = false;
				for (int i = 0; i < size; i++) {
					if ((ch.getPOSs().get(i).startsWith("N") || ch.getPOSs().get(i)
							.startsWith("J") || ch.getPOSs().get(i).startsWith("CD") ) )
					//		&& StringUtils.isAlpha(ch.getLemmas().get(i)))
					{
						query += ch.getLemmas().get(i) + " ";
						phraseBeingFormed = true;
					} else 
						if ((ch.getPOSs().get(i).startsWith("PR") || ch.getPOSs().get(i).startsWith("IN") || ch.getPOSs().get(i).startsWith("TO")  ) 
								&& phraseBeingFormed )
							break;
						else if (ch.getPOSs().get(i).startsWith("DT") || ch.getPOSs().get(i).startsWith("CC"))
						continue;
				}
				query = query.trim();
				int len = query.split(" ").length;
				if (len > 5 || len < 2) // too long or too short
					continue;
				
	/*				
				if (len < 4 && len>1) { // every word should start with capital
					String[] qs = query.split(" ");
					boolean bAccept = true;
					for (String w : qs) {
						if (w.toLowerCase().equals(w)) // idf only two words then
														// has to be person name,
														// title or geo
														// location
							bAccept = false;
					}
					if (!bAccept)
						continue;
				}
		*/		
				 // individual word, possibly a frequent word
				// if len==1 do nothing

				query = query.trim();
				queryArrayStr.add(query);

			}
	/*		
			if (queryArrayStr.size() < 1) { // release constraints on NP down to 2
											// keywords
				for (ParseTreeChunk ch : nPhrases) {
					String query = "";
					int size = ch.getLemmas().size();

					for (int i = 0; i < size; i++) {
						if (ch.getPOSs().get(i).startsWith("N")
								|| ch.getPOSs().get(i).startsWith("J")) {
							query += ch.getLemmas().get(i) + " ";
						}
					}
					query = query.trim();
					int len = query.split(" ").length;
					if (len < 2)
						continue;

					query = TextProcessor.fastTokenize(query.toLowerCase(), false)
							.toString().replace('[', ' ').replace(']', ' ').trim();
					if (query.length() > 6)
						queryArrayStr.add(query);
				}
			}
			//queryArrayStr = Utils
			//		.removeDuplicatesFromQueries(queryArrayStr);
			if (quoted1 != null
					&& ((quoted1.length() > 5 && !stopList.isCommonWord(quoted1)) || quoted1
							.length() > 10))
				queryArrayStr.add(quoted1);
			if (quoted2 != null
					&& ((quoted2.length() > 5 && !stopList.isCommonWord(quoted2)) || quoted2
							.length() > 10))
				queryArrayStr.add(quoted2);
		*/	return queryArrayStr;
		}
		

	
		
		public static void main(String[] args){
			String sent = "Appliances and Kitchen Gadgets - CNET Blogs";
					//"The tablet phenomenon turns Silicon Valley upside down - SiliconValley.com";
			List<String> res = new PhraseProcessor().extractNounPhraseProductNameCandidate(sent);
			System.out.println(res);
		}
}
