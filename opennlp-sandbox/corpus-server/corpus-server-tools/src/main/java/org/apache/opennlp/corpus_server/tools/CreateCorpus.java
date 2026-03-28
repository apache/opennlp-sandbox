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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLParser;

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

    Client c = ClientBuilder.newClient();
    WebTarget r = c.target(args[0]);

    byte[][] resources = new byte[2][];

    // Load and resolve type system before importing it
    try (InputStream typeSystemIn = new BufferedInputStream(new FileInputStream(args[2]))) {

      XMLParser xmlParser = UIMAFramework.getXMLParser();
      XMLInputSource xmlTypeSystemSource = new XMLInputSource(typeSystemIn,new File(args[2]));

      TypeSystemDescription typeSystemDescriptor =
              (TypeSystemDescription) xmlParser.parse(xmlTypeSystemSource);
      typeSystemDescriptor.resolveImports();

      ByteArrayOutputStream typeSystemBytes = new ByteArrayOutputStream();
      typeSystemDescriptor.toXML(typeSystemBytes);

      resources[0] = typeSystemBytes.toByteArray();

      byte[] indexMappingBytes = FileUtil.fileToBytes(new File(args[3]));
      resources[1] = indexMappingBytes;

      try (Response response = r.path("_createCorpus")
              .queryParam("corpusName", corpusName)
              .request(MediaType.APPLICATION_JSON)
              .header("Content-Type", MediaType.APPLICATION_JSON_TYPE)
              .post(Entity.entity(resources, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {

        System.out.println("Result: " + response.getStatus());
      }
    }
  }
}
