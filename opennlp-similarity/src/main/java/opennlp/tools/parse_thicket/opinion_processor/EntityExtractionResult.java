package opennlp.tools.parse_thicket.opinion_processor;

import java.util.List;

import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.similarity.apps.HitBase;

public class EntityExtractionResult {
	List<List<ParseTreeNode>> extractedNERs;
	public List<String> extractedNERWords;
	// phrases w/sentiments
	public List<List<ParseTreeNode>> extractedSentimentPhrases;
	public List<String> extractedSentimentPhrasesStr;
	// phrases w/o sentiments
	public List<List<ParseTreeNode>> extractedNONSentimentPhrases;
	public List<String> extractedNONSentimentPhrasesStr;
	public List<Float> sentimentProfile;
	
	
	public List<String> getExtractedSentimentPhrasesStr() {
		return extractedSentimentPhrasesStr;
	}

	public void setExtractedSentimentPhrasesStr(List<String> extractedSentimentPhrasesStr) {
		this.extractedSentimentPhrasesStr = extractedSentimentPhrasesStr;
	}
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
	public List<List<ParseTreeNode>> getExtractedNONSentimentPhrases() {
		return extractedNONSentimentPhrases;
	}

	public void setExtractedNONSentimentPhrases(List<List<ParseTreeNode>> extractedNONSentimentPhrases) {
		this.extractedNONSentimentPhrases = extractedNONSentimentPhrases;
	}

	public List<String> getExtractedNONSentimentPhrasesStr() {
		return extractedNONSentimentPhrasesStr;
	}

	public void setExtractedNONSentimentPhrasesStr(List<String> extractedNONSentimentPhrasesStr) {
		this.extractedNONSentimentPhrasesStr = extractedNONSentimentPhrasesStr;
	}

	public List<HitBase> hits;
	private List<List<ParseTreeNode>> extractedNerPhrases;
	private List<String> extractedNerPhrasesStr;
	private List<String> extractedNerPhraseTags;
	private List<List<ParseTreeNode>> extractedNerExactPhrases;
	private List<String> extractedNerExactStr;

	public void setExtractedNERWords(List<String> extractedNERWords) {
		this.extractedNERWords = extractedNERWords;
	}

	public void setExtractedSentimentPhrases(List<List<ParseTreeNode>> extractedSentimentPhrases) {
		this.extractedSentimentPhrases = extractedSentimentPhrases;
	}

	public void setExtractedNER(List<List<ParseTreeNode>> extractedNERs) {
		this.extractedNERs = extractedNERs;
	}

	public void setGossipHits(List<HitBase> hitsForAnEntity) {
		hits = hitsForAnEntity;
	}

	public List<List<ParseTreeNode>> getExtractedNERs() {
		return extractedNERs;
	}

	public void setExtractedNERs(List<List<ParseTreeNode>> extractedNERs) {
		this.extractedNERs = extractedNERs;
	}

	public List<HitBase> getHits() {
		return hits;
	}

	public void setHits(List<HitBase> hits) {
		this.hits = hits;
	}

	public List<String> getExtractedNERWords() {
		return extractedNERWords;
	}

	public List<List<ParseTreeNode>> getExtractedSentimentPhrases() {
		return extractedSentimentPhrases;
	}

	public void setSentimentProfile(List<Float> sentimentProfile) {
	    this.sentimentProfile = sentimentProfile;
    }

	public List<Float> getSentimentProfile() {
		return sentimentProfile;
	}

	public void setExtractedNerPhrases(List<List<ParseTreeNode>> extractedNerPhrases) {
	    this.extractedNerPhrases = extractedNerPhrases;
	    
    }

	public void setExtractedNerPhrasesStr(List<String> extractedNerPhrasesStr) {
	    this.extractedNerPhrasesStr = extractedNerPhrasesStr;
	    
    }

	public List<List<ParseTreeNode>> getExtractedNerPhrases() {
		return extractedNerPhrases;
	}

	public List<String> getExtractedNerPhrasesStr() {
		return extractedNerPhrasesStr;
	}

	public void setExtractedNerPhraseTags(List<String> extractedNerPhraseTags) {
	    this.extractedNerPhraseTags = extractedNerPhraseTags;	    
    }

	public List<String> getExtractedNerPhraseTags() {
	    return this.extractedNerPhraseTags;    
    }

	public void setExtractedNerExactPhrases(List<List<ParseTreeNode>> extractedNerExactPhrases) {
	   this.extractedNerExactPhrases = extractedNerExactPhrases;
	    
    }

	public void setExtractedNerExactStr(List<String> extractedNerExactStr) {
	    this.extractedNerExactStr = extractedNerExactStr;
	    
    }

	public List<List<ParseTreeNode>> getExtractedNerExactPhrases() {
		return extractedNerExactPhrases;
	}

	public List<String> getExtractedNerExactStr() {
		return extractedNerExactStr;
	}
	
}
