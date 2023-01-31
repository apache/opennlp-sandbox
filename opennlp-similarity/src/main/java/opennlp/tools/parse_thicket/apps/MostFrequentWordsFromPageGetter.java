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
package opennlp.tools.parse_thicket.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.ValueSortMap;

public class MostFrequentWordsFromPageGetter {
	
	public List<String> getMostFrequentWordsInText(String input)
	{
		int maxRes = 4;
		Scanner in = new Scanner(input);
        in.useDelimiter("\\s+");
        Map<String, Integer> words = 
                new HashMap<String, Integer>();
        
        while (in.hasNext()) {
            String word = in.next();
            if (!StringUtils.isAlpha(word) || word.length()<4 )
            	continue;
            
            if (!words.containsKey(word)) {
                words.put(word, 1);
            } else {
                words.put(word, words.get(word) + 1);
            }
        }
        
        words = ValueSortMap.sortMapByValue(words, false);
        List<String> results = new ArrayList<String>(words.keySet());
		
		if (results.size() > maxRes )
			results = results.subList(0, maxRes); // get maxRes elements
       
        return results;
    }
	public List<String> getMostFrequentWordsInTextArr(String[] longestSents) {
		StringBuffer buffer = new StringBuffer();
		for(String s: longestSents){
			buffer.append(s);
		}
		
		return getMostFrequentWordsInText(buffer.toString());
	}
	
}
