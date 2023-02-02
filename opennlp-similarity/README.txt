Apache OpenNLP ${pom.version}
===============================


Building from the Source Distribution
-------------------------------------

At least Maven 3.0.0 is required for building.

To build everything go into the opennlp directory and run the following command:
    mvn clean install
   
The results of the build will be placed  in:
    opennlp-distr/target/apache-opennlp-[version]-bin.tar-gz (or .zip)

What is in Similarity component in Apache OpenNLP ${pom.version}
---------------------------------------
SIMILARITY COMPONENT of OpenNLP

1. Introduction
This component does text relevance assessment. It takes two portions of texts (phrases, sentences, paragraphs) and returns a similarity score.
Similarity component can be used on top of search to improve relevance, computing similarity score between a question and all search results (snippets). 
Also, this component is useful for web mining of images, videos, forums, blogs, and other media with textual descriptions. Such applications as content generation 
and filtering meaningless speech recognition results are included in the sample applications of this component.
   Relevance assessment is based on machine learning of syntactic parse trees (constituency trees, http://en.wikipedia.org/wiki/Parse_tree). 
The similarity score is calculated as the size of all maximal common sub-trees for sentences from a pair of texts (
www.aaai.org/ocs/index.php/WS/AAAIW11/paper/download/3971/4187, www.aaai.org/ocs/index.php/FLAIRS/FLAIRS11/paper/download/2573/3018,
www.aaai.org/ocs/index.php/SSS/SSS10/paper/download/1146/1448).
   The objective of Similarity component is to give an application engineer as tool for text relevance which can be used as a black box, no need to understand 
 computational linguistics or machine learning. 
 
 2. Installation
 Please refer to OpenNLP installation instructions
 
 3. First use case of Similarity component: search
 
 To start with this component, please refer to SearchResultsProcessorTest.java in package opennlp.tools.similarity.apps
   public void testSearchOrder() runs web search using Bing API and improves search relevance.
   Look at the code of 
      public List<HitBase> runSearch(String query) 
   and then at 
      private	BingResponse calculateMatchScoreResortHits(BingResponse resp, String searchQuery)
   which gets search results from Bing and re-ranks them based on computed similarity score.
 
   The main entry to Similarity component is 
    SentencePairMatchResult matchRes = sm.assessRelevance(snapshot, searchQuery);
    where we pass the search query and the snapshot and obtain the similarity assessment structure which includes the similarity score.
   
   To run this test you need to obtain search API key from Bing at www.bing.com/developers/s/APIBasics.html and specify it in public class BingQueryRunner in
  protected static final String APP_ID. 
  
  4. Solving a unique problem: content generation
  To demonstrate the usability of Similarity component to tackle a problem which is hard to solve without a linguistic-based technology, 
  we introduce a content generation component:
   RelatedSentenceFinder.java
   
   The entry point here is the function call
   hits = f.generateContentAbout("Albert Einstein");
   which writes a biography of Albert Einstein by finding sentences on the web about various kinds of his activities (such as 'born', 'graduate', 'invented' etc.).
   The key here is to compute similarity between the seed expression like "Albert Einstein invented relativity theory" and search result like 
   "Albert Einstein College of Medicine | Medical Education | Biomedical ...
    www.einstein.yu.edu/Albert Einstein College of Medicine is one of the nation's premier institutions for medical education, ..."
    and filter out irrelevant search results.
   
   This is done in function 
   public HitBase augmentWithMinedSentencesAndVerifyRelevance(HitBase item, String originalSentence,
			List<String> sentsAll)
			
   	  SentencePairMatchResult matchRes = sm.assessRelevance(pageSentence + " " + title, originalSentence);
   You can consult the results in gen.txt, where an essay on Einstein bio is written.
   
   These are examples of generated articles, given the article title
     http://www.allvoices.com/contributed-news/9423860/content/81937916-ichie-sings-jazz-blues-contemporary-tunes
     http://www.allvoices.com/contributed-news/9415063-britney-spears-femme-fatale-in-north-sf-bay-area
     
  5. Solving a high-importance problem: filtering out meaningless speech recognition results.
  Speech recognitions SDKs usually produce a number of phrases as results, such as 
  			 "remember to buy milk tomorrow from trader joes",
			 "remember to buy milk tomorrow from 3 to jones"
  One can see that the former is meaningful, and the latter is meaningless (although similar in terms of how it is pronounced).
  We use web mining and Similarity component to detect a meaningful option (a mistake caused by trying to interpret meaningless 
  request by a query understanding system such as Siri for iPhone can be costly).
 
  SpeechRecognitionResultsProcessor.java does the job:
  public List<SentenceMeaningfullnessScore> runSearchAndScoreMeaningfulness(List<String> sents)
  re-ranks the phrases in the order of decrease of meaningfulness.
  
  6. Similarity component internals
  in the package   opennlp.tools.textsimilarity.chunker2matcher
  ParserChunker2MatcherProcessor.java does parsing of two portions of text and matching the resultant parse trees to assess similarity between 
  these portions of text.
  To run ParserChunker2MatcherProcessor
     private static String MODEL_DIR = "resources/models";
  needs to be specified
  
  The key function
  public SentencePairMatchResult assessRelevance(String para1, String para2)
  takes two portions of text and does similarity assessment by finding the set of all maximum common subtrees 
  of the set of parse trees for each portion of text
  
  It splits paragraphs into sentences, parses them, obtained chunking information and produces grouped phrases (noun, evrn, prepositional etc.):
  public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForPara(String para)
  
  and then attempts to find common subtrees:
  in ParseTreeMatcherDeterministic.java
		List<List<ParseTreeChunk>> res = md.matchTwoSentencesGroupedChunksDeterministic(sent1GrpLst, sent2GrpLst)
  
  Phrase matching functionality is in package opennlp.tools.textsimilarity;
  ParseTreeMatcherDeterministic.java:
  Here's the key matching function which takes two phrases, aligns them and finds a set of maximum common sub-phrase
  public List<ParseTreeChunk> generalizeTwoGroupedPhrasesDeterministic
  
  7. Package structure
  	opennlp.tools.similarity.apps : 3 main applications
	opennlp.tools.similarity.apps.utils: utilities for above applications
	
	opennlp.tools.textsimilarity.chunker2matcher: parser which converts text into a form for matching parse trees
	opennlp.tools.textsimilarity: parse tree matching functionality
	



Requirements
------------
Java 1.5 is required to run OpenNLP
Maven 3.0.0 is required for building it

Known OSGi Issues
------------
In an OSGi environment the following things are not supported:
- The coreference resolution component
- The ability to load a user provided feature generator class

Note
----
The current API contains still many deprecated methods, these
will be removed in one of our next releases, please
migrate to our new API.
