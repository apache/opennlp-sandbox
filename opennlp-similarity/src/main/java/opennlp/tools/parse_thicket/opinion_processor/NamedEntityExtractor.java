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

public class NamedEntityExtractor {
	protected static Matcher matcher;
	private static int PARA_LENGTH_IN_SENTENCES = 5, PARA_LENGTH = 250;
	protected ArrayList<File> queue = new ArrayList<File>();
	protected static PT2ThicketPhraseBuilder phraseBuilder;
	protected static SentimentVocab sVocab = SentimentVocab.getInstance();
	String resourceDirSentimentList = null;
	Set<String> sentimentVcb = new HashSet<String> ();

	static {
		synchronized (NamedEntityExtractor.class) {
			matcher = new Matcher();
			phraseBuilder = new PT2ThicketPhraseBuilder();
		}
	}

	public NamedEntityExtractor(){
		try {
			resourceDirSentimentList = new File( "." ).getCanonicalPath()+"/src/test/resources/opinions/sentiment_listReduced.csv";
		} catch (IOException e) {
			e.printStackTrace();
		}
		List<String[]> sentimentList=null;
		sentimentList = ProfileReaderWriter.readProfiles(resourceDirSentimentList);
		for(String[] line: sentimentList){
			sentimentVcb.add(line[0]);
		}
	}

	protected boolean isSentimentWord(String word){
		if (sentimentVcb.contains(word))
			return true;
		else
			return false;		
	}

	public EntityExtractionResult extractEntities(String para){
		List<List<ParseTreeNode>> extractedNERs = new ArrayList<List<ParseTreeNode>>();
		List<String> extractedNERsWords = new ArrayList<String>();
		List<List<ParseTreeNode>> extractedSentimentPhrases = 
				new ArrayList<List<ParseTreeNode>>();
		EntityExtractionResult result = new EntityExtractionResult();

		ParseThicket pt = null;

		System.out.println("Processing paragraph of length "+para.length() + " | "+ para);
		pt = matcher.buildParseThicketFromTextWithRST(para);
		List<List<ParseTreeNode>> nodeList = pt.getSentenceNodes();


		for(List<ParseTreeNode> sentence: nodeList){
			//System.out.println("   Processing sentence: "+ sentence);
			boolean bInsideNER = false; 
			String currentPhrase = "";
			List<ParseTreeNode> currentPhraseNode = new ArrayList<ParseTreeNode>(); 
			for(ParseTreeNode word: sentence){
				if (isNERforPhraseExtraction(word)){
					//System.out.println("++Found word ="+word + " | NER="+ word.getNe());
					if (bInsideNER){
						currentPhrase += " "+word.getWord();
						currentPhraseNode.add(word);
					} else {
						bInsideNER=true;
						currentPhrase = word.getWord();
						currentPhraseNode.add(word);
					}
				} else {
					if (bInsideNER){
						if (currentPhrase.indexOf(' ')>-1) // at least two tokens
							extractedNERsWords.add(currentPhrase);
							extractedNERs.add(currentPhraseNode);
						currentPhrase = "";
						bInsideNER=false;
					} else {
						// do nothing, continue scan
					}
				}
			}
			if (currentPhrase.length()>1 && currentPhrase.indexOf(' ')>-1){
				extractedNERs.add(currentPhraseNode);
				extractedNERsWords.add(currentPhrase);
			}

			Set<String> foundSentimentWords = new HashSet<String>();
			// now we extract phrases
			List<List<ParseTreeNode>> phrases = pt.getPhrases();
			for(List<ParseTreeNode> phrase: phrases){
				// find a noun phrase under sentiment
				try {
					for(int i = phrase.size()-1; i>-1; i--){
						ParseTreeNode word = phrase.get(i);
						if ((isSentimentWord(word.getWord()) ||
								sVocab.isSentimentWord(word.getWord()) && !foundSentimentWords.contains(word.getWord()) )){
							foundSentimentWords.add(word.getWord());
							System.out.println("Sentim = " + word.getWord() + " | Found opinionated phrase "+phrase.toString());
							if (phrase.size()>1 && phrase.size()<7)
								extractedSentimentPhrases.add(phrase);			
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} 
		
		extractedSentimentPhrases = reduceExtractedPhrases(extractedSentimentPhrases);
		
		result.setExtractedNER(extractedNERs);
		result.setExtractedNERWords(extractedNERsWords);
		result.setExtractedSentimentPhrases(extractedSentimentPhrases);
		return result;
	}

	private List<List<ParseTreeNode>> reduceExtractedPhrases(List<List<ParseTreeNode>> extractedSentimentPhrases) {
	    List<Integer> idsToDelete = new ArrayList<Integer>();
		for(int i = 0; i<extractedSentimentPhrases.size(); i++){
			for(int j = i+1; j<extractedSentimentPhrases.size(); j++){
				String phrStr1 = ParseTreeNode.toWordString(extractedSentimentPhrases.get(i));
				String phrStr2 = ParseTreeNode.toWordString(extractedSentimentPhrases.get(j));
				if (phrStr1 .indexOf(phrStr2 )>-1)
					idsToDelete.add(j);
			}
		}
		List<List<ParseTreeNode>> resultPhrases = new ArrayList<List<ParseTreeNode>>();
		for(int i = 0; i<extractedSentimentPhrases.size(); i++){
			if (!idsToDelete.contains(i))
				resultPhrases .add(extractedSentimentPhrases.get(i));
		}
	    return resultPhrases ;
    }

	private boolean isNERforPhraseExtraction(ParseTreeNode word){
		if ((word.getNe().equals("ORGANIZATION") ||word.getNe().equals("LOCATION") || word.getNe().equals("PERSON") ) &&
				(word.getPos().startsWith("NN") || word.getPos().startsWith("PR") || word.getPos().startsWith("IN")|| 
						word.getPos().startsWith("JJ") || word.getPos().startsWith("DT")  ))
			return true;

		return false;

	}


}
