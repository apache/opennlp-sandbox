# OpenNLP.Similarity Component

It is a project under Apache OpenNLP which subjects results of parsing, part-of-speech tagging and rhetoric parsing to machine learning.
It is leveraged in search, content generation & enrichment, chat bots and other text processing domains where relevance assessment task is a key.

## What is OpenNLP.Similarity?

OpenNLP.Similarity is an NLP engine which solves a number of text processing and search tasks based on OpenNLP and Stanford NLP parsers. It is designed to be used by a non-linguist software engineer to build linguistically-enabled: 
<ul>
<li>search engines</li>
<li>recommendation systems</li>
<li>dialogue systems</li>
<li>text analysis and semantic processing engines</li>
<li>data-loss prevention system</li>
<li>content & document generation tools</li>
<li>text writing style, authenticity, sentiment, sensitivity to sharing recognizers</li>
<li>general-purpose deterministic inductive learner equipped with abductive, deductive and analogical reasoning which also embraces concept learning and tree kernel learning. </li>
</ul>

OpenNLP similarity provides a series of techniques to support the overall content pipeline, from text collection to cleaning, classification, personalization and distribution. Technology and implementation of content pipeline developed at eBay is described [here](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/examples/ContentPipeline.pdf). 
## Installation
 0) Do [`git clone`](https://github.com/bgalitsky/relevance-based-on-parse-trees.git) to setup the environment including resources. Besides what you get from git, `/resources` directory requires some additional work:
 
 1) Download the main [jar](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/opennlp-similarity.11.jar).
 
 2) Set all necessary jars in /lib folder. Larger size jars are not on git so please download them from [Stanford NLP site](http://nlp.stanford.edu/)
 <li>edu.mit.jverbnet-1.2.0.jar</li>
 <li>ejml-0.23.jar</li>
 <li>joda-time.jar</li>
 <li>jollyday.jar</li>
 <li>stanford-corenlp-3.5.2-models.jar</li>
 <li>xom.jar</li>
 The rest of jars are available via maven.
 
 3) Set up src/test/resources directory
 - new_vn.zip needs to be unzipped
 - OpenNLP models need to be downloaded into the directory 'models' from [here](http://opennlp.sourceforge.net/models-1.5/)
  
  As a result the following folders should be in in /resources:
  As obtained [from git](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/test/resources):
 <li>/new_vn (VerbNet)</li>
 <li>/maps (some lookup files such as products, brands, first names etc.)</li>
 <li>/external_rst (examples of import of rhetoric parses from other systems)</li>
 <li>/fca (Formal Concept Analysis learning)</li>
 <li>/taxonomies (for search support, taxonomies are auto-mined from the web)</li>
 <li>/tree_kernel (for tree kernel learning, representation of parse trees, thickets and trained models)</li>
  Manual downloading is also required for:
  <li>/new_vn</li>
  <li>/w2v (where word2vector model needs to be downloaded, if desired)</li>
  
 4) Try running tests which will give you a hint on how to integrate OpenNLP.Similarity functionality into your application. You can start with [Matcher test](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/949bac8c2a41c21a1e54fec075f2966d693114a4/src/test/java/opennlp/tools/parse_thicket/matching/PTMatcherTest.java) and observe how long paragraphs can be linguistically matched (you can compare this with just an intersection of keywords)
  
 5) Look at [example POMs](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/examples) for how to better integrate into your existing project
  
## Creating a simple project

  Create a project from [MyMatcher.java](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/examples/MyMatcher.java).  
 
## Engines and Systems of OpenNLP.Similarity

### Main relevance assessment function
It takes two texts and returns the cardinality of a maximum common subgraph representations of these texts. This measure is supposed to be much more accurate than keyword statistics, compositional semantic models word2vec because linguistic structure is taken into account, not just co-occurrences of keywords. 
  [Matching class](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/main/java/opennlp/tools/parse_thicket/matching/Matcher.java) in [matching package] (https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/parse_thicket/matching) has 

`List<List<ParseTreeChunk>> assessRelevance(String para1, String para2)`

function which returns the list of [common phrases between these paragraph]s.

To avoid re-parsing the same strings and improve the speed, use

`List<List<ParseTreeChunk>> assessRelevanceCache(String para1, String para2)`

It operates on the level of sentences (giving [maximal common subtree](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/examples/Inferring_sem_prop_of_sentences.pdf)) and paragraphs (giving maximal common [sub-parse thicket](https://en.wikipedia.org/wiki/Parse_Thicket)). Maximal common sub-parse thicket is also represented as a [list of common phrases](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/examples/MachineLearningSyntParseTreesGalitsky.pdf).

<li>Search results re-ranker based on linguistic similarity</li>
<li>Request Handler for SOLR which used parse tree similarity</li>

### Search engine
The following set of functionalities is available to enable search with linguistic features. It is desirable when query is long (more than 4 keywords), logically complex, ambiguous or 
<li>Search results re-ranker based on linguistic similarity</li>
<li>Request Handler for SOLR which used parse tree similarity</li>
<li>Taxonomy builder via learning from the web</li>
<li>Appropriate rhetoric map of an answer verifier. If parts of the answer are located in distinct discourse units, this answer might be irrelevant even if all keywords are mapped</li>
<li>Tree kernel learning re-ranker to improve search relevance within a given domain with pre-trained model</li>

SOLR request handlers are available [here](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/similarity/apps/solr)

Taxonomy builder is [here](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/similarity/apps/taxo_builder).
 Examples of pre-built taxonomy are available in [this directory](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/test/resources/taxonomies). Please pay attention at taxonomies built for languages other than English. A [music taxonomy](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/test/resources/taxonomies/musicTaxonomyRoot.csv) is an example of the seed data for taxonomy building, and [this taxonomy hashmap dump](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/similarity/apps/taxo_builder/taxonomy.txt) is a good example of what can be automatically constructed. A paper on taxonomy learning is [here](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/examples/taxonomyBuilder.pdf). 
 
#### Search results re-ranker
Re-ranking scores similarity between a given `orderedListOfAnswers` and  `question`

  `List<Pair<String,Double>> pairList = new ArrayList<Pair<String,Double>>();`
  
  `for (String ans: orderedListOfAnswers) {`
  
            `List<List<ParseTreeChunk>> similarityResult = m.assessRelevanceCache(question, ans);`
            
            `double score = parseTreeChunkListScorer.getParseTreeChunkListScoreAggregPhraseType(similarityResult);`
            
            `Pair<String,Double> p = new Pair<String, Double>(ans, score);`
            
            `pairList.add(p);`
            
        `}`
        
   `Collections.sort(pairList, Comparator.comparing(p -> p.getSecond()));`
   
   Then `pairList` is then ranked according to the linguistic relevance score. This score can be combined with other sources such as popularity, geo-proximity and others.

### Content generator
 It takes a topic, builds a taxonomy for it and forms a table of content. It then  mines the web for documents for each table of content item, finds relevant sentences and paragraphs and merges them into a document [package](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/similarity/apps). The resultant document has a TOC, sections, figures & captions and also a reference section. We attempt to reproduce how humans cut-and-paste content from the web while writing on a topic. 
  Content generation has a [demo](http://37.46.135.20/)  and to run it from IDE start [here](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/main/java/opennlp/tools/similarity/apps/ContentGeneratorRunner.java). Examples of written documents are [here](http://37.46.135.20/wrt_latest/).
  Another content generation option is about opinion data. Reviews are mined for, cross-bred and made "original" for search engines. This and general content generation is done for SEO purposes. [Review builder](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/apps/review_builder/ReviewBuilderRunner.java) composes fake reviews which are in turn should be recognized by a Fake Review detector

### Text classifier / feature detector in text
The [classifier code](https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/main/java/opennlp/tools/parse_thicket/kernel_interface/TreeKernelBasedClassifierMultiplePara.java) is the same but the [model files](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/test/resources/tree_kernel/TRAINING) vary for the applications below:
<li>detect security leaks
<li>detect argumentation
<li>detect low cohesiveness in text
<li>detect authors’ doubt and low confidence
<li>detect fake review

Document classification to six major classes {finance, business, legal, computing, engineering, health} is available via [nearest neighbor model](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/doc_classifier/DocClassifier.java). A Lucene training model (1G file) is obtained from Wikipedia corpus. This classifier can be trained for an arbitrary classes once respective Wiki pages are selected and respective [Lucene index is built](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/doc_classifier/ClassifierTrainingSetIndexer.java). Once proper training documents are selected from Wikipedia with adequate coverage, the accuracy is usually higher than can be achieved by word2vec classification models.

### General-purpose [deterministic inductive learner](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/jsmlearning) implements JS Mills method of induction and abduction (deduction is also partially implemented).

 Inductive learning implemented as a base for syntactic tree-based learning is similar to the family of approaches such as Explanation-based Learning and Inductive Logic Programming.
 
#### Tree-kernel learning 
 
 is integrated to allow application of SVM learning to sentence-level and paragraph-level linguistic data including discourse. Unlike learning in numerical space, each dimension in tree kernel learning is an occurrence of a particular subtree. Similarity is not a numerical distance but a count of common subtrees. A set of parse trees for individual sentences to represent a paragraph is called
 [parse thicket](https://en.wikipedia.org/wiki/Parse_Thicket). Its representation as a graph is coded in a tree representation via parenthesis such as [model*.txt] (https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/test/resources/tree_kernel/model_pos_neg_sentiment.txt).
 To do model building and predictions, C modules are run in [this directory](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/test/resources/tree_kernel), so proper choice need to be made: {svm_classify.linux, svm_classify.max, svm_classify.exe, svm_learn.*}. Also, proper run permissions needs to be set for these files.
 
#### Concept learning 
 
  is a branch of deterministic learning which is applied to attribute-value pairs and possesses useful explainability feature, unlike statistical and deep learning. It is fairly useful for data exploration and visualization since all interesting relations can be visualized. 
    Concept learning covers inductive and abductive learning and also some cases of deduction. Explore [this package](https://github.com/bgalitsky/relevance-based-on-parse-trees/tree/master/src/main/java/opennlp/tools/fca) for the concept learning-related features.

### Filtering results for Speech Recognition based on semantic meaningfulness
It takes results from a speech-to-text system and subjects them to [filtering]
(https://github.com/bgalitsky/relevance-based-on-parse-trees/blob/master/src/main/java/opennlp/tools/similarity/apps/SpeechRecognitionResultsProcessor.java). Those recognized candidate words which do not make sense together are filtered out, based on the frequency of co-occurrences found on the web.
## Related Research
Here's the link to the book on [question-answering](https://www.amazon.com/Natural-Language-Question-Answering-system/dp/0868039799/ref=sr_1_10?ie=UTF8&qid=1478871097&sr=8-10&keywords=galitsky)

and [research papers](https://scholar.google.com/citations?hl=ru&user=kR_M3HIAAAAJ).

Also the recent [book related to reasoning and linguistics in humans & machines](https://www.amazon.com/Computational-Autism-Human-Computer-Interaction-Galitsky/dp/3319399713)

## Configuring OpenNLP.Similarity component

VerbNet model is included by default, so that the hand-coded meanings of the verb are used when simularity between verb phrases are computed.

To include word2vector model, [download it](https://deeplearning4j.org/) and make sure the following path is valid:
`resourceDir + "/w2v/GoogleNews-vectors-negative300.bin.gz"`