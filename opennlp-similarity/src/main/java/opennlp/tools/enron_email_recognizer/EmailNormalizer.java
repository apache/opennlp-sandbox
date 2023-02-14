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

package opennlp.tools.enron_email_recognizer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;

public class EmailNormalizer {

	protected ArrayList<File> queue = new ArrayList<>();
	
	protected void addFilesPos(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				addFilesPos(f);
				System.out.println(f.getName());
			}
		} else {
			queue.add(file);
		}
	}
	
	public static final String[] headers = new String[] {
		"Message-ID:",
		"Date:",
		"From:",
		"To:",
		"Subject:",
		"Mime-Version:",
		"Content-T",
		"X-From:",
		"X-To:",
		"X-cc:",
		"X-bcc:",
		"X-Folder:",
		"X-Origin:",
		"X-FileName",
		"cc:",
		"----",
	};
	
	public static final String[] prohibitedStrings = new String[] {
		"@", "<", ">"
	};

	public void normalizeAndWriteIntoANewFile(File f){
		String content="";
		try {
			content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] lines = content.split("\n");
		StringBuilder buf = new StringBuilder();
		for(String l: lines){
			boolean bAccept = true;
			for(String h: headers){
				if (l.startsWith(h)){
					bAccept = false;
				}
			}
			for(String h: prohibitedStrings){
				if (l.indexOf(h)>0){
					bAccept = false;
				}
			}
			if (bAccept)
				buf.append(l).append("\n");
		}
		String origFolder = "maildir_ENRON_EMAILS";
		String newFolder = "data";
		String directoryNew = f.getAbsolutePath().replace(origFolder, newFolder);
		try {
			String fullFileNameNew = directoryNew +"txt";
	        FileUtils.writeStringToFile(new File(fullFileNameNew), buf.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
	        e.printStackTrace();
        }
	}
	
	public void normalizeDirectory(File f){
		addFilesPos(f);
		for(File e: queue){
			normalizeAndWriteIntoANewFile(e);
		}
	}
	
	public static void main(String[] args){
		EmailNormalizer nrm = new EmailNormalizer();
		nrm.normalizeDirectory(new File("/Users/bgalitsky/Documents/ENRON/maildir_ENRON_EMAILS"));
	}
}
