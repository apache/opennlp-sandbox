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
import java.io.FilenameFilter;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class CASImporter {

  public static void main(String[] args) throws Exception {

    if (args.length != 2) {
      System.out.println("WikinewsImporter address xmiFileOrFolder");
      System.exit(-1);
    }

    Client c = Client.create();

    WebResource r = c.resource(args[0]);

    File xmiFileOrFolder = new File(args[1]);

    File xmiFiles[];

    if (xmiFileOrFolder.isFile()) {
      xmiFiles = new File[] { xmiFileOrFolder };
    } else {
      xmiFiles = xmiFileOrFolder.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.toLowerCase().endsWith(".xmi");
        }
      });
    }

    for (File xmiFile : xmiFiles) {
      byte xmiBytes[] = FileUtil.fileToBytes(xmiFile);

      ClientResponse response = r.path(xmiFile.getName())
          .accept(MediaType.TEXT_XML)
          // TODO: How to fix this? Shouldn't accept do it?
          .header("Content-Type", MediaType.TEXT_XML)
          .post(ClientResponse.class, xmiBytes);

      System.out.println(xmiFile.getName() + " " + response.getStatus());
    }
  }
}
