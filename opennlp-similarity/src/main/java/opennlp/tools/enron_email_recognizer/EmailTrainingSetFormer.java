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
import java.util.List;

import org.apache.commons.io.FileUtils;

public class EmailTrainingSetFormer {
	static String dataDir = "/Users/bgalitsky/Downloads/",
			//enron_with_categories/",
			fileListFile = "cats4_11-17.txt",
			destinationDir = "/Users/bgalitsky/Documents/ENRON/data11_17/";

	//enron_with_categories/5/70665.cats:4,10,1
	public static void  createPosTrainingSet(){
		try {
			List<String> lines = FileUtils.readLines(new File(dataDir+fileListFile), StandardCharsets.UTF_8);
			for(String l: lines){
				int endOfFname = l.indexOf('.'), startOfFname = l.lastIndexOf('/');
				String filenameOld =dataDir+ l.substring(0, endOfFname)+".txt";
				String content = normalize(new File(filenameOld));
				String filenameNew = destinationDir  + l.substring(startOfFname+1, endOfFname)+".txt";
				//FileUtils.copyFile(new File(filenameOld), new File(filenameNew));
				FileUtils.writeStringToFile(new File(filenameNew), content, StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String origFolder = "maildir_ENRON_EMAILS", newFolder = "data11_17";

	public static String normalize(File f){
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
			for(String h: EmailNormalizer.headers){
				if (l.startsWith(h)){
					bAccept = false;
				}
			}
			for(String h: EmailNormalizer.prohibitedStrings){
				if (l.indexOf(h)>0){
					bAccept = false;
				}
			}
			if (bAccept)
				buf.append(l).append("\n");
		}
		return buf.toString();
	}

	public static void main(String[] args){
		EmailTrainingSetFormer.createPosTrainingSet();
	}
}
