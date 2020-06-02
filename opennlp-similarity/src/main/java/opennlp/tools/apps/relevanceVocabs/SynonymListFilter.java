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

package opennlp.tools.apps.relevanceVocabs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class SynonymListFilter {
	SynonymMap map=null;
	
	public SynonymListFilter(String dir){
		dir = dir.replace("maps/analytics","");
		try {
			map = new SynonymMap( new FileInputStream(dir+"wn_s.pl"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected static Map<String, List<String>> filteredKeyword_synonyms = new HashMap<String, List<String>>();

	static public List<String> getFileLines(File aFile) {

		List<String> items = new ArrayList<String>();

		StringBuilder contents = new StringBuilder();		    
		try {

			BufferedReader input =  new BufferedReader(new FileReader(aFile));
			try {
				String line = null; //not declared within while loop
				while (( line = input.readLine()) != null){
					int endOfWord = line.indexOf(';');
					if (endOfWord>2)
						line = line.substring(1, endOfWord -1 );

					items.add(line);

				}
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){
			ex.printStackTrace();
		}

		return items;
	}
	public String getSynonym (String word){
			String[] synonyms = map.getSynonyms(word);
			if (synonyms==null || synonyms.length<1)
				return null;
			Random rand = new Random();
			int index = (int) Math.floor(rand.nextDouble()*(double)synonyms.length);
			System.out.println("Found synonyms "+Arrays.asList(synonyms).toString()+ " | selected synonym = "+synonyms[index] +" | for the input = "+ word);
			return synonyms[index];
			
	}	
	public static void main(String[] args){
		SynonymListFilter filter = new  SynonymListFilter("/src/test/resources");
		String syn = filter.getSynonym("bring");
		syn = filter.getSynonym("yell");
	}
}
