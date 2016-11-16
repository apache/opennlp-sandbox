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

import java.util.List;

import opennlp.tools.parse_thicket.matching.LemmaGeneralizer;

public class ParseTreeChunkListScorer {
  // find the single expression with the highest score
  public double getParseTreeChunkListScore(
      List<List<ParseTreeChunk>> matchResult) {
    double currScore = 0.0;
    for (List<ParseTreeChunk> chunksGivenPhraseType : matchResult)
      for (ParseTreeChunk chunk : chunksGivenPhraseType) {
        Double score = getScore(chunk);
        // System.out.println(chunk+ " => score >>> "+score);
        if (score > currScore) {
          currScore = score;
        }
      }
    return currScore;
  }

  // get max score per phrase type and then sum up
  public double getParseTreeChunkListScoreAggregPhraseType(
      List<List<ParseTreeChunk>> matchResult) {
    double currScoreTotal = 0.0;
    for (List<ParseTreeChunk> chunksGivenPhraseType : matchResult) {
      double currScorePT = 0.0;
      for (ParseTreeChunk chunk : chunksGivenPhraseType) {
        Double score = getScore(chunk);
        // System.out.println(chunk+ " => score >>> "+score);
        if (score > currScorePT) {
          currScorePT = score;
        }
      }
      // if substantial for given phrase type
      if (currScorePT > 0.5) {
        currScoreTotal += currScorePT;
      }
    }
    return currScoreTotal;
  }

  // score is meaningful only for chunks which are results of generalization

  public double getScore(ParseTreeChunk chunk) {
    double score = 0.0;
    int i = 0;
    for (String l : chunk.getLemmas()) {
      String pos = chunk.getPOSs().get(i);
      if (l.equals("*")) {
        if (pos.startsWith("CD")) { // number vs number gives high score
                                    // although different numbers
          score += 0.7;
        } else if (pos.endsWith("_high")) { // if query modification adds 'high'
          score += 1.0;
        } else {
          score += 0.1;
        }
      } else if (l.startsWith(LemmaGeneralizer.w2vPrefix) ){
    	  try {
			float val = Float.parseFloat(l.substring(LemmaGeneralizer.w2vPrefix.length()));
			  score+= 1- val;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
      }
      
      else {

        if (pos.startsWith("NN") || pos.startsWith("NP")
            || pos.startsWith("CD") || pos.startsWith("RB")) {
          score += 1.0;
        } else if (pos.startsWith("VB") || pos.startsWith("JJ")) {
          if (l.equals("get")) { // 'common' verbs are not that important
            score += 0.3;
          } else {
            score += 0.5;
          }
        } else {
          score += 0.3;
        }
      }
      i++;

    }
    return score;
  }

}
