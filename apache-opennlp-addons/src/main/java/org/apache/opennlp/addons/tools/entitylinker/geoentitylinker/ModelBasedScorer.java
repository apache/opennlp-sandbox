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
package org.apache.opennlp.addons.tools.entitylinker.geoentitylinker;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.entitylinker.EntityLinkerProperties;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

/**
 *
 *Utilizes a doccat model to score toponyms based on surrounding context
 */
public class ModelBasedScorer implements LinkedEntityScorer<CountryContext> {

  public static ModelBasedScorer scorer;

  static {
    scorer = new ModelBasedScorer();
  }
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
      Logger.getLogger(ModelBasedScorer.class.getName()).log(Level.SEVERE, null, ex);
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

  private String getTextChunk(int mentionIdx, String docText, int radius) {
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
    if (documentCategorizerME == null) {
      documentCategorizerME = new DocumentCategorizerME(new DoccatModel(new File("")));
    }
    double[] categorize = documentCategorizerME.categorize(text);
    int catSize = documentCategorizerME.getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = documentCategorizerME.getCategory(i);
      scoreMap.put(category, categorize[documentCategorizerME.getIndex(category)]);
    }
    return scoreMap;
  }

  /**
   *
   * @param documents         A list of document texts, for best results try to
   *                          ensure each country you care about will be
   *                          represented by the collection
   * @param annotationOutFile the location where the annotated doccat text file
   *                          will be stored
   * @param modelOutFile      the location where the doccat model will be stored
   * @param properties        the properties where the country context object
   *                          will find it's country data from this property:
   *                          opennlp.geoentitylinker.countrycontext.filepath
   * @throws IOException
   */
  public static void buildCountryContextModel(Collection<String> documents, File annotationOutFile, File modelOutFile, EntityLinkerProperties properties) throws IOException {
    CountryContext context = new CountryContext();
    FileWriter writer = new FileWriter(annotationOutFile, true);
    for (String docText : documents) {

      Map<String, Set<Integer>> regexfind = context.regexfind(docText, properties);
      Map<String, ArrayList<String>> modelCountryContext = modelCountryContext(docText, context, RADIUS);
      for (String key : modelCountryContext.keySet()) {
        for (String wordbag : modelCountryContext.get(key)) {
          writer.write(key + " " + wordbag + "\n");
        }
      }
    }

    writer.close();

    DoccatModel model = null;

    InputStream dataIn = new FileInputStream(annotationOutFile);
    try {

      ObjectStream<String> lineStream =
              new PlainTextByLineStream(dataIn, "UTF-8");
      ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

      model = DocumentCategorizerME.train("en", sampleStream);
      OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelOutFile));
      model.serialize(modelOut);
    } catch (IOException e) {
      // Failed to read or parse training data, training failed
      e.printStackTrace();
    }

  }

  /**
   * generates proximal wordbags within the radius of a country mention within
   * the doctext based on the country context object
   *
   *
   * @param docText
   * @param additionalContext
   * @param radius
   * @return
   */
  public static Map<String, ArrayList<String>> modelCountryContext(String docText, CountryContext additionalContext, int radius) {
    Map<String, ArrayList< String>> featureBags = new HashMap<>();
    Map<String, Set<Integer>> countryMentions = additionalContext.getCountryMentions();
    /**
     * iterator over the map that contains a mapping of every country code to
     * all of its mentions in the document
     */
    for (String code : countryMentions.keySet()) {
      /**
       * for each mention, collect features from around each mention, then
       * consolidate the features into another map
       */
      for (int mentionIdx : countryMentions.get(code)) {
        String chunk = scorer.getTextChunk(mentionIdx, docText, radius);
        //   Collection<String> extractFeatures = super.extractFeatures(chunk.split(" "));
        if (featureBags.containsKey(code)) {
          featureBags.get(code).add(chunk);
        } else {
          ArrayList<String> newlist = new ArrayList<>();
          newlist.add(chunk);
          featureBags.put(code, newlist);
        }
      }
    }
    return featureBags;
  }
}
