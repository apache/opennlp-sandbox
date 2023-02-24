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

package opennlp.tools.similarity.apps.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class PageFetcher {
  private static final Logger LOG = Logger.getLogger("opennlp.tools.similarity.apps.utils.PageFetcher");
  private final Tika tika = new Tika();

  private static int DEFAULT_TIMEOUT = 1500;
  private void setTimeout(int to){
	  DEFAULT_TIMEOUT = to;
  }

  public String fetchPage(final String url) {
    return fetchPage(url, DEFAULT_TIMEOUT);
  }
  
  public String fetchPageAutoDetectParser(final String url ){
	  String fetchURL = addHttp(url);
	  String pageContent = null;
    URLConnection connection;
    try {
      connection = new URL(fetchURL).openConnection();
      connection.setReadTimeout(DEFAULT_TIMEOUT);
      // parse method parameters
      Parser parser = new AutoDetectParser();
      BodyContentHandler handler = new BodyContentHandler();
      Metadata metadata = new Metadata();
      ParseContext context = new ParseContext();

      // parsing the file
      parser.parse(connection.getInputStream(), handler, metadata, context);

      pageContent = handler.toString();
    } catch (Exception e) {
      LOG.severe(e.getMessage() + "\n" + e);
    }
    return  pageContent;
  }
  

  public String fetchPage(final String url, final int timeout) {
    String fetchURL = addHttp(url);
    String pageContent = null;
    URLConnection connection;
    try {
      connection = new URL(fetchURL).openConnection();
      connection.setReadTimeout(DEFAULT_TIMEOUT);
      pageContent = tika.parseToString(connection.getInputStream())
          .replace('\n', ' ').replace('\t', ' ');
    } catch (IOException | TikaException e) {
      LOG.severe(e.getMessage() + "\n" + e);
    }
    return pageContent;
  }

  private String addHttp(final String url) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return "http://" + url;
    }
    return url;
  }
  
  public String fetchOrigHTML(String url, int timeout) {
	  setTimeout(timeout);
	  return fetchOrigHTML(url);
  }

  public String fetchOrigHTML(String url) {
    LOG.info("fetch url " + url);
    StringBuilder buf = new StringBuilder();
    try {
      URLConnection connection = new URL(url).openConnection();
      connection.setReadTimeout(DEFAULT_TIMEOUT);
      connection
          .setRequestProperty(
              "User-Agent",
              "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.0.3) Gecko/2008092417 Firefox/3.0.3");
      String line;
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));
      } catch (Exception e) {
        // we don't always need to log trial web pages if access fails
        LOG.severe(e.toString());
      }

      while ((line = reader.readLine()) != null) {
        buf.append(line);
      }

    }
    // normal case when a hypothetical page does not exist
    catch (Exception e) {

      // LOG.error(e.getMessage(), e);
      // System.err.println("error fetching url " + url);
    }
/*    try {
      Thread.sleep(50); // do nothing 4 sec
    } catch (InterruptedException e) {
      e.printStackTrace();
    } */
    return buf.toString();
  }
  
  public static void main(String[] args){
	  PageFetcher fetcher = new PageFetcher();
	  String content = fetcher.fetchPageAutoDetectParser("http://www.elastica.net/");
	  System.out.println(content);
	  content = fetcher.
			  fetchPageAutoDetectParser("http://www.cnn.com");
	  System.out.println(content);
	  content = new PageFetcher().fetchPage("https://github.com");
	  System.out.println(content);
	  content = new PageFetcher().fetchOrigHTML("http://www.cnn.com");
	  System.out.println(content);
	  
  }

}
