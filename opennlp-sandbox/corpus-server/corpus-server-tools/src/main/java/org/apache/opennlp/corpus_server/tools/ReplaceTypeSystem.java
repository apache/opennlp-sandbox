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

public class ReplaceTypeSystem {

  public static void main(String[] args) throws Exception {
    
    if (args.length != 2) {
      System.out.println("ReplaceTypeSystem address typeSystemFile");
      System.exit(-1);
    }
    
    Client c = ClientBuilder.newClient();
    WebTarget r = c.target(args[0]);

    // Load and resolve type system before importing it
    try (InputStream typeSystemIn = new BufferedInputStream(new FileInputStream(args[1]))) {
      XMLParser xmlParser = UIMAFramework.getXMLParser();
      XMLInputSource xmlTypeSystemSource = new XMLInputSource(typeSystemIn, new File(args[1]));

      TypeSystemDescription typeSystemDesciptor = (TypeSystemDescription) xmlParser
              .parse(xmlTypeSystemSource);
      typeSystemDesciptor.resolveImports();

      ByteArrayOutputStream typeSystemBytes = new ByteArrayOutputStream();
      typeSystemDesciptor.toXML(typeSystemBytes);

      byte[] bytes = typeSystemBytes.toByteArray();
      try (Response response = r.path("_replaceTypeSystem")
              .request(MediaType.TEXT_XML)
              .header("Content-Type", MediaType.TEXT_XML)
              .header("Content-Length", bytes.length)
              .put(Entity.entity(bytes, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {

        System.out.println("Response: " + response.getStatus());
      }

    }
  }
}
