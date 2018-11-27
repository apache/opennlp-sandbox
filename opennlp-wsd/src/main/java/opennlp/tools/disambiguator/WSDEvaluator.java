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

package opennlp.tools.disambiguator;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link WSDEvaluator} measures the performance of the given
 * {@link WSDisambiguator} with the provided reference
 * {@link WordToDisambiguate}.
 *
 * @see Evaluator
 * @see WSDisambiguator
 * @see WordToDisambiguate
 */
public class WSDEvaluator extends Evaluator<WSDSample> {

  private Mean accuracy = new Mean();

  /**
   * The {@link WSDisambiguator} used to create the disambiguated senses.
   */
  private WSDisambiguator disambiguator;

  /**
   * Initializes the current instance with the given {@link WSDisambiguator}.
   *
   * @param disambiguator
   *          the {@link WSDisambiguator} to evaluate.
   * @param listeners
   *          evaluation sample listeners
   */
  public WSDEvaluator(WSDisambiguator disambiguator,
      WSDEvaluationMonitor... listeners) {
    super(listeners);
    this.disambiguator = disambiguator;
  }

  // @Override
  protected WSDSample processSample(WSDSample reference) {

    String[] referenceSenses = reference.getSenseIDs();

    // get the best predicted sense
    String predictedSense = disambiguator.disambiguate(reference.getSentence(),
        reference.getTags(), reference.getLemmas(),
        reference.getTargetPosition());

    if (predictedSense == null) {
      System.out
          .println("There was no sense for : " + reference.getTargetWord());
      return null;
    }
    // get the senseKey from the result
    String senseKey = predictedSense.split(" ")[1];

    if (referenceSenses[0].equals(senseKey)) {
      accuracy.add(1);
    } else {
      accuracy.add(0);
    }

    return new WSDSample(reference.getSentence(), reference.getTags(),
        reference.getLemmas(), reference.getTargetPosition());
  }

  /**
   * Retrieves the WSD accuracy.
   *
   * This is defined as: WSD accuracy = correctly disambiguated / total words
   *
   * @return the WSD accuracy
   */
  public double getAccuracy() {
    return accuracy.mean();
  }

  /**
   * Retrieves the total number of words considered in the evaluation.
   *
   * @return the word count
   */
  public long getWordCount() {
    return accuracy.count();
  }

  /**
   * Represents this objects as human readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy: " + (accuracy.mean() * 100) + "%"
        + "\tNumber of Samples: " + accuracy.count();
  }
}
