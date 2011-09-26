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

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/** 
 * Tools to back up a corpus from the corpus server into a zip package.
 * 
 * Sample server address: http://localhost:8080/corpus-server/rest
 */
public class CorpusBackup {

  private static void copyStream(InputStream in,
      OutputStream out) throws IOException {
    
    byte buffer[] = new byte[1024];
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

    Client c = Client.create();

    WebResource r = c.resource(args[0] + "/queues");

    String corpusId = args[1];
    String backupQueueId = args[1] + "BackupQueue";
    
    ClientResponse createQueueResponse = r.path("_createTaskQueue")
        .queryParam("corpusId", args[1])
        .queryParam("queueId", backupQueueId)
        .queryParam("q", "*:*")
        .accept(MediaType.TEXT_XML)
        // TODO: How to fix this? Shouldn't accept do it?
        .header("Content-Type", MediaType.TEXT_XML)
        .post(ClientResponse.class);
    
    // zip file name ...
    File backupFile = new File(args[2]);

    // create zip file
    OutputStream backupOut = null;
        
    try {
      backupOut = new FileOutputStream(backupFile);
      ZipOutputStream zipPackageOut = new ZipOutputStream(backupOut);
    
      WebResource corpusWebResource = c.resource(args[0] + "/corpora/" + corpusId);
      
      // fetch ts, does it work like this!?
      ClientResponse tsResponse = corpusWebResource
          .path("_typesystem")
          .accept(MediaType.TEXT_XML)
          // TODO: How to fix this? Shouldn't accept do it?
          .header("Content-Type", MediaType.TEXT_XML)
          .get(ClientResponse.class);
      
      zipPackageOut.putNextEntry(new ZipEntry("TypeSystem.xml"));
      InputStream tsIn = tsResponse.getEntityInputStream();
      
      copyStream(tsIn, zipPackageOut);
      tsIn.close();
      zipPackageOut.closeEntry();
      
      // consume task queue
      WebResource r2 = c.resource(args[0] + "/queues/" + backupQueueId);
      
      while (true) {
        // TODO: Make query configurable ...
        ClientResponse response2 = r2
                .path("_nextTask")
                .queryParam("q", args[1])
                .accept(MediaType.APPLICATION_JSON)
                .header("Content-Type", MediaType.TEXT_XML)
                .get(ClientResponse.class);
        
        if (response2.getStatus() == ClientResponse.Status.NO_CONTENT.getStatusCode()) {
          System.out.println("##### FINISHED #####");
          break;
        }
        
        // check if response was ok ...
        
        String casId = response2.getEntity(String.class);
        
        ClientResponse casResponse = corpusWebResource
            .path(casId)
            .accept(MediaType.TEXT_XML)
            .header("Content-Type", MediaType.TEXT_XML)
            .get(ClientResponse.class);
        
        zipPackageOut.putNextEntry(new ZipEntry(casId));
        
        InputStream casIn = casResponse.getEntityInputStream();
        
        try {
          copyStream(casIn, zipPackageOut);
        }
        finally {
          try {
            casIn.close();
          }
          catch (IOException e) {
          }
        }
        
        zipPackageOut.closeEntry();
        
        System.out.println(casId);
      }
      
      zipPackageOut.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
