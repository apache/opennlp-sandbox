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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import opennlp.tools.similarity.apps.utils.FileHandler;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

//import com.thoughtworks.xstream.XStream;

/**
 * This class can be used to generate scores based on the overlapping between a
 * text and a given taxonomy.
 * 
 */
public class TaxoQuerySnapshotMatcher {

  ParserChunker2MatcherProcessor sm;
  // XStream xStream= new XStream();
  Map<String, List<List<String>>> lemma_ExtendedAssocWords;
  TaxonomySerializer taxo;
  private static Logger LOG = Logger
      .getLogger("opennlp.tools.similarity.apps.taxo_builder.TaxoQuerySnapshotMatcher");

  public TaxoQuerySnapshotMatcher(String taxoFileName) {
    sm = ParserChunker2MatcherProcessor.getInstance();
    taxo = TaxonomySerializer.readTaxonomy(taxoFileName); // "src/test/resources/taxonomies/irs_domTaxo.dat");
  }

  /**
   * Can be used to generate scores based on the overlapping between a text and
   * a given taxonomy.
   * 
   * @param query
   *          The query string the user used for ask a question.
   * @param snapshot
   *          The abstract of a hit the system gave back
   * @return
   */
  public int getTaxoScore(String query, String snapshot) {

    lemma_ExtendedAssocWords = (HashMap<String, List<List<String>>>) taxo
        .getLemma_ExtendedAssocWords();

    query = query.toLowerCase();
    snapshot = snapshot.toLowerCase();
    String[] queryWords = null, snapshotWords = null;
    try {
      queryWords = sm.getTokenizer().tokenize(query);
      snapshotWords = sm.getTokenizer().tokenize(snapshot);
    } catch (Exception e) { // if OpenNLP model is unavailable, use different tokenizer
      queryWords = TextProcessor.fastTokenize(query, false).toArray(new String[0]);
      snapshotWords = TextProcessor.fastTokenize(snapshot, false).toArray(new String[0]);
    }

    List<String> queryList = Arrays.asList(queryWords);
    List<String> snapshotList = Arrays.asList(snapshotWords);

    List<String> commonBetweenQuerySnapshot = (new ArrayList<String>(queryList));
    commonBetweenQuerySnapshot.retainAll(snapshotList);// Still could be
                                                       // duplicated words (even
                                                       // more if I would retain
                                                       // all the opposite ways)

    int score = 0;
    List<String> accumCommonParams = new ArrayList<String>();
    for (String qWord : commonBetweenQuerySnapshot) {
      if (!lemma_ExtendedAssocWords.containsKey(qWord))
        continue;
      List<List<String>> foundParams = new ArrayList<List<String>>();
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

  /**
   * It loads a serialized taxonomy in .dat format and serializes it into a much
   * more readable XML format.
   * 
   * @param taxonomyPath
   * @param taxonomyXML_Path
   * 

  public void convertDatToXML(String taxonomyXML_Path, TaxonomySerializer taxo) {
    XStream xStream = new XStream();
    FileHandler fileHandler = new FileHandler();
    try {
      fileHandler.writeToTextFile(xStream.toXML(taxo), taxonomyXML_Path, false);
    } catch (Exception e) {
      e.printStackTrace();
      LOG.info(e.toString());
    }

  }

  public void xmlWork() {
    TaxoQuerySnapshotMatcher matcher = new TaxoQuerySnapshotMatcher(
        "src/test/resources/taxonomies/irs_domTaxo.dat");
    XStream xStream = new XStream();
    FileHandler fileHandler = new FileHandler();
    matcher.taxo = (TaxonomySerializer) xStream.fromXML(fileHandler
        .readFromTextFile("src/test/resources/taxo_English.xml"));
  }
*/
  public void close() {
    sm.close();
  }

  /**
   * demonstrates the usage of the taxonomy matcher
   * 
   * @param args
   */
  static public void main(String[] args) {

    TaxoQuerySnapshotMatcher matcher = new TaxoQuerySnapshotMatcher(
        "src/test/resources/taxonomies/irs_domTaxo.dat");

    System.out
        .println("The score is: "
            + matcher
                .getTaxoScore(
                    "Can Form 1040 EZ be used to claim the earned income credit.",
                    "Can Form 1040EZ be used to claim the earned income credit? . Must I be entitled to claim a child as a dependent to claim the earned income credit based on the child being "));
  }

}
