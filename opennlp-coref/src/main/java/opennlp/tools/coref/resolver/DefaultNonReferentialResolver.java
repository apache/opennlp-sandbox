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

package opennlp.tools.coref.resolver;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.maxent.io.BinaryGISModelReader;
import opennlp.tools.ml.maxent.io.BinaryGISModelWriter;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

/**
 * Default implementation of the {@link NonReferentialResolver} interface.
 */
public class DefaultNonReferentialResolver implements NonReferentialResolver {

  private MaxentModel model;
  private List<Event> events;
  private boolean loadAsResource;
  private final boolean debugOn = false;
  private final ResolverMode mode;
  private final String modelName;
  private final String modelExtension = ".bin.gz";
  private int nonRefIndex;

  public DefaultNonReferentialResolver(String projectName, String name, ResolverMode mode)
      throws IOException {
    this.mode = mode;
    this.modelName = projectName + "/" + name + ".nr";
    if (mode == ResolverMode.TRAIN) {
      events = new ArrayList<>();
    }
    else if (mode == ResolverMode.TEST) {
      if (loadAsResource) {
        model = new BinaryGISModelReader(new DataInputStream(
            this.getClass().getResourceAsStream(modelName))).getModel();
      }
      else {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(modelName + modelExtension)))) {
          model = new BinaryGISModelReader(dis).getModel();
        }
      }
      nonRefIndex = model.getIndex(MaxentResolver.SAME);
    }
    else {
      throw new RuntimeException("unexpected mode " + mode);
    }
  }

  @Override
  public double getNonReferentialProbability(MentionContext mention) {
    List<String> features = getFeatures(mention);
    double r = model.eval(features.toArray(new String[0]))[nonRefIndex];
    if (debugOn) System.err.println(this + " " + mention.toText() + " ->  null " + r + " " + features);
    return r;
  }

  @Override
  public void addEvent(MentionContext ec) {
    List<String> features = getFeatures(ec);
    if (-1 == ec.getId()) {
      events.add(new Event(MaxentResolver.SAME, features.toArray(new String[0])));
    }
    else {
      events.add(new Event(MaxentResolver.DIFF, features.toArray(new String[0])));
    }
  }

  protected List<String> getFeatures(MentionContext mention) {
    List<String> features = new ArrayList<>();
    features.add(MaxentResolver.DEFAULT);
    features.addAll(getNonReferentialFeatures(mention));
    return features;
  }

  /**
   * Returns a list of features used to predict whether the specified mention is non-referential.
   * @param mention The mention under consideration.
   * @return a list of features used to predict whether the specified mention is non-referential.
   */
  protected List<String> getNonReferentialFeatures(MentionContext mention) {
    List<String> features = new ArrayList<>();
    Parse[] mtokens = mention.getTokenParses();
    //System.err.println("getNonReferentialFeatures: mention has "+mtokens.length+" tokens");
    for (int ti = 0; ti <= mention.getHeadTokenIndex(); ti++) {
      Parse tok = mtokens[ti];
      List<String> wfs = ResolverUtils.getWordFeatures(tok);
      for (String wf : wfs) {
        features.add("nr" + wf);
      }
    }
    features.addAll(ResolverUtils.getContextFeatures(mention));
    return features;
  }

  @Override
  public void train() throws IOException {
    if (ResolverMode.TRAIN == mode) {
      System.err.println(this + " referential");
      if (debugOn) {
        Path p = Path.of(modelName + ".events");
        try (Writer writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
          for (Event e : events) {
            writer.write(e.toString() + "\n");
          }
        }
      }
      TrainingParameters params = TrainingParameters.defaultParams();
      params.put(TrainingParameters.ITERATIONS_PARAM, 100);
      params.put(TrainingParameters.CUTOFF_PARAM, 10);
      GISTrainer trainer = new GISTrainer();
      trainer.init(params, null);
      GISModel trainedModel = trainer.trainModel(ObjectStreamUtils.createObjectStream(events));
      new BinaryGISModelWriter(trainedModel, new File(modelName + modelExtension)).persist();
    }
  }
}
