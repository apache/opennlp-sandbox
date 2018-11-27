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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.ws.rs.core.MediaType;

import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLParser;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

/**
 * Command Line Tool to create a new corpus in the corpus server.
 */
public class CreateCorpus {
  public static void main(String[] args) throws Exception {

    if (args.length != 4) {
      System.out
          .println("CreateCorpus address corpusName typeSystemFile mappingFile");
      System.exit(-1);
    }

    String corpusName = args[1];

    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING,
        Boolean.TRUE);

    Client c = Client.create(clientConfig);

    WebResource r = c.resource(args[0]);

    byte[][] resources = new byte[2][];

    // Load and resolve type system before importing it
    InputStream typeSystemIn = new FileInputStream(new File(args[2]));

    XMLInputSource xmlTypeSystemSource = new XMLInputSource(typeSystemIn,
        new File(args[2]));

    XMLParser xmlParser = UIMAFramework.getXMLParser();

    TypeSystemDescription typeSystemDesciptor = (TypeSystemDescription) xmlParser
        .parse(xmlTypeSystemSource);

    typeSystemDesciptor.resolveImports();

    ByteArrayOutputStream typeSystemBytes = new ByteArrayOutputStream();
    typeSystemDesciptor.toXML(typeSystemBytes);

    resources[0] = typeSystemBytes.toByteArray();

    byte indexMappingBytes[] = FileUtil.fileToBytes(new File(args[3]));
    resources[1] = indexMappingBytes;

    ClientResponse response = r.path("_createCorpus")
        .queryParam("corpusName", corpusName)
        .accept(MediaType.APPLICATION_JSON)
        // TODO: How to fix this? Shouldn't accept do it?
        .header("Content-Type", MediaType.APPLICATION_JSON_TYPE)
        .post(ClientResponse.class, resources);

    System.out.println("Result: " + response.getStatus());
  }
}
