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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.models.ModelType;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.ThreadSafePOSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;

/**
 * A {@link POSTagger} wrapper implementation that relies on an OpenNLP {@link POSTaggerME}.
 *
 * @see POSTagger
 * @see POSTaggerME
 */
public class NounPOSTagger implements POSTagger {

  public static final String[] TAGS_NOUNS = {"NOUN", "NN", "NNS", "NNP", "NNPS"};
  private static final Set<String> EOS_CHARS = Set.of(".", "?", "!");

  private static final ClassPathModelProvider MODEL_PROVIDER = new DefaultClassPathModelProvider();

  private final ThreadSafePOSTaggerME tagger;
  private final Map<Integer, String[]> tagMap = new Hashtable<>();

  /**
   * Instantiates a {@link NounPOSTagger} for a POS model for the specified {@code languageCode}.
   *
   * @param languageCode An ISO-language code for obtaining a {@link POSModel}.
   *                     Must not be {@code null}.
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public NounPOSTagger(String languageCode) throws IOException {
    if (languageCode == null || languageCode.isBlank())
      throw new IllegalArgumentException("Parameter 'languageCode' must not be null");
    // init Tag map
    tagMap.put(POSTagger.NOUN, TAGS_NOUNS);
    final POSModel pm = MODEL_PROVIDER.load(languageCode, ModelType.POS_GENERIC, POSModel.class);
    tagger = new ThreadSafePOSTaggerME(pm);
  }

  /**
   * @return {@code true} if the type string belongs to one of the (noun) tags for the type,
   *         {@code false} otherwise.
   */
  public boolean isType(String typeStr, int type) {
    boolean ret = false;
    String[] tags = tagMap.get(type);
    if (tags != null) {
      for (String tag : tags) {
        if (typeStr.equalsIgnoreCase(tag)) {
          ret = true;
          break;
        }
      }
      return ret;
    } else {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTaggedString(String input) {
    if (input == null) throw new IllegalArgumentException("Parameter 'input' must not be null");
    
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(input);
    String[] tags = tagger.tag(tokens);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      sb.append(tokens[i]).append("/").append(tags[i]);
      // whitespace appending only for non-EOS / PUNCT tokens, skipping for actual EOS tokens
      if (! (EOS_CHARS.contains(tokens[i]) && tokens.length == i + 1)) {
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getWordsOfType(String[] tokens, int type) {
    if (tokens == null)
      throw new IllegalArgumentException("Parameter 'tokens' must not be null");
    if (type < 0 || type > PRONOUN)
      throw new IllegalArgumentException("Parameter 'type' must be in range [0, 4]");

    List<String> ret = new ArrayList<>();
    for (String t : tokens) {
      String[] wordPlusType = t.split("/");
      if (wordPlusType.length == 2) {
        if (isType(wordPlusType[1], type))
          ret.add(wordPlusType[0]);
      } else {
        throw new IllegalArgumentException("Token '" + t + "' is not tagged correctly!");
      }
    }
    // log.info(ret.toString());
    return ret;
  }
}
