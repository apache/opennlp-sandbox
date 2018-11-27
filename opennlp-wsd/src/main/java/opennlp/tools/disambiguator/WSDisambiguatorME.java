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

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class WSDisambiguatorME extends WSDisambiguator {

  protected WSDModel model;

  protected static WSDContextGenerator cg = new IMSWSDContextGenerator();

  public WSDisambiguatorME(WSDParameters params) {
    this.params = params;
  }

  public WSDisambiguatorME(WSDModel model, WSDParameters params) {
    this.model = model;
    this.params = params;
  }

  public WSDModel getModel() {
    return model;
  }

  public void setModel(WSDModel model) {
    this.model = model;
  }

  public void setParameters(WSDParameters parameters) {
    this.params = parameters;
  }

  public static WSDModel train(String lang, ObjectStream<WSDSample> samples,
    TrainingParameters mlParams, WSDParameters params) throws IOException {

    ArrayList<String> surroundingContext = buildSurroundingContext(samples,
      ((WSDDefaultParameters) params).getWindowSize());

    HashMap<String, String> manifestInfoEntries = new HashMap<String, String>();

    MaxentModel meModel = null;

    ArrayList<Event> events = new ArrayList<Event>();
    ObjectStream<Event> es = null;

    WSDSample sample = samples.read();
    String wordTag = "";
    if (sample != null) {
      wordTag = sample.getTargetWordTag();
      do {
        String sense = sample.getSenseIDs()[0];
        String[] context = cg
          .getContext(sample, ((WSDDefaultParameters) params).ngram,
            ((WSDDefaultParameters) params).windowSize, surroundingContext);
        Event ev = new Event(sense + "", context);
        events.add(ev);
      } while ((sample = samples.read()) != null);
    }

    es = ObjectStreamUtils.createObjectStream(events);
    EventTrainer trainer = TrainerFactory
      .getEventTrainer(mlParams.getSettings(), manifestInfoEntries);

    meModel = trainer.train(es);

    return new WSDModel(lang, wordTag,
      ((WSDDefaultParameters) params).windowSize,
      ((WSDDefaultParameters) params).ngram, meModel, surroundingContext,
      manifestInfoEntries);
  }

  public static ArrayList<String> buildSurroundingContext(
    ObjectStream<WSDSample> samples, int windowSize) throws IOException {
    IMSWSDContextGenerator contextGenerator = new IMSWSDContextGenerator();
    ArrayList<String> surroundingWordsModel = new ArrayList<String>();
    WSDSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = contextGenerator
        .extractSurroundingContext(sample.getTargetPosition(),
          sample.getSentence(), sample.getLemmas(), windowSize);

      if (words.length > 0) {
        for (String word : words) {
          surroundingWordsModel.add(word);
        }
      }
    }
    samples.reset();
    return surroundingWordsModel;
  }

  @Override public String disambiguate(WSDSample sample) {
    if (WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      String wordTag = sample.getTargetWordTag();

      if (model == null || !model.getWordTag()
        .equals(sample.getTargetWordTag())) {

        String trainingFile =
          ((WSDDefaultParameters) this.getParams()).getTrainingDataDirectory()
            + sample.getTargetWordTag();

        File file = new File(trainingFile + ".wsd.model");
        if (file.exists() && !file.isDirectory()) {
          try {
            setModel(new WSDModel(file));

          } catch (InvalidFormatException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }

          String outcome = "";

          String[] context = cg
            .getContext(sample, ((WSDDefaultParameters) this.params).ngram,
              ((WSDDefaultParameters) this.params).windowSize,
              this.model.getContextEntries());

          double[] outcomeProbs = model.getWSDMaxentModel().eval(context);
          outcome = model.getWSDMaxentModel().getBestOutcome(outcomeProbs);

          if (outcome != null && !outcome.equals("")) {

            return this.getParams().getSenseSource().name() + " " + wordTag
              .split("\\.")[0] + "%" + outcome;

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

        String[] context = cg
          .getContext(sample, ((WSDDefaultParameters) this.params).ngram,
            ((WSDDefaultParameters) this.params).windowSize,
            this.model.getContextEntries());

        double[] outcomeProbs = model.getWSDMaxentModel().eval(context);
        outcome = model.getWSDMaxentModel().getBestOutcome(outcomeProbs);

        if (outcome != null && !outcome.equals("")) {

          return this.getParams().getSenseSource().name() + " " + wordTag
            .split("\\.")[0] + "%" + outcome;
        } else {

          MFS mfs = new MFS();
          return mfs.disambiguate(wordTag);
        }
      }
    } else {

      if (WSDHelper.getNonRelevWordsDef(sample.getTargetTag()) != null) {
        return WSDParameters.SenseSource.WSDHELPER.name() + " " + sample
          .getTargetTag();
      } else {
        return null;
      }

    }

  }

  /**
   * The IMS disambiguation method for a single word
   *
   * @param tokenizedContext : the text containing the word to disambiguate
   * @param tokenTags        : the tags corresponding to the context
   * @param lemmas           : the lemmas of ALL the words in the context
   * @param index            : the index of the word to disambiguate
   * @return an array of the senses of the word to disambiguate
   */
  public String disambiguate(String[] tokenizedContext, String[] tokenTags,
    String[] lemmas, int index) {
    return disambiguate(
      new WSDSample(tokenizedContext, tokenTags, lemmas, index));
  }

}
