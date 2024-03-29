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

import java.util.*;

import opennlp.summarization.*;
import opennlp.summarization.preprocess.IDFWordWeight;
import opennlp.summarization.preprocess.WordWeight;

/*
 * A wrapper around the text rank algorithm.  This class
 * a) Sets up the data for the TextRank class
 * b) Takes the ranked sentences and does some basic rearranging (e.g. ordering) to provide a more reasonable summary.
 */
public class TextRankSummarizer implements Summarizer {

  // An optional file to store idf of words. If idf is not available it uses a default equal weight for all words.
  private final String idfFile = "resources/idf.csv";
  public TextRankSummarizer() {
  }

  /*Sets up data and calls the TextRank algorithm..*/
  public List<Score> rankSentences(String doc, List<Sentence> sentences,
                                   DocProcessor dp, int maxWords ) {
    try {
      //Rank sentences
      TextRank summ = new TextRank(dp);
      List<String> sentenceStrL = new ArrayList<>();
      List<String> processedSent = new ArrayList<>();
      Hashtable<String, List<Integer>> iidx = new Hashtable<>();
      //     dp.getSentences(sentences, sentenceStrL, iidx, processedSent);

      for(Sentence s : sentences){
        sentenceStrL.add(s.getStringVal());
        String stemmedSent = s.stem();
        processedSent.add(stemmedSent);

        String[] wrds = stemmedSent.split(" ");
        for(String w: wrds)
        {
          if(iidx.get(w)!=null)
            iidx.get(w).add(s.getSentId());
          else{
            List<Integer> l = new ArrayList<>();
            l.add(s.getSentId());
            iidx.put(w, l);
          }
        }
      }

      WordWeight wordWt = new IDFWordWeight(idfFile);////new

      List<Score> finalScores = summ.getRankedSentences(doc, sentenceStrL, iidx, processedSent);
      List<String> sentenceStrList = summ.getSentences();

      // SentenceClusterer clust = new SentenceClusterer();
      //  clust.runClusterer(doc, summ.processedSent);

      Hashtable<Integer,List<Integer>> links= summ.getLinks();

      for(int i=0;i<sentences.size();i++)
      {
        Sentence st = sentences.get(i);

        //Add links..
        List<Integer> currLnks = links.get(i);
        if(currLnks==null) continue;
        for(int j=0;j<currLnks.size();j++)
        {
          if(j<i) st.addLink(sentences.get(j));
        }
      }

      for (Score s : finalScores) {
        Sentence st = sentences.get(s.getSentId());
        st.setPageRankScore(s);
      }

      List<Score> reRank = finalScores;//reRank(sentences, finalScores, iidx, wordWt, maxWords);

      return reRank;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  //Returns the summary as a string.
  @Override
  public String summarize(String article, DocProcessor dp, int maxWords) {
    List<Sentence> sentences = dp.getSentencesFromStr(article);
    List<Score> scores = this.rankSentences(article, sentences, dp, maxWords);
    return scores2String(sentences, scores, maxWords);
  }

  /* Use the page rank scores to determine the summary.*/
  public String scores2String(List<Sentence> sentences, List<Score> scores, int maxWords) {
    StringBuilder b = new StringBuilder();
    // for(int i=0;i< min(maxWords, scores.size()-1);i++)
    int i=0;
    while(b.length()< maxWords && i< scores.size())
    {
      String sent = sentences.get(scores.get(i).getSentId()).getStringVal();
      b.append(sent).append(scores.get(i));
      i++;
    }
    return b.toString();
  }

}
