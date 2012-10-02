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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocSlice;

public class SyntGenRequestHandler extends SearchHandler {

	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();

	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp){
      try {
          super.handleRequestBody(req, rsp);
      } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
      }
      //modify rsp
      NamedList values = rsp.getValues();
      ResultContext c = (ResultContext) values.get("response");
      if (c==null)
          return;

      DocList dList = c.docs;
      DocList dListResult=null;
      try {
          dListResult = filterResultsBySyntMatchReduceDocSet(dList,
                  req,  req.getParams());
      } catch (Exception e) {
          dListResult = dList;
          // TODO Auto-generated catch block
          e.printStackTrace();
      }
      c.docs = dListResult;
      values.remove("response");
      values.add("response", c.docs);
      rsp.setAllValues(values);
  }


  public DocList filterResultsBySyntMatchReduceDocSet(DocList docList,
          SolrQueryRequest req,  SolrParams params) {     
      //if (!docList.hasScores()) 
      //  return docList;

      int len = docList.size();
      if (len < 1) // do nothing
          return docList;
      ParserChunker2MatcherProcessor pos = ParserChunker2MatcherProcessor .getInstance();

      DocIterator iter = docList.iterator();
      float[] syntMatchScoreArr = new float[len];
      String requestExpression = req.getParamString();
      String[] exprParts = requestExpression.split("&");
      for(String part: exprParts){
          if (part.startsWith("q="))
              requestExpression = part;           
      }
      String fieldNameQuery = StringUtils.substringBetween(requestExpression, "=", ":");
      // extract phrase query (in double-quotes)
      String[] queryParts = requestExpression.split("\"");
      if  (queryParts.length>=2 && queryParts[1].length()>5)
          requestExpression = queryParts[1].replace('+', ' ');    
      else if (requestExpression.indexOf(":") > -1 ) {// still field-based expression
          requestExpression = requestExpression.replaceAll(fieldNameQuery+":", "").replace('+',' ').replaceAll("  ", " ").replace("q=", "");
      }
      
      if (fieldNameQuery ==null)
          return docList;
      if (requestExpression==null || requestExpression.length()<5  || requestExpression.split(" ").length<3)
          return docList;
      int[] docIDsHits = new int[len]; 

      IndexReader indexReader = req.getSearcher().getIndexReader();
      List<Integer> bestMatchesDocIds = new ArrayList<Integer>(); List<Float> bestMatchesScore = new ArrayList<Float>();
      List<Pair<Integer, Float>> docIdsScores = new ArrayList<Pair<Integer, Float>> ();
      try {
          for (int i=0; i<docList.size(); ++i) {
              int docId = iter.nextDoc();
              docIDsHits[i] = docId;
              Document doc = indexReader.document(docId);

              // get text for event
              String answerText = doc.get(fieldNameQuery);
              if (answerText==null)
                  continue;
              SentencePairMatchResult matchResult = pos.assessRelevance( requestExpression , answerText);
              float syntMatchScore =  new Double(parseTreeChunkListScorer.getParseTreeChunkListScore(matchResult.getMatchResult())).floatValue();
              bestMatchesDocIds.add(docId);
              bestMatchesScore.add(syntMatchScore);
              syntMatchScoreArr[i] = (float)syntMatchScore; //*iter.score();
              System.out.println(" Matched query = '"+requestExpression + "' with answer = '"+answerText +"' | doc_id = '"+docId);
              System.out.println(" Match result = '"+matchResult.getMatchResult() + "' with score = '"+syntMatchScore +"';" );
              docIdsScores.add(new Pair(docId, syntMatchScore));
          }

      } catch (CorruptIndexException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
          //log.severe("Corrupt index"+e1);
      } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
          //log.severe("File read IO / index"+e1);
      }
      
      
      Collections.sort(docIdsScores, new PairComparable());
      for(int i = 0; i<docIdsScores.size(); i++){
          bestMatchesDocIds.set(i, docIdsScores.get(i).getFirst());
          bestMatchesScore.set(i, docIdsScores.get(i).getSecond());
      }
      System.out.println(bestMatchesScore);
      float maxScore = docList.maxScore(); // do not change
      int limit = docIdsScores.size();
      int start = 0; 
      DocSlice ds = null;

      ds = new DocSlice(start, limit, 
              ArrayUtils.toPrimitive(bestMatchesDocIds.toArray(new Integer[0])), 
              ArrayUtils.toPrimitive(bestMatchesScore.toArray(new Float[0])), 
              bestMatchesDocIds.size(), maxScore);



      return ds;
  }

	public class PairComparable implements Comparator<Pair> {
		// @Override
		public int compare(Pair o1, Pair o2) {
			int b = -2;
			if ( o1.getSecond() instanceof Float && o2.getSecond() instanceof Float){

				b =  (((Float)o1.getSecond()> (Float)o2.getSecond()) ? -1
						: (((Float)o1.getSecond() == (Float)o2.getSecond()) ? 0 : 1));
			}
			return b;
		}
	}

}
