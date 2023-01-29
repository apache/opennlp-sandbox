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

package org.apache.opennlp.corpus_server.tools;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class CreateTaskQueue {
  
  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      System.out.println("CreateCorpus address corpusId queueId query");
      System.exit(-1);
    }

    String corpusName = args[1];

    Client c = Client.create();

    WebResource r = c.resource(args[0]);

    ClientResponse response = r.path("_createTaskQueue")
        .queryParam("corpusId", corpusName)
        .queryParam("queueId", args[2])
        .queryParam("q", args[3])
        .accept(MediaType.TEXT_XML)
        // TODO: How to fix this? Shouldn't accept do it?
        .header("Content-Type", MediaType.TEXT_XML)
        .post(ClientResponse.class);

    System.out.println("Result: " + response.getStatus());
  }
}
