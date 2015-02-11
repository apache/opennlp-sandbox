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

package opennlp.summarization.textrank;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.summarization.*;
import opennlp.summarization.preprocess.DefaultDocProcessor;
import opennlp.summarization.preprocess.IDFWordWeight;
import opennlp.summarization.preprocess.WordWeight;

/*
 * A wrapper around the text rank algorithm.  This class 
 * a) Sets up the data for the TextRank class 
 * b) Takes the ranked sentences and does some basic rearranging (e.g. ordering) to provide a more reasonable summary.  
 */
public class TextRankSummarizer implements Summarizer
{
	//An optional file to store idf of words. If idf is not available it uses a default equal weight for all words.
    private String idfFile = "resources/idf.csv";
    public TextRankSummarizer() throws Exception
    {
    }
 
    /*Sets up data and calls the TextRank algorithm..*/
    public List<Score> rankSentences(String doc, List<Sentence> sentences, 
    							     DocProcessor dp, int maxWords )
    { 
        try {            
    	    //Rank sentences	
            TextRank summ = new TextRank(dp);
            List<String> sentenceStrL = new ArrayList<String>();
            List<String> processedSent = new ArrayList<String>();
            Hashtable<String, List<Integer>> iidx = new Hashtable<String, List<Integer>>();
       //     dp.getSentences(sentences, sentenceStrL, iidx, processedSent);
            
            for(Sentence s : sentences){           	
            	sentenceStrL.add(s.getStringVal());
            	String stemmedSent = s.stem();
            	processedSent.add(stemmedSent);
            	
            	String[] wrds = stemmedSent.split(" ");
            	for(String w: wrds)
            	{
            		if(iidx.get(w)!=null) 
            			iidx.get(w).add(s.getSentId());
            		else{
            			List<Integer> l = new ArrayList<Integer>();
            			l.add(s.getSentId());
            			iidx.put(w, l);
            		}
            	}
            }        
           
            WordWeight wordWt = new IDFWordWeight(idfFile);////new 
            
    	    List<Score> finalScores = summ.getRankedSentences(doc, sentenceStrL, iidx, processedSent);
    	    List<String> sentenceStrList = summ.getSentences();
    	    
    	   // SentenceClusterer clust = new SentenceClusterer();
    	   //  clust.runClusterer(doc, summ.processedSent);
                
    		Hashtable<Integer,List<Integer>> links= summ.getLinks();

			for(int i=0;i<sentences.size();i++)
			{
				Sentence st = sentences.get(i);
				
				//Add links..
				List<Integer> currLnks = links.get(i);
				if(currLnks==null) continue;
				for(int j=0;j<currLnks.size();j++)
				{
					if(j<i) st.addLink(sentences.get(j));	
				}
			}
			
			for(int i=0;i<finalScores.size();i++)
			{
				Score s = finalScores.get(i);
				Sentence st = sentences.get(s.getSentId());
				st.setPageRankScore(s);
			}

			List<Score> reRank = finalScores;//reRank(sentences, finalScores, iidx, wordWt, maxWords);
			
			return reRank;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }

    //Returns the summary as a string. 
	@Override
	public String summarize(String article, DocProcessor dp, int maxWords) {
        List<Sentence> sentences = dp.getSentencesFromStr(article);        
        List<Score> scores = this.rankSentences(article, sentences, dp, maxWords);
        return scores2String(sentences, scores, maxWords);
	}
    
	/* Use the page rank scores to determine the summary.*/
    public String scores2String(List<Sentence> sentences, List<Score> scores, int maxWords)
    {
        StringBuffer b = new StringBuffer();
       // for(int i=0;i< min(maxWords, scores.size()-1);i++)
        int i=0;
        while(b.length()< maxWords && i< scores.size())
        {
        	String sent = sentences.get(scores.get(i).getSentId()).getStringVal();
        	b.append(sent + scores.get(i));
        	i++;
        }
        return b.toString();
    }
    
}
