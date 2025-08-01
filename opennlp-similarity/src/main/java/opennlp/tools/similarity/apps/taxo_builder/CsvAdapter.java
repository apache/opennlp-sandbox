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
package opennlp.tools.similarity.apps.taxo_builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ProfileReaderWriter;

public class CsvAdapter {

	private final Map<String, List<List<String>>> lemma_AssocWords = new HashMap<>();

	public CsvAdapter(String resourceDir, String filenameToImport) {
		importCSV(resourceDir + "/taxonomies/" + filenameToImport);
	}

	private void importCSV(String fileNameToImport) {
		List<String[]> lines = ProfileReaderWriter.readProfiles(fileNameToImport);
		String topNode=null;
		for(String[] line: lines) {
			String line0 = extractEntity(line[0]).toLowerCase();
			List<String> path = new ArrayList<>();
			List<List<String>> paths = new ArrayList<>();
			if (line[1]!=null && line[1].equals("1")){
				topNode = line0;
			} else {
				path.add(topNode);
				path.add(line0);
				paths.add(path);
				lemma_AssocWords.put(line0, paths);
			}
		}
	}

	public Map<String, List<List<String>>> getLemmaAssocWords() {
		return lemma_AssocWords;
	}

	private String extractEntity(String s) {
		Integer[] poss = new Integer[]{s.indexOf('/'), s.indexOf('('),	 s.indexOf('_')};
		
		int cutPos = 100;
		for(int p: poss){
			if (p>-1 && p< cutPos)
				cutPos=p;
		}
		if (cutPos<100) {
			s = s.substring(0,cutPos).trim();
		}
		return s;
	}
}
