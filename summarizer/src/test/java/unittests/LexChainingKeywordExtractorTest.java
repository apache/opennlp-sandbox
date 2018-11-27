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

import java.util.List;

import opennlp.summarization.Sentence;
import opennlp.summarization.lexicalchaining.LexChainingKeywordExtractor;
import opennlp.summarization.lexicalchaining.LexicalChain;
import opennlp.summarization.lexicalchaining.LexicalChainingSummarizer;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import org.junit.BeforeClass;
import org.junit.Test;

public class LexChainingKeywordExtractorTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Test
	public void testGetKeywords() {
		try {
			String sentFragModel = "resources/en-sent.bin";
			DefaultDocProcessor dp =new DefaultDocProcessor(sentFragModel);
			String article = dp.docToString("/Users/ram/dev/summarizer/test/forram/topnews/input/8.txt");
			LexicalChainingSummarizer lcs;
			lcs = new LexicalChainingSummarizer(dp,"resources/en-pos-maxent.bin");

			long strt = System.currentTimeMillis();

			List<Sentence> sent = dp.getSentencesFromStr(article);
			List<LexicalChain> vh = lcs.buildLexicalChains(article, sent);
			LexChainingKeywordExtractor ke = new LexChainingKeywordExtractor();
			List<String> keywords = ke.getKeywords(vh, 5);
			//lazy
			System.out.println(keywords);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
