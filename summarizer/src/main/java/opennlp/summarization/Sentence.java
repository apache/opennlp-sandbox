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

package opennlp.summarization;

import java.text.BreakIterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import opennlp.summarization.preprocess.PorterStemmer;
import opennlp.summarization.preprocess.StopWords;

/*
 * A representation of a sentence geared toward pagerank and summarization.
 */
public class Sentence {	
	//sentId is always position of sentence in doc..
	private int sentId;
	private String stringVal, procStringVal;
	private Score pageRankScore;
	private int paragraph;
	private int paraPos;
	private boolean hasQuote;
	private double wordWt = 0;
	private int wordCnt;
	
	private List<Sentence> links;
	private PorterStemmer stemmer;
	
	public Sentence(){
		links = new ArrayList<Sentence>();
	}

	public Sentence(int id){
		this();
		this.sentId = id;
	}
	
	public void setSentId(int sentId) {
		this.sentId = sentId;
	}
	
	public int getSentId() {
		return sentId;
	}
	
	public void setPageRankScore(Score pageRankScore) {
		this.pageRankScore = pageRankScore;
	}
	
	public Score getPageRankScore() {
		return pageRankScore;
	}
	
	public void setParagraph(int paragraph) {
		this.paragraph = paragraph;
	}
	
	public int getParagraph() {
		return paragraph;
	}
	
	public void setParaPos(int paraPos) {
		this.paraPos = paraPos;
	}
	
	public int getParaPos() {
		return paraPos;
	}

	public void setStringVal(String stringVal) {
		this.stringVal = stringVal;
		if(stringVal.contains("\"")) this.hasQuote = true;
		this.wordCnt = calcWrdCnt(stringVal);
	}

	private int calcWrdCnt(String stringVal2) {
		int ret = 0;
		StopWords sw = StopWords.getInstance();
		String[] wrds = stringVal.split(" ");
		for(String wrd: wrds){
			if(!sw.isStopWord(wrd)&&!wrd.startsWith("'")&&!wrd.equals(".")&&!wrd.equals("?"))
				ret++;
		}
		return ret;
	}

	public String getStringVal() {
		return stringVal;
	}
	
	public void addLink(Sentence s)
	{
		this.links.add(s);
	}
	
	public List<Sentence> getLinks()
	{
		return this.links;
	}
	
	public String toString()
	{
		return this.stringVal ;//+ "("+ this.paragraph +", "+this.paraPos+")";
	}

	public void setWordWt(double wordWt) {
		this.wordWt = wordWt;
	}

	public double getWordWt() {
		return wordWt;
	}
	
	public int getWordCnt()
	{
		return wordCnt==0? this.getStringVal().split(" ").length: wordCnt;
	}

	//Should add an article id to the sentence class.. For now returns true if the ids are the same..
	public boolean equals(Object o){
		if(! (o instanceof Sentence)) return false;

		Sentence s = (Sentence)o;
		if(s.sentId == this.sentId) return true;
		return false;
	}

	static final String space=" ";
	public String stem() {
		PorterStemmer stemmer = new PorterStemmer();
	    StopWords sw = StopWords.getInstance();      

	    BreakIterator wrdItr = BreakIterator.getWordInstance(Locale.US);
		int wrdStrt = 0;
		StringBuffer b = new StringBuffer();
		wrdItr.setText(stringVal);	
		for(int wrdEnd = wrdItr.next(); wrdEnd != BreakIterator.DONE; 
				wrdStrt = wrdEnd, wrdEnd = wrdItr.next())
		{
			String word = this.getStringVal().substring(wrdStrt, wrdEnd);//words[i].trim();
			word.replaceAll("\"|'","");

			//Skip stop words and stem the word..
			if(sw.isStopWord(word)) continue;                        
            stemmer.stem(word);
			b.append(stemmer.toString()); 
			b.append(space);
		}
		// TODO Auto-generated method stub
		return b.toString();
	}
}
