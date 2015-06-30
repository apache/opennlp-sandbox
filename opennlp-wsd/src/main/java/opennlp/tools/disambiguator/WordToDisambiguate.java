/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator;

import java.util.ArrayList;

import net.sf.extjwnl.data.POS;




public class WordToDisambiguate {
	
	// TODO Check if it is necessary to add an attribute [word] since the word in the sentence is not necessarily in the base form ??
		
	protected String [] sentence;
	protected String [] posTags;
	
	protected int wordIndex;

	protected int sense;
	
	protected ArrayList<String> senseID;
	
	
	
	/**
	 * Constructor
	 */
	
	
	public WordToDisambiguate(String[] sentence, int wordIndex, int sense) throws IllegalArgumentException{
		super();
		
		if (wordIndex>sentence.length){
			throw new IllegalArgumentException("The index is out of bounds !");
		}
		
		this.sentence = sentence;
		this.posTags = PreProcessor.tag(sentence);
		
		this.wordIndex = wordIndex;
		
		this.sense = sense;
	}
	
	public WordToDisambiguate(String[] sentence, int wordIndex) {
		this(sentence,wordIndex,-1);
	}
	
	public WordToDisambiguate() {
		String[] emptyString = {};
		int emptyInteger = 0;
		
		this.sentence = emptyString;
		this.wordIndex = emptyInteger;
		this.sense = -1;
		
	}

	
	/**
	 * Getters and Setters
	 */
	
	// Sentence
	public String[] getSentence() {
		return sentence;
	}

	public void setSentence(String[] sentence) {
		this.sentence = sentence;
	}

	
	// Sentence Pos-Tags
	public String[] getPosTags() {
		return posTags;
	}

	public void setPosTags(String[] posTags) {
		this.posTags = posTags;
	}

	
	// Word to disambiguate
	public int getWordIndex() {
		return wordIndex;
	}

	public String getRawWord() {
		
		/**
		 * For example, from the word "running" it returns "run.v"
		 */
		
		String wordBaseForm = Loader.getLemmatizer().lemmatize(this.sentence[wordIndex], this.posTags[wordIndex]);
		
		String ref = "";
		
		if (Constants.getPOS(this.posTags[wordIndex]).equals(POS.VERB)) {
			ref = wordBaseForm + ".v";
		} else 	if (Constants.getPOS(this.posTags[wordIndex]).equals(POS.NOUN)) {
			ref = wordBaseForm + ".n";
		} else 	if (Constants.getPOS(this.posTags[wordIndex]).equals(POS.ADJECTIVE)) {
			ref = wordBaseForm + ".a";
		} else 	if (Constants.getPOS(this.posTags[wordIndex]).equals(POS.ADVERB)) {
			ref = wordBaseForm + ".r";
		} else {
			
		}
		
		return ref;
		
	}
	
	public String getWord() {
		return this.sentence[this.wordIndex];
	}
	
	public String getPosTag() {
		return this.posTags[this.wordIndex];
	}
	
	public void setWordIndex(int wordIndex) {
		this.wordIndex = wordIndex;
	}
	

	
	
	// Word to disambiguate sense
	public int getSense() {
		return sense;
	}

	public void setSense(int sense) {
		this.sense = sense;
	}

	
	
	// Sense as in the source
	// TODO fix the conflict between this ID of the sense and that in the attribute [sense]
	public ArrayList<String> getSenseID() {
		return senseID;
	}

	public void setSenseID(ArrayList<String> senseID) {
		this.senseID = senseID;
	}
	
	


	/**
	 * toString
	 */
			
	public String toString() {
		return (wordIndex + "\t" + getWord() + "\n" + sentence);
	}
	

	

}
