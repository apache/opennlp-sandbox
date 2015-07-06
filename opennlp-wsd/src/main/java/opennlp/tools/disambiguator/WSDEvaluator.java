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

import net.sf.extjwnl.data.POS;
import opennlp.tools.disambiguator.lesk.Lesk;
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
public class WSDEvaluator extends Evaluator<WordToDisambiguate> {

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
  protected WordToDisambiguate processSample(WordToDisambiguate reference) {

    String[] referenceSenses = reference.getSenseIDs().toArray(
        new String[reference.getSenseIDs().size()]);

    // get the best predicted sense
    String predictedSense = disambiguator.disambiguate(reference.sentence,
        reference.getWordIndex())[0];

    // TODO review this pattern
    String[] parts = predictedSense.split("@");
    POS pos = POS.getPOSForKey(parts[0]);
    long offset = Long.parseLong(parts[1]);
    String senseKey = parts[2];
    double score = Double.parseDouble(parts[3]);

    // if we have multiple senses mapped to one sense
    if (disambiguator.getParams().isCoarseSense()) {

      // if we find the sense in one of the coarse senses
      int found = -1;
      for (int i = 0; i < referenceSenses.length; i++) {
        if (referenceSenses[i].equals(senseKey)) {
          // Constants.print("++++++++++++++++++++++++ YES");
          accuracy.add(1);
          found = i;
          break;
        }
      }
      if (found < 0) {
        // Constants.print("NO : "+referenceSenses[0]+"+++" + senseKey);
        accuracy.add(0);
      }

    } // else we have fine grained senses (only one mapped sense)
    else {
      if (referenceSenses[0].equals(senseKey)) {
        // Constants.print("++++++++++++++++++++++++ YES");
        accuracy.add(1);
      } else {
        // Constants.print("NO : "+referenceSenses[0]+"+++" + senseKey);
        accuracy.add(0);
      }
    }
    return new WordToDisambiguate(reference.getSentence(),
        reference.getWordIndex());
  }

  public double getAccuracy() {
    return accuracy.mean();
  }

  public long getWordCount() {
    return accuracy.count();
  }

}

