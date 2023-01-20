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

import org.apache.commons.lang.StringUtils;

import net.billylieurance.azuresearch.AzureSearchImageQuery;
import net.billylieurance.azuresearch.AzureSearchImageResult;
import net.billylieurance.azuresearch.AzureSearchResultSet;
import net.billylieurance.azuresearch.AzureSearchWebQuery;
import net.billylieurance.azuresearch.AzureSearchWebResult;

public class BingQueryRunner {
	
	protected static String BING_KEY = 
			"WFoNMM706MMJ5JYfcHaSEDP+faHj3xAxt28CPljUAHA";
			//"pjtCgujmf9TtfjCVBdcQ2rBUQwGLmtLtgCG4Ex7kekw";		
			//"e8ADxIjn9YyHx36EihdjH/tMqJJItUrrbPTUpKahiU0=";
			//"Cec1TlE67kPGDA/1MbeqPfHzP0I1eJypf3o0pYxRsuU=";
	private static final Logger LOG = Logger
		      .getLogger("opennlp.tools.similarity.apps.BingQueryRunner");
	protected AzureSearchWebQuery aq = new AzureSearchWebQuery();
	private AzureSearchImageQuery iq = new AzureSearchImageQuery();
	
	public void setKey(String key){
		BING_KEY = key;
	}
	
	private int MAX_QUERY_LENGTH = 100;
	
	public void setLang(String language){
		aq.setMarket(language);
	}
  
	public List<HitBase> runSearchMultiplePages(String query, int nPages) {
		List<HitBase> results = new ArrayList<HitBase>();
		for(int i=0; i< nPages; i++){
			aq.setPage(i);
		    results.addAll( runSearch(query, 50));
		}
		return results;
	}
	
	public List<HitBase> runSearch(String query, int nRes) {
		
		if (query.length()>MAX_QUERY_LENGTH){
			try {
				query = query.substring(0, MAX_QUERY_LENGTH);
				//should not cut words, need the last space to end the query
				query = query.substring(0, StringUtils.lastIndexOf(query, " "));
			} catch (Exception e) {
				LOG.severe("Problem reducing the length of query :"+query);
			}
		}
		aq.setAppid(BING_KEY);
		aq.setQuery(query);		
		aq.setPerPage(nRes);
		try {
			aq.doQuery();
		} catch (Exception e) { // most likely exception is due to limit on bing key
			aq.setAppid("pjtCgujmf9TtfjCVBdcQ2rBUQwGLmtLtgCG4Ex7kekw");
			try {
				aq.doQuery();
			} catch (Exception e1) {
				aq.setAppid("Cec1TlE67kPGDA/1MbeqPfHzP0I1eJypf3o0pYxRsuU=");
				try {
					aq.doQuery();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
			e.printStackTrace();
		}
		
		//org.xml.sax.SAXParseException
		
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
	
	
	public AzureSearchResultSet<AzureSearchImageResult> runImageSearch(String query) {
		iq.setAppid(BING_KEY);
		iq.setQuery(query);		
		iq.doQuery();
		
		AzureSearchResultSet<AzureSearchImageResult> ars = iq.getQueryResult();

		return ars;
	}
	public int getTotalPagesAtASite(String site)
	{
		return runSearch("site:"+site, 1000000).size();
	}
	

	public List<HitBase> runSearch(String query) {
		return runSearch(query, 100);
	}	
	
	
	

  private float snapshotSimilarityThreshold = 0.4f;

  

  public void setSnapshotSimilarityThreshold(float thr) {
    snapshotSimilarityThreshold = thr;
  }

  public float getSnapshotSimilarityThreshold() {
    return snapshotSimilarityThreshold;
  }

  public BingQueryRunner() {

  }

 

  public static void main(String[] args) {
    BingQueryRunner self = new BingQueryRunner();
    List<HitBase> resp1 = self.runSearch("albert einstein", 15);
    System.out.println(resp1);
    
    AzureSearchResultSet<AzureSearchImageResult> res = self.runImageSearch("albert einstein");
    System.out.println(res);
    try {
    	self.setLang("es-MX");
    	self.setKey(
    			"e8ADxIjn9YyHx36EihdjH/tMqJJItUrrbPTUpKahiU0=");
      List<HitBase> resp = self
          .runSearch(//"art scene");
        		  "biomecanica las palancas");
      System.out.print(resp.get(0));
    } catch (Exception e) {
      e.printStackTrace();
    }

    /*
     * 
     * de-DE
     * es-MX
     * es-SP
     */
    /*
     * String[] submittedNews = new String[]{
     * "Asian airports had already increased security following the Christmas Day attack, but South Korea and Pakistan are thinking about additional measures."
     * ,
     * "Europe remains the key origin for air travelers heading to the United States, with about 1000 trans-Atlantic flights a day in 2009."
     * ,
     * "DeLaughter became an instant hero of the civil rights movement. Alec Baldwin portrayed him in the 1996 movie, Ghosts of Mississippi and his closing statement was once dubbed one of the greatest closing arguments in modern law."
     * ,
     * "After US president made the statement, Cuba protested extra screening for Cubans coming to the US"
     * ,
     * 
     * }; for(String query: submittedNews){ System.out.println(query);
     * List<CopyrightViolations> genResult =
     * self.runCopyRightViolExtenralSearch(query, query); if
     * (genResult.size()>0){
     * 
     * System.out.println(genResult.toString()); System.out.println("\n\n");
     * 
     * } }
     */

  }

}
