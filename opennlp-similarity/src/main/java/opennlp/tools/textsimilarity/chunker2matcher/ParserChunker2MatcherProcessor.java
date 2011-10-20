package opennlp.tools.textsimilarity.chunker2matcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.textsimilarity.LemmaPair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

public class ParserChunker2MatcherProcessor {
	private static final int MIN_SENTENCE_LENGTH = 10;
	private static final String MODEL_DIR_KEY = "nlp.models.dir";
	private static final String MODEL_DIR ;
	private static ParserChunker2MatcherProcessor instance;

	private SentenceDetector sentenceDetector;
	private Tokenizer tokenizer;
	private POSTagger posTagger;
	private Parser parser;
	private ChunkerME chunker;
	private final int NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS = 5;
	Logger logger = Logger.getLogger("opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor");

	static {
		//TODO config
		MODEL_DIR = "C:\\workspace\\similarity\\src\\main\\resources";
	}

	private ParserChunker2MatcherProcessor() {
		initializeSentenceDetector();
		initializeTokenizer();
		initializePosTagger();
		initializeParser();
		initializeChunker();
		
	}

	public synchronized static ParserChunker2MatcherProcessor getInstance() {
		if (instance == null)
			instance = new ParserChunker2MatcherProcessor();

		return instance;
	}

	public List<List<Parse>> parseTextNlp(String text) {
		if (text == null || text.trim().length() == 0)
			return null;

		List<List<Parse>> textParses = new ArrayList<List<Parse>>(1);

		// parse paragraph by paragraph
		String[] paragraphList = splitParagraph(text);
		for (String paragraph : paragraphList) {
			if (paragraph.length() == 0)
				continue;

			List<Parse> paragraphParses = parseParagraphNlp(paragraph);
			if (paragraphParses != null)
				textParses.add(paragraphParses);
		}

		return textParses;
	}

	public List<Parse> parseParagraphNlp(String paragraph) {
		if (paragraph == null || paragraph.trim().length() == 0)
			return null;

		// normalize the text before parsing, otherwise, the sentences may not
		// be
		// separated correctly

		//paragraph = TextNormalizer.normalizeText(paragraph);

		// parse sentence by sentence
		String[] sentences = splitSentences(paragraph);
		List<Parse> parseList = new ArrayList<Parse>(sentences.length);
		for (String sentence : sentences) {
			sentence = sentence.trim();
			if (sentence.length() == 0)
				continue;

			Parse sentenceParse = parseSentenceNlp(sentence, false);
			if (sentenceParse != null)
				parseList.add(sentenceParse);
		}

		return parseList;
	}

	public Parse parseSentenceNlp(String sentence) {
		// if we parse an individual sentence, we want to normalize the text
		// before parsing
		return parseSentenceNlp(sentence, true);
	}

	public synchronized Parse parseSentenceNlp(String sentence,
			boolean normalizeText) {
		// don't try to parse very short sentence, not much info in it anyway,
		// most likely a heading
		if (sentence == null || sentence.trim().length() < MIN_SENTENCE_LENGTH)
			return null;

		//if (normalizeText)
		//	sentence = TextNormalizer.normalizeText(sentence);

		Parse[] parseArray = null;
		try {
			parseArray = ParserTool.parseLine(sentence, parser, 1);
		} catch (Throwable t) {
			logger.log(Level.WARNING, "failed to parse the sentence : '"+sentence, t);
			return null;
		}

		// there should be only one result parse
		if (parseArray != null && parseArray.length > 0)
			return parseArray[0];
		else
			return null;
	}


	public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForPara(String para){
		List<List<ParseTreeChunk>> listOfChunksAccum = new ArrayList<List<ParseTreeChunk>>();
		String[] sentences = splitSentences(para);
		for(String sent: sentences){
			List<List<ParseTreeChunk>> singleSentChunks = formGroupedPhrasesFromChunksForSentence(sent); 
			if (listOfChunksAccum.size()<1 ){
				listOfChunksAccum = new ArrayList<List<ParseTreeChunk>>(singleSentChunks);
			} else 
				for(int i= 0; i<NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS; i++){
					//make sure not null
					if (singleSentChunks.size()!=NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS)
						break;
					List<ParseTreeChunk> phraseI = singleSentChunks.get(i);
					List<ParseTreeChunk> phraseIaccum  = listOfChunksAccum.get(i);
					phraseIaccum.addAll(phraseI);
					listOfChunksAccum.set(i, phraseIaccum);
				}
		}

		return listOfChunksAccum;
	}


	public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForSentence(String sentence) {
		if (sentence == null || sentence.trim().length() < MIN_SENTENCE_LENGTH)
			return null;

		sentence = TextProcessor.removePunctuation(sentence);

		String[] toks = tokenizer.tokenize(sentence);
		String[] tags = posTagger.tag(toks);
		String[] res = chunker.chunk(toks, tags);
		Span[] span =  chunker.chunkAsSpans(toks, tags);
		Sequence[] seq = chunker.topKSequences(toks, tags);

		// correction for chunking tags
		for(int i=0; i< toks.length; i++){
			if (toks[i].equalsIgnoreCase("is")){
				res[i] = "B-VP";
			}
		}

		List<List<ParseTreeChunk>> listOfChunks = new ArrayList<List<ParseTreeChunk>>();
		List<ParseTreeChunk> nounPhr = new ArrayList<ParseTreeChunk>(), 
		prepPhr = new ArrayList<ParseTreeChunk>(), verbPhr  = new ArrayList<ParseTreeChunk>(), 
		adjPhr  = new ArrayList<ParseTreeChunk>(), 
		// to store the whole sentence
		wholeSentence = new ArrayList<ParseTreeChunk>();

		List<String> pOSsAll = new ArrayList<String>(), lemmasAll = new ArrayList<String>();

		for(int i = 0; i< toks.length; i++){
			pOSsAll.add(tags[i]);
			lemmasAll.add(toks[i]);
		}
		wholeSentence.add(new ParseTreeChunk("SENTENCE", lemmasAll, pOSsAll));

		boolean currPhraseClosed = false;
		for(int i=0; i< res.length; i++){
			String bi_POS = res[i];
			currPhraseClosed = false;
			if (bi_POS.startsWith("B-NP")){// beginning of a phrase

				List<String> pOSs = new ArrayList<String>(), lemmas = new ArrayList<String>();
				pOSs.add(tags[i]);
				lemmas.add(toks[i]);
				for(int j=i+1; j<res.length; j++){
					if (res[j].startsWith("B-VP")){
						nounPhr.add(new ParseTreeChunk("NP", lemmas, pOSs));
						logger.info(i + " => " +lemmas);
						currPhraseClosed = true;
						break;
					} else {
						pOSs.add(tags[j]);
						lemmas.add(toks[j]);
					}
				}
				if (!currPhraseClosed){
					nounPhr.add(new ParseTreeChunk("NP", lemmas, pOSs));
					logger.info(i + " => " + lemmas);
				}

			} else if (bi_POS.startsWith("B-PP")){// beginning of a phrase
				List<String> pOSs = new ArrayList<String>(), lemmas = new ArrayList<String>();
				pOSs.add(tags[i]);
				lemmas.add(toks[i]);

				for(int j=i+1; j<res.length; j++){
					if (res[j].startsWith("B-VP")){
						prepPhr.add(new ParseTreeChunk("PP", lemmas, pOSs));
						logger.info(i + " => " + lemmas);
						currPhraseClosed = true;
						break;
					} else {
						pOSs.add(tags[j]);
						lemmas.add(toks[j]);
					}
				}
				if (!currPhraseClosed){
					prepPhr.add(new ParseTreeChunk("PP", lemmas, pOSs));
					logger.info(i + " => " + lemmas);
				}
			} else
				if (bi_POS.startsWith("B-VP")){// beginning of a phrase
					List<String> pOSs = new ArrayList<String>(), lemmas = new ArrayList<String>();
					pOSs.add(tags[i]);
					lemmas.add(toks[i]);

					for(int j=i+1; j<res.length; j++){
						if (res[j].startsWith("B-VP")){
							verbPhr.add(new ParseTreeChunk("VP", lemmas, pOSs));
							logger.info(i + " => " +lemmas);
							currPhraseClosed = true;
							break;
						} else {
							pOSs.add(tags[j]);
							lemmas.add(toks[j]);
						}
					}
					if (!currPhraseClosed){
						verbPhr.add(new ParseTreeChunk("VP", lemmas, pOSs));
						logger.info(i + " => " + lemmas);
					}
				} else
					if (bi_POS.startsWith("B-ADJP") ){// beginning of a phrase
						List<String> pOSs = new ArrayList<String>(), lemmas = new ArrayList<String>();
						pOSs.add(tags[i]);
						lemmas.add(toks[i]);

						for(int j=i+1; j<res.length; j++){
							if (res[j].startsWith("B-VP")){
								adjPhr.add(new ParseTreeChunk("ADJP", lemmas, pOSs));
								logger.info(i + " => " +lemmas);
								currPhraseClosed = true;
								break;
							} else {
								pOSs.add(tags[j]);
								lemmas.add(toks[j]);
							}
						}
						if (!currPhraseClosed){
							adjPhr.add(new ParseTreeChunk("ADJP", lemmas, pOSs));
							logger.info(i + " => " + lemmas);
						}
					}
		}
		listOfChunks.add(nounPhr);
		listOfChunks.add(verbPhr);
		listOfChunks.add(prepPhr);
		listOfChunks.add(adjPhr);
		listOfChunks.add(wholeSentence);

		return listOfChunks;
	}

	public static List<List<SentenceNode>> textToSentenceNodes(
			List<List<Parse>> textParses) {
		if (textParses == null || textParses.size() == 0)
			return null;

		List<List<SentenceNode>> textNodes = new ArrayList<List<SentenceNode>>(
				textParses.size());
		for (List<Parse> paragraphParses : textParses) {
			List<SentenceNode> paragraphNodes = paragraphToSentenceNodes(paragraphParses);

			// append paragraph node if any
			if (paragraphNodes != null && paragraphNodes.size() > 0)
				textNodes.add(paragraphNodes);
		}

		if (textNodes.size() > 0)
			return textNodes;
		else
			return null;
	}

	public static List<SentenceNode> paragraphToSentenceNodes(
			List<Parse> paragraphParses) {
		if (paragraphParses == null || paragraphParses.size() == 0)
			return null;

		List<SentenceNode> paragraphNodes = new ArrayList<SentenceNode>(
				paragraphParses.size());
		for (Parse sentenceParse : paragraphParses) {
			SentenceNode sentenceNode = null;
			try {
				sentenceNode = sentenceToSentenceNode(sentenceParse);
			} catch (Exception e) {
				// don't fail the whole paragraph when a single sentence fails
				System.err.println("Faile to convert sentence to node. error: " + e);
				sentenceNode = null;
			}

			if (sentenceNode != null)
				paragraphNodes.add(sentenceNode);
		}

		if (paragraphNodes.size() > 0)
			return paragraphNodes;
		else
			return null;
	}

	public static SentenceNode sentenceToSentenceNode(Parse sentenceParse) {
		if (sentenceParse == null)
			return null;

		// convert the OpenNLP Parse to our own tree nodes
		SyntacticTreeNode node = toSyntacticTreeNode(sentenceParse);
		if ((node == null) || !(node instanceof SentenceNode))
			return null;

		SentenceNode sentenceNode = (SentenceNode) node;

		// fix the parsing tree
		fixParsingTree(sentenceNode);

		return sentenceNode;
	}

	public List<List<SentenceNode>> parseTextNode(String text) {
		List<List<Parse>> textParseList = parseTextNlp(text);
		return textToSentenceNodes(textParseList);
	}

	public List<SentenceNode> parseParagraphNode(String paragraph) {
		List<Parse> paragraphParseList = parseParagraphNlp(paragraph);
		return paragraphToSentenceNodes(paragraphParseList);
	}

	public SentenceNode parseSentenceNode(String sentence) {
		return parseSentenceNode(sentence, true);
	}

	public synchronized SentenceNode parseSentenceNode(String sentence,
			boolean normalizeText) {
		Parse sentenceParse = parseSentenceNlp(sentence, normalizeText);
		return sentenceToSentenceNode(sentenceParse);
	}

	public String[] splitParagraph(String text) {
		String[] res = text.split("\n");
		if (res == null || res.length<=1)
			return new String[] {text};
		else 
			return res;

	}

	public String[] splitSentences(String text) {
		if (text == null)
			return null;

		return sentenceDetector.sentDetect(text);
	}

	public String[] tokenizeSentence(String sentence) {
		if (sentence == null)
			return null;

		return tokenizer.tokenize(sentence);
	}

	private void initializeSentenceDetector() {
		InputStream is = null;
		try {
			is = new FileInputStream(
					MODEL_DIR + "/en-sent.bin"

			);
			SentenceModel model = new SentenceModel(is);
			sentenceDetector = new SentenceDetectorME(model);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void initializeTokenizer() {
		InputStream is = null;
		try {
			is = new FileInputStream(
					MODEL_DIR+ "/en-token.bin"
			);
			TokenizerModel model = new TokenizerModel(is);
			tokenizer = new TokenizerME(model);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void initializePosTagger() {
		InputStream is = null;
		try {
			is = new FileInputStream(MODEL_DIR
					+ "/en-pos-maxent.bin");
			POSModel model = new POSModel(is);
			posTagger = new POSTaggerME(model);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void initializeParser() {
		InputStream is = null;
		try {
			is = new FileInputStream(MODEL_DIR
					+ "/en-parser-chunking.bin");
			ParserModel model = new ParserModel(is);
			parser = ParserFactory.create(model);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void initializeChunker() {
		InputStream is = null;
		try {
			is = new FileInputStream(MODEL_DIR
					+ "/en-chunker.bin");
			ChunkerModel model = new ChunkerModel(is);
			chunker = new ChunkerME(model);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * convert an instance of Parse to SyntacticTreeNode, by filtering out the
	 * unnecessary data and assigning the word for each node
	 * 
	 * @param parse
	 */
	private static SyntacticTreeNode toSyntacticTreeNode(Parse parse) {
		if (parse == null)
			return null;

		// check for junk types
		String type = parse.getType();
		if (SyntacticTreeNode.isJunkType(type))
			return null;

		String text = parse.getText();
		ArrayList<SyntacticTreeNode> childrenNodeList = convertChildrenNodes(parse);

		// check sentence node, the node contained in the top node
		if (type.equals(AbstractBottomUpParser.TOP_NODE)
				&& childrenNodeList != null && childrenNodeList.size() > 0) {
			PhraseNode rootNode = (PhraseNode) childrenNodeList.get(0);
			return new SentenceNode(text, rootNode.getChildren());
		}

		// if this node contains children nodes, then it is a phrase node
		if (childrenNodeList != null && childrenNodeList.size() > 0) {
			return new PhraseNode(type, childrenNodeList);
		}

		// otherwise, it is a word node
		Span span = parse.getSpan();
		String word = text.substring(span.getStart(), span.getEnd()).trim();

		return new WordNode(type, word);
	}

	private static ArrayList<SyntacticTreeNode> convertChildrenNodes(Parse parse) {
		if (parse == null)
			return null;

		Parse[] children = parse.getChildren();
		if (children == null || children.length == 0)
			return null;

		ArrayList<SyntacticTreeNode> childrenNodeList = new ArrayList<SyntacticTreeNode>();
		for (Parse child : children) {
			SyntacticTreeNode childNode = toSyntacticTreeNode(child);
			if (childNode != null)
				childrenNodeList.add(childNode);
		}

		return childrenNodeList;
	}

	private static void fixParsingTree(SentenceNode sentenceNode) {
		// logger.finest("before = " + sentenceNode);
		//	for (ParsingTreeFixer fixer : FIXER_LIST) {
		//		fixer.fix(sentenceNode);
		//	}
		// logger.finest("after = " + sentenceNode);
	}

	public SentencePairMatchResult assessRelevance(String para1, String para2)
	{
		ParserChunker2MatcherProcessor parser = ParserChunker2MatcherProcessor.getInstance();
		List<List<ParseTreeChunk>> sent1GrpLst = parser.formGroupedPhrasesFromChunksForPara(para1), 
		sent2GrpLst = parser.formGroupedPhrasesFromChunksForPara(para2);

		List<LemmaPair> origChunks1 = listListParseTreeChunk2ListLemmaPairs(sent1GrpLst); //TODO  need to populate it!


		ParseTreeMatcherDeterministic md = new ParseTreeMatcherDeterministic();
		List<List<ParseTreeChunk>> res = md.matchTwoSentencesGroupedChunksDeterministic(sent1GrpLst, sent2GrpLst);
		return new SentencePairMatchResult(res, origChunks1);

	}
	private List<LemmaPair> listListParseTreeChunk2ListLemmaPairs(
			List<List<ParseTreeChunk>> sent1GrpLst) {
		List<ParseTreeChunk> wholeSentence = sent1GrpLst.get(sent1GrpLst.size()-1); // whole sentence is last list in the list of lists
		List<LemmaPair>  results = new ArrayList<LemmaPair>();
		List<String> pOSs = wholeSentence.get(0).getPOSs();
		List<String> lemmas = wholeSentence.get(0).getLemmas();
		for(int i= 0; i< lemmas.size(); i++){
			results.add(new LemmaPair( pOSs.get(i), lemmas.get(i), i  ));
		}

		return results;
	}

	public static void main(String[] args) throws Exception {



		/*
		 * String text =
		 * "I have been driving a 96 accord to death for 10 years.  " +
		 * "Lately it has been costing to much in repairs.  " +
		 * "I am looking for something 8,000-13,000.  " +
		 * "My last three vehicles have been Accords.  " +
		 * "I like them but I would like something different this time.";
		 */
		/*
		 * String text = "I love Fresh body styling. " + "I love lots of grip. "
		 * + "I love strong engine and grippy tires. " + "I like Head turner. "
		 * + "I like Right and left rearward blind spots. " +
		 * "I like Great acceleration. " + "I like great noise. " +
		 * "I like great brakes. " + "I like cheap feeling interior. " +
		 * "I like uncomfortable seats. " + "I like nav system hard to read.";
		 */
		// String sentence = "I love Fresh body styling";
		// String phrase = "I captures way more detail in high contrast scenes";
		String phrase1 = "Its classy design and the Mercedes name make it a very cool vehicle to drive. "
			+ "The engine makes it a powerful car. "
			+ "The strong engine gives it enough power. "
			+ "The strong engine gives the car a lot of power.";
		String phrase2 = "This car has a great engine. "
			+ "This car has an amazingly good engine. "
			+ "This car provides you a very good mileage.";
		String sentence = "Not to worry with the 2cv.";
		ParserChunker2MatcherProcessor parser = ParserChunker2MatcherProcessor.getInstance();

		System.out.println(parser.assessRelevance(phrase1, phrase2));


		parser.formGroupedPhrasesFromChunksForSentence("Its classy design and the Mercedes name make it a very cool vehicle to drive. ");
		parser.formGroupedPhrasesFromChunksForSentence("Sounds too good to be true but it actually is, the world's first flying car is finally here. ");
		parser.formGroupedPhrasesFromChunksForSentence("UN Ambassador Ron Prosor repeated the Israeli position that the only way the Palestinians will get UN membership and statehood is through direct negotiations with the Israelis on a comprehensive peace agreement");

		List<List<SentenceNode>> nodeListList = parser.parseTextNode(phrase1);
		for (List<SentenceNode> nodeList : nodeListList) {
			for (SentenceNode node : nodeList) {
				System.out.println(node);
			}
		}
	}
}
