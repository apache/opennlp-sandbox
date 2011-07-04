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

package org.apache.opennlp.wikinews_importer;

import java.io.File;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * Command Line Tool to create a new corpus in the corpus server.
 */
public class CreateCorpus {
	public static void main(String[] args) throws Exception {
		
		if (args.length != 3) {
			System.out.println("CreateCorpus address corpusName typeSystemFile");
			System.exit(-1);
		}
		
		String corpusName = args[1];
		
		Client c = Client.create();
		
		WebResource r = c.resource(args[0]);
		
		byte typeSystemBytes[] = FileUtil.fileToBytes(new File(args[2]));
		
		
		// load ts file from disk into mem ...
		
		ClientResponse response = r
				.path("_createCorpus")
				.queryParam("corpusName", corpusName)
				.accept(MediaType.TEXT_XML)
				// TODO: How to fix this? Shouldn't accept do it?
				.header("Content-Type", MediaType.TEXT_XML)
				.post(ClientResponse.class, typeSystemBytes);
		
		System.out.println("Result: " + response.getStatus());
	}
}
