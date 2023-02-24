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

import java.util.logging.Logger;

import opennlp.tools.nl2code.NL2Obj;
import opennlp.tools.nl2code.NL2ObjCreateAssign;
import opennlp.tools.nl2code.ObjectPhraseListForSentence;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

public class NLProgram2CodeRequestHandler extends SearchHandler {
	private static final Logger LOG =
					Logger.getLogger("opennlp.tools.similarity.apps.solr.NLProgram2CodeRequestHandler");
	private final static int MAX_SEARCH_RESULTS = 100;
	private final ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	private final int MAX_QUERY_LENGTH_NOT_TO_RERANK = 3;
	private static final String RESOURCES = "C:/workspace/TestSolr/src/test/resources";
	//"/data1/solr/example/src/test/resources";
	
	final NL2Obj compiler = new NL2ObjCreateAssign(RESOURCES);

	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp){
		// get query string
		String requestExpression = req.getParamString();
		String[] exprParts = requestExpression.split("&");
		String[] text = new String[exprParts.length];
			int count=0;
			for(String val : exprParts){
				if (val.startsWith("line=")){
					val = StringUtils.mid(val, 5, val.length());
					text[count] = val;
					count++;
				}
			}

		StringBuilder buf = new StringBuilder();
		for(String sent:text){
			ObjectPhraseListForSentence opls=null;
			try {
				opls = compiler.convertSentenceToControlObjectPhrase(sent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(sent+"\n"+opls+"\n");
			buf.append(sent).append("\n |=> ").append(opls).append("\n");
		}

		LOG.info("re-ranking results: " + buf);
		NamedList<Object> values = rsp.getValues();
		values.remove("response");
		values.add("response", buf.toString().trim());
		rsp.setAllValues(values);
		
	}

}

/*

http://dev1.exava.us:8086/solr/collection1/reranker/?q=search_keywords:I+want+style+in+my+every+day+fresh+design+iphone+cases
&t1=Personalized+iPhone+4+Cases&d1=spend+a+day+with+a+custom+iPhone+case
&t2=iPhone+Cases+to+spend+a+day&d2=Add+style+to+your+every+day+fresh+design+with+a+custom+iPhone+case
&t3=Plastic+iPhone+Cases&d3=Add+style+to+your+every+day+with+mobile+case+for+your+family
&t4=Personalized+iPhone+and+iPad+Cases&d4=Add+style+to+your+iPhone+and+iPad
&t5=iPhone+accessories+from+Apple&d5=Add+Apple+fashion+to+your+iPhone+and+iPad

http://dev1.exava.us:8086/solr/collection1/reranker/?q=search_keywords:I+want+style+in+my+every+day+fresh+design+iphone+cases&t1=Personalized+iPhone+4+Cases&d1=spend+a+day+with+a+custom+iPhone+case&t2=iPhone+Cases+to+spend+a+day&d2=Add+style+to+your+every+day+fresh+design+with+a+custom+iPhone+case&t3=Plastic+iPhone+Cases&d3=Add+style+to+your+every+day+with+mobile+case+for+your+family&t4=Personalized+iPhone+and+iPad+Cases&d4=Add+style+to+your+iPhone+and+iPad&t5=iPhone+accessories+from+Apple&d5=Add+Apple+fashion+to+your+iPhone+and+iPad
 */