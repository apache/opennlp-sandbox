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

package opennlp.tools.apps.review_builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

public class MachineTranslationWrapper  {
	private static final String TRANSLATOR_URL = "http://mymemory.translated.net/api/get?q=";
	
	public String translate(String sentence, String lang2lang){
		if (sentence==null)
			return null;
		String request = TRANSLATOR_URL + sentence.replace(' ','+') + "&langpair="+lang2lang;//"en|es";
		try {
			URL urlC = new URL(request);
			URLConnection connection = urlC.openConnection();

			String line;
			StringBuilder result = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			int count = 0;
			while ((line = reader.readLine()) != null)
			{
				result.append(line);
				count++;
			}
			JSONObject rootObject = new JSONObject(result.toString());
			JSONObject  findObject = rootObject.getJSONObject("responseData");
			String transl = findObject.getString("translatedText");
			return URLDecoder.decode(transl, StandardCharsets.UTF_8);
			
		} catch (IOException | JSONException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public String rePhrase(String sentence){
		System.out.println("orig = "+ sentence);
		String transl = translate(sentence, "en|es");
		System.out.println("tranls = "+transl);
		String inverseTransl = translate(transl, "es|en");
		if (!(inverseTransl.contains("NO QUERY SPECIFIED")) && !(inverseTransl.contains("INVALID LANGUAGE")) && !(inverseTransl.contains("MYMEMORY WARNING")))
			return inverseTransl;
		else 
			return sentence;
	}

	public static void main(String[] args){
		MachineTranslationWrapper rePhraser = new MachineTranslationWrapper();
		System.out.println(rePhraser.translate("I went to the nearest bookstore to buy a book written by my friend and his aunt", "en|ru"));
		System.out.println(rePhraser.rePhrase("I went to the nearest bookstore to buy a book written by my friend and his aunt"));
	}
		
}
