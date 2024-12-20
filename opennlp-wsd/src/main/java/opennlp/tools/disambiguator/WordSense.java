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

/**
 * Represents a type that associates {@link WSDSample samples} with a
 * {@link SynNode syn node} which holds one or more senses.
 * Elements of this type can be scored and thereby be ranked. This is
 * why {@link WordSense} implements {@link Comparable}.
 *
 * @see SynNode
 * @see WSDSample
 */
public class WordSense implements Comparable<WordSense> {

  private final int id;
  private final SynNode node;
  private WSDSample sample;
  private double score;

  /**
   * Initializes a {@link WordSense} via a numerical {@code id} and
   * a {@link SynNode} instance.
   *
   * @param id    Must be a positive number.
   * @param node  The {@link SynNode node} to link senses to.
   */
  public WordSense(int id, SynNode node) {
    this.id = id;
    this.node = node;
  }

  /**
   * Initializes a {@link WordSense} via a {@link WSDSample} and
   * a {@link SynNode} instance.
   *
   * @param sample  The {@link WSDSample word sample} to associate.
   * @param node    The {@link SynNode node} to link senses to.
   */
  public WordSense(WSDSample sample, SynNode node) {
    this(sample.getSenseID(), node);
    this.sample = sample;
  }

  /**
   * @return Retrieves to numerical identifier.
   */
  public int getId() {
    return id;
  }

  /**
   * @return Retrieves the gloss available via WordNet.
   */
  public String getGloss() {
    return node.getGloss();
  }

  /**
   * @return Retrieves {@link SynNode syn node} instance.
   */
  public SynNode getNode() {
    return node;
  }

  /**
   * @return Retrieves {@link WSDSample word sample} instance.
   */
  public WSDSample getWSDSample() {
    return sample;
  }

  void setWSDSample(WSDSample sample) {
    this.sample = sample;
  }

  /**
   * @return Retrieves numerical score. The value might be undefined, aka not set.
   */
  public double getScore() {
    return score;
  }

  /**
   * @param score The score to assign. No restrictions on this parameter.
   */
  void setScore(double score) {
    this.score = score;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int compareTo(WordSense o) {
    return Double.compare(this.score, o.score);
  }

}
