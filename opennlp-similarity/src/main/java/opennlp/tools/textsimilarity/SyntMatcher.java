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

package opennlp.tools.textsimilarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.lang.english.SentenceDetector;
import opennlp.tools.lang.english.Tokenizer;
import opennlp.tools.lang.english.TreebankParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.util.Span;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class SyntMatcher {
  public static final String resourcesDir = (System.getProperty("os.name")
      .toLowerCase().indexOf("win") > -1 ? "C:/workspace/ZSearch/resources_external"
      : "/var/search/solr-1.2/resources");
  static private SyntMatcher m_SyntMatcher = null;

  private static final Logger LOG = LoggerFactory.getLogger(SyntMatcher.class);

  private SentenceDetectorME sentenceDetectorME = null;

  private Tokenizer tokenizer = null;

  private Parser parser = null;

  private final boolean useTagDict = true;

  private final boolean useCaseInsensitiveTagDict = false;

  private final int beamSize = Parser.defaultBeamSize;

  private final double advancePercentage = Parser.defaultAdvancePercentage;

  private Map<String, List<List<ParseTreeChunk>>> parsingsCache = new HashMap<String, List<List<ParseTreeChunk>>>();

  private ParseTreeChunkListScorer parseTreeChunkListScorer;

  private ParseTreeMatcherDeterministic parseTreeMatcherDeterministic = new ParseTreeMatcherDeterministic();

  /**
   * Get the StopList singleton instance.
   * 
   * @return The StopList
   */
  static public SyntMatcher getInstance() {
    String dir = resourcesDir + "/models";
    if (m_SyntMatcher == null) {
      m_SyntMatcher = new SyntMatcher();

      try {
        m_SyntMatcher.loadOpenNLP(dir);
      } catch (Exception e) {
        LOG.error("Problem loading openNLP! ", 2);
      }
    }
    return m_SyntMatcher;
  }

  static public SyntMatcher getInstance(String resourceDirSpec) {
    String dir = resourceDirSpec + "/models";
    if (m_SyntMatcher == null) {
      m_SyntMatcher = new SyntMatcher();

      try {
        m_SyntMatcher.loadOpenNLP(dir);
      } catch (Exception e) {
        e.printStackTrace();
        LOG.error("Problem loading openNLP! ", e);
      }
    }
    return m_SyntMatcher;
  }

  public SyntMatcher() {
    /*
     * try { loadOpenNLP(resourcesDir); } catch (IOException e) {
     * LOG.error("Problem loading openNLP! ", e); }
     */
  }

  public SyntMatcher(String resourcesDir) {
    try {
      loadOpenNLP(resourcesDir);
    } catch (IOException e) {
      LOG.error("Problem loading openNLP! ", e);
    }
  }

  public SyntMatcher(String resourcesDir, String language) {
    try {
      loadOpenNLP(resourcesDir, language);
    } catch (IOException e) {
      LOG.error("Problem loading openNLP! ", e);
    }
  }

  protected void loadOpenNLP(String dir) throws IOException {
    sentenceDetectorME = new SentenceDetector(dir
        + "/sentdetect/EnglishSD.bin.gz");
    tokenizer = new Tokenizer(dir + "/tokenize/EnglishTok.bin.gz");
    parser = (Parser) TreebankParser.getParser(dir + "/parser", useTagDict,
        useCaseInsensitiveTagDict, beamSize, advancePercentage);

  }

  protected void loadOpenNLP(String dir, String lang) throws IOException {
    if (lang.equalsIgnoreCase("es")) {
      sentenceDetectorME = new SentenceDetector(dir
          + "/sentdetect/EnglishSD.bin.gz");
      tokenizer = new Tokenizer(dir + "/tokenize/EnglishTok.bin.gz");
      parser = (Parser) TreebankParser.getParser(dir + "/parser", useTagDict,
          useCaseInsensitiveTagDict, beamSize, advancePercentage);
    }
  }

  // TODO is synchronized needed here?
  public synchronized Parse[] parseLine(String line, Parser p, double confidence) {
    String[] tokens = tokenizer.tokenize(line);
    // tokens = TextProcessor.fastTokenize(line, false).toArray(new String[0]);

    StringBuilder sb = new StringBuilder();
    for (String t : tokens)
      sb.append(t).append(" ");

    Parse[] ps = null;
    try {
      ps = TreebankParser.parseLine(sb.toString(), parser, 2);
    } catch (Exception e) {
      System.out.println("Problem parsing " + sb.toString());
      e.printStackTrace(); // unable to parse for whatever reason
    }
    int i = 1;
    for (; i < ps.length; i++) {
      if (ps[i - 1].getProb() - ps[i].getProb() > confidence)
        break;
    }
    if (i < ps.length) {
      Parse[] retp = new Parse[i];
      for (int j = 0; j < i; j++)
        retp[j] = ps[j];
      return retp;
    } else
      return ps;
  }

  // TODO is synchronized needed here?
  protected synchronized Double[] getPhrasingAcceptabilityData(String line) {
    int nParsings = 5;
    String[] tokens = tokenizer.tokenize(line);
    int numWords = tokens.length;
    StringBuilder sb = new StringBuilder();
    for (String t : tokens)
      sb.append(t).append(" ");
    Double result[] = new Double[5];

    Parse[] ps = null;
    try {
      ps = TreebankParser.parseLine(sb.toString(), parser, nParsings);
    } catch (Exception e) {
      // unable to parse for whatever reason
      for (int i = 0; i < result.length; i++) {
        result[i] = -20.0;
      }
    }

    for (int i = 0; i < ps.length; i++) {
      result[i] = Math.abs(ps[i].getProb() / (double) numWords);
    }
    return result;
  }

  protected boolean allChildNodesArePOSTags(Parse p) {
    Parse[] subParses = p.getChildren();
    for (int pi = 0; pi < subParses.length; pi++)
      if (!((Parse) subParses[pi]).isPosTag())
        return false;
    return true;
  }

  protected ArrayList<String> getNounPhrases(Parse p) {
    ArrayList<String> nounphrases = new ArrayList<String>();

    Parse[] subparses = p.getChildren();
    for (int pi = 0; pi < subparses.length; pi++) {
      // System.out.println("Processing Label: " + subparses[pi].getLabel());
      // System.out.println("Processing Type: " + subparses[pi].getType());
      if (subparses[pi].getType().equals("NP")
          && allChildNodesArePOSTags(subparses[pi]))// &&
      // ((Parse)subparses[pi]).getLabel()
      // == "NP")
      {
        // System.out.println("Processing: " + subparses[pi].getLabel() +
        // " as Chunk...");
        Span _span = subparses[pi].getSpan();
        nounphrases
            .add(p.getText().substring(_span.getStart(), _span.getEnd()));
      } else if (!((Parse) subparses[pi]).isPosTag())
        nounphrases.addAll(getNounPhrases(subparses[pi]));
    }

    return nounphrases;
  }

  public List<LemmaPair> getAllPhrasesTWPairs(Parse p) {
    List<String> nounphrases = new ArrayList<String>();
    List<LemmaPair> LemmaPairs = new ArrayList<LemmaPair>();

    Parse[] subparses = p.getChildren();
    for (int pi = 0; pi < subparses.length; pi++) {
      Span _span = subparses[pi].getSpan();

      nounphrases.add(p.getText().substring(_span.getStart(), _span.getEnd()));
      String expr = p.getText().substring(_span.getStart(), _span.getEnd());

      // if (expr.indexOf(" ")>0)
      LemmaPairs.add(new LemmaPair(subparses[pi].getType(), expr, _span
          .getStart()));
      if (!((Parse) subparses[pi]).isPosTag())
        LemmaPairs.addAll(getAllPhrasesTWPairs(subparses[pi]));
    }

    return LemmaPairs;
  }

  protected List<List<ParseTreeChunk>> matchOrigSentences(String sent1,
      String sent2) {
    // with tokenizer now
    Parse[] parses1 = parseLine(sent1, parser, 1);
    Parse[] parses2 = parseLine(sent2, parser, 1);
    List<LemmaPair> origChunks1 = getAllPhrasesTWPairs(parses1[0]);
    List<LemmaPair> origChunks2 = getAllPhrasesTWPairs(parses2[0]);
    System.out.println(origChunks1);
    System.out.println(origChunks2);

    ParseTreeChunk matcher = new ParseTreeChunk();
    List<List<ParseTreeChunk>> matchResult = matcher
        .matchTwoSentencesGivenPairLists(origChunks1, origChunks2);
    return matchResult;
  }

  public List<List<ParseTreeChunk>> matchOrigSentencesCache(String sent1,
      String sent2) {
    sent1 = sent1.replace("'s", " 's").replace(":", " ");
    sent2 = sent2.replace("'s", " 's").replace(":", " ");

    ParseTreeChunk matcher = new ParseTreeChunk();
    List<List<ParseTreeChunk>> sent1GrpLst = null, sent2GrpLst = null;

    sent1GrpLst = parsingsCache.get(sent1);
    if (sent1GrpLst == null) {
      List<LemmaPair> origChunks1 = new ArrayList<LemmaPair>();
      String[] sents1 = sentenceDetectorME.sentDetect(sent1);
      for (String s1 : sents1) {
        Parse[] parses1 = parseLine(s1, parser, 1);
        origChunks1.addAll(getAllPhrasesTWPairs(parses1[0]));
      }
      List<ParseTreeChunk> chunk1List = matcher.buildChunks(origChunks1);
      sent1GrpLst = matcher.groupChunksAsParses(chunk1List);
      parsingsCache.put(sent1, sent1GrpLst);
      System.out.println(origChunks1);
      // System.out.println("=== Grouped chunks 1 "+ sent1GrpLst);
    }
    sent2GrpLst = parsingsCache.get(sent2);
    if (sent2GrpLst == null) {
      List<LemmaPair> origChunks2 = new ArrayList<LemmaPair>();
      String[] sents2 = sentenceDetectorME.sentDetect(sent2);
      for (String s2 : sents2) {
        Parse[] parses2 = parseLine(s2, parser, 1);
        origChunks2.addAll(getAllPhrasesTWPairs(parses2[0]));
      }
      List<ParseTreeChunk> chunk2List = matcher.buildChunks(origChunks2);
      sent2GrpLst = matcher.groupChunksAsParses(chunk2List);
      parsingsCache.put(sent2, sent2GrpLst);
      System.out.println(origChunks2);
      // System.out.println("=== Grouped chunks 2 "+ sent2GrpLst);
    }

    return parseTreeMatcherDeterministic
        .matchTwoSentencesGroupedChunksDeterministic(sent1GrpLst, sent2GrpLst);

  }

  public SentencePairMatchResult assessRelevance(String minedSent1, String sent2) {
    minedSent1 = minedSent1.replace("'s", " 's").replace(":", " ")
        .replace("’s", " 's");
    sent2 = sent2.replace("'s", " 's").replace(":", " ").replace("’s", " 's");

    ParseTreeChunk matcher = new ParseTreeChunk();
    List<List<ParseTreeChunk>> sent1GrpLst = null, sent2GrpLst = null;

    // sent1GrpLst = parsingsCache.get(minedSent1);
    // if (sent1GrpLst==null){
    List<LemmaPair> origChunks1 = new ArrayList<LemmaPair>();
    String[] sents1 = sentenceDetectorME.sentDetect(minedSent1);
    for (String s1 : sents1) {
      Parse[] parses1 = parseLine(s1, parser, 1);
      origChunks1.addAll(getAllPhrasesTWPairs(parses1[0]));
    }
    List<ParseTreeChunk> chunk1List = matcher.buildChunks(origChunks1);
    sent1GrpLst = matcher.groupChunksAsParses(chunk1List);
    parsingsCache.put(minedSent1, sent1GrpLst);
    // System.out.println(origChunks1);
    // System.out.println("=== Grouped chunks 1 "+ sent1GrpLst);
    // }
    sent2GrpLst = parsingsCache.get(sent2);
    if (sent2GrpLst == null) {
      List<LemmaPair> origChunks2 = new ArrayList<LemmaPair>();
      String[] sents2 = sentenceDetectorME.sentDetect(sent2);
      for (String s2 : sents2) {
        Parse[] parses2 = parseLine(s2, parser, 1);
        origChunks2.addAll(getAllPhrasesTWPairs(parses2[0]));
      }
      List<ParseTreeChunk> chunk2List = matcher.buildChunks(origChunks2);
      sent2GrpLst = matcher.groupChunksAsParses(chunk2List);
      parsingsCache.put(sent2, sent2GrpLst);
      // System.out.println(origChunks2);
      // System.out.println("=== Grouped chunks 2 "+ sent2GrpLst);
    }

    ParseTreeMatcherDeterministic md = new ParseTreeMatcherDeterministic();
    List<List<ParseTreeChunk>> res = md
        .matchTwoSentencesGroupedChunksDeterministic(sent1GrpLst, sent2GrpLst);
    return new SentencePairMatchResult(res, origChunks1);

  }

  public Map<String, List<LemmaPair>> findMappingBetweenSentencesOfAParagraphAndAClassReps(
      String para1, String classStr) {
    // profile of matches
    List<List<List<ParseTreeChunk>>> matchResultPerSentence = new ArrayList<List<List<ParseTreeChunk>>>();

    ParseTreeChunk matcher = new ParseTreeChunk();

    String[] sents = sentenceDetectorME.sentDetect(para1);
    String[] classSents = sentenceDetectorME.sentDetect(classStr);

    List<List<LemmaPair>> parseSentList = new ArrayList<List<LemmaPair>>();
    for (String s : sents) {
      parseSentList.add(getAllPhrasesTWPairs((parseLine(s, parser, 1)[0])));
    }

    List<List<LemmaPair>> parseClassList = new ArrayList<List<LemmaPair>>();
    for (String s : classSents) {
      parseClassList.add(getAllPhrasesTWPairs((parseLine(s, parser, 1)[0])));
    }

    Map<String, List<LemmaPair>> sentence_bestClassRep = new HashMap<String, List<LemmaPair>>();
    for (List<LemmaPair> chunksSent : parseSentList) {
      Double maxScore = -1.0;
      for (List<LemmaPair> chunksClass : parseClassList) {
        List<List<ParseTreeChunk>> matchResult = matcher
            .matchTwoSentencesGivenPairLists(chunksSent, chunksClass);
        Double score = parseTreeChunkListScorer
            .getParseTreeChunkListScore(matchResult);
        if (score > maxScore) {
          maxScore = score;
          sentence_bestClassRep.put(chunksSent.toString(), chunksClass);
        }
      }
    }
    return sentence_bestClassRep;
  }

  public SentenceDetectorME getSentenceDetectorME() {
    return sentenceDetectorME;
  }

  public Parser getParser() {
    return parser;
  }
}

// -Xms500M -Xmx500M
