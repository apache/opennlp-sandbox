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

package opennlp.tools.coref.sim;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.coref.dictionary.Dictionary;
import opennlp.tools.coref.dictionary.DictionaryFactory;
import opennlp.tools.coref.mention.HeadFinder;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.util.Span;

/**
 * Specifies the context of a mention for computing gender, number, and semantic compatibility.
 *
 * @see Mention
 */
public class Context extends Mention {

  protected String headTokenText;
  protected String headTokenTag;
  protected Set<String> synsets;
  protected Object[] tokens;

  /** The token index in of the head word of this mention. */
  protected int headTokenIndex;

  public Context(Span span, Span headSpan, int entityId, Parse parse,
                 String extentType, String nameType, HeadFinder headFinder) {
    super(span,headSpan,entityId,parse,extentType,nameType);
    init(headFinder);
  }

  public Context(Object[] tokens, String headToken, String headTag, String neType) {
    super(null,null,1,null,null,neType);
    this.tokens = tokens;
    this.headTokenIndex = tokens.length - 1;
    this.headTokenText = headToken;
    this.headTokenTag = headTag;
    this.synsets = getSynsetSet(this);
  }

  public Context(Mention mention, HeadFinder headFinder) {
    super(mention);
    init(headFinder);
  }

  private void init(HeadFinder headFinder) {
    Parse head = headFinder.getLastHead(parse);
    List<Parse> tokenList = head.getTokens();
    headTokenIndex = headFinder.getHeadIndex(head);
    Parse headToken = headFinder.getHeadToken(head);
    tokens = tokenList.toArray(new Parse[0]);
    this.headTokenTag = headToken.getSyntacticType();
    this.headTokenText = headToken.toString();
    if (headTokenTag.startsWith("NN") && !headTokenTag.startsWith("NNP")) {
      this.synsets = getSynsetSet(this);
    }
    else {
      this.synsets = Collections.emptySet();
    }
  }


  public static Context[] constructContexts(Mention[] mentions,HeadFinder headFinder) {
    Context[] contexts = new Context[mentions.length];
    for (int mi = 0; mi < mentions.length;mi++) {
      contexts[mi] = new Context(mentions[mi],headFinder);
    }
    return contexts;
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Object token : tokens) {
      sb.append(token).append(" ");
    }
    return sb.toString();
  }

  public Object[] getTokens() {
    return tokens;
  }

  public String getHeadTokenText() {
    return headTokenText;
  }

  public String getHeadTokenTag() {
    return headTokenTag;
  }

  public Set<String> getSynsets() {
    return synsets;
  }

  public static Context parseContext(String word) {
    String[] parts = word.split("/");
    if (parts.length == 2) {
      String[] tokens = parts[0].split(" ");
      return new Context(tokens, tokens[tokens.length - 1], parts[1], null);
    } else if (parts.length == 3) {
      String[] tokens = parts[0].split(" ");
      return new Context(tokens, tokens[tokens.length - 1], parts[1], parts[2]);
    }
    return null;
  }

  private static Set<String> getSynsetSet(Context c) {
    Set<String> synsetSet = new HashSet<>();
    String[] lemmas = getLemmas(c);
    Dictionary dict = DictionaryFactory.getDictionary();
    //System.err.println(lemmas.length+" lemmas for "+c.headToken);
    for (String lemma : lemmas) {
      String senseKey = dict.getSenseKey(lemma, "NN", 0);
      if (senseKey != null) {
        synsetSet.add(senseKey);
        String[] synsets = dict.getParentSenseKeys(lemma, "NN", 0);
        synsetSet.addAll(Arrays.asList(synsets));
      }
    }
    return synsetSet;
  }

  private static String[] getLemmas(Context c) {
    String word = c.headTokenText.toLowerCase();
    return DictionaryFactory.getDictionary().getLemmas(word,"NN");
  }

  /**
   * @return Retrieves the token index into the mention for the head word.
   */
  public int getHeadTokenIndex() {
    return headTokenIndex;
  }
}
