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

package opennlp.summarization.lexicalchaining;

import java.util.ArrayList;
import java.util.List;

import opennlp.summarization.Sentence;


public class LexicalChain implements Comparable<LexicalChain>{
		List<Word> word;
		
		List<Sentence> sentences;
		
		int start, last;
		int score;
		int occurences=1;
		
		public LexicalChain()
		{
			word = new ArrayList<Word>();
			sentences = new ArrayList<Sentence>();
		}
		
		public double score()
		{
			return length() ;//* homogeneity();
		}
		
		public int length(){
			return word.size();
		}
		
		public float homogeneity()
		{
			return (1.0f - (float)occurences/(float)length());
		}
		
		public void addWord(Word w)
		{
			word.add(w);
		}
		
		public void addSentence(Sentence sent)
		{
			if(!sentences.contains(sent))
				sentences.add(sent);
		}
		
		public List<Word> getWord()
		{
			return word;
		}
		
		public List<Sentence>getSentences()
		{
			return this.sentences;
		}

		@Override
		public int compareTo(LexicalChain o) {
			double diff = (score() - o.score());
			return diff ==0? 0: diff > 0 ?1:-1;
		}
		
		@Override
		public boolean equals(Object o){
			return super.equals(o);
		}
}
