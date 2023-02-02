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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.StringCleaner;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

/**
 * Results of taxonomy learning are two maps 0) For an entity like tax it gives
 * all lists of associated parameters obtained from the taxonomy kernel (done
 * manually) Now, given 0, we obtain the derived list of parameters as
 * commonalities of search results snapshots output map 1) for the entity,
 * derived list output map 2) for such manual list of words -> derived list of
 * words
 * 
 * 
 */

public class TaxonomyExtenderViaMebMining extends BingQueryRunner {
  private static Logger LOG = Logger
      .getLogger("opennlp.tools.similarity.apps.taxo_builder.TaxonomyExtenderSearchResultFromYahoo");
  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  ParserChunker2MatcherProcessor sm;

  private Map<String, List<List<String>>> lemma_ExtendedAssocWords = new HashMap<String, List<List<String>>>();
  private Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords = new HashMap<List<String>, List<List<String>>>();
  private PStemmer ps;

  public Map<List<String>, List<List<String>>> getAssocWords_ExtendedAssocWords() {
    return assocWords_ExtendedAssocWords;
  }

  public Map<String, List<List<String>>> getLemma_ExtendedAssocWords() {
    return lemma_ExtendedAssocWords;
  }

  public void setLemma_ExtendedAssocWords(
      Map<String, List<List<String>>> lemma_ExtendedAssocWords) {
    this.lemma_ExtendedAssocWords = lemma_ExtendedAssocWords;
  }

  public TaxonomyExtenderViaMebMining() {
    try {
      sm = ParserChunker2MatcherProcessor.getInstance();
    } catch (Exception e) { // now try 'local' openNLP
      System.err.println("Problem loading synt matcher");

    }
    ps = new PStemmer();

  }

  private List<List<String>> getCommonWordsFromList_List_ParseTreeChunk(
      List<List<ParseTreeChunk>> matchList, List<String> queryWordsToRemove,
      List<String> toAddAtEnd) {
    List<List<String>> res = new ArrayList<List<String>>();
    for (List<ParseTreeChunk> chunks : matchList) {
      List<String> wordRes = new ArrayList<String>();
      for (ParseTreeChunk ch : chunks) {
        List<String> lemmas = ch.getLemmas();
        for (int w = 0; w < lemmas.size(); w++)
          if ((!lemmas.get(w).equals("*"))
              && ((ch.getPOSs().get(w).startsWith("NN") || ch.getPOSs().get(w)
                  .startsWith("VB"))) && lemmas.get(w).length() > 2) {
            String formedWord = lemmas.get(w);
            String stemmedFormedWord = ps.stem(formedWord);
            if (!stemmedFormedWord.startsWith("invalid"))
              wordRes.add(formedWord);
          }
      }
      wordRes = new ArrayList<String>(new HashSet<String>(wordRes));
      wordRes.removeAll(queryWordsToRemove);
      if (wordRes.size() > 0) {
        wordRes.addAll(toAddAtEnd);
        res.add(wordRes);
      }
    }
    res = new ArrayList<List<String>>(new HashSet<List<String>>(res));
    return res;
  }

  public void extendTaxonomy(String fileName, String domain, String lang) {
    AriAdapter ad = new AriAdapter();
    ad.getChainsFromARIfile(fileName);
    List<String> entries = new ArrayList<String>((ad.lemma_AssocWords.keySet()));
    try {
      for (String entity : entries) { // .
        List<List<String>> paths = ad.lemma_AssocWords.get(entity);
        for (List<String> taxoPath : paths) {
          String query = taxoPath.toString() + " " + entity + " " + domain; // todo:
                                                                            // query
                                                                            // forming
                                                                            // function
                                                                            // here
          query = query.replace('[', ' ').replace(']', ' ').replace(',', ' ')
              .replace('_', ' ');
          List<List<ParseTreeChunk>> matchList = runSearchForTaxonomyPath(
              query, "", lang, 30);
          List<String> toRemoveFromExtension = new ArrayList<String>(taxoPath);
          toRemoveFromExtension.add(entity);
          toRemoveFromExtension.add(domain);
          List<List<String>> resList = getCommonWordsFromList_List_ParseTreeChunk(
              matchList, toRemoveFromExtension, taxoPath);
          assocWords_ExtendedAssocWords.put(taxoPath, resList);
          resList.add(taxoPath);
          lemma_ExtendedAssocWords.put(entity, resList);
        }
      }
    } catch (Exception e) {
      System.err.println("Problem taxonomy matching");
    }

    TaxonomySerializer ser = new TaxonomySerializer(lemma_ExtendedAssocWords,
        assocWords_ExtendedAssocWords);
    ser.writeTaxonomy(fileName.replace(".ari", "Taxo.dat"));
  }

  public List<List<ParseTreeChunk>> runSearchForTaxonomyPath(String query,
      String domain, String lang, int numbOfHits) {
    List<List<ParseTreeChunk>> genResult = new ArrayList<List<ParseTreeChunk>>();
    try {
      List<HitBase> resultList = runSearch(query, numbOfHits);

      for (int i = 0; i < resultList.size(); i++) {
        {
          for (int j = i + 1; j < resultList.size(); j++) {
            HitBase h1 = resultList.get(i);
            HitBase h2 = resultList.get(j);
            String snapshot1 = StringCleaner.processSnapshotForMatching(h1
                .getTitle() + " . " + h1.getAbstractText());
            String snapshot2 = StringCleaner.processSnapshotForMatching(h2
                .getTitle() + " . " + h2.getAbstractText());
            SentencePairMatchResult matchRes = sm.assessRelevance(snapshot1,
                snapshot2);
            List<List<ParseTreeChunk>> matchResult = matchRes.getMatchResult();
            genResult.addAll(matchResult);
          }
        }
      }

    } catch (Exception e) {
      System.err.print("Problem extracting taxonomy node");
    }

    return genResult;
  }

  public void close() {
    sm.close();

  }

  public static void main(String[] args) {
    TaxonomyExtenderViaMebMining self = new TaxonomyExtenderViaMebMining();
    self.extendTaxonomy("src/test/resources/taxonomies/irs_dom.ari", "tax",
        "en");

  }

}
