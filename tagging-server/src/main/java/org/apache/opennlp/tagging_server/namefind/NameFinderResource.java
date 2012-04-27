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
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.tagging_server.ModelUtil;
import org.osgi.framework.ServiceReference;


@Path("/namefinder")
public class NameFinderResource {

  public static class NameFinderDocument {
    private String document[][];
    private List<Span[]> names;
    
    NameFinderDocument(String document[][], List<Span[]> names) {
      this.document = document;
      this.names = names;
    }
    
    public List<Span[]> getNames() {
      return names;
    }
    
    public String[][] getDocument() {
      return document;
    }
  }
  
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_find")
  public List<Span> find(String[][] document) {
    
    ServiceReference modelService = ModelUtil.getService(TokenNameFinderModel.class);
    
    try {
      NameFinderME nameFinder = new NameFinderME(
          ModelUtil.getModel(modelService, TokenNameFinderModel.class));
      
      List<Span> names = new ArrayList<Span>();
      
      for (String sentence[] : document) {
        names.addAll(Arrays.asList(nameFinder.find(sentence)));
      }
      
      return names;
    }
    finally {
      ModelUtil.releaseService(modelService);
    }
  }
  
  // Just a hack to get arround cross domain issues in my test environment!
  // Need to investigate how this should be done!
  @OPTIONS
  @Path("_findRawText")
  public Response findRawTextOptions() {
    System.out.println("Called options ...");
    return Response.ok()
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Headers", "Content-Type")
        .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        .build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_findRawText")
  public NameFinderDocument findRawText(String document) { // input could be a single string ... return contains everything!
    
    
    System.out.println("Request: " + document);
    String[][] tokenizedSentences = new String[][]{SimpleTokenizer.INSTANCE.tokenize(document)};
    
    // TODO: Fix this. User should be able to define this in blueprint!
    
    ServiceReference modelService = ModelUtil.getService(TokenNameFinderModel.class);
      
    try {
      NameFinderME nameFinder = new NameFinderME(
              ModelUtil.getModel(modelService, TokenNameFinderModel.class));

      List<Span[]> names = new ArrayList<Span[]>();
      
      for (String sentence[] : tokenizedSentences) {
        names.add(nameFinder.find(sentence));
      }
      
      return new NameFinderDocument(tokenizedSentences, names);
    }
    finally {
      ModelUtil.releaseService(modelService);
    }
  }
}
