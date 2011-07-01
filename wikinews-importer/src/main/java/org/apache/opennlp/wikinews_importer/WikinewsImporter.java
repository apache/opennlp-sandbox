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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class WikinewsImporter {

	public static void main(String[] args) throws Exception {
		
		if (args.length != 2) {
			System.out.println("WikinewsImporter address xmiFile");
			System.exit(-1);
		}
		
		Client c = Client.create();
		
		WebResource r = c.resource(args[0]);
	
		// read file into bytes
		File xmiFile = new File(args[1]);
		ByteArrayOutputStream xmiBytes = new ByteArrayOutputStream((int) xmiFile.length());
		
		InputStream xmiIn = new FileInputStream(xmiFile);
		
		byte buffer[] = new byte[1024]; 
		int length;
		while ((length = xmiIn.read(buffer)) > 0) {
			xmiBytes.write(buffer, 0, length);
		}
		
		xmiIn.close();
		
		ClientResponse response = r
				.path(xmiFile.getName())
				.accept(MediaType.TEXT_XML)
				// TODO: How to fix this? Shouldn't accept do it?
				.header("Content-Type", MediaType.TEXT_XML)
				.post(ClientResponse.class, xmiBytes.toByteArray());
		
		System.out.println(xmiFile.getName() + " " + response.getStatus());
	}
}
