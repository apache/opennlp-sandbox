/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization.preprocess;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.BreakIterator;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Hashtable;
import java.util.regex.Pattern;

import opennlp.summarization.Sentence;
import opennlp.summarization.DocProcessor;
import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;

/**
 * Parses a document to sentences.
 */
public class DefaultDocProcessor implements DocProcessor {

  private static final ClassPathModelProvider MODEL_PROVIDER = new DefaultClassPathModelProvider();

  private static final String REGEX = "\"|'";
  private final static Pattern REPLACEMENT_PATTERN =
          Pattern.compile("&#?[0-9a-zA-Z][0-9a-zA-Z][0-9a-zA-Z]?;");

  // Sentence fragmentation to use..
  private static final int OPEN_NLP = 1;
  private static final int SIMPLE = 2;
  private static final int SENTENCE_FRAG = OPEN_NLP;

  private final Stemmer stemmer;
  private final SentenceModel sentModel;

  /**
   * Instantiates a {@link DocProcessor} for a Sentence detection model for the specified {@code languageCode}.
   *
   * @param languageCode An ISO-language code for obtaining a {@link SentenceModel}.
   *                     Must not be {@code null} and not be blank.
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public DefaultDocProcessor(String languageCode) throws IOException {
    if (languageCode == null || languageCode.isBlank())
      throw new IllegalArgumentException("Parameter 'languageCode' must not be null or blank");
    stemmer = new PorterStemmer();
    sentModel = MODEL_PROVIDER.load(languageCode, ModelType.SENTENCE_DETECTOR, SentenceModel.class);
  }

  // Str - Document or paragraph
  // sentences - List containing returned sentences
  // iidx - if not null update with the words in the sentence + sent id
  // processedSent - Sentences after stemming and stopword removal..
  private void getSentences(String str, List<String> sentences,
                            Hashtable<String, List<Integer>> iidx, List<String> processedSent) {
    StopWords sw = StopWords.getInstance();
    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
    BreakIterator wrdItr = BreakIterator.getWordInstance(Locale.US);
    iterator.setText(str);
    int start = iterator.first();
    int sentCnt = 0;

    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      String sentence = str.substring(start, end);//str.substring(oldSentEndIdx, sentEndIdx).trim();

      // Add the sentence as-is; do any processing at the word level
      // To lower case and trim all punctuations
      sentences.add(sentence);
      wrdItr.setText(sentence);
      StringBuilder procSent = new StringBuilder();
      int wrdStrt = 0;

      for (int wrdEnd = wrdItr.next(); wrdEnd != BreakIterator.DONE;
           wrdStrt = wrdEnd, wrdEnd = wrdItr.next()) {
        String word = sentence.substring(wrdStrt, wrdEnd);//words[i].trim();
        word = word.replace(REGEX, "");

        // Skip stop words and stem the word
        if (sw.isStopWord(word)) continue;

        String stemedWrd = stemmer.stem(word).toString();

        // update iidx by adding the current sentence to the list
        if (iidx != null) {
          if (stemedWrd.length() > 1) {
            List<Integer> sentList = iidx.get(stemedWrd);
            if (sentList == null) {
              sentList = new ArrayList<>();
            }

            sentList.add(sentCnt);
            // Save it back
            iidx.put(stemedWrd, sentList);
          }
        }
        procSent.append(stemedWrd).append(" ");
      }

      sentCnt++;
      if (processedSent != null)
        processedSent.add(procSent.toString());
    }
  }

  /**
   * Reads a document's content from a file.
   *
   * @param fileName The path relative file reference of the resource to read in.
   *                 If {@code null} or empty, an empty String is returned.
   * @return A string representation of the file's contents.
   */
  public String docToString(String fileName) throws IOException {
    if (fileName == null || fileName.isBlank()) {
      return "";
    } else {
      StringBuilder docBuffer = new StringBuilder();
      try (InputStream in = DefaultDocProcessor.class.getResourceAsStream(fileName);
           LineNumberReader lnr = new LineNumberReader(new InputStreamReader(in))) {
        String nextLine;

        while ((nextLine = lnr.readLine()) != null) {
          String trimmedLine = nextLine.trim();
          if (!trimmedLine.isEmpty()) {
            docBuffer.append(REPLACEMENT_PATTERN.matcher(trimmedLine).replaceAll("")).append(" ");
          }
        }
      }
      return docBuffer.toString();
    }
  }

  /**
   * Reads a document's content from a file.
   *
   * @param fileName The path relative file reference of the resource to read in.
   *                 If {@code null} or empty, an empty List is returned.
   * @return A list {@link Sentence sentences} representing the file's contents.
   */
  public List<Sentence> docToSentences(String fileName) throws IOException {
    if (fileName == null || fileName.isBlank()) {
      return Collections.emptyList();
    } else {
      List<Sentence> sentList = new ArrayList<>();
      try (InputStream in = DefaultDocProcessor.class.getResourceAsStream(fileName);
           LineNumberReader lnr = new LineNumberReader(new InputStreamReader(in))) {
        String nextLine;
        int paraNo = 0;
        int sentNo = 0;
        while ((nextLine = lnr.readLine()) != null) {
          String trimmedLine = nextLine.trim();
          if (!trimmedLine.isEmpty()) {
            List<String> sents = new ArrayList<>();
            List<String> cleanedSents = new ArrayList<>();
            this.getSentences(trimmedLine, sents, null, cleanedSents);
            int paraPos = 1;
            for (String sen : sents) {
              Sentence s = new Sentence(sentNo++, sen, paraNo, paraPos++);
              sentList.add(s);
            }
            paraNo++;
          }
        }
      }
      return sentList;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Sentence> getSentences(String text) {
    if (text == null || text.isBlank()) {
      return Collections.emptyList();
    }
    List<Sentence> ret = new ArrayList<>();
    List<String> sentStrs = new ArrayList<>();
    List<String> cleanedSents = new ArrayList<>();

    //Custom/simple method if specified or open nlp model was not found
    if (sentModel == null || SENTENCE_FRAG == SIMPLE)
      getSentences(text, sentStrs, null, cleanedSents);
    else {
      SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentModel);
      String[] sentences = sentenceDetector.sentDetect(text);
      Collections.addAll(sentStrs, sentences);
    }
    int sentNo = 0;
    for (String sen : sentStrs) {
      Sentence s = new Sentence(sentNo, sen, 1, sentNo);
      ret.add(s);
      sentNo++;
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getWords(String sent) {
    if (sent == null || sent.isBlank()) {
      return new String[0];
    }
    return sent.trim().split("\\s+");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Stemmer getStemmer() {
    return stemmer;
  }

}