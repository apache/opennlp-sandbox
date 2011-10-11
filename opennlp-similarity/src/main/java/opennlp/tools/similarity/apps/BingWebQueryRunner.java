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
import java.io.FileWriter;
import java.io.IOException;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.zvents.ce.common.util.StringDistanceMeasurer;
import com.zvents.recommend.event_event.utils.CSVWriter;

public class BingWebQueryRunner {
  private static final Logger LOG = LoggerFactory
      .getLogger(BingWebQueryRunner.class);

  private String constructBingWebUrl(String query, String domainWeb,
      String lang, int numbOfHits) throws Exception {
    String codedQuery = URLEncoder.encode(query, "UTF-8");

    String yahooRequest = "http://api.search.live.net/json.aspx?Appid="
        + BingQueryRunner.APP_ID + "&query=" + codedQuery // +
        // "&sources=web"+
        + "&Sources=Web"
        // Common request fields (optional)
        + "&Version=2.0" + "&Market=en-us&web.count=" + numbOfHits
        // + "&Options=EnableHighlighting"

        // News-specific request fields (optional)
        + "&News.Offset=0";

    return yahooRequest;
  }

  /*
     *  
     */

  public BingResponse populateBingHit(String response) throws Exception {
    BingResponse resp = new BingResponse();
    JSONObject rootObject = new JSONObject(response);
    // each response is object that under the key of "ysearchresponse"
    JSONObject responseObject = rootObject.getJSONObject("SearchResponse");
    JSONObject web = responseObject.getJSONObject("Web"); // "News"

    // the search result is in an array under the name of "results"
    JSONArray resultSet = null;
    try {
      resultSet = web.getJSONArray("Results");
      int count = (int) web.getLong("Total");
      resp.setTotalHits(new Integer(count));
    } catch (Exception e) {
      e.printStackTrace();
      LOG.error("\nNo search results", e);
      LOG.error(response);
      LOG.error("---------------------");

    }
    if (resultSet != null) {
      for (int i = 0; i < resultSet.length(); i++) {
        BingHit hit = new BingHit();
        JSONObject singleResult = resultSet.getJSONObject(i);
        hit.setAbstractText(singleResult.getString("Description"));
        hit.setDate(singleResult.getString("DateTime"));
        String title = StringUtils.replace(singleResult.getString("Title"),
            "", " ");
        hit.setTitle(title);
        hit.setUrl(singleResult.getString("Url"));

        resp.appendHits(hit);
      }
    }
    return resp;
  }

  public ArrayList<String> search(String query, String domainWeb, String lang,
      int numbOfHits) throws Exception {
    URL url = new URL(constructBingWebUrl(query, domainWeb, lang, numbOfHits));
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

    hits = removeDuplicates(hits, 0.9);

    writeHitsToCsv(query, hits);

    return hits;
  }

  protected void writeHitsToCsv(String query, List<HitBase> hits) {
    try {
      CSVWriter writer = new CSVWriter(new FileWriter("bingSearchResults.csv",
          true));
      for (HitBase hit : hits) {
        writer.writeNext(new String[] { query, hit.getTitle(), hit.getUrl() });
      }
      writer.close();
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
    }
  }

  public List<HitBase> runSearch(String query, int num) {
    BingResponse resp = null;
    try {
      List<String> resultList = search(query, "", "", num);
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

    hits = removeDuplicates(hits, 0.9);
    return hits;
  }

  public static List<HitBase> removeDuplicates(List<HitBase> hits,
      double imageDupeThresh) {
    StringDistanceMeasurer meas = new StringDistanceMeasurer();

    List<Integer> idsToRemove = new ArrayList<Integer>();
    List<HitBase> hitsDedup = new ArrayList<HitBase>();
    try {
      for (int i = 0; i < hits.size(); i++)
        for (int j = i + 1; j < hits.size(); j++) {
          String title1 = hits.get(i).getTitle();
          String title2 = hits.get(j).getTitle();
          if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
            continue;
          if (meas.measureStringDistance(title1, title2) > imageDupeThresh) {
            idsToRemove.add(j); // dupes found, later list member to
            // be deleted
          }
        }
      for (int i = 0; i < hits.size(); i++)
        if (!idsToRemove.contains(i))
          hitsDedup.add(hits.get(i));
      if (hitsDedup.size() < hits.size()) {
        LOG.debug("Removed duplicates from relevant search results, including "
            + hits.get(idsToRemove.get(0)).getTitle());
      }
    } catch (Exception e) {
      LOG.error("Problem removing duplicates from relevant images");
    }

    return hitsDedup;

  }

  public int getTotalPagesAtASite(String site) {
    BingResponse resp = null;
    try {
      List<String> resultList = search("site:" + site, "", "", 10);
      resp = populateBingHit(resultList.get(0));

    } catch (Exception e) {
      // e.printStackTrace();
      LOG.debug("No news search results for query = 'site:" + site);
      return 0;
    }

    return resp.totalHits;
  }

  public static void main(String[] args) {
    int res = new BingWebQueryRunner().getTotalPagesAtASite("www.zvents.com");
    new BingWebQueryRunner().runSearch("site:www.tripadvisor.com", 10);
  };
}
