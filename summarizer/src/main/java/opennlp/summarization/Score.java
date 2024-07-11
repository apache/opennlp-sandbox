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

package opennlp.summarization;

/**
 * Encapsulates the score of a sentence for the purpose of ranking sentences within a document.
 */
public class Score implements Comparable<Score> {
  private int sentId;
  private double score;

  public Score(int sentId, double score) {
    this.sentId = sentId;
    this.score = score;
  }

  public int getSentId() {
    return sentId;
  }

  public void setSentId(int sentId) {
    this.sentId = sentId;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  @Override
  public int compareTo(Score o) {
    if (o.score > score) return 1;
    else if (o.score < score) return -1;
    return 0;
  }

  @Override
  public String toString() {
    return sentId + " " + score;
  }
}
