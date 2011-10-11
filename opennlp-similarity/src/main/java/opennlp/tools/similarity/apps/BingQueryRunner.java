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

package opennlp.tools.similarity.apps;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BingQueryRunner {
  protected static final String APP_ID = "XXX";

  private float snapshotSimilarityThreshold = 0.4f;

  private static final Logger LOG = LoggerFactory
      .getLogger(BingQueryRunner.class);

  public void setSnapshotSimilarityThreshold(float thr) {
    snapshotSimilarityThreshold = thr;
  }

  public float getSnapshotSimilarityThreshold() {
    return snapshotSimilarityThreshold;
  }

  public BingQueryRunner() {

  }

  private String constructBingUrl(String query, String domainWeb, String lang,
      int numbOfHits) throws Exception {
    String codedQuery = URLEncoder.encode(query, "UTF-8");
    // http://boss.yahooapis.com/ysearch/news/v1/{query}?appid=xyz[&param1=val1&param2=val2&etc
    String yahooRequest = "http://api.search.live.net/json.aspx?Appid="
        + APP_ID + "&query=" + codedQuery // +
        // "&sources=web"+
        + "&Sources=News"
        // Common request fields (optional)
        + "&Version=2.0" + "&Market=en-us"
        // + "&Options=EnableHighlighting"

        // News-specific request fields (optional)
        + "&News.Offset=0";

    return yahooRequest;
  }

  /*
     *  
     */
  public ArrayList<String> search(String query, String domainWeb, String lang,
      int numbOfHits) throws Exception {
    URL url = new URL(constructBingUrl(query, domainWeb, lang, numbOfHits));
    URLConnection connection = url.openConnection();

    String line;
    ArrayList<String> result = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        connection.getInputStream()));
    int count = 0;
    while ((line = reader.readLine()) != null) {
      result.add(line);
      count++;
    }
    return result;
  }

  public BingResponse populateBingHit(String response) throws Exception {
    BingResponse resp = new BingResponse();
    JSONObject rootObject = new JSONObject(response);
    // each response is object that under the key of "ysearchresponse"
    JSONObject responseObject = rootObject.getJSONObject("SearchResponse");
    JSONObject web = responseObject.getJSONObject("News");

    // the search result is in an array under the name of "results"
    JSONArray resultSet = null;
    try {
      resultSet = web.getJSONArray("Results");
    } catch (Exception e) {
      System.err.print("\n!!!!!!!");
      LOG.error("\nNo search results");

    }
    if (resultSet != null) {
      for (int i = 0; i < resultSet.length(); i++) {
        HitBase hit = new HitBase();
        JSONObject singleResult = resultSet.getJSONObject(i);
        hit.setAbstractText(singleResult.getString("Snippet"));
        hit.setDate(singleResult.getString("Date"));
        String title = StringUtils.replace(singleResult.getString("Title"),
            "", " ");
        hit.setTitle(title);
        hit.setUrl(singleResult.getString("Url"));
        hit.setSource(singleResult.getString("Source"));

        resp.appendHits(hit);
      }
    }
    return resp;
  }

  public List<HitBase> runSearch(String query) {
    BingResponse resp = null;
    try {
      List<String> resultList = search(query, "", "", 8);
      resp = populateBingHit(resultList.get(0));

    } catch (Exception e) {
      // e.printStackTrace();
      LOG.debug("No news search results for query " + query);
      return null;
    }
    // cast to super class
    List<HitBase> hits = new ArrayList<HitBase>();
    for (HitBase h : resp.getHits())
      hits.add((HitBase) h);

    hits = HitBase.removeDuplicates(hits);
    return hits;
  }

  // TODO comment back when dependencies resolved (CopyrightViolations)
  /*
   * public List<CopyrightViolations> runCopyRightViolExtenralSearch(String
   * query, String report) {
   * 
   * List<CopyrightViolations> genResult = new ArrayList<CopyrightViolations>();
   * BingResponse newResp = null; StringDistanceMeasurer meas = new
   * StringDistanceMeasurer(); try { List<String> resultList = search(query, "",
   * "", 5);
   * 
   * BingResponse resp = populateBingHit(resultList.get(0));
   * //printSearchResult(resultList.get(0));
   * 
   * for(int i=0; i<resp.getHits().size(); i++){ BingHit h1 =
   * resp.getHits().get(i); String snippet = h1.getAbstractText(); Double sim =
   * meas.measureStringDistance(report, snippet); if
   * (sim>snapshotSimilarityThreshold){ //genResult.add(snapshot);
   * CopyrightViolations cvr = new CopyrightViolations();
   * cvr.setSnippet(snippet); cvr.setTitle(h1.getTitle());
   * cvr.setUrl(h1.getDisplayUrl()); genResult.add(cvr); log.debug(new
   * String("Copyright violation detected in snapshot"
   * ).toUpperCase()+" : sim = "+ new Double(sim).toString().substring(0, 3)+
   * " \n "+snippet);
   * 
   * } else { log.debug("Different news: sim = "+ new
   * Double(sim).toString().substring(0, 3)+ " \n "+snippet);
   * 
   * }
   * 
   * }
   * 
   * } catch (Exception e) { e.printStackTrace(); }
   * 
   * 
   * return genResult; }
   */

  public static void main(String[] args) {
    BingQueryRunner self = new BingQueryRunner();
    try {
      List<HitBase> resp = self
          .runSearch("Rates rise at weekly Treasury auction");
      // "British Actress Lynn Redgrave dies at 67");
      System.out.print(resp.get(0));
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    /*
     * String[] submittedNews = new String[]{
     * "Asian airports had already increased security following the Christmas Day attack, but South Korea and Pakistan are thinking about additional measures."
     * ,
     * "Europe remains the key origin for air travelers heading to the United States, with about 1000 trans-Atlantic flights a day in 2009."
     * ,
     * "DeLaughter became an instant hero of the civil rights movement. Alec Baldwin portrayed him in the 1996 movie, Ghosts of Mississippi and his closing statement was once dubbed one of the greatest closing arguments in modern law."
     * ,
     * "After US president made the statement, Cuba protested extra screening for Cubans coming to the US"
     * ,
     * 
     * }; for(String query: submittedNews){ System.out.println(query);
     * List<CopyrightViolations> genResult =
     * self.runCopyRightViolExtenralSearch(query, query); if
     * (genResult.size()>0){
     * 
     * System.out.println(genResult.toString()); System.out.println("\n\n");
     * 
     * } }
     */

  }

}
