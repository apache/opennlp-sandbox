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

package org.apache.opennlp.tagging_server.namefind;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.tagging_server.ServiceUtil;
import org.osgi.framework.ServiceReference;


@Path("/namefinder")
public class NameFinderResource {

  public static class NameFinderDocument {
    private List<Span[]> document;
    private List<Span[]> names;
    
    NameFinderDocument(List<Span[]> document, List<Span[]> names) {
      this.document = document;
      this.names = names;
    }
    
    public List<Span[]> getNames() {
      return names;
    }
    
    public List<Span[]> getDocument() {
      return document;
    }
  }
  
  private List<Span[]> find(TokenNameFinder nameFinders[], String[][] document) {

    List<Span[]> names = new ArrayList<Span[]>();

    for (String sentence[] : document) {
      for (TokenNameFinder nameFinder : nameFinders) {
        names.add(nameFinder.find(sentence));
      }
    }

    return names;
  }
  
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_find")
  public List<Span[]> find(String[][] document) {
    
    ServiceReference modelService = ServiceUtil.getServiceReference(TokenNameFinderModel.class);
    
    try {
      NameFinderME nameFinder = new NameFinderME(
          ServiceUtil.getService(modelService, TokenNameFinderModel.class));
      
      List<Span[]> names = new ArrayList<Span[]>();
      
      for (String sentence[] : document) {
        names.add(nameFinder.find(sentence));
      }
      
      return names;
    }
    finally {
      ServiceUtil.releaseService(modelService);
    }
  }

  // TODO:
  // User should pass a key for the models (e.g. default_eng)
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("   ")
  public NameFinderDocument findRawText(String document) {

    ServiceReference preprocessFactoryService = ServiceUtil.getServiceReference(RawTextNameFinderFactory.class);
    
    try {
      // TODO: Pass a key here!
      RawTextNameFinderFactory factory =
              ServiceUtil.getService(preprocessFactoryService, RawTextNameFinderFactory.class);
      
      SentenceDetector sentDetect = factory.createSentenceDetector();
      Tokenizer tokenizer = factory.createTokenizer();
      
      Span sentenceSpans[] = sentDetect.sentPosDetect(document);
      
      List<Span[]> tokenizedSentencesSpan = new ArrayList<Span[]>();
      String[][] tokenizedSentences = new String[sentenceSpans.length][];
      
      for (int i = 0; i < sentenceSpans.length; i++) {
        // offset of sentence gets lost here!
        Span tokenSpans[] = tokenizer.tokenizePos(sentenceSpans[i].getCoveredText(document).toString());
        // all spans need to be sentence offset adjusted!
        tokenSpans = offsetSpans(tokenSpans, sentenceSpans[i].getStart());
        
        tokenizedSentencesSpan.add(tokenSpans);
        
        String tokens[] = new String[tokenSpans.length];
        for (int ti = 0; ti < tokenSpans.length; ti++) {
          tokens[ti] = tokenSpans[ti].getCoveredText(document).toString();
        }
        
        tokenizedSentences[i] = tokens;
      }
      
      TokenNameFinder nameFinders[] = factory.createNameFinders();
      
      return new NameFinderDocument(tokenizedSentencesSpan, find(nameFinders, tokenizedSentences));
    }
    finally {
      ServiceUtil.releaseService(preprocessFactoryService);
    }
  }

  private Span[] offsetSpans(
      Span[] tokenSpans, int offset) {
    
    Span spans[] = new Span[tokenSpans.length];
    
    for (int i = 0; i < tokenSpans.length; i++) {
      spans[i] = new Span(tokenSpans[i].getStart() + offset,
          tokenSpans[i].getEnd() + offset);
    }
    
    return spans;
  }
}
