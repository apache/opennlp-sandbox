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

package opennlp.tools.similarity.apps;

public class YahooHit extends com.zvents.bing.HitBase implements
    Comparable<YahooHit> {

  public YahooHit(String orig, String[] generateds) {
    super(orig, generateds);

  }

  int originalRank = -1;

  int taxoScore = 0;

  public YahooHit() {
  };

  public int getOriginalRank() {
    return originalRank;
  }

  public void setOriginalRank(int originalRank) {
    this.originalRank = originalRank;
  }

  public String processSnapshotForMatching(String snapshot) {
    snapshot = snapshot.replace("<b>...</b>", ". ").replace("<b>", "")
        .replace("</b>", "").replace(". . ", " ").replace(" . . . ", " ")
        .replace("...", " ").replace(",..", " ").replace("&amp;", " ")
        .replace("  ", " ");
    snapshot = snapshot.replace('\'', ' ').replace('-', ' ');

    return snapshot;
  }

  public int getTaxoScore() {
    return taxoScore;
  }

  public void setTaxoScore(int taxoScore) {
    this.taxoScore = taxoScore;
  }

  @Override
  public int compareTo(YahooHit obj) {
    YahooHit tmp = (YahooHit) obj;
    if (this.taxoScore > tmp.taxoScore) {
      return -1;
    } else if (this.taxoScore < tmp.taxoScore) {
      return 1;
    }
    return 0;
  }

}
