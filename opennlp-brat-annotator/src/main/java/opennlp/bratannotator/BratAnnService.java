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

package opennlp.bratannotator;

import java.io.File;
import java.net.URI;
import java.net.URL;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class BratAnnService {
  
  public static SentenceDetector sentenceDetector;
  public static Tokenizer tokenizer;
  public static TokenNameFinder nameFinders[];
  
  public static void main(String[] args) throws Exception {
    
    if (args.length < 3) {
      System.out.println("sentenceDetectorURI tokenizerURI namefinderURI_1 ... nameFinderURI_n");
      return;
    }

    URI sentenceDetectorUri = URI.create(args[0]);
    if ("sentenceDetector".equals(sentenceDetectorUri.getScheme())) {
      
      if ("newline".equals(sentenceDetectorUri.getSchemeSpecificPart())) {
        sentenceDetector = new NewlineSentenceDetector();
      }
      else {
        System.out.println("unkown sentence detector");
        return;
      }
    }
    else {
      sentenceDetector = new SentenceDetectorME(new SentenceModel(new File(args[0])));
    }
    
    URI tokenizerUri = URI.create(args[1]);
    if ("tokenizer".equals(tokenizerUri.getScheme())) {
      if ("whitespace".equals(tokenizerUri.getSchemeSpecificPart())) {
        tokenizer = WhitespaceTokenizer.INSTANCE;
      }
      else if ("simple".equals(tokenizerUri.getSchemeSpecificPart())) {
        tokenizer = SimpleTokenizer.INSTANCE;
      } 
      else {
        System.out.println("unkown sentence detector");
        return;
      }

    }
    else {
      tokenizer = new TokenizerME(new TokenizerModel(new File(args[1])));
    }
    
    nameFinders = new TokenNameFinder[] {new NameFinderME(new TokenNameFinderModel(new URL(args[2])))};
    
    ServletContextHandler context = new ServletContextHandler(
        ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    Server jettyServer = new Server(8080);
    jettyServer.setHandler(context);

    ServletHolder jerseyServlet = context
        .addServlet(com.sun.jersey.spi.container.servlet.ServletContainer.class, "/*");
    jerseyServlet.setInitParameter("com.sun.jersey.config.property.packages", "opennlp.bratannotator");
    jerseyServlet.setInitParameter("com.sun.jersey.api.json.POJOMappingFeature", "true");
    jerseyServlet.setInitOrder(0);

    jerseyServlet.setInitParameter("jersey.config.server.provider.classnames",
        BratNameFinderResource.class.getCanonicalName());

    try {
      jettyServer.start();
      jettyServer.join();
    } finally {
      jettyServer.destroy();
    }
  }
}
