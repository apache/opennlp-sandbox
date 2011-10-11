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

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the superclass of all classes which are using yahoo-websearch
 * API with JSON.
 * 
 */
public class YahooQueryRunner {
  protected static final String APP_ID = "XXX";

  private static final Logger LOG = LoggerFactory
      .getLogger(YahooQueryRunner.class);

  /**
   * To run a query on Yahoo, one needs the
   * 
   * @param query
   *          it can be a row text with some pre-processing
   * @param domainWeb
   *          some sub-domain if necessary (default "")
   * @param lang
   *          language settings
   * @param numbOfHits
   * @return
   * @throws Exception
   */
  protected String constructBossUrl(String query, String domainWeb,
      String lang, int numbOfHits) throws Exception {
    String _lang = "en";

    String codedQuery = URLEncoder.encode(query, "UTF-8");
    String yahooRequest = "http://boss.yahooapis.com/ysearch/web" + "/v1/"
        + codedQuery + "?appid=" + APP_ID + "&count=" + numbOfHits
        + "&format=json&sites=" + domainWeb + "&lang=" + _lang;
    return yahooRequest;
  }

  /**
   * 
   * @param query
   * @param domainWeb
   * @param numbOfHits
   *          For more details
   *          http://developer.yahoo.com/search/image/V1/imageSearch.html
   * @return
   * @throws Exception
   */
  protected String constructBossImageSearchUrl(String query, String domainWeb,
      int numbOfHits) throws Exception {
    String codedQuery = URLEncoder.encode(query, "UTF-8");
    String yahooRequest = "http://boss.yahooapis.com/ysearch/images/v1/"
        + codedQuery + "?appid=" + APP_ID + "&count=" + numbOfHits
        + "&format=json&sites=" + domainWeb;
    return yahooRequest;
  }

  public ArrayList<String> search(String query, String domainWeb, String lang,
      int numbOfHits) throws Exception {
    URL url = new URL(constructBossUrl(query, domainWeb, lang, numbOfHits));
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

  public ArrayList<String> searchImage(String query, String domainWeb,
      int numbOfHits) throws Exception {
    URL url = new URL(constructBossImageSearchUrl(query, domainWeb, numbOfHits));
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

  public YahooResponse populateYahooHit(String response) throws Exception {
    YahooResponse resp = new YahooResponse();
    JSONObject rootObject = new JSONObject(response);
    // each response is object that under the key of "ysearchresponse"
    JSONObject responseObject = rootObject.getJSONObject("ysearchresponse");
    try {
      resp.setResponseCode(responseObject.getInt("responsecode"));
      resp.setNextPageUrl(responseObject.getString("nextpage"));
      resp.setTotalHits(responseObject.getInt("totalhits"));
      resp.setDeepHits(responseObject.getInt("deephits"));
      resp.setStartIndex(responseObject.getInt("start"));
      resp.setPageSize(responseObject.getInt("count"));
    } catch (Exception e) {
      LOG.error("Reduced number of original results");
    }

    // the search result is in an array under the name of "resultset_web"
    JSONArray resultSet = null;
    try {
      resultSet = responseObject.getJSONArray("resultset_web");
    } catch (Exception e) {
      System.err.print("\n!!!!!");
      LOG.error("\nNo search results", e);
      resultSet = null;
    }
    if (resultSet != null) {
      for (int i = 0; i < resultSet.length(); i++) {
        HitBase hit = new HitBase();
        JSONObject singleResult = resultSet.getJSONObject(i);
        hit.setAbstractText(singleResult.getString("abstract"));
        hit.setClickUrl(singleResult.getString("clickurl"));
        hit.setDisplayUrl(singleResult.getString("dispurl"));
        hit.setUrl(singleResult.getString("url"));
        hit.setDate(singleResult.getString("date"));
        hit.setTitle(singleResult.getString("title"));

        resp.appendHits(hit);
      }
    } else {
      return null;
    }
    return resp;
  }

  protected void printSearchResult(String response) throws Exception {
    JSONObject rootObject = new JSONObject(response);
    // each response is object that under the key of "ysearchresponse"
    JSONObject responseObject = rootObject.getJSONObject("ysearchresponse");
    // printResponseAttributes(responseObject);

    // the search result is in an array under the name of "resultset_web"
    JSONArray resultSet = responseObject.getJSONArray("resultset_web");
    System.out.println("Search Result:");
    System.out.println("---------------------------");
    for (int i = 0; i < resultSet.length(); i++) {
      printSingleSearchResult(resultSet.getJSONObject(i));
    }
  }

  protected void printResponseAttributes(JSONObject responseObject)
      throws Exception {
    // the response object has a few top level attributes
    int responseCode = responseObject.getInt("responsecode");
    String nextPageUrl = responseObject.getString("nextpage");
    int totalHits = responseObject.getInt("totalhits");
    int deepHits = responseObject.getInt("deephits");
    int startIndex = responseObject.getInt("start");
    int pageSize = responseObject.getInt("count");

    System.out.println("responseCode = " + responseCode + ", totalHits = "
        + totalHits + ", deepHits = " + deepHits + ", startIndex = "
        + startIndex + ", pageSize = " + pageSize);
    System.out.println("nextPageUrl = " + nextPageUrl);
  }

  protected void printSingleSearchResult(JSONObject singleResult)
      throws Exception {
    // each single search result has a few attributes
    String abstractText = singleResult.getString("abstract");
    String clickUrl = singleResult.getString("clickurl");
    String displayUrl = singleResult.getString("dispurl");
    String url = singleResult.getString("url");
    String date = singleResult.getString("date");

    // System.out.println("URL = " + url + ", date = " + date);
    System.out.println("Abstract = " + abstractText);
    // System.out.println("Display URL = " + displayUrl);
    // System.out.println("Click URL = " + clickUrl);
    System.out.println("---------------------------");
  }

  public List<HitBase> runSearch(String query) {
    YahooResponse resp = null;
    try {

      List<String> resultList = search(query, "", "en", 30);
      LOG.info(query);
      if (resultList.size() != 0) {
        resp = populateYahooHit(resultList.get(0));
      } else {
        LOG.info("Fikamika " + query);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (resp != null) {
      List<HitBase> hits = new ArrayList<HitBase>();
      for (HitBase h : resp.getHits())
        hits.add((HitBase) h);

      hits = HitBase.removeDuplicates(hits);
      return hits;
    } else {
      return null;
    }

  }

  public List<HitBase> runSearchInDomain(String domain) {
    YahooResponse resp = null;
    try {

      List<String> resultList = search("the", domain, "en", 30);

      if (resultList.size() != 0) {
        resp = populateYahooHit(resultList.get(0));
      } else {
        LOG.info("No search results in domain " + domain);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (resp != null) {
      List<HitBase> hits = new ArrayList<HitBase>();
      for (HitBase h : resp.getHits())
        hits.add((HitBase) h);

      hits = HitBase.removeDuplicates(hits);
      return hits;
    } else {
      return null;
    }

  }
}
