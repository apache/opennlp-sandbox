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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;
import opennlp.tools.entitylinker.EntityLinkerProperties;

/**
 *
 * Searches Gazateers stored in a MMapDirectory lucene index
 */
public class GazateerSearcher {

  private double scoreCutoff = .75;
  private Directory geonamesIndex;//= new MMapDirectory(new File(indexloc));
  private IndexReader geonamesReader;// = DirectoryReader.open(geonamesIndex);
  private IndexSearcher geonamesSearcher;// = new IndexSearcher(geonamesReader);
  private Analyzer geonamesAnalyzer;
  //usgs US gazateer
  private Directory usgsIndex;//= new MMapDirectory(new File(indexloc));
  private IndexReader usgsReader;// = DirectoryReader.open(geonamesIndex);
  private IndexSearcher usgsSearcher;// = new IndexSearcher(geonamesReader);
  private Analyzer usgsAnalyzer;

  public GazateerSearcher() {
  }

  /**
   *
   * @param searchString the nameed entity to look up in the lucene index
   * @param rowsReturned how many rows to allow lucene to return
   * @param code         the country code
   * @param properties   properties file that states where the lucene indexes
   *                     are
   * @return
   */
  public ArrayList<GazateerEntry> geonamesFind(String searchString, int rowsReturned, String code, EntityLinkerProperties properties) {
    ArrayList<GazateerEntry> linkedData = new ArrayList<>();
    try {
      /**
       * build the search string
       */
      String luceneQueryString = !code.equals("")
              ? "FULL_NAME_ND_RO:" + searchString.toLowerCase().trim() + " AND CC1:" + code.toLowerCase() + "^1000"
              : "FULL_NAME_ND_RO:" + searchString.toLowerCase().trim();
      /**
       * check the cache and go no further if the records already exist
       */
      ArrayList<GazateerEntry> get = GazateerSearchCache.get(searchString);
      if (get != null) {
        return get;
      }
      if (geonamesIndex == null) {
        String indexloc = properties.getProperty("opennlp.geoentitylinker.gaz.geonames", "");
        String cutoff = properties.getProperty("opennlp.geoentitylinker.gaz.lucenescore.min", ".60");
        scoreCutoff = Double.valueOf(cutoff);
        geonamesIndex = new MMapDirectory(new File(indexloc));
        geonamesReader = DirectoryReader.open(geonamesIndex);
        geonamesSearcher = new IndexSearcher(geonamesReader);
        geonamesAnalyzer = new StandardAnalyzer(Version.LUCENE_45);

      }



      QueryParser parser = new QueryParser(Version.LUCENE_45, luceneQueryString, geonamesAnalyzer);
      Query q = parser.parse(luceneQueryString);


      TopDocs search = geonamesSearcher.search(q, rowsReturned);
      double maxScore = (double) search.getMaxScore();

      for (int i = 0; i < search.scoreDocs.length; ++i) {
        GazateerEntry entry = new GazateerEntry();
        int docId = search.scoreDocs[i].doc;
        double sc = search.scoreDocs[i].score;

        entry.getScoreMap().put("lucene", sc);

        entry.getScoreMap().put("rawlucene", sc);
        entry.setIndexID(docId + "");
        entry.setSource("geonames");

        Document d = geonamesSearcher.doc(docId);
        List<IndexableField> fields = d.getFields();
        for (int idx = 0; idx < fields.size(); idx++) {
          String value = d.get(fields.get(idx).name());
          value = value.toLowerCase();
          switch (idx) {
            case 1:
              entry.setItemID(value);
              break;
            case 3:
              entry.setLatitude(Double.valueOf(value));
              break;
            case 4:
              entry.setLongitude(Double.valueOf(value));
              break;
            case 10:
              entry.setItemType(value);
              break;
            case 12:
              entry.setItemParentID(value);
              break;
            case 23:
              entry.setItemName(value);
              break;
          }
          entry.getIndexData().put(fields.get(idx).name(), value);
        }
        //only keep it if the country code is a match
        if (entry.getItemParentID().toLowerCase().equals(code.toLowerCase())) {
          linkedData.add(entry);
        }
      }

      normalize(linkedData, 0d, maxScore);
      prune(linkedData);
    } catch (IOException | ParseException ex) {
      System.err.println(ex);
    }
    /**
     * add the records to the cache for this query
     */
    GazateerSearchCache.put(searchString, linkedData);
    return linkedData;
  }

  /**
   * Looks up the name in the USGS gazateer, after checking the cache
   *
   * @param searchString the nameed entity to look up in the lucene index
   * @param rowsReturned how many rows to allow lucene to return
   *
   * @param properties   properties file that states where the lucene indexes
   * @return
   */
  public ArrayList<GazateerEntry> usgsFind(String searchString, int rowsReturned, EntityLinkerProperties properties) {
    ArrayList<GazateerEntry> linkedData = new ArrayList<>();
    try {

      String luceneQueryString = "FEATURE_NAME:" + searchString.toLowerCase().trim() + " OR MAP_NAME: " + searchString.toLowerCase().trim();
      /**
       * hit the cache
       */
      ArrayList<GazateerEntry> get = GazateerSearchCache.get(searchString);
      if (get != null) {
        //if the name is already there, return the list of cavhed results
        return get;
      }
      if (usgsIndex == null) {
        String indexloc = properties.getProperty("opennlp.geoentitylinker.gaz.usgs", "");
        String cutoff = properties.getProperty("opennlp.geoentitylinker.gaz.lucenescore.min", ".75");
        scoreCutoff = Double.valueOf(cutoff);
        usgsIndex = new MMapDirectory(new File(indexloc));
        usgsReader = DirectoryReader.open(usgsIndex);
        usgsSearcher = new IndexSearcher(usgsReader);
        usgsAnalyzer = new StandardAnalyzer(Version.LUCENE_45);
      }


      QueryParser parser = new QueryParser(Version.LUCENE_45, luceneQueryString, usgsAnalyzer);
      Query q = parser.parse(luceneQueryString);


      TopDocs search = usgsSearcher.search(q, rowsReturned);
      double maxScore = (double) search.getMaxScore();


      for (int i = 0; i < search.scoreDocs.length; ++i) {
        GazateerEntry entry = new GazateerEntry();
        int docId = search.scoreDocs[i].doc;
        double sc = search.scoreDocs[i].score;
        //keep track of the min score for normalization

        entry.getScoreMap().put("lucene", sc);
        entry.getScoreMap().put("rawlucene", sc);
        entry.setIndexID(docId + "");
        entry.setSource("usgs");
        entry.setItemParentID("us");


        Document d = usgsSearcher.doc(docId);
        List<IndexableField> fields = d.getFields();
        for (int idx = 0; idx < fields.size(); idx++) {
          String value = d.get(fields.get(idx).name());
          value = value.toLowerCase();
          switch (idx) {
            case 0:
              entry.setItemID(value);
              break;
            case 1:
              entry.setItemName(value);
              break;
            case 2:
              entry.setItemType(value);
              break;
            case 9:
              entry.setLatitude(Double.valueOf(value));
              break;
            case 10:
              entry.setLongitude(Double.valueOf(value));
              break;
          }
          entry.getIndexData().put(fields.get(idx).name(), value);
        }
        linkedData.add(entry);


      }

      normalize(linkedData, 0d, maxScore);
      prune(linkedData);
    } catch (IOException | ParseException ex) {
      System.err.println(ex);
    }
    /**
     * add the records to the cache for this query
     */
    GazateerSearchCache.put(searchString, linkedData);
    return linkedData;
  }

  private void normalize(ArrayList<GazateerEntry> linkedData, Double minScore, Double maxScore) {
    for (GazateerEntry gazateerEntry : linkedData) {

      double luceneScore = gazateerEntry.getScoreMap().get("lucene");
      luceneScore = normalize(luceneScore, minScore, maxScore);
      luceneScore = luceneScore > 1.0 ? 1.0 : luceneScore;
      luceneScore = (luceneScore == Double.NaN) ? 0.001 : luceneScore;
      gazateerEntry.getScoreMap().put("lucene", luceneScore);
    }
  }

  private void prune(ArrayList<GazateerEntry> linkedData) {
    for (Iterator<GazateerEntry> itr = linkedData.iterator(); itr.hasNext();) {
      GazateerEntry ge = itr.next();
      if (ge.getScoreMap().get("lucene") < scoreCutoff) {
        itr.remove();
      }
    }
  }

  private Double normalize(Double valueToNormalize, Double minimum, Double maximum) {
    Double d = (double) ((1 - 0) * (valueToNormalize - minimum)) / (maximum - minimum) + 0;
    d = d == null ? 0d : d;
    return d;
  }
}
