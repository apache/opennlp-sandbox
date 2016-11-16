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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class AbstractEngineRunner {
	private List<File> queue;
	private final static String reviewSource = "/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources/opinions/macbook_pro.txt";
	NamedEntityExtractor neExtractor = new NamedEntityExtractor();
	
	public void processJSONfileWithReviews(){
		List<String[]> report = new ArrayList<String[]>();
		report.add(new String[] { "text", "phrases of potential interest list" , });

		
		String content=null;
		try {
			content = FileUtils.readFileToString(new File(reviewSource));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] texts = StringUtils.substringsBetween(content, "reviewText\": \"", "\", \"overall");
		for(String text: texts){
			EntityExtractionResult result = neExtractor.extractEntities(text);
			report.add(new String[]{text});
			//report.add((String[])result.extractedNERWords.toArray(new String[0]));
			//report.add((String[])result.extractedSentimentPhrases.toArray(new String[0]));
			List<String> stringPhrases = new ArrayList<String>(),
					nodePhrases = new ArrayList<String>();
			for(List<ParseTreeNode> chList: result.extractedSentimentPhrases){
				String buf = "", nodeBuf="";
				for(ParseTreeNode ch: chList){
					buf+=ch.getWord()+ " ";
					nodeBuf+=ch.toString()+ " ";
				}
				stringPhrases.add(buf.trim());
				nodePhrases.add(nodeBuf.trim());
			}
			report.add((String[])stringPhrases.toArray(new String[0]));
			report.add((String[])nodePhrases.toArray(new String[0]));
			report.add(new String[]{"-----------------------------"});
			ProfileReaderWriter.writeReport(report, "nameEntitiesTopicsOfInterestExtracted.csv");
		}
	}

	// this func collects files 
		private void addFiles(File file) {

			if (!file.exists()) {
				System.out.println(file + " does not exist.");
			}
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					if (f.getName().startsWith("."))
						continue;
					addFiles(f);
					System.out.println(f.getName());
				}
			} else {
				queue.add(file);

			}
		}
	
	public static void main(String[] args){
		AbstractEngineRunner runner = new AbstractEngineRunner();
		runner.processJSONfileWithReviews();

	}
}

/*
	public void processDirectory(String path){
		List<String[]> report = new ArrayList<String[]>();
		report.add(new String[] { "filename", "named entity list", "phrases of potential interest list" });

		List<String> allNamedEntities = new ArrayList<String>();

		addFiles(new File(path));
		for(File f: queue){
			List<String> entities = (List<String>) extractEntities(f.getAbsolutePath()).getFirst();
			List<String> opinions = (List<String>) extractEntities(f.getAbsolutePath()).getSecond();
			report.add(new String[]{ f.getName(), entities.toString(),  opinions.toString()});	
			ProfileReaderWriter.writeReport(report, "nameEntitiesExtracted.csv");

			allNamedEntities.addAll(entities);

			allNamedEntities = new ArrayList<String>(new HashSet<String> (allNamedEntities ));


		}
		ProfileReaderWriter.writeReport(report, "nameEntitiesTopicsOfInterestExtracted.csv");
	} 
} */
