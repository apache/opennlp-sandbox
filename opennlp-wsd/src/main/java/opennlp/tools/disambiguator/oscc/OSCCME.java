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

package opennlp.tools.disambiguator.oscc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.mfs.MFS;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

/**
 * Maximum Entropy version of the <b>one sence per cluster</b> approach in
 * 
 * http://nlp.cs.rpi.edu/paper/wsd.pdf
 * 
 * The approach is a hybrid approach using unsupervised context clustering to
 * enhance disambiguation using a typical classifier.
 * 
 * The context clusters are considered a group of words representing an enriched
 * context of a target word.
 * 
 * The clusters can be formed by clustering techniques like K-means, or a
 * simpler version can use WordNet to get clusters simply from SynSets.
 * 
 * Please see {@link DefaultOSCCContextGenerator}
 * 
 * The approach finds the context clusters surrounding the target and uses a
 * classifier to judge on the best case.
 * 
 * Here an ME classifier is used.
 * 
 */
public class OSCCME extends WSDisambiguator {

  protected OSCCModel osccModel;

  protected static OSCCContextGenerator cg = new DefaultOSCCContextGenerator();

  public OSCCME(OSCCParameters params) {
    this.params = params;
  }

  public OSCCME(OSCCModel model, OSCCParameters params) {
    this.osccModel = model;
    this.params = params;
  }

  public OSCCModel getModel() {
    return osccModel;
  }

  public void setModel(OSCCModel model) {
    this.osccModel = model;
  }

  public void setParameters(OSCCParameters parameters) {
    this.params = parameters;
  }

  public static OSCCModel train(String lang, ObjectStream<WSDSample> samples,
      TrainingParameters mlParams, OSCCParameters osccParams,
      OSCCFactory osccFactory) throws IOException {

    ArrayList<String> surroundingClusterModel = buildSurroundingClusters(
        samples, osccParams.getWindowSize());

    HashMap<String, String> manifestInfoEntries = new HashMap<String, String>();

    MaxentModel osccModel = null;

    ArrayList<Event> events = new ArrayList<Event>();
    ObjectStream<Event> es = null;

    WSDSample sample = samples.read();
    String wordTag = "";
    if (sample != null) {
      wordTag = sample.getTargetWordTag();
      do {
        String sense = sample.getSenseIDs().get(0);
        String[] context = cg.getContext(sample, osccParams.windowSize,
            surroundingClusterModel);
        Event ev = new Event(sense + "", context);
        events.add(ev);
      } while ((sample = samples.read()) != null);
    }

    es = ObjectStreamUtils.createObjectStream(events);
    EventTrainer trainer = TrainerFactory
        .getEventTrainer(mlParams.getSettings(), manifestInfoEntries);

    osccModel = trainer.train(es);

    return new OSCCModel(lang, wordTag, osccParams.windowSize, osccModel,
        surroundingClusterModel, manifestInfoEntries, osccFactory);
  }

  public static ArrayList<String> buildSurroundingClusters(
      ObjectStream<WSDSample> samples, int windowSize) throws IOException {
    // TODO modify to clusters
    DefaultOSCCContextGenerator osccCG = new DefaultOSCCContextGenerator();
    ArrayList<String> surroundingWordsModel = new ArrayList<String>();
    WSDSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = osccCG.extractSurroundingContextClusters(
          sample.getTargetPosition(), sample.getSentence(), sample.getTags(),
          sample.getLemmas(), windowSize);

      if (words.length > 0) {
        for (String word : words) {
          surroundingWordsModel.add(word);
        }
      }
    }
    samples.reset();
    return surroundingWordsModel;
  }

  @Override
  public String[] disambiguate(WSDSample sample) {
    if (WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      String wordTag = sample.getTargetWordTag();

      if (osccModel == null
          || !osccModel.getWordTag().equals(sample.getTargetWordTag())) {

        String trainingFile = ((OSCCParameters) this.getParams())
            .getTrainingDataDirectory() + sample.getTargetWordTag();

        File file = new File(trainingFile + ".oscc.model");
        if (file.exists() && !file.isDirectory()) {
          try {
            setModel(new OSCCModel(file));

          } catch (InvalidFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }

          String outcome = "";

          String[] context = cg.getContext(sample,
              ((OSCCParameters) this.params).windowSize,
              osccModel.getContextClusters());

          double[] outcomeProbs = osccModel.getOSCCMaxentModel().eval(context);
          outcome = osccModel.getOSCCMaxentModel().getBestOutcome(outcomeProbs);

          if (outcome != null && !outcome.equals("")) {

            outcome = this.getParams().getSenseSource().name() + " "
                + wordTag.split("\\.")[0] + "%" + outcome;

            String[] s = { outcome };

            return s;
          } else {
            MFS mfs = new MFS();
            return mfs.disambiguate(wordTag);
          }

        } else {

          MFS mfs = new MFS();
          return mfs.disambiguate(wordTag);
        }
      } else {
        String outcome = "";

        String[] context = cg.getContext(sample,
            ((OSCCParameters) this.params).windowSize,
            osccModel.getContextClusters());

        double[] outcomeProbs = osccModel.getOSCCMaxentModel().eval(context);
        outcome = osccModel.getOSCCMaxentModel().getBestOutcome(outcomeProbs);

        if (outcome != null && !outcome.equals("")) {

          outcome = this.getParams().getSenseSource().name() + " "
              + wordTag.split("\\.")[0] + "%" + outcome;

          String[] s = { outcome };

          return s;
        } else {

          MFS mfs = new MFS();
          return mfs.disambiguate(wordTag);
        }
      }
    } else {

      if (WSDHelper.getNonRelevWordsDef(sample.getTargetTag()) != null) {
        String s = OSCCParameters.SenseSource.WSDHELPER.name() + " "
            + sample.getTargetTag();
        String[] sense = { s };
        return sense;
      } else {
        return null;
      }

    }

  }

  /**
   * The IMS disambiguation method for a single word
   * 
   * @param tokenizedContext
   *          : the text containing the word to disambiguate
   * @param tokenTags
   *          : the tags corresponding to the context
   * @param lemmas
   *          : the lemmas of ALL the words in the context
   * @param index
   *          : the index of the word to disambiguate
   * @return an array of the senses of the word to disambiguate
   */
  public String[] disambiguate(String[] tokenizedContext, String[] tokenTags,
      String[] lemmas, int index) {
    return disambiguate(
        new WSDSample(tokenizedContext, tokenTags, lemmas, index));
  }

}
