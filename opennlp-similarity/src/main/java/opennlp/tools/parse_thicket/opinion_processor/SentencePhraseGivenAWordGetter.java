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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.apps.relevanceVocabs.SentimentVocab;
import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.kernel_interface.DescriptiveParagraphFromDocExtractor;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;
import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.TextProcessor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class SentencePhraseGivenAWordGetter {
	protected static Matcher matcher;
	protected ArrayList<File> queue = new ArrayList<File>();
	protected static PT2ThicketPhraseBuilder phraseBuilder;


	static {
		synchronized (SentencePhraseGivenAWordGetter.class) {
			matcher = new Matcher();
			phraseBuilder = new PT2ThicketPhraseBuilder();
		}
	}

	public SentencePhraseGivenAWordGetter(){
	}

	public EntityExtractionResult extractEntities(String para, String keyword){
		List<List<ParseTreeNode>> extractedPhrases = new ArrayList<List<ParseTreeNode>>();

		EntityExtractionResult result = new EntityExtractionResult();

		ParseThicket pt =  matcher.buildParseThicketFromTextWithRST(para);

		List<List<ParseTreeNode>> phrases = pt.getPhrases();
		for(List<ParseTreeNode> phrase: phrases){
			// find a noun phrase under sentiment
			try {
				for(int i = 0; i<phrase.size(); i++){
					ParseTreeNode word = phrase.get(i);
					if (word.getWord().toLowerCase().equals(keyword.toLowerCase())){
						extractedPhrases.add(phrase);		
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		result.setExtractedSentimentPhrases(extractedPhrases);
		return result;
	}


	public static void main(String[] args){
		SentencePhraseGivenAWordGetter self = new SentencePhraseGivenAWordGetter();
		EntityExtractionResult result = self.extractEntities("However i put a foam panel inside the main case if i do not have my headphones or an iPad to brace the mac book", 
				"panel");
		System.out.println(result.getExtractedSentimentPhrases());
	}
}


/*
 3 phrases are given as a result
 * 
[[<2>SBAR'i':FW, <3>SBAR'put':VBD, <4>SBAR'a':DT, <5>SBAR'foam':NN, <6>SBAR'panel':NN, <7>SBAR'inside':IN, <8>SBAR'the':DT, <9>SBAR'main':JJ, <10>SBAR'case':NN, <11>SBAR'if':IN, <12>SBAR'i':FW, 
<13>SBAR'do':VBP, <14>SBAR'not':RB, <15>SBAR'have':VB, <16>SBAR'my':PRP$, <17>SBAR'headphones':NNS, <18>SBAR'or':CC, <19>SBAR'an':DT, <20>SBAR'iPad':NN, <21>SBAR'to':TO, 
<22>SBAR'brace':VB, <23>SBAR'the':DT, <24>SBAR'mac':NN, <25>SBAR'book':NN], 

[<3>VP'put':VBD, <4>VP'a':DT, <5>VP'foam':NN, <6>VP'panel':NN, <7>VP'inside':IN, <8>VP'the':DT, <9>VP'main':JJ, <10>VP'case':NN, <11>VP'if':IN, <12>VP'i':FW, <13>VP'do':VBP, 
<14>VP'not':RB, <15>VP'have':VB, <16>VP'my':PRP$, <17>VP'headphones':NNS, <18>VP'or':CC, <19>VP'an':DT, <20>VP'iPad':NN, <21>VP'to':TO, <22>VP'brace':VB, <23>VP'the':DT, 
<24>VP'mac':NN, <25>VP'book':NN], 

[<4>NP'a':DT, <5>NP'foam':NN, <6>NP'panel':NN]]

*/
