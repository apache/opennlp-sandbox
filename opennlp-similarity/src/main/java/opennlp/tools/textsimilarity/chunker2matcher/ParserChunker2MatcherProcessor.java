
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

package opennlp.tools.textsimilarity.chunker2matcher;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import opennlp.tools.postag.ThreadSafePOSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.ThreadSafeSentenceDetectorME;
import opennlp.tools.textsimilarity.LemmaPair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserChunker2MatcherProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  static final int MIN_SENTENCE_LENGTH = 10;
  protected static ParserChunker2MatcherProcessor instance;

  private SentenceDetector sentenceDetector;
  private Tokenizer tokenizer;
  private POSTagger posTagger;
  private Parser parser;
  private ChunkerME chunker;
  private static final int NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS = 5;
  private Map<String, String[][]> sentence_parseObject;

  @SuppressWarnings("unchecked")
  protected ParserChunker2MatcherProcessor() {
    try {
      sentence_parseObject = (Map<String, String[][]>) ParserCacheSerializer.readObject();
    } catch (Exception e) {
      // this file might not exist initially
      LOG.warn("parsing cache file does not exist (but should be created)");
      sentence_parseObject = new HashMap<>();
    }

    try {
      initializeSentenceDetector();
      initializeTokenizer();
      initializePosTagger();
      initializeParser();
      initializeChunker();
    } catch (IOException e) {
      LOG.warn("A model can't be loaded: {}", e.getMessage());
    }
  }

  protected void initializeSentenceDetector() throws IOException {
    SentenceModel model = DownloadUtil.downloadModel(
            "en", DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class);
    sentenceDetector = new ThreadSafeSentenceDetectorME(model);
  }

  protected void initializeTokenizer() throws IOException {
    TokenizerModel model = DownloadUtil.downloadModel(
            "en", DownloadUtil.ModelType.TOKENIZER, TokenizerModel.class);
    tokenizer = new ThreadSafeTokenizerME(model);
  }

  protected void initializePosTagger() throws IOException {
    POSModel model = DownloadUtil.downloadModel(
            "en", DownloadUtil.ModelType.POS, POSModel.class);
    posTagger = new ThreadSafePOSTaggerME(model);
  }

  protected void initializeParser() throws IOException {
    ParserModel model = DownloadUtil.downloadModel(
            "en", DownloadUtil.ModelType.PARSER, ParserModel.class);
    parser = ParserFactory.create(model);
  }

  private void initializeChunker() throws IOException {
    ChunkerModel model = DownloadUtil.downloadModel(
            "en", DownloadUtil.ModelType.CHUNKER, ChunkerModel.class);
    chunker = new ChunkerME(model);
  }

  public SentenceDetector getSentenceDetector() {
    return sentenceDetector;
  }

  public Tokenizer getTokenizer() {
    return tokenizer;
  }

  public POSTagger getPOSTagger() {
    return posTagger;
  }

  public ChunkerME getChunker() {
    return chunker;
  }

  // closing the processor and serializing parsing cache
  public void close() {
    instance = null;
    ParserCacheSerializer.writeObject(sentence_parseObject);
  }

  /**
   * singleton method of instantiating the processor
   * 
   * @return the instance
   */
  public synchronized static ParserChunker2MatcherProcessor getInstance() {
    if (instance == null)
      instance = new ParserChunker2MatcherProcessor();

    return instance;
  }

  /**
   * General parsing function, which returns lists of parses for a portion of
   * text
   * 
   * @param text
   *          to be parsed
   * @return lists of parses
   */
  public List<List<Parse>> parseTextNlp(String text) {
    if (text == null || text.trim().isEmpty())
      return null;

    List<List<Parse>> textParses = new ArrayList<>(1);

    // parse paragraph by paragraph
    String[] paragraphList = splitParagraph(text);
    for (String paragraph : paragraphList) {
      if (paragraph.isEmpty())
        continue;

      List<Parse> paragraphParses = parseParagraphNlp(paragraph);
      if (paragraphParses != null)
        textParses.add(paragraphParses);
    }

    return textParses;
  }

  public List<Parse> parseParagraphNlp(String paragraph) {
    if (paragraph == null || paragraph.trim().isEmpty())
      return null;

    // normalize the text before parsing, otherwise, the sentences may not
    // be
    // separated correctly

    // parse sentence by sentence
    String[] sentences = splitSentences(paragraph);
    List<Parse> parseList = new ArrayList<>(sentences.length);
    for (String sentence : sentences) {
      sentence = sentence.trim();
      if (sentence.isEmpty())
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

  public synchronized Parse parseSentenceNlp(String sentence, boolean normalizeText) {
    // don't try to parse very short sentence, not much info in it anyway,
    // most likely a heading
    if (sentence == null || sentence.trim().length() < MIN_SENTENCE_LENGTH)
      return null;

    Parse[] parseArray;
    try {
      parseArray = ParserTool.parseLine(sentence, parser, 1);
    } catch (Throwable t) {
      LOG.warn("failed to parse the sentence : '{}'", sentence);
      return null;
    }
    // there should be only one result parse
    if (parseArray != null && parseArray.length > 0)
      return parseArray[0];
    else
      return null;
  }

  /**
   * 
   * @param para
   *          input text string which is assumed to be a paragraph and is split
   *          into sentences
   * @return a list of lists of phrases with their POS tags for each phrase type
   *         (noun, verb etc.)
   */

  public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForPara(String para) {
    List<List<ParseTreeChunk>> listOfChunksAccum = new ArrayList<>();
    String[] sentences = splitSentences(para);
    for (String sent : sentences) {
      List<List<ParseTreeChunk>> singleSentChunks = formGroupedPhrasesFromChunksForSentence(sent);
      if (singleSentChunks == null)
        continue;
      if (listOfChunksAccum.isEmpty()) {
        listOfChunksAccum = new ArrayList<>(singleSentChunks);
      } else
        for (int i = 0; i < NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS; i++) {
          // make sure not null
          if (singleSentChunks == null
              || singleSentChunks.size() != NUMBER_OF_SECTIONS_IN_SENTENCE_CHUNKS)
            break;
          List<ParseTreeChunk> phraseI = singleSentChunks.get(i);
          List<ParseTreeChunk> phraseIaccum = listOfChunksAccum.get(i);
          phraseIaccum.addAll(phraseI);
          listOfChunksAccum.set(i, phraseIaccum);
        }
    }
    return listOfChunksAccum;
  }

  String[][] parseChunkSentence(String sentenceInp) {
    String[][] resToksTags = sentence_parseObject.get(sentenceInp);
    if (resToksTags != null)
      return resToksTags;
    if (tokenizer == null)
      return null;

    String sentence = TextProcessor.removePunctuation(sentenceInp);

    String[] toks = tokenizer.tokenize(sentence);
    String[] tags; // posTagger.tag(toks);
    SentenceNode node = parseSentenceNode(sentence);
    if (node == null) {
      LOG.warn("Problem parsing sentence '{}'", sentence);
      return null;
    }
    List<String> POSlist = node.getOrderedPOSList();

    tags = POSlist.toArray(new String[0]);
    if (toks.length != tags.length) {
      LOG.trace("disagreement between toks and tags; sent =  '{}'\n" +
              "{} \n will now try this sentence in lower case", sentence, tags);
      node = parseSentenceNode(sentence.toLowerCase());
      if (node == null) {
        LOG.warn("Problem parsing sentence '{}'", sentence);
        return null;
      }
      POSlist = node.getOrderedPOSList();
      tags = POSlist.toArray(new String[0]);
      if (toks.length != tags.length) {
        LOG.trace("AGAIN: disagreement between toks and tags for lower case!");
        if (toks.length > tags.length) {
          String[] newToks = new String[tags.length];
          for (int i = 0; i < tags.length; i++) {
            newToks[i] = toks[i];
          }
          toks = newToks;

        } else
          return null;
      }
    }

    String[] res = chunker.chunk(toks, tags);
    String[][] resTagToks = new String[][] { res, tags, toks };
    sentence_parseObject.put(sentenceInp, resTagToks);
    return resTagToks;
  }

  /**
   * @param sentence The input text string which is assumed to be a sentence
   * @return a list of lists of phrases with their POS tags for each phrase type
   *         (noun, verb etc.)
   */
  public synchronized List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForSentence(
      String sentence) {
    if (sentence == null || sentence.trim().length() < MIN_SENTENCE_LENGTH)
      return null;
    /*
     * sentence = TextProcessor.removePunctuation(sentence);
     * 
     * String[] toks = tokenizer.tokenize(sentence); String[] tags = new
     * String[toks.length]; //posTagger.tag(toks); SentenceNode node =
     * parseSentenceNode(sentence); if (node==null){
     * LOG.info("Problem parsing sentence '"+sentence); return null; }
     * List<String> POSlist = node.getOrderedPOSList();
     * 
     * tags = POSlist.toArray(new String[0]); if (toks.length != tags.length){
     * LOG.info("disagreement between toks and tags; sent =  '"+sentence +
     * "'\n tags = "+tags + "\n will now try this sentence in lower case" );
     * node = parseSentenceNode(sentence.toLowerCase()); if (node==null){
     * LOG.info("Problem parsing sentence '"+sentence); return null; } POSlist =
     * node.getOrderedPOSList(); tags = POSlist.toArray(new String[0]); if
     * (toks.length != tags.length){
     * LOG.info("AGAIN: disagreement between toks and tags for lower case! ");
     * if (toks.length>tags.length){ String[] newToks = new String[tags.length];
     * for(int i = 0; i<tags.length; i++ ){ newToks[i] = toks[i]; } toks =
     * newToks;
     * 
     * } else return null; } }
     */
    String[][] resTagToks = parseChunkSentence(sentence);
    if (resTagToks == null)
      return null;
    String[] res = resTagToks[0];
    String[] tags = resTagToks[1];
    String[] toks = resTagToks[2];

    // String[] res = chunker.chunk(toks, tags);

    List<List<ParseTreeChunk>> listOfChunks = new ArrayList<>();
    List<ParseTreeChunk> nounPhr = new ArrayList<>(), prepPhr = new ArrayList<>(), verbPhr = new ArrayList<>(), adjPhr = new ArrayList<>(),
    // to store the whole sentence
    wholeSentence = new ArrayList<>();
    List<String> pOSsAll = new ArrayList<>(), lemmasAll = new ArrayList<>();

    for (int i = 0; i < toks.length; i++) {
      pOSsAll.add(tags[i]);
      lemmasAll.add(toks[i]);
    }
    wholeSentence.add(new ParseTreeChunk("SENTENCE", lemmasAll, pOSsAll));

    boolean currPhraseClosed;
    for (int i = 0; i < res.length; i++) {
      String bi_POS = res[i];
      currPhraseClosed = false;
      if (bi_POS.startsWith("B-NP")) {// beginning of a phrase

        List<String> pOSs = new ArrayList<>(), lemmas = new ArrayList<>();
        pOSs.add(tags[i]);
        lemmas.add(toks[i]);
        for (int j = i + 1; j < res.length; j++) {
          if (res[j].startsWith("B-VP")) {
            nounPhr.add(new ParseTreeChunk("NP", lemmas, pOSs));
            // LOG.info(i + " => " +lemmas);
            currPhraseClosed = true;
            break;
          } else {
            pOSs.add(tags[j]);
            lemmas.add(toks[j]);
          }
        }
        if (!currPhraseClosed) {
          nounPhr.add(new ParseTreeChunk("NP", lemmas, pOSs));
          // LOG.fine(i + " => " + lemmas);
        }

      } else if (bi_POS.startsWith("B-PP")) {// beginning of a phrase
        List<String> pOSs = new ArrayList<>(), lemmas = new ArrayList<>();
        pOSs.add(tags[i]);
        lemmas.add(toks[i]);

        for (int j = i + 1; j < res.length; j++) {
          if (res[j].startsWith("B-VP")) {
            prepPhr.add(new ParseTreeChunk("PP", lemmas, pOSs));
            // LOG.fine(i + " => " + lemmas);
            currPhraseClosed = true;
            break;
          } else {
            pOSs.add(tags[j]);
            lemmas.add(toks[j]);
          }
        }
        if (!currPhraseClosed) {
          prepPhr.add(new ParseTreeChunk("PP", lemmas, pOSs));
          // LOG.fine(i + " => " + lemmas);
        }
      } else if (bi_POS.startsWith("B-VP")) {// beginning of a phrase
        List<String> pOSs = new ArrayList<>(), lemmas = new ArrayList<>();
        pOSs.add(tags[i]);
        lemmas.add(toks[i]);

        for (int j = i + 1; j < res.length; j++) {
          if (res[j].startsWith("B-VP")) {
            verbPhr.add(new ParseTreeChunk("VP", lemmas, pOSs));
            // LOG.fine(i + " => " +lemmas);
            currPhraseClosed = true;
            break;
          } else {
            pOSs.add(tags[j]);
            lemmas.add(toks[j]);
          }
        }
        if (!currPhraseClosed) {
          verbPhr.add(new ParseTreeChunk("VP", lemmas, pOSs));
          // LOG.fine(i + " => " + lemmas);
        }
      } else if (bi_POS.startsWith("B-ADJP")) {// beginning of a phrase
        List<String> pOSs = new ArrayList<>(), lemmas = new ArrayList<>();
        pOSs.add(tags[i]);
        lemmas.add(toks[i]);

        for (int j = i + 1; j < res.length; j++) {
          if (res[j].startsWith("B-VP")) {
            adjPhr.add(new ParseTreeChunk("ADJP", lemmas, pOSs));
            // LOG.fine(i + " => " +lemmas);
            currPhraseClosed = true;
            break;
          } else {
            pOSs.add(tags[j]);
            lemmas.add(toks[j]);
          }
        }
        if (!currPhraseClosed) {
          adjPhr.add(new ParseTreeChunk("ADJP", lemmas, pOSs));
          // LOG.fine(i + " => " + lemmas);
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
    if (textParses == null || textParses.isEmpty())
      return null;

    List<List<SentenceNode>> textNodes = new ArrayList<>(
            textParses.size());
    for (List<Parse> paragraphParses : textParses) {
      List<SentenceNode> paragraphNodes = paragraphToSentenceNodes(paragraphParses);

      // append paragraph node if any
      if (paragraphNodes != null && !paragraphNodes.isEmpty())
        textNodes.add(paragraphNodes);
    }

    if (!textNodes.isEmpty())
      return textNodes;
    else
      return null;
  }

  public static List<SentenceNode> paragraphToSentenceNodes(List<Parse> paragraphParses) {
    if (paragraphParses == null || paragraphParses.isEmpty())
      return null;

    List<SentenceNode> paragraphNodes = new ArrayList<>(paragraphParses.size());
    for (Parse sentenceParse : paragraphParses) {
      SentenceNode sentenceNode;
      try {
        sentenceNode = sentenceToSentenceNode(sentenceParse);
      } catch (Exception e) {
        // don't fail the whole paragraph when a single sentence fails
        LOG.warn("Failed to convert sentence to node. error: {}", e.getLocalizedMessage());
        sentenceNode = null;
      }

      if (sentenceNode != null)
        paragraphNodes.add(sentenceNode);
    }

    if (!paragraphNodes.isEmpty())
      return paragraphNodes;
    else
      return null;
  }

  public static SentenceNode sentenceToSentenceNode(Parse sentenceParse) {
    if (sentenceParse == null)
      return null;

    // convert the OpenNLP Parse to our own tree nodes
    SyntacticTreeNode node = toSyntacticTreeNode(sentenceParse);
    if (node == null)
      return null;
    if (node instanceof SentenceNode sn)
      return sn;
    else if (node instanceof PhraseNode) {
      return new SentenceNode("sentence", node.getChildren());
    } else
      return null;
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
    if (res == null || res.length <= 1)
      return new String[] { text };
    else
      return res;

  }

  public String[] splitSentences(String text) {
    if (text == null)
      return null;
    // if (sentenceDetector!=null)
    // return sentenceDetector.sentDetect(text);
    else {
      List<String> sents = TextProcessor.splitToSentences(text);
      return sents.toArray(new String[0]);
    }
  }

  public String[] tokenizeSentence(String sentence) {
    if (sentence == null)
      return null;

    return tokenizer.tokenize(sentence);
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
    if (SyntacticTreeNode.isJunkType(type, parse))
      return null;

    String text = parse.getText();
    List<SyntacticTreeNode> childrenNodeList = convertChildrenNodes(parse);

    // check sentence node, the node contained in the top node
    if (type.equals(AbstractBottomUpParser.TOP_NODE)
        && childrenNodeList != null && !childrenNodeList.isEmpty()) {
      PhraseNode rootNode;
    try {
      rootNode = (PhraseNode) childrenNodeList.get(0);
    } catch (Exception e) {
      return null;
    }
      return new SentenceNode(text, rootNode.getChildren());
    }

    // if this node contains children nodes, then it is a phrase node
    if (childrenNodeList != null && !childrenNodeList.isEmpty()) {
      // System.out.println("Found "+ type + " phrase = "+ childrenNodeList);
      return new PhraseNode(type, childrenNodeList);

    }

    // otherwise, it is a word node
    Span span = parse.getSpan();
    String word = text.substring(span.getStart(), span.getEnd()).trim();

    return new WordNode(type, word);
  }

  private static List<SyntacticTreeNode> convertChildrenNodes(Parse parse) {
    if (parse == null)
      return null;

    Parse[] children = parse.getChildren();
    if (children == null || children.length == 0)
      return null;

    List<SyntacticTreeNode> childrenNodeList = new ArrayList<>();
    for (Parse child : children) {
      SyntacticTreeNode childNode = toSyntacticTreeNode(child);
      if (childNode != null)
        childrenNodeList.add(childNode);
    }

    return childrenNodeList;
  }

  /**
   * The key function of similarity component which takes two portions of text
   * and does similarity assessment by finding the set of all maximum common
   * subtrees of the set of parse trees for each portion of text
   * 
   * @param para1 text 1
   * @param para2 text 2
   * @return the matching results structure, which includes the similarity score
   */
  public SentencePairMatchResult assessRelevance(String para1, String para2) {
    List<List<ParseTreeChunk>> sent1GrpLst = formGroupedPhrasesFromChunksForPara(para1), sent2GrpLst = formGroupedPhrasesFromChunksForPara(para2);

    List<LemmaPair> origChunks1 = listListParseTreeChunk2ListLemmaPairs(sent1GrpLst);

    ParseTreeMatcherDeterministic md = new ParseTreeMatcherDeterministic();
    List<List<ParseTreeChunk>> res = md
        .matchTwoSentencesGroupedChunksDeterministic(sent1GrpLst, sent2GrpLst);
    return new SentencePairMatchResult(res, origChunks1);

  }

  protected List<LemmaPair> listListParseTreeChunk2ListLemmaPairs(
      List<List<ParseTreeChunk>> sent1GrpLst) {
    List<LemmaPair> results = new ArrayList<>();
    if (sent1GrpLst == null || sent1GrpLst.isEmpty())
      return results;
    List<ParseTreeChunk> wholeSentence = sent1GrpLst
        .get(sent1GrpLst.size() - 1); // whole sentence is last list in the list
                                      // of lists

    List<String> pOSs = wholeSentence.get(0).getPOSs();
    List<String> lemmas = wholeSentence.get(0).getLemmas();
    for (int i = 0; i < lemmas.size(); i++) {
      results.add(new LemmaPair(pOSs.get(i), lemmas.get(i), i));
    }

    return results;
  }

  public void printParseTree(String phrase1) {
    ParserChunker2MatcherProcessor p = ParserChunker2MatcherProcessor.getInstance();
    List<List<SentenceNode>> nodeListList = p.parseTextNode(phrase1);
    for (List<SentenceNode> nodeList : nodeListList) {
      for (SentenceNode node : nodeList) {
        System.out.println(node);
      }
    }
  }
}
