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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.response.ResultContext;
import org.apache.solr.search.DocList;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.QueryParsing;

public class IterativeQueryComponent extends QueryComponent{
	public static final String COMPONENT_NAME = "iterative_query";
	private static final String[] FIELD_SEQUENCE = new String[]{"cat", "name", "content", "author"};

	/**
	 * Run the query multiple times against various fields, trying to recognize search intention
	 */
	@Override
	public void process(ResponseBuilder rb) throws IOException {

		NamedList<Object> nameValuePairs = rb.rsp.getValues();
		nameValuePairs.remove("response");
		rb.rsp.setAllValues(nameValuePairs);
		rb = substituteField(rb, FIELD_SEQUENCE[0] );
		super.process(rb);

		for(int iter = 1; iter< FIELD_SEQUENCE.length; iter++){
			nameValuePairs = rb.rsp.getValues();
			ResultContext c = (ResultContext) nameValuePairs.get("response");
			if (c!=null){			
				DocList dList = c.getDocList();
				if (dList.size()<1){
					nameValuePairs.remove("response");
					rb.rsp.setAllValues(nameValuePairs);
					rb = substituteField(rb, FIELD_SEQUENCE[iter] );

					super.process(rb);
				}
				else {
					return;
				}
			}
		}
/*
		nameValuePairs = rb.rsp.getValues();
		c = (ResultContext) nameValuePairs.get("response");
		if (c!=null){
			DocList dList = c.docs;
			if (dList.size()<1){
				nameValuePairs.remove("response");
				rb.rsp.setAllValues(nameValuePairs);
				rb = substituteField(rb, fieldSequence[2] );
				super.process(rb);
			}
			else {
				return;
			}
		}
		nameValuePairs = rb.rsp.getValues();
		c = (ResultContext) nameValuePairs.get("response");
		if (c!=null){
			DocList dList = c.docs;
			if (dList.size()<1){
				nameValuePairs.remove("response");
				rb.rsp.setAllValues(nameValuePairs);
				rb = substituteField(rb, fieldSequence[3] );
				super.process(rb);
			}
			else {
				return;
			}
		}
*/
	}

	private ResponseBuilder substituteField(ResponseBuilder rb, String newFieldName) {
		SolrParams params = rb.req.getParams();
		String query = params.get("q");
		String currField = StringUtils.substringBetween(" "+query, " ", ":");
		if ( currField !=null && newFieldName!=null)
			query = query.replace(currField, newFieldName);
		NamedList<Object> values = params.toNamedList();
		values.remove("q");
		values.add("q", query);
		params = values.toSolrParams();
		rb.req.setParams(params);
		rb.setQueryString(query);


		String defType = params.get(QueryParsing.DEFTYPE,QParserPlugin.DEFAULT_QTYPE);

		// get it from the response builder to give a different component a chance
		// to set it.
		String queryString = rb.getQueryString();
		if (queryString == null) {
			// this is the normal way it's set.
			queryString = params.get( CommonParams.Q );
			rb.setQueryString(queryString);
		}

		QParser parser = null;
		try {
			parser = QParser.getParser(rb.getQueryString(), defType, rb.req);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Query q = null;
		try {
			q = parser.getQuery();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (q == null) {
			// normalize a null query to a query that matches nothing
			q = new BooleanQuery.Builder().build();
		}
		rb.setQuery( q );
		try {
			rb.setSortSpec(parser.getSortSpec(true));
		} catch (Exception e) {
			e.printStackTrace();
		}
		rb.setQparser(parser);
		/*
		try {
				rb.setScoreDoc(parser.getPaging());
			} catch (Exception e) {
				e.printStackTrace();
		}
		*/
		String[] fqs = rb.req.getParams().getParams(CommonParams.FQ);
		if (fqs!=null && fqs.length!=0) {
			List<Query> filters = rb.getFilters();
			if (filters==null) {
				filters = new ArrayList<>(fqs.length);
			}
			for (String fq : fqs) {
				if (fq != null && fq.trim().length()!=0) {
					QParser fqp = null;
					try {
						fqp = QParser.getParser(fq, null, rb.req);
					} catch (Exception e) {
						e.printStackTrace();
					}
					try {
						filters.add(fqp.getQuery());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			// only set the filters if they are not empty otherwise
			// fq=&someotherParam= will trigger all docs filter for every request 
			// if filter cache is disabled
			if (!filters.isEmpty()) {
				rb.setFilters( filters );
			}
		}
		return rb;
	}
}
