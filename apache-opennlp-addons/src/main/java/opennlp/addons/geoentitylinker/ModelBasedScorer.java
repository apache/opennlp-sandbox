/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.addons.geoentitylinker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.entitylinker.EntityLinkerProperties;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 *
 * Utilizes a doccat model to score toponyms based on surrounding context
 */
public class ModelBasedScorer implements LinkedEntityScorer<CountryContext> {


  DocumentCategorizerME documentCategorizerME;
  DoccatModel doccatModel;
  public static final int RADIUS = 100;

  @Override
  public void score(List<LinkedSpan> linkedSpans, String docText, Span[] sentenceSpans, EntityLinkerProperties properties, CountryContext additionalContext) {
    try {
      if (doccatModel == null) {
        String path = properties.getProperty("opennlp.geoentitylinker.modelbasedscorer.modelpath", "");
        if (path.equals("")) {
          return;
        }
        doccatModel = new DoccatModel(new File(path));
        documentCategorizerME = new DocumentCategorizerME(doccatModel);
      }
      Map<Integer, String> proximalFeatures = generateProximalFeatures(linkedSpans, sentenceSpans, docText, RADIUS);
      for (Map.Entry<Integer, String> entry : proximalFeatures.entrySet()) {
        Map<String, Double> scores = this.getScore(entry.getValue());
        for (BaseLink link : (List<BaseLink>) linkedSpans.get(entry.getKey()).getLinkedEntries()) {
          double score = 0d;
          if (scores.containsKey(link.getItemParentID())) {
            score = scores.get(link.getItemParentID());
          }
          link.getScoreMap().put("countrymodel", score);
        }
      }

    } catch (FileNotFoundException ex) {
      System.err.println("could not find modelpath using EntityLinkerProperties. Property should be \"opennlp.geoentitylinker.modelbasedscorer.modelpath\"");
    } catch (IOException ex) {
      System.err.println(ex);
    } catch (Exception ex) {
      System.err.println(ex);
    }
  }

  /**
   * generates features using a BagOfWordsfeatureGenerator that are within the
   * radius of a mention within the doctext
   *
   * @param linkedSpans
   * @param docText
   * @param additionalContext
   * @param radius
   * @return a map of the index of the linked span to the string of surrounding
   *         text: Map<indexofspan,surrounding text>
   */
  public Map<Integer, String> generateProximalFeatures(List<LinkedSpan> linkedSpans, Span[] sentenceSpans, String docText, int radius) {
    Map<Integer, String> featureBags = new HashMap<>();
    Map<Integer, Integer> nameMentionMap = new HashMap<>();
    /**
     * iterator over the map that contains a mapping of every country code to
     * all of its mentions in the document
     */
    for (int i = 0; i < linkedSpans.size(); i++) {
      LinkedSpan span = linkedSpans.get(i);
      if (span.getLinkedEntries().isEmpty()) {
        //don't care about spans that did not get linked to anything at all; nothing to work with
        continue;
      }
      /**
       * get the sentence the name span was found in, the beginning of the
       * sentence will suffice as a centroid for feature generation around the
       * named entity
       */
      Integer mentionIdx = sentenceSpans[span.getSentenceid()].getStart();
      nameMentionMap.put(i, mentionIdx);
    }
    /**
     * now associate each span to a string that will be used for categorization
     * against the model.
     */
    for (Map.Entry<Integer, Integer> entry : nameMentionMap.entrySet()) {
      featureBags.put(entry.getKey(), getTextChunk(entry.getValue(), docText, radius));
    }


    return featureBags;
  }

  public String getTextChunk(int mentionIdx, String docText, int radius) {
    int docSize = docText.length();
    int left = 0, right = 0;
    left = (mentionIdx - radius < 0) ? 0 : mentionIdx - radius;
    right = (mentionIdx + radius > docSize) ? docSize : mentionIdx + radius;
    String chunk = "";
    if (right <= left) {
      chunk = "";
    } else {
      /**
       * don't want to chop any words in half, so take fron the first space to
       * the last space in the chunk string
       */
      chunk = docText.substring(left, right);
      if (left != 0) {
        left = chunk.indexOf(" ");
      }
      right = chunk.lastIndexOf(" ");
      /**
       * now get the substring again with only whole words
       */
      if (left < right) {
        chunk = chunk.substring(left, right);
      }
    }

    return chunk;
  }

  private Map<String, Double> getScore(String text) throws Exception {
    Map<String, Double> scoreMap = new HashMap<>();
    double[] categorize = documentCategorizerME.categorize(text);
    int catSize = documentCategorizerME.getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = documentCategorizerME.getCategory(i);
      scoreMap.put(category, categorize[documentCategorizerME.getIndex(category)]);
    }
    return scoreMap;
  }

  
}
