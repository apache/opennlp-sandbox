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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


import net.billylieurance.azuresearch.AzureSearchResultSet;
import net.billylieurance.azuresearch.AzureSearchWebQuery;
import net.billylieurance.azuresearch.AzureSearchWebResult;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;

import org.apache.commons.lang.StringUtils;



public class BingWebQueryRunner {
  private static final Logger LOG = Logger
      .getLogger("opennlp.tools.similarity.apps.BingWebQueryRunner");
    public static final String BING_KEY = "TyfmF/4t1qbnA5X6sBXiTf80l29cSn+7IT0fPw2FNsU=";
	private AzureSearchWebQuery aq = new AzureSearchWebQuery();
  
	public List<HitBase> runSearch(String query, int nRes) {
	aq.setAppid(BING_KEY);
	aq.setQuery(query);		                        
	aq.doQuery();
	
	List<HitBase> results = new ArrayList<HitBase> ();
	AzureSearchResultSet<AzureSearchWebResult> ars = aq.getQueryResult();
	
	for (AzureSearchWebResult anr : ars){
	    HitBase h = new HitBase();
	    h.setAbstractText(anr.getDescription());
	    h.setTitle(anr.getTitle());
	    h.setUrl(anr.getUrl());
	    results.add(h);
	    results = removeDuplicates(results, 0.9);
	}
	return results;
}
   

  public static List<HitBase> removeDuplicates(List<HitBase> hits,
      double imageDupeThresh) {
    StringDistanceMeasurer meas = new StringDistanceMeasurer();

    List<Integer> idsToRemove = new ArrayList<Integer>();
    List<HitBase> hitsDedup = new ArrayList<HitBase>();
    try {
      for (int i = 0; i < hits.size(); i++)
        for (int j = i + 1; j < hits.size(); j++) {
          String title1 = hits.get(i).getTitle();
          String title2 = hits.get(j).getTitle();
          if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
            continue;
          if (meas.measureStringDistance(title1, title2) > imageDupeThresh) {
            idsToRemove.add(j); // dupes found, later list member to
            // be deleted
          }
        }
      for (int i = 0; i < hits.size(); i++)
        if (!idsToRemove.contains(i))
          hitsDedup.add(hits.get(i));
      if (hitsDedup.size() < hits.size()) {
        LOG.info("Removed duplicates from relevant search results, including "
            + hits.get(idsToRemove.get(0)).getTitle());
      }
    } catch (Exception e) {
      LOG.severe("Problem removing duplicates from relevant images");
    }

    return hitsDedup;

  }

  public int getTotalPagesAtASite(String site) {
   
    try {
      List<HitBase> resultList = runSearch("site:" + site, 10);
     

    } catch (Exception e) {
      // e.printStackTrace();
      LOG.info("No news search results for query = 'site:" + site);
      return 0;
    }

    return 0;
  }
  
  public static void main(String[] args) {
	    BingWebQueryRunner self = new BingWebQueryRunner();
	    
	    List<HitBase> res = self.runSearch ("albert einstein", 10);
  }

  
}
