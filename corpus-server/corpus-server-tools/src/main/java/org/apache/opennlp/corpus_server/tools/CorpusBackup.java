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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientResponse;

/** 
 * Tools to back up a corpus from the corpus server into a zip package.
 * <p>
 * Sample server address:
 * <a href="http://localhost:8080/corpus-server/rest">http://localhost:8080/corpus-server/rest</a>.
 */
public class CorpusBackup {

  private static void copyStream(InputStream in, OutputStream out) throws IOException {
    
    byte[] buffer = new byte[1024];
    int len;
    while ((len = in.read(buffer)) > 0) {
      out.write(buffer, 0, len);
    }
  }
  
  // TODO: Make query configurable, maybe user just wants to pull out
  //       some CAses into the zip package ...
  
  public static void main(String[] args) {

    if (args.length != 3) {
      System.out.println("CorpusBackup address corpusName backupFile");
      System.exit(-1);
    }

    Client c = ClientBuilder.newClient();
    WebTarget r = c.target(args[0] + "/queues");

    String corpusId = args[1];
    String backupQueueId = args[1] + "BackupQueue";
    
    try (Response createQueueResponse = r.path("_createTaskQueue")
        .queryParam("corpusId", args[1])
        .queryParam("queueId", backupQueueId)
        .queryParam("q", "*:*")
        .request(MediaType.TEXT_XML)
        .header("Content-Type", MediaType.TEXT_XML)
        // as this is an query-param driven POST request,
        // we just set an empty string to the body.
        .post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))) {
      
      System.out.println("Result (_createTaskQueue): " + createQueueResponse.getStatus());
    }
    
    // zip file name ...
    File backupFile = new File(args[2]);

    // create zip file

    try (OutputStream backupOut = new FileOutputStream(backupFile);
         ZipOutputStream zipPackageOut = new ZipOutputStream(backupOut)){

      WebTarget corpusWebResource = c.target(args[0] + "/corpora/" + corpusId);

      // fetch ts
      ClientResponse tsResponse = corpusWebResource.path("_typesystem")
          .request(MediaType.TEXT_XML)
          .header("Content-Type", MediaType.TEXT_XML)
          .get(ClientResponse.class);

      zipPackageOut.putNextEntry(new ZipEntry("TypeSystem.xml"));
      try (InputStream tsIn = tsResponse.getEntityStream()) {
        copyStream(tsIn, zipPackageOut);
      }
      zipPackageOut.closeEntry();

      // consume task queue
      WebTarget r2 = c.target(args[0] + "/queues/" + backupQueueId);

      while (true) {
        // TODO: Make query configurable ...
        ClientResponse response2 = r2.path("_nextTask")
                .queryParam("q", args[1])
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", MediaType.TEXT_XML)
                .get(ClientResponse.class);

        if (response2.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
          System.out.println("##### FINISHED #####");
          break;
        }

        // check if response was ok ...
        String casId = response2.readEntity(String.class);

        ClientResponse casResponse = corpusWebResource.path(casId)
            .request(MediaType.TEXT_XML)
            .header("Content-Type", MediaType.TEXT_XML)
            .get(ClientResponse.class);

        zipPackageOut.putNextEntry(new ZipEntry(casId));

        try (InputStream casIn = casResponse.getEntityStream()) {
          copyStream(casIn, zipPackageOut);
        }

        zipPackageOut.closeEntry();

        System.out.println(casId);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
