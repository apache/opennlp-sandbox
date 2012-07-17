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

package org.apache.opennlp.tagging_server.tokenize;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import org.apache.opennlp.tagging_server.ServiceUtil;
import org.osgi.framework.ServiceReference;

@Path("/tokenize")
public class TokenizerResource {

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_tokenize")
  public List<String[]> tokenize(String[] document) {
    ServiceReference modelService = ServiceUtil.getServiceReference(TokenizerModel.class);
    
    try {
      TokenizerME tokenizer = new TokenizerME(
          ServiceUtil.getService(modelService, TokenizerModel.class));
      
      List<String[]> tokenizedSentences = new ArrayList<String[]>();
      
      for (String sentence : document) {
        tokenizedSentences.add(tokenizer.tokenize(sentence));
      }
      
      return tokenizedSentences;
    }
    finally {
      ServiceUtil.releaseService(modelService);
    }
  }
  
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_tokenizePos")
  public List<Span[]> tokenizePos(String[] document) {
    ServiceReference modelService = ServiceUtil.getServiceReference(TokenizerModel.class);
    
    try {
      TokenizerME tokenizer = new TokenizerME(
              ServiceUtil.getService(modelService, TokenizerModel.class));

      List<Span[]> tokenizedSentences = new ArrayList<Span[]>();
      
      for (String sentence : document) {
        tokenizedSentences.add(tokenizer.tokenizePos(sentence));
      }
      
      return tokenizedSentences;
    }
    finally {
      ServiceUtil.releaseService(modelService);
    }
  }
}
