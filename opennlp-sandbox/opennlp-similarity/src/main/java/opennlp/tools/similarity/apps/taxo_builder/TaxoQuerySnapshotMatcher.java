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

package opennlp.tools.similarity.apps.taxo_builder;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.models.ModelType;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerModel;

/**
 * This class can be used to generate scores based on the overlapping between a
 * text and a given taxonomy.
 */
public class TaxoQuerySnapshotMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PRIMARY_LOCALE = "en";
  
  private static final ClassPathModelProvider MODEL_PROVIDER = new DefaultClassPathModelProvider();

  private final Tokenizer tokenizer;
  private final ParserChunker2MatcherProcessor sm;
  private final TaxonomySerializer taxo;

  /**
   * Initializes a {@link TaxoQuerySnapshotMatcher} with the specified taxonomy.
   *
   * @param taxoFileName The {@link java.io.File} that holds the taxonomy data.
   */
  public TaxoQuerySnapshotMatcher(String taxoFileName) {
    try {
      TokenizerModel tm = MODEL_PROVIDER.load(PRIMARY_LOCALE, ModelType.TOKENIZER, TokenizerModel.class);
      tokenizer = new ThreadSafeTokenizerME(tm);
    } catch (IOException e) {
      LOG.warn("A model can't be loaded: {}", e.getMessage());
      throw new RuntimeException(e.getLocalizedMessage(), e);
    }
    sm = ParserChunker2MatcherProcessor.getInstance();
    taxo = TaxonomySerializer.readTaxonomy(taxoFileName);
  }
  
  /**
   * Can be used to generate scores based on the overlapping between a text and
   * a given taxonomy.
   * 
   * @param query
   *          The query string the user used for ask a question.
   * @param snapshot
   *          The abstract of a hit the system gave back
   * @return The score, guaranteed to be greater or equal to zero.
   */
  public int getTaxoScore(String query, String snapshot) {

    // XStream xStream= new XStream();
    Map<String, List<List<String>>> lemma_ExtendedAssocWords = taxo.getLemma_ExtendedAssocWords();

    query = query.toLowerCase();
    snapshot = snapshot.toLowerCase();
    String[] queryWords, snapshotWords;
    try {
      queryWords = tokenizer.tokenize(query);
      snapshotWords = tokenizer.tokenize(snapshot);
    } catch (Exception e) { // if OpenNLP model is unavailable, use different tokenizer
      queryWords = TextProcessor.fastTokenize(query, false).toArray(new String[0]);
      snapshotWords = TextProcessor.fastTokenize(snapshot, false).toArray(new String[0]);
    }

    List<String> queryList = Arrays.asList(queryWords);
    List<String> snapshotList = Arrays.asList(snapshotWords);

    List<String> commonBetweenQuerySnapshot = new ArrayList<>(queryList);
    commonBetweenQuerySnapshot.retainAll(snapshotList);
    // Still could be duplicated words (even more if I would retain all the opposite ways)

    int score = 0;
    List<String> accumCommonParams = new ArrayList<>();
    for (String qWord : commonBetweenQuerySnapshot) {
      if (!lemma_ExtendedAssocWords.containsKey(qWord))
        continue;
      List<List<String>> foundParams;
      foundParams = lemma_ExtendedAssocWords.get(qWord);

      for (List<String> paramsForGivenMeaning : foundParams) {
        paramsForGivenMeaning.retainAll(queryList);
        paramsForGivenMeaning.retainAll(snapshotList);
        int size = paramsForGivenMeaning.size();

        if (size > 0 && !accumCommonParams.containsAll(paramsForGivenMeaning)) {
          score += size;
          accumCommonParams.addAll(paramsForGivenMeaning);
        }
      }
    }
    return score;
  }

  public void close() {
    sm.close();
  }

}
