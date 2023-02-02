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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.billylieurance.azuresearch.AzureSearchResultSet;
import net.billylieurance.azuresearch.AzureSearchWebQuery;
import net.billylieurance.azuresearch.AzureSearchWebResult;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;


public class BingQueryRunnerMultipageSearchResults extends BingQueryRunner {
	
	private static String BING_KEY = "e8ADxIjn9YyHx36EihdjH/tMqJJItUrrbPTUpKahiU0=";
	private static final Logger LOG = Logger
		      .getLogger("opennlp.tools.similarity.apps.BingQueryRunnerMultipageSearchResults");
	private AzureSearchWebQuery aq = new AzureSearchWebQuery();

	public List<HitBase> runSearch(String query, int nRes, boolean bHighRank) {
		aq.setAppid(BING_KEY);
		aq.setQuery(query);		  		
		aq.doQuery();
		if (!bHighRank)
			aq.setPage(5);
		aq.setPerPage(nRes);
		
		List<HitBase> results = new ArrayList<HitBase> ();
		AzureSearchResultSet<AzureSearchWebResult> ars = aq.getQueryResult();
		
		for (AzureSearchWebResult anr : ars){
		    HitBase h = new HitBase();
		    h.setAbstractText(anr.getDescription());
		    h.setTitle(anr.getTitle());
		    h.setUrl(anr.getUrl());
		    results.add(h);
		}
		return results;
	}
	
	


}
