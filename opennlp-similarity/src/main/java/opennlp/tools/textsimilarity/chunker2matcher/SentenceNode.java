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

import opennlp.tools.textsimilarity.ParseTreeChunk;

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
  public List<String> getOrderedPOSList() {
    List<String> types = new ArrayList<String>();
    if (this.getChildren() != null && this.getChildren().size() > 0) {
      for (SyntacticTreeNode child : this.getChildren()) {
        types.addAll(child.getOrderedPOSList());
      }
    }
    return types;
  }

  @Override
  public List<String> getOrderedLemmaList() {
    List<String> types = new ArrayList<String>();
    if (this.getChildren() != null && this.getChildren().size() > 0) {
      for (SyntacticTreeNode child : this.getChildren()) {
        types.addAll(child.getOrderedLemmaList());
      }
    }
    return types;
  }

  public List<ParseTreeChunk> getParseTreeChunkList() {
    List<ParseTreeChunk> chunks = new ArrayList<ParseTreeChunk>();

    if (this.getChildren() != null && this.getChildren().size() > 0) {
      for (SyntacticTreeNode child : this.getChildren()) {
        // if (child.getType().endsWith("P"))
        chunks.add(new ParseTreeChunk(child.getType(), child
            .getOrderedPOSList(), child.getOrderedLemmaList()));
      }
    }
    return chunks;
  }

}

/*
 * [[NP [PRP$-your NN-town NN-office CC-or NN-city NN-hall ], NP [PRP$-your
 * NN-town NN-doesn NN-t ], NP [DT-an NN-office ], NP [DT-the NN-town NN-clerk
 * CC-or DT-a NNP-Selectman ], NP [DT-a NNP-Selectman ], NP [PRP-them IN-that
 * PRP-you ], NP [PRP-you ], NP [DT-a CD-1040 NN-tax NN-form ], NP [PRP-I ], NP
 * [DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], NP [PRP$-my
 * NNP-House WP-What MD-Can PRP-I ], NP [WP-What MD-Can PRP-I ], NP [PRP-I ], NP
 * [NNP-Pine NNP-Tree NNP-Legal ]],
 * 
 * [VP [VBP-do RB-I VB-apply ], VP [VB-Go TO-to PRP$-your NN-town NN-office
 * CC-or NN-city NN-hall ], VP [VBP-have DT-an NN-office ], VP [VB-ask DT-the
 * NN-town NN-clerk CC-or DT-a NNP-Selectman ], VP [VB-Tell PRP-them IN-that
 * PRP-you ], VP [VBP-need DT-a CD-1040 NN-tax NN-form ], VP [MD-Can VB-t VB-Pay
 * DT-the NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ], VP [VB-Do
 * NNP-Pine NNP-Tree NNP-Legal ]], [PP [TO-to PRP$-your NN-town NN-office CC-or
 * NN-city NN-hall ], PP [IN-on PRP$-my NNP-House WP-What MD-Can PRP-I ]], [],
 * [SENTENCE [WRB-Where VBP-do RB-I VB-apply ], SENTENCE [VB-Go TO-to PRP$-your
 * NN-town NN-office CC-or NN-city NN-hall ], SENTENCE [IN-If PRP$-your NN-town
 * NN-doesn NN-t VBP-have DT-an NN-office VB-ask DT-the NN-town NN-clerk CC-or
 * DT-a NNP-Selectman ], SENTENCE [VB-Tell PRP-them IN-that PRP-you VBP-need
 * DT-a CD-1040 NN-tax NN-form ], SENTENCE [PRP-I MD-Can VB-t VB-Pay DT-the
 * NNS-Taxes IN-on PRP$-my NNP-House WP-What MD-Can PRP-I VB-Do NNP-Pine
 * NNP-Tree NNP-Legal ]]]
 */
