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

package org.apache.opennlp.tagging_server;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

import org.osgi.framework.ServiceReference;

@Path("/postagger")
public class POSTaggerResource {

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_tag")
  // @QueryParam("lang") String lang,
  // @QueryParam("model") String modelName
  public String[][] tag(String document[][]) {
    
    ServiceReference modelService = ModelUtil.getModelService(POSModel.class);
    
    try {
      String[][] tags = new String[document.length][];
      
      POSTagger tagger = new POSTaggerME(ModelUtil.getModel(modelService, POSModel.class));
      for (int i = 0; i < document.length; i++) {
        tags[i] = tagger.tag(document[i]);
      }
      
      return tags;
    }
    finally {
      ModelUtil.releaseModel(modelService);
    }
  }
}
