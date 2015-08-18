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

import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.SynNode;

public class WordSense implements Comparable {

  protected WSDSample sample;
  protected SynNode node;
  protected int id;
  protected double score;

  public WordSense(WSDSample sample, SynNode node) {
    super();
    this.sample = sample;
    this.node = node;
  }

  public WordSense() {
    super();
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

  public void setId(int id) {
    this.id = id;
  }

  public int compareTo(Object o) {
    return (this.score - ((WordSense) o).score) < 0 ? 1 : -1;
  }

  public String getGloss() {
    return node.getGloss();
  }

}
