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
package opennlp.tools.similarity.apps;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.PageFetcher;

public class GoogleAutoCompleteQueryRunner {
	protected PageFetcher pageFetcher = new PageFetcher();
	private static String searchRequest = "http://google.com/complete/search?q=",
			suffix = "&output=toolbar";
	
	
	public List<String> getAutoCompleteExpression(String rawExpr){
		// insert spaces into camel cases
		rawExpr= rawExpr.replaceAll("([a-z][a-z])([A-Z][a-z])", "$1 $2");
		String query = rawExpr.replace(' ', '+');
		try {
			query = URLEncoder.encode(query, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String pageOrigHTML = pageFetcher.fetchOrigHTML(searchRequest +query+suffix);
		String[] results = StringUtils.substringsBetween(pageOrigHTML, "<CompleteSuggestion>", "</CompleteSuggestion>");
		List<List<String>> accum = new ArrayList<List<String>>();
		if (results==null)
				return null;
		for(String wrapped: results){
			List<String> accumCase = new ArrayList<String>();
			String[] words = null;
			try {
				words = StringUtils.substringBetween(wrapped, "\"").split(" ");
			} catch (Exception e){
				
			}
			if (words==null || words.length<1)
				continue;
			accumCase = Arrays.asList(words);
			accum.add(accumCase);
		}
		
		//TODO make more noise-resistant algo
		if (accum.size()>1){
			List<String> first = new ArrayList<String>(accum.get(0));
			List<String> second = new ArrayList<String>(accum.get(1));
			
			first.retainAll(second);
			if (first.size()>0)
			   return first;
			else
			   return accum.get(0);
		} 
		
		if (accum.size()==1)
			return accum.get(0);
		
		
		return null;
	}
	
	public static String handleCamelCases(String input){
		String s = input.replaceAll("([a-z,A-Z][a-z])([A-Z][a-z])", "$1 $2").replaceAll("([a-z,A-Z][a-z])([A-Z][a-z])", "$1 $2");
		s = s.replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
		return s;
	}
	
	public static void main(String[] args){
	
		
		String[] tests = new String[]{"SharingInviteNotification", "SharedByMeSortingOptions", "SharedByMeCurrentSortingOption", "GroupedPrivatelySharedByMe",
				"StorageMeter", "RecentActivities", "StorageMeter", "SharingInviteNotification", 
				"RecentActivities", "ImporterSuggestionsPrefABC",
				"WSItem",
				"SharingInviteNotification",
				"UserDesktopDevices",
				"RootFoldersPaginated",
				"SharingInviteNotification", "apply security settings"};
		for(String s: tests){
			System.out.println(handleCamelCases(s));
		}
		
		GoogleAutoCompleteQueryRunner runner = new GoogleAutoCompleteQueryRunner();
		List<String> 
		res = runner.getAutoCompleteExpression("commentcount");
		System.out.println(res);
		res = runner.getAutoCompleteExpression("clearSess");
		System.out.println(res);
	    res = runner.getAutoCompleteExpression("ImporterSuggestionsPref");
		System.out.println(res);
	    res = runner.getAutoCompleteExpression("breadCrumbs");
		System.out.println(res);
		res = runner.getAutoCompleteExpression("RootFolder");
		System.out.println(res);
		
		res = runner.getAutoCompleteExpression("BreadCrumbList");
		System.out.println(res);
		
	}

}

