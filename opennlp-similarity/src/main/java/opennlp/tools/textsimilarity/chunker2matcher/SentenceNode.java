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

package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentence node is the first clause node contained in the top node
 * 
 */
public class SentenceNode extends PhraseNode {
	private String sentence;

	public SentenceNode(String sentence, List<SyntacticTreeNode> children) {
		super(ParserConstants.TYPE_S, children);

		this.sentence = sentence;
	}

	@Override
	public String getText() {
		return sentence;
	}

	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	@Override
	public String toStringIndented(int numTabs) {
		StringBuilder builder = new StringBuilder();
		String indent = SyntacticTreeNode.getIndent(numTabs);

		// output the sentence
		builder.append(indent).append(sentence).append("\n");
		builder.append(super.toStringIndented(numTabs));

		return builder.toString();
	}
	
	@Override
	public List<String> getOrderedPOSList(){
		List<String> types = new ArrayList<String>(); 
		if (this.getChildren()!= null && this.getChildren().size() > 0) {
			for (SyntacticTreeNode child : this.getChildren()) {
				types.addAll(child.getOrderedPOSList());
			}
		}
		return types;
	}
}
