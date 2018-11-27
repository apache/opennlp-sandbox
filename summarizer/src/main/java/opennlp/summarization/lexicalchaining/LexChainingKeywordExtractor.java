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
import java.util.Collections;
import java.util.List;

/*
 * Use the lexical chaining algorithm to extract keywords.
 */
public class LexChainingKeywordExtractor {
	
	//Simple logic to pull out the keyword based on longest lexical chains..
	public List<String> getKeywords(List<LexicalChain> lexicalChains, int noOfKeywrds){
		Collections.sort(lexicalChains);
		List<String> ret = new ArrayList<String>();
		for(int i=0;i<Math.min(lexicalChains.size(), noOfKeywrds);i++)
		{
			List<Word> words = lexicalChains.get(i).getWord();
			if(words.size()>0 &&!ret.contains(words.get(0).getLexicon())){
				ret.add(words.get(0).getLexicon());
			}
		}
		return ret;		
	}
}
