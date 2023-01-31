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
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

public class SentencePairMatchResult {
  public List<List<ParseTreeChunk>> matchResult;
  private static Logger LOG = Logger
      .getLogger("opennlp.tools.textsimilarity.SentencePairMatchResult");

  public List<List<ParseTreeChunk>> getMatchResult() {
    return matchResult;
  }

  public void setMatchResult(List<List<ParseTreeChunk>> matchResult) {
    this.matchResult = matchResult;
  }

  public List<LemmaPair> getResForMinedSent1() {
    return resForMinedSent1;
  }

  public void setResForMinedSent1(List<LemmaPair> resForMinedSent1) {
    this.resForMinedSent1 = resForMinedSent1;
  }

  public boolean isVerbExists() {
    return verbExists;
  }

  public void setVerbExists(boolean verbExists) {
    this.verbExists = verbExists;
  }

  public boolean isImperativeVerb() {
    return imperativeVerb;
  }

  public void setImperativeVerb(boolean imperativeVerb) {
    this.imperativeVerb = imperativeVerb;
  }

  private List<LemmaPair> resForMinedSent1;

  public boolean verbExists = false;

  public boolean imperativeVerb = false;

  public SentencePairMatchResult(List<List<ParseTreeChunk>> matchResult,
      List<LemmaPair> resForMinedSent1) {
    super();
    verbExists = false;
    imperativeVerb = false;
    // LOG.info("Assessing sentence for inclusion " + resForMinedSent1);
    this.matchResult = matchResult;
    this.resForMinedSent1 = resForMinedSent1;
    for (LemmaPair word : resForMinedSent1) {
      if (word.getPOS().startsWith("VB") && word.getLemma().length() > 2
          && StringUtils.isAlpha(word.getLemma())) {// ||
                                                    // word.getPOS().startsWith("VP"))
        verbExists = true;
        // LOG.info("Found verb=" + word);
      }
    }
    // various form of sales pitch: 'get something', or 'we offer'
    if (resForMinedSent1.size() > 2
        && (resForMinedSent1.get(1).getLemma().startsWith("We") || resForMinedSent1
            .get(2).getLemma().startsWith("We")))
      imperativeVerb = true;

    for (LemmaPair word : resForMinedSent1) {
      if (word.getPOS().startsWith("VB") && word.getStartPos() < 1
          && word.getEndPos() < 1) {
        imperativeVerb = true;
        // LOG.info("Found imperative verb=" + word);
      }
    }

  }

}
