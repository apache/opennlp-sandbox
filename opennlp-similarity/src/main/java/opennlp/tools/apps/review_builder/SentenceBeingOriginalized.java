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

package opennlp.tools.apps.review_builder;

import java.util.List;
import java.util.Map;

import opennlp.tools.textsimilarity.ParseTreeChunk;

public class SentenceBeingOriginalized {
	private Map<String, String> sentKey_value;
	private String sentence;
	private List<List<ParseTreeChunk>> groupedChunks;
	
	
	
	public Map<String, String> getSentKey_value() {
		return sentKey_value;
	}



	public void setSentKey_value(Map<String, String> sentKey_value) {
		this.sentKey_value = sentKey_value;
	}



	public String getSentence() {
		return sentence;
	}



	public void setSentence(String sentence) {
		this.sentence = sentence;
	}



	public List<List<ParseTreeChunk>> getGroupedChunks() {
		return groupedChunks;
	}



	public void setGroupedChunks(List<List<ParseTreeChunk>> groupedChunks) {
		this.groupedChunks = groupedChunks;
	}



	public SentenceBeingOriginalized(Map<String, String> sentKey_value,
			String sentence, List<List<ParseTreeChunk>> groupedChunks) {
		super();
		this.sentKey_value = sentKey_value;
		this.sentence = sentence;
		this.groupedChunks = groupedChunks;
	}
}
