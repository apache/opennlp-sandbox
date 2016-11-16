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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVWriter;
import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class TwitterEngineRunner {
	private List<File> queue;
	private final static String twSource = "/Users/bgalitsky/Documents/workspace/TwitterMiner/data/TwitterArtistsDynamicsTot12_07.csv";
	TwitterFilter neExtractor = new TwitterFilter();
	private static int iWind = 80;

	public void processTweetFile(int nRun){
		List<String[]> report = new ArrayList<String[]>(), ful_less =  new ArrayList<String[]>();
		List<String> meaningLESS = new ArrayList<String>(), meaningFUL = new ArrayList<String>();
		report.add(new String[] { "text", "phrases of potential interest list" , });

		List<String[]> texts = ProfileReaderWriter.readProfiles(twSource);
		int offset = iWind*nRun;
		
		//for(int i=offset; i< offset+iWind; i++){
			
		//	String[] text = texts.get(i);
		for(String[] text: texts){
			List<String> textDeduped = new ArrayList<String>(new HashSet<String>(Arrays.asList(text)));
			EntityExtractionResult result = null;
			if (text==null || text.length<4)
				continue;

			for(int nInLine=3; nInLine<textDeduped.size(); nInLine++){
				if (textDeduped.get(nInLine).length()>180)
					continue;
				
				String cleanedTweet = textDeduped.get(nInLine).replace("/\\bs\\@+/ig","");
				try {
					result = neExtractor.extractEntities(cleanedTweet);
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				report.add(new String[]{text[0],text[nInLine]});
				report.add((String[])result.extractedNERWords.toArray(new String[0]));
				//report.add((String[])result.extractedSentimentPhrases.toArray(new String[0]));
				List<String> stringPhrases = new ArrayList<String>(),
						nodePhrases = new ArrayList<String>();
				Boolean bMeaningf = false;

				//stringPhrases.add(""); nodePhrases.add(""); // to make report more readable
				for(List<ParseTreeNode> chList: result.extractedSentimentPhrases){
					String buf = "", nodeBuf="";
					for(ParseTreeNode ch: chList){
						buf+=ch.getWord()+ " ";
						nodeBuf+=ch.toString()+ " ";
					}
					stringPhrases.add(buf.trim());
					nodePhrases.add(nodeBuf.trim());
				}
				// selecting MEANINGFULL
				if (nodePhrases.size()>1){
					if ((nodePhrases.get(0).indexOf(">VP'")>-1 || nodePhrases.get(0).indexOf(">NNP'")>-1) &&
							(nodePhrases.get(1).indexOf(">VP'")>-1 || nodePhrases.get(1).indexOf(">NNP'")>-1)){
						bMeaningf = true;

					}
				}

				report.add((String[])stringPhrases.toArray(new String[0]));
				report.add((String[])nodePhrases.toArray(new String[0]));
				if (bMeaningf){
					report.add(new String[]{"===", "MEANINGFUL tweet"});
					if (!meaningFUL.contains(cleanedTweet))
						meaningFUL.add(cleanedTweet);
				} else {
					if (!meaningLESS.contains(cleanedTweet))
						meaningLESS.add(cleanedTweet);
				}

				int count = 0;
				ful_less.clear();
				for(String less: meaningLESS ){
					String fl = "";
					if (count<meaningFUL.size())
						fl = meaningFUL.get(count);
					ful_less.add(new String[]{less, fl});
					count++;
				}

				report.add(new String[]{"-----------------------------------------------------"});
					ProfileReaderWriter.writeReport(report, "phrasesExtractedFromTweets3_"+nRun+".csv");
					ProfileReaderWriter.writeReport(ful_less, "ful_lessTweets3_"+nRun+".csv");
				
			}
		}
	}


	public static void main(String[] args){
		TwitterEngineRunner runner = new TwitterEngineRunner();
		int nRun = Integer.parseInt(args[0]);
		runner.processTweetFile(nRun);

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


