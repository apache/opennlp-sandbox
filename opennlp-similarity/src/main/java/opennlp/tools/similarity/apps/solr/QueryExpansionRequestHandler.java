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
package opennlp.tools.similarity.apps.solr;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

public class QueryExpansionRequestHandler extends SearchHandler {

	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp){
		try {
			//System.out.println("request before ="+req);
			SolrQueryRequest req1 = substituteField(req);
			//System.out.println("request after ="+req1);
			super.handleRequestBody(req1, rsp);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	public static SolrQueryRequest substituteField(SolrQueryRequest req){
		SolrParams params = req.getParams();
		String query = params.get("q");
		System.out.println("query before ="+query);
		query = query.replace(' ', '_');
		System.out.println("query after ="+query);
		NamedList<Object> values = params.toNamedList();
		values.remove("q");
		values.add("q", query);
		params = values.toSolrParams();
		req.setParams(params);
		return req;
	}
}
