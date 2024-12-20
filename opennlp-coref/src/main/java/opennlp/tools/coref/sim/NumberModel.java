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

package opennlp.tools.coref.sim;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.coref.resolver.ResolverUtils;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.maxent.io.BinaryGISModelReader;
import opennlp.tools.ml.maxent.io.BinaryGISModelWriter;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;


/**
 * Class which models the number of particular mentions and the entities made up of mentions.
 */
public class NumberModel implements TestNumberModel, TrainModel<NumberModel> {

  private static final Logger logger = LoggerFactory.getLogger(NumberModel.class);
  private static final double MIN_NUMBER_PROB = 0.66;

  private final String modelName;
  private MaxentModel meModel;
  private List<Event> events;

  // TODO: Note those need to be written / serialized to the binary model file
  private int singularIndex;
  private int pluralIndex;

  public static NumberModel testModel(String name) throws IOException {
    return new NumberModel(name, false);
  }

  public static NumberModel trainModel(String modelName) throws IOException {
    return new NumberModel(modelName, true);
  }

  private NumberModel(String modelName, boolean train) throws IOException {
    this.modelName = modelName;
    if (train) {
      events = new ArrayList<>();
    } else {
      try (DataInputStream dis = new DataInputStream(
              new BufferedInputStream(new FileInputStream(modelName + MODEL_EXTENSION)))) {
        meModel = new BinaryGISModelReader(dis).getModel();
      }
      singularIndex = meModel.getIndex(NumberEnum.SINGULAR.toString());
      pluralIndex = meModel.getIndex(NumberEnum.PLURAL.toString());
    }
  }

  private List<String> getFeatures(Context np1) {
    List<String> features = new ArrayList<>();
    features.add("default");
    Object[] npTokens = np1.getTokens();
    for (int ti = 0, tl = npTokens.length - 1; ti < tl; ti++) {
      features.add("mw=" + npTokens[ti].toString());
    }
    features.add("hw=" + np1.getHeadTokenText().toLowerCase());
    features.add("ht=" + np1.getHeadTokenTag());
    return features;
  }

  private void addEvent(String outcome, Context np1) {
    List<String> feats = getFeatures(np1);
    events.add(new Event(outcome, feats.toArray(new String[0])));
  }

  public NumberEnum getNumber(Context ec) {
    if (ResolverUtils.SINGULAR_PRONOUN_PATTERN.matcher(ec.getHeadTokenText()).matches()) {
      return NumberEnum.SINGULAR;
    }
    else if (ResolverUtils.PLURAL_PRONOUN_PATTERN.matcher(ec.getHeadTokenText()).matches()) {
      return NumberEnum.PLURAL;
    }
    else {
      return NumberEnum.UNKNOWN;
    }
  }

  @Override
  public void setExtents(Context[] extentContexts) {
    Map<Integer,Context> entities = new HashMap<>();
    List<Context> singletons = new ArrayList<>();
    for (Context ec : extentContexts) {
      if (ec != null) {
        logger.debug("NumberModel.setExtents: ec({}) {}", ec.getId(), ec);
        if (ec.getId() != -1) {
          entities.put(ec.getId(), ec);
        } else {
          singletons.add(ec);
        }
      }
    }
    List<Context> singles = new ArrayList<>();
    List<Context> plurals = new ArrayList<>();
    // coref entities
    for (Integer key : entities.keySet()) {
      Context entityContext = entities.get(key);
      NumberEnum number = getNumber(entityContext);
      if (number == NumberEnum.SINGULAR) {
        singles.add(entityContext);
      } else if (number == NumberEnum.PLURAL) {
        plurals.add(entityContext);
      }
    }
    // non-coref entities.
    for (Context ec : singletons) {
      NumberEnum number = getNumber(ec);
      if (number == NumberEnum.SINGULAR) {
        singles.add(ec);
      } else if (number == NumberEnum.PLURAL) {
        plurals.add(ec);
      }
    }

    for (Context ec : singles) {
      addEvent(NumberEnum.SINGULAR.toString(), ec);
    }
    for (Context ec : plurals) {
      addEvent(NumberEnum.PLURAL.toString(), ec);
    }
  }

  @Override
  public double[] numberDist(Context c) {
    List<String> feats = getFeatures(c);
    return meModel.eval(feats.toArray(new String[0]));
  }

  @Override
  public int getSingularIndex() {
    return singularIndex;
  }

  @Override
  public int getPluralIndex() {
    return pluralIndex;
  }

  public Number computeNumber(Context c) {
    double[] dist = numberDist(c);
    Number number;
    logger.debug("Computing number: {} sing={} plural={}", c, dist[getSingularIndex()], dist[getPluralIndex()]);
    if (dist[getSingularIndex()] > MIN_NUMBER_PROB) {
      number = new Number(NumberEnum.SINGULAR,dist[getSingularIndex()]);
    }
    else if (dist[getPluralIndex()] > MIN_NUMBER_PROB) {
      number = new Number(NumberEnum.PLURAL,dist[getPluralIndex()]);
    }
    else {
      number = new Number(NumberEnum.UNKNOWN, MIN_NUMBER_PROB);
    }
    return number;
  }

  @Override
  public NumberModel trainModel() throws IOException {
    TrainingParameters params = TrainingParameters.defaultParams();
    params.put(TrainingParameters.ITERATIONS_PARAM, 100);
    params.put(TrainingParameters.CUTOFF_PARAM, 10);
    GISTrainer trainer = new GISTrainer();
    trainer.init(params, null);
    GISModel trainedModel = trainer.trainModel(ObjectStreamUtils.createObjectStream(events));
    this.meModel = trainedModel;
    new BinaryGISModelWriter(trainedModel, new File(modelName + MODEL_EXTENSION)).persist();
    return this;
  }
}
