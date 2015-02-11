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

package opennlp.summarization.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;

import opennlp.summarization.Score;
import opennlp.summarization.Sentence;
import opennlp.summarization.lexicalchaining.LexicalChain;
import opennlp.summarization.lexicalchaining.LexicalChainingSummarizer;
import opennlp.summarization.preprocess.DefaultDocProcessor;
import opennlp.summarization.textrank.TextRankSummarizer;

import java.util.logging.*;

import opennlp.summarization.DocProcessor;
/*
 * A summarizer that combines results from the text rank algorithm and the lexical chaining algorithm.
 * It runs both algorithm and uses the the lexical chains to identify the main topics and relative importance 
 * and the text rank to pick sentences from lexical chains.
 */
public class MetaSummarizer{
	DocProcessor dp ;
	TextRankSummarizer textRank;
	LexicalChainingSummarizer lcs;
	String sentFragModel = "resources/en-sent.bin";
	
	public MetaSummarizer(String posModelFile) throws Exception
	{
		Logger.getAnonymousLogger().info("Initializing Meta Summarizer");
		dp = new DefaultDocProcessor(sentFragModel);
		textRank = new TextRankSummarizer();
		lcs = new LexicalChainingSummarizer(dp, posModelFile);
	}
	
	//An Utility method to sort the ranked sentences by sentence order.
    private List<Score> order(List<Score> s)
    {
            Collections.sort(s, new Comparator<Score>()
            {

                    @Override
                    public int compare(Score o1, Score o2) {
                            // TODO Auto-generated method stub

                            return o1.getSentId() - o2.getSentId();
                    }
            });
            return s;
    }

	// Rank sentences by merging the scores from lexical chaining and text rank..
	// maxWords -1 indicates rank all sentences..
	public int getBestSent(LexicalChain l, Hashtable<Integer, Score> pageRankScores)
	{
		double bestScore = 0; int bestStr=-1;
		for(Sentence s : l.getSentences())
		{
			Score sc = pageRankScores.get(new Integer(s.getSentId()));
			if(sc!=null && sc.getScore() > bestScore)
			{
				bestScore = sc.getScore();
				bestStr = sc.getSentId();
			}
		}
		return bestStr;
	}
	
	public List<Score> rankSentences(String article, List<Sentence> sent, int maxWords)
	{
		List<LexicalChain> lc = lcs.buildLexicalChains(article, sent);
		Collections.sort(lc);
		Hashtable<Integer, Score> sentScores = new Hashtable<Integer, Score>();
		try{
	        List<Score> scores = textRank.rankSentences(article, sent, dp, article.length());
	        for(Score s: scores) sentScores.put(s.getSentId(), s);
		}catch(Exception ex){
			ex.printStackTrace();
		}
		
		Hashtable<Sentence, Boolean> summSents = new Hashtable<Sentence,Boolean>();
		List<Score> finalSc = new ArrayList<Score>();
		int currWordCnt = 0;
		for(int i=lc.size()-1;i>=0;i--)
		{
			LexicalChain l = lc.get(i);
			boolean added =false;
			while(l.getSentences().size()>0)
			{
				int sentId = getBestSent(l, sentScores);
				if(sentId == -1) break;
				
				Sentence s = sent.get(sentId);
				
				//Sentence already added, try again..
				if(summSents.containsKey(s)) 
					l.getSentences().remove(s);
				else{
					finalSc.add(sentScores.get(s.getSentId()));
					summSents.put(s, true);
					currWordCnt += s.getWordCnt();
					break;
				}
			}
			if(maxWords>0 && currWordCnt>maxWords) break;
		}
		
		order(finalSc);
		return finalSc;
	}
	
	//Default Summarization using only lexical chains..
	public String summarize(String article, int maxWords)
	{
		//Build lexical Chains..
		List<Sentence> sent = dp.getSentencesFromStr(article);
		
		List<Score>finalSc = rankSentences(article, sent, maxWords);

		StringBuilder sb = new StringBuilder();
		for(int i=0;i<finalSc.size();i++) 
		{
			sb.append(sent.get(finalSc.get(i).getSentId()).toString().trim() +".. ");
		}
		// Pick sentences		
		return sb.toString();
	}

	public static void main(String[] args)
	{
		try{
			String posModelFileName = "./resources/en-pos-maxent.bin";
			String sentFragModel = "resources/en-sent.bin";
			DefaultDocProcessor dp =new DefaultDocProcessor(sentFragModel);
			Logger l = Logger.getAnonymousLogger();
			MetaSummarizer lcs = new MetaSummarizer(posModelFileName);
			String article = dp.docToString("test/tax.txt");
			long strt = System.currentTimeMillis();
			System.out.println(lcs.summarize(article, 50));
			System.out.println(System.currentTimeMillis() - strt);
			
			article = dp.docToString("test/houston-rep-nopara.txt");
			strt = System.currentTimeMillis();
			System.out.println(lcs.summarize(article, 50));
			System.out.println(System.currentTimeMillis() - strt);
	
			article = dp.docToString("gunman.txt");
			strt = System.currentTimeMillis();
			System.out.println(lcs.summarize(article, 50));
			System.out.println(System.currentTimeMillis() - strt);
			
			article = dp.docToString("satellite.txt");
			strt = System.currentTimeMillis();
			System.out.println(lcs.summarize(article, 50));
			System.out.println(System.currentTimeMillis() - strt);
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}
}
