package opennlp.tools.similarity.apps.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import opennlp.tools.nl2code.NL2Obj;
import opennlp.tools.nl2code.NL2ObjCreateAssign;
import opennlp.tools.nl2code.ObjectPhraseListForSentence;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;



public class NLProgram2CodeRequestHandler extends SearchHandler {
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.solr.NLProgram2CodeRequestHandler");
	private final static int MAX_SEARCH_RESULTS = 100;
	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	private ParserChunker2MatcherProcessor sm = null;
	private int MAX_QUERY_LENGTH_NOT_TO_RERANK=3;
	private static String resourceDir = "/home/solr/solr-4.4.0/example/src/test/resources";
	//"C:/workspace/TestSolr/src/test/resources";

	//"/data1/solr/example/src/test/resources";
	
	NL2Obj compiler = new NL2ObjCreateAssign(resourceDir);

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
		

			StringBuffer buf = new StringBuffer();
		    for(String sent:text){
		      ObjectPhraseListForSentence opls=null;
		      try {
		        opls = compiler.convertSentenceToControlObjectPhrase(sent);
		      } catch (Exception e) {
		        e.printStackTrace();
		      }
		      System.out.println(sent+"\n"+opls+"\n");
		      buf.append(sent+"\n |=> "+opls+"\n");
		    }
		
		
		LOG.info("re-ranking results: "+buf.toString());
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