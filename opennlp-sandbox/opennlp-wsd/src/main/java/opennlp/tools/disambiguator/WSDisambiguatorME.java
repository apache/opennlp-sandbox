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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

/**
 * A {@link Disambiguator} implementation based on a Maximum Entropy (ME) approach.
 * <p>
 * This approach returns the senses based on a classification via
 * a pre-trained {@link WSDModel}.
 *
 * @see Disambiguator
 * @see WSDModel
 * @see WSDParameters
 */
public class WSDisambiguatorME extends AbstractWSDisambiguator {

  private final WSDContextGenerator cg;
  private final WSDModel model;

  /**
   * Instantiates a {@link WSDisambiguatorME} with the specified {@code model} and {@code params}.
   * 
   * @param model   The {@link WSDModel} to use in this instance. Must not be {@code null}.
   * @param params  The {@link WSDParameters} to set. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if specified parameters are invalid.
   */
  public WSDisambiguatorME(WSDModel model, WSDParameters params) {
    super(params);
    if (model == null || params == null) {
      throw new IllegalArgumentException("Parameters cannot be null!");
    }
    this.model = model;
    WSDisambiguatorFactory factory = model.getWSDFactory();
    cg = factory.getContextGenerator();
  }

  /**
   * @return Retrieves the {@link WSDModel} used by this instance.
   */
  public WSDModel getModel() {
    return model;
  }
  
  /**
   * Trains a {@link WSDModel model} for a {@link WSDisambiguatorME}.
   *
   * @param lang    The ISO language code. Must not be {@code null}.
   * @param samples The samples used for the training. Must not be {@code null}.
   * @param mlParams The machine learning {@link TrainingParameters train parameters}.
   * @param params The {@link WSDParameters WSD parameters}.
   * @return A trained {@link WSDModel}.
   *
   * @throws IOException Thrown during IO operations on a temp file which is created
   *           during training. Or if reading from the {@link ObjectStream} fails.
   */
  public static WSDModel train(String lang, ObjectStream<WSDSample> samples,
                               TrainingParameters mlParams, WSDParameters params,
                               WSDisambiguatorFactory factory) throws IOException {

    final WSDDefaultParameters defParams = ((WSDDefaultParameters) params);
    final int wSize = defParams.getIntParameter(
            WSDDefaultParameters.WINDOW_SIZE_PARAM, WSDDefaultParameters.WINDOW_SIZE_DEFAULT);
    final int ngram = defParams.getIntParameter(
            WSDDefaultParameters.NGRAM_PARAM, WSDDefaultParameters.NGRAM_DEFAULT);
    List<String> surroundingContext = buildSurroundingContext(samples, wSize);

    List<Event> events = new ArrayList<>();
    WSDSample sample = samples.read();
    String wordTag = "";
    if (sample != null) {
      final WSDContextGenerator cg = factory.getContextGenerator();
      wordTag = sample.getTargetWordTag();
      do {
        String sense = sample.getSenseIDs()[0];
        String[] context = cg.getContext(sample, ngram, wSize, surroundingContext);
        Event ev = new Event(sense, context);
        events.add(ev);
      } while ((sample = samples.read()) != null);
    }

    final Map<String, String> manifestInfoEntries = new HashMap<>();
    ObjectStream<Event> es = ObjectStreamUtils.createObjectStream(events);
    EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
    MaxentModel meModel = trainer.train(es);

    return new WSDModel(lang, wordTag, wSize, ngram, meModel, surroundingContext, manifestInfoEntries);
  }

  private static List<String> buildSurroundingContext(ObjectStream<WSDSample> samples,
                                                      int windowSize) throws IOException {
    IMSWSDContextGenerator cg = new IMSWSDContextGenerator();
    List<String> surroundingWordsModel = new ArrayList<>();
    WSDSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = cg.extractSurroundingContext(sample.getTargetPosition(),
          sample.getSentence(), sample.getLemmas(), windowSize);

      if (words.length > 0) {
        surroundingWordsModel.addAll(Arrays.asList(words));
      }
    }
    samples.reset();
    return surroundingWordsModel;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public String disambiguate(WSDSample sample) {
    if (sample == null) {
      throw new IllegalArgumentException("WSDSample object must not be null!");
    }
    final WSDDefaultParameters defParams = ((WSDDefaultParameters) getParams());
    final int wSize = defParams.getIntParameter(
            WSDDefaultParameters.WINDOW_SIZE_PARAM, WSDDefaultParameters.WINDOW_SIZE_DEFAULT);
    final int ngram = defParams.getIntParameter(
            WSDDefaultParameters.NGRAM_PARAM, WSDDefaultParameters.NGRAM_DEFAULT);

    final String wordTag = sample.getTargetWordTag();
    if (WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      if (!model.getWordTag().equals(wordTag)) {
        return disambiguate(wordTag);
      } else {
        String[] context = cg.getContext(sample, wSize, ngram, this.model.getContextEntries());
        double[] outcomeProbs = model.getWSDMaxentModel().eval(context);
        String outcome = model.getWSDMaxentModel().getBestOutcome(outcomeProbs);

        if (outcome != null && !outcome.isEmpty()) {
          return getParams().getSenseSource().name() + " " + wordTag.split("\\.")[0] + "%" + outcome;
        } else {
          return disambiguate(wordTag);
        }
      }
    } else {
      if (WSDHelper.getNonRelevWordsDef(wordTag) != null) {
        return WSDParameters.SenseSource.WSDHELPER.name() + " " + wordTag;
      } else {
        return null;
      }
    }
  }
}
