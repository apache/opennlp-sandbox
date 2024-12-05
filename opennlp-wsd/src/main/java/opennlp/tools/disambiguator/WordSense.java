/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator;

public class WordSense implements Comparable<WordSense> {

  private WSDSample sample;
  private SynNode node;
  private int id;
  private double score;

  public WordSense() {
    super();
  }

  public WordSense(int id, SynNode node) {
    this();
    this.id = id;
    setNode(node);
  }

  public WordSense(WSDSample sample, SynNode node) {
    this();
    this.sample = sample;
    this.node = node;
  }

  public WSDSample getWSDSample() {
    return sample;
  }

  public void setWSDSample(WSDSample sample) {
    this.sample = sample;
  }

  public SynNode getNode() {
    return node;
  }

  public void setNode(SynNode node) {
    this.node = node;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public int getId() {
    return id;
  }

  @Override
  public int compareTo(WordSense o) {
    return Double.compare(this.score, o.score);
  }

  public String getGloss() {
    return node.getGloss();
  }

}
