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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class BingSearchResultsScraper {

  protected static String fetchPageBing(String url) {
    System.out.println("fetch url " + url);
    String pageContent = null;
    StringBuffer buf = new StringBuffer();
    try {
      URLConnection connection = new URL(url).openConnection();
      connection.setReadTimeout(50000);
      connection
          .setRequestProperty(
              "User-Agent",
              "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-GB; rv:1.9.0.3) Gecko/2008092417 Firefox/3.0.3");
      String line;
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));
      } catch (Exception e) {
        e.printStackTrace();
      }

      while ((line = reader.readLine()) != null) {
        buf.append(line);
      }

    } catch (Exception e) {
      // e.printStackTrace();
      System.err.println("error fetching url " + url);
    }

    return buf.toString();
  }

  private static List<String> extractURLesFromPage(String content, String domain) {
    List<String> results = new ArrayList<String>();
    if (content == null)
      return results;
    content = StringUtils.substringBetween(content, ">Advanced</a></div>",
        "<input type=\"text\" value=");
    if (content == null)
      return results;
    String[] urls = content.split("<cite>");
    if (urls == null)
      return results;
    for (String u : urls) {
      int endPos = u.indexOf("</cite>");

      if (endPos > 0) {
        u = u.substring(0, endPos).replace("</strong>", "")
            .replace("<strong>", "");
        if (!u.equals(domain))
          results.add(u);
      }
    }

    return results;
  }

  private static String formRequestURL(String seedURL) {
    String requestUrl = "http://www.bing.com/search?q=site:" + seedURL;

    return requestUrl;
  }

  public List<String> getURLsForWebDomain(String domain) {
    return extractURLesFromPage(fetchPageBing(formRequestURL(domain)), domain);
  }

  public Set<String> getURLsForWebDomainIterations(String domain) {
    List<String> results = new ArrayList<String>();
    List<String> res = extractURLesFromPage(
        fetchPageBing(formRequestURL(domain)), domain);
    for (String r : res)
      results.addAll(extractURLesFromPage(fetchPageBing(formRequestURL(r)), r));

    return new HashSet<String>(results);
  }

  public static void main(String[] args) {
    System.out.println(new BingSearchResultsScraper()
        .getURLsForWebDomainIterations("www.sfgate.com/entertainment/"));
  }

}
