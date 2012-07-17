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

package org.apache.opennlp.tagging_server.sentdetect;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

import org.apache.opennlp.tagging_server.ServiceUtil;
import org.osgi.framework.ServiceReference;

@Path("/sentdetect")
public class SentenceDetectorResource {

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_sentPosDetect")
  public List<Span> sentPosDetect(String document) {
    
    ServiceReference modelService = ServiceUtil.getServiceReference(SentenceModel.class);
      
    try {
      SentenceDetector sentDetector = new SentenceDetectorME(
              ServiceUtil.getService(modelService, SentenceModel.class));
      
      return Arrays.asList(sentDetector.sentPosDetect(document));
    }
    finally {
      ServiceUtil.releaseService(modelService);
    }
  }
}
