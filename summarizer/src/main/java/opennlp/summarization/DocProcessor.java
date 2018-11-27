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

import java.util.List;

import opennlp.tools.stemmer.Stemmer;

/*
 * A document processor abstracts a lot of the underlying complexities of parsing the document and 
 * preparing it (e.g. stemming, stop word removal) from the summarization algorithm. The current package
 * supports sentence extraction based algorithms. Thus extracting Sentences from the text is the
 * first step and the basis for the algorithms.
 */
public interface DocProcessor {
	/* Extract sentences from a string representing an article.*/
	public List<Sentence> getSentencesFromStr(String text) ;
	/* Utility method to parse out words from a string.*/
	public String[] getWords(String sent);
	/* Provide a stemmer to stem words*/
	public Stemmer getStemmer();
}
