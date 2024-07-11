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

package opennlp.summarization.textrank;

import opennlp.summarization.DocProcessor;
import opennlp.summarization.Score;
import opennlp.summarization.Sentence;
import opennlp.summarization.Summarizer;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * A wrapper {@link Summarizer} implementation around the {@link TextRank text rank} algorithm.
 * <p>
 * This implementation:
 * <ol>
 * <li>sets up the data for the {@link TextRank} class</li>
 * <li>takes the ranked sentences and conducts rearranging (e.g. ordering) to provide
 * a more reasonable summary.</li>
 * </ol>
 *
 * @see TextRank
 * @see Summarizer
 */
public class TextRankSummarizer implements Summarizer {

  private final DocProcessor docProcessor;

  public TextRankSummarizer(DocProcessor docProcessor) {
    this.docProcessor = docProcessor;
  }

  /* Sets up data and calls the TextRank algorithm..*/
  public List<Score> rankSentences(List<Sentence> sentences, int maxWords) {
    final TextRank summ = new TextRank(docProcessor);
    final List<String> sentenceStrL = new ArrayList<>();
    final List<String> processedSent = new ArrayList<>();
    final Hashtable<String, List<Integer>> iidx = new Hashtable<>();

    //Rank sentences
    for (Sentence s : sentences) {
      sentenceStrL.add(s.getStringVal());
      String stemmedSent = s.stem();
      processedSent.add(stemmedSent);

      String[] wrds = stemmedSent.split("\\s+");
      for (String w : wrds) {
        if (iidx.get(w) != null)
          iidx.get(w).add(s.getSentId());
        else {
          List<Integer> l = new ArrayList<>();
          l.add(s.getSentId());
          iidx.put(w, l);
        }
      }
    }

    List<Score> finalScores = summ.getRankedSentences(sentenceStrL, iidx, processedSent);

    // SentenceClusterer clust = new SentenceClusterer();
    //  clust.runClusterer(doc, summ.processedSent);

    Hashtable<Integer, List<Integer>> links = summ.getLinks();

    for (int i = 0; i < sentences.size(); i++) {
      Sentence st = sentences.get(i);

      // Add links..
      List<Integer> currLnks = links.get(i);
      if (currLnks == null) continue;
      for (int j = 0; j < currLnks.size(); j++) {
        if (j < i) st.addLink(sentences.get(j));
      }
    }

    for (Score s : finalScores) {
      Sentence st = sentences.get(s.getSentId());
      st.setPageRankScore(s);
    }

    return finalScores; //reRank(sentences, finalScores, iidx, wordWt, maxWords);
  }

  @Override
  public String summarize(String article, int maxWords) {
    List<Sentence> sentences = docProcessor.getSentences(article);
    List<Score> scores = rankSentences(sentences, maxWords);
    return scores2String(sentences, scores, maxWords);
  }

  /* Use the page rank scores to determine the summary.*/
  public String scores2String(List<Sentence> sentences, List<Score> scores, int maxWords) {
    StringBuilder b = new StringBuilder();
    int i = 0;
    while (b.length() < maxWords && i < scores.size()) {
      String sent = sentences.get(scores.get(i).getSentId()).getStringVal();
      b.append(sent); //.append(scores.get(i));
      i++;
    }
    return b.toString();
  }

}
