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

package opennlp.addons.mallet;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import opennlp.tools.ml.AbstractSequenceTrainer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.ml.model.SequenceStream;
import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFOptimizableByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByValueGradients;
import cc.mallet.fst.Transducer;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;

// Transducer should be abstract, we have two CRF and HMM.
// For HMM we don't need to generate any features (how to do that nicely?!)
// Dummy feature generator ?!
public class CRFTrainer extends AbstractSequenceTrainer {

  private int[] getOrders() {
    String[] ordersString = "0,1".split(",");
    int[] orders = new int[ordersString.length];
    for (int i = 0; i < ordersString.length; i++) {
      orders[i] = Integer.parseInt(ordersString[i]);
      System.err.println("Orders: " + orders[i]);
    }
    return orders;
  }

  // TODO: Interface has to be changed here,
  @Override
  public SequenceClassificationModel<String> doTrain(SequenceStream sequences)
      throws IOException {

    Alphabet dataAlphabet = new Alphabet();
    LabelAlphabet targetAlphabet = new LabelAlphabet();

    InstanceList trainingData = new InstanceList(dataAlphabet, targetAlphabet);

    int nameIndex = 0;
    Sequence sequence;
    while ((sequence = sequences.read()) != null) {
      FeatureVector featureVectors[] = new FeatureVector[sequence.getEvents().length];
      Label malletOutcomes[] = new Label[sequence.getEvents().length];

      Event events[] = sequence.getEvents();

      for (int eventIndex = 0; eventIndex < events.length; eventIndex++) {

        Event event = events[eventIndex];

        String features[] = event.getContext();
        int malletFeatures[] = new int[features.length];

        for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
          malletFeatures[featureIndex] = dataAlphabet.lookupIndex(
              features[featureIndex], true);
        }

        // Note: Might contain a feature more than once ... will that
        // work ?!
        featureVectors[eventIndex] = new FeatureVector(dataAlphabet,
            malletFeatures);

        malletOutcomes[eventIndex] = targetAlphabet.lookupLabel(
            event.getOutcome(), true);
      }

      LabelSequence malletOutcomeSequence = new LabelSequence(malletOutcomes);

      FeatureVectorSequence malletSequence = new FeatureVectorSequence(
          featureVectors);

      trainingData.add(new Instance(malletSequence, malletOutcomeSequence,
          "name" + nameIndex++, "source"));
    }

    CRF crf = new CRF(trainingData.getDataAlphabet(),
        trainingData.getTargetAlphabet());

    String startStateName = crf.addOrderNStates(trainingData, getOrders(),
        (boolean[]) null,
        // default label
        "other", Pattern.compile("other,*-cont"), // forbidden pattern
        null, // allowed pattern
        true);
    crf.getState(startStateName).setInitialWeight(0.0);

    for (int i = 0; i < crf.numStates(); i++) {
      crf.getState(i).setInitialWeight(Transducer.IMPOSSIBLE_WEIGHT);
    }

    crf.getState(startStateName).setInitialWeight(0.0);
    crf.setWeightsDimensionAsIn(trainingData, true);

    // CRFOptimizableBy* objects (terms in the objective function)
    // objective 1: label likelihood objective

//    CRFTrainerByLabelLikelihood crfTrainer = new CRFTrainerByLabelLikelihood(crf);
//    crfTrainer.setGaussianPriorVariance(1.0);

    CRFOptimizableByLabelLikelihood optLabel = new
        CRFOptimizableByLabelLikelihood(crf, trainingData);

//    // CRF trainer
     Optimizable.ByGradientValue[] opts = new Optimizable.ByGradientValue[] {
     optLabel };

//     by default, use L-BFGS as the optimizer
     CRFTrainerByValueGradients crfTrainer = new CRFTrainerByValueGradients(
     crf, opts);
     crfTrainer.setMaxResets(0);

    // SNIP

    crfTrainer.train(trainingData, Integer.MAX_VALUE);
    
    // can be very similar to the other model
    // one important difference is that the feature gen needs to be integrated
    // ...
    return new TransducerModel(crf);
  }

  // TODO: We need to return a sequence model here. How should that be done ?!
  //

}
