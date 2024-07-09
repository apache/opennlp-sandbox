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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import opennlp.summarization.DocProcessor;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class OpenNLPPOSTagger implements POSTagger {

  private final POSTaggerME tagger;
  private final DocProcessor dp;
  private final String[] nounTags = {"NOUN", "NN", "NNS", "NNP", "NNPS"};
  private Hashtable<Integer, String[]> tagMap;

  public OpenNLPPOSTagger(DocProcessor dp, InputStream posModelFile) throws IOException {
    this.dp = dp;
    initTagMap();

    try (InputStream modelIn = new BufferedInputStream(posModelFile)) {
      POSModel model = new POSModel(modelIn);
      tagger = new POSTaggerME(model);
    }
  }

  private void initTagMap() {
    tagMap = new Hashtable<>();
    tagMap.put(POSTagger.NOUN, nounTags);
  }

  // Returns true if the type string belongs to one of the tags for the type
  public boolean isType(String typeStr, int type) {
    boolean ret = false;
    String[] tags = tagMap.get(type);
    for (String tag : tags) {
      if (typeStr.equalsIgnoreCase(tag)) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  @Override
  public String getTaggedString(String input) {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(input);
    String[] tags = tagger.tag(tokens);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      sb.append(tokens[i]).append("/").append(tags[i]).append(" ");
    }
    return sb.toString();
  }

  @Override
  public List<String> getWordsOfType(String sent, int type) {
    List<String> ret = new ArrayList<>();
    String[] tokens = dp.getWords(sent);
    for (String t : tokens) {
      String[] wordPlusType = t.split("/");
      if (wordPlusType.length == 2) {
        if (isType(wordPlusType[1], type))
          ret.add(wordPlusType[0]);
      }
    }
    // log.info(ret.toString());
    return ret;
  }
}
