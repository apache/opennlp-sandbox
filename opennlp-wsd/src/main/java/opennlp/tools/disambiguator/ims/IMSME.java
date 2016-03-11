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

package opennlp.tools.disambiguator.ims;

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

public class IMSME extends WSDisambiguator {

  protected IMSModel imsModel;

  protected static IMSContextGenerator cg = new DefaultIMSContextGenerator();

  public IMSME(IMSParameters params) {
    this.params = params;
  }

  public IMSME(IMSModel model, IMSParameters params) {
    this.imsModel = model;
    this.params = params;
  }

  public IMSModel getModel() {
    return imsModel;
  }

  public void setModel(IMSModel model) {
    this.imsModel = model;
  }

  public void setParameters(IMSParameters parameters) {
    this.params = parameters;
  }

  public static IMSModel train(String lang, ObjectStream<WSDSample> samples,
      TrainingParameters mlParams, IMSParameters imsParams,
      IMSFactory imsfactory) throws IOException {

    ArrayList<String> surroundingWordModel = buildSurroundingWords(samples, imsParams.getWindowSize());

    HashMap<String, String> manifestInfoEntries = new HashMap<String, String>();

    MaxentModel imsModel = null;

    ArrayList<Event> events = new ArrayList<Event>();
    ObjectStream<Event> es = null;

    WSDSample sample = samples.read();
    String wordTag = "";
    if (sample != null) {
      wordTag = sample.getTargetWordTag();
      do {

        String sense = sample.getSenseIDs().get(0);

        String[] context = cg.getContext(sample, imsParams.ngram,
            imsParams.windowSize, surroundingWordModel);
        Event ev = new Event(sense + "", context);

        events.add(ev);

      } while ((sample = samples.read()) != null);
    }

    es = ObjectStreamUtils.createObjectStream(events);

    EventTrainer trainer = TrainerFactory
        .getEventTrainer(mlParams.getSettings(), manifestInfoEntries);
    imsModel = trainer.train(es);

    return new IMSModel(lang, wordTag, imsParams.windowSize, imsParams.ngram,
        imsModel, surroundingWordModel, manifestInfoEntries, imsfactory);
  }

  public static ArrayList<String> buildSurroundingWords(
      ObjectStream<WSDSample> samples, int windowSize) throws IOException {
    DefaultIMSContextGenerator imsCG = new DefaultIMSContextGenerator();
    ArrayList<String> surroundingWordsModel = new ArrayList<String>();
    WSDSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = imsCG.extractSurroundingWords(sample.getTargetPosition(),
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

  @Override
  public String[] disambiguate(WSDSample sample) {
    if (WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      String wordTag = sample.getTargetWordTag();

      if (imsModel == null
          || !imsModel.getWordTag().equals(sample.getTargetWordTag())) {

        String trainingFile = ((IMSParameters) this.getParams())
            .getTrainingDataDirectory() + sample.getTargetWordTag();

        File file = new File(trainingFile + ".ims.model");
        if (file.exists() && !file.isDirectory()) {
          try {
            setModel(new IMSModel(file));

          } catch (InvalidFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }

          String outcome = "";

          String[] context = cg.getContext(sample,
              ((IMSParameters) this.params).ngram,
              ((IMSParameters) this.params).windowSize,
              imsModel.getSurroundingWords());

          double[] outcomeProbs = imsModel.getIMSMaxentModel().eval(context);
          outcome = imsModel.getIMSMaxentModel().getBestOutcome(outcomeProbs);

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
            ((IMSParameters) this.params).ngram,
            ((IMSParameters) this.params).windowSize,
            imsModel.getSurroundingWords());

        double[] outcomeProbs = imsModel.getIMSMaxentModel().eval(context);
        outcome = imsModel.getIMSMaxentModel().getBestOutcome(outcomeProbs);

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
        String s = IMSParameters.SenseSource.WSDHELPER.name() + " "
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
