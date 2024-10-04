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
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * Class which models the gender of a particular mentions and entities made up of mentions.
 */
public class GenderModel implements TestGenderModel, TrainSimilarityModel {

  private int maleIndex;
  private int femaleIndex;
  private int neuterIndex;

  private final String modelName;
  private final String modelExtension = ".bin";
  private MaxentModel testModel;
  private Collection<Event> events;
  private final boolean debugOn = true;

  private final Set<String> maleNames;
  private final Set<String> femaleNames;

  public static GenderModel testModel(String name) throws IOException {
    return new GenderModel(name, false);
  }

  public static GenderModel trainModel(String name) throws IOException {
    return new GenderModel(name, true);
  }

  private Set<String> readNames(String nameFile) throws IOException {
    Set<String> names = new HashSet<>();
    try (BufferedReader nameReader = new BufferedReader(new FileReader(nameFile))) {
      for (String line = nameReader.readLine(); line != null; line = nameReader.readLine()) {
        names.add(line);
      }
      return names;
    }
  }

  private GenderModel(String modelName, boolean train) throws IOException {
    this.modelName = modelName;
    maleNames = readNames(modelName + ".mas");
    femaleNames = readNames(modelName + ".fem");
    if (train) {
      events = new ArrayList<>();
    }
    else {
      try (DataInputStream dis = new DataInputStream(
              new BufferedInputStream(new FileInputStream(modelName + modelExtension)))) {
        testModel = new BinaryGISModelReader(dis).getModel();
        maleIndex = testModel.getIndex(GenderEnum.MALE.toString());
        femaleIndex = testModel.getIndex(GenderEnum.FEMALE.toString());
        neuterIndex = testModel.getIndex(GenderEnum.NEUTER.toString());
      }
    }
  }

  private List<String> getFeatures(Context np1) {
    List<String> features = new ArrayList<>();
    features.add("default");
    for (int ti = 0, tl = np1.getHeadTokenIndex(); ti < tl; ti++) {
      features.add("mw=" + np1.getTokens()[ti].toString());
    }
    features.add("hw=" + np1.getHeadTokenText());
    features.add("n=" + np1.getNameType());
    if (np1.getNameType() != null && np1.getNameType().equals("person")) {
      Object[] tokens = np1.getTokens();
      //System.err.println("GenderModel.getFeatures: person name="+np1);
      for (int ti = 0; ti < np1.getHeadTokenIndex() || ti == 0; ti++) {
        String name = tokens[ti].toString().toLowerCase();
        if (femaleNames.contains(name)) {
          features.add("fem");
          //System.err.println("GenderModel.getFeatures: person (fem) "+np1);
        }
        if (maleNames.contains(name)) {
          features.add("mas");
          //System.err.println("GenderModel.getFeatures: person (mas) "+np1);
        }
      }
    }

    for (String si : np1.getSynsets()) {
      features.add("ss=" + si);
    }
    return features;
  }

  private void addEvent(String outcome, Context np1) {
    List<String> feats = getFeatures(np1);
    events.add(new Event(outcome, feats.toArray(new String[0])));
  }

  /**
   * Heuristic computation of gender for a mention context using pronouns and honorifics.
   * @param mention The mention whose gender is to be computed.
   * @return The heuristically determined gender or unknown.
   */
  private GenderEnum getGender(Context mention) {
    if (ResolverUtils.MALE_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.MALE;
    }
    else if (ResolverUtils.FEMALE_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.FEMALE;
    }
    else if (ResolverUtils.NEUTER_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.NEUTER;
    }
    Object[] mtokens = mention.getTokens();
    for (int ti = 0, tl = mtokens.length - 1; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      if (token.equals("Mr.") || token.equals("Mr")) {
        return GenderEnum.MALE;
      }
      else if (token.equals("Mrs.") || token.equals("Mrs") || token.equals("Ms.") || token.equals("Ms")) {
        return GenderEnum.FEMALE;
      }
    }

    return GenderEnum.UNKNOWN;
  }

  private GenderEnum getGender(List<Context> entity) {
    for (Context ec : entity) {
      GenderEnum ge = getGender(ec);
      if (ge != GenderEnum.UNKNOWN) {
        return ge;
      }
    }

    return GenderEnum.UNKNOWN;
  }

  @SuppressWarnings("unchecked")
  public void setExtents(Context[] extentContexts) {
    HashMap<Integer,Context> entities = new HashMap<>();
    List<Context> singletons = new ArrayList<>();
    for (Context ec : extentContexts) {
      //System.err.println("GenderModel.setExtents: ec("+ec.getId()+") "+ec.toText());
      if (ec.getId() != -1) {
        entities.put(ec.getId(), ec);
      } else {
        singletons.add(ec);
      }
    }
    List<Context> males = new ArrayList<>();
    List<Context> females = new ArrayList<>();
    List<Context> eunuches = new ArrayList<>();
    //coref entities
    for (Integer key : entities.keySet()) {
      List<Context> entityContexts = (List<Context>) entities.get(key);
      GenderEnum gender = getGender(entityContexts);
      if (gender != null) {
        if (gender == GenderEnum.MALE) {
          males.addAll(entityContexts);
        } else if (gender == GenderEnum.FEMALE) {
          females.addAll(entityContexts);
        } else if (gender == GenderEnum.NEUTER) {
          eunuches.addAll(entityContexts);
        }
      }
    }
    //non-coref entities
    for (Context ec : singletons) {
      GenderEnum gender = getGender(ec);
      if (gender == GenderEnum.MALE) {
        males.add(ec);
      } else if (gender == GenderEnum.FEMALE) {
        females.add(ec);
      } else if (gender == GenderEnum.NEUTER) {
        eunuches.add(ec);
      }
    }
    for (Context ec : males) {
      addEvent(GenderEnum.MALE.toString(), ec);
    }
    for (Context ec : females) {
      addEvent(GenderEnum.FEMALE.toString(), ec);
    }
    for (Context ec : eunuches) {
      addEvent(GenderEnum.NEUTER.toString(), ec);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: GenderModel modelName < tiger/NN bear/NN");
      System.exit(1);
    }
    String modelName = args[0];
    GenderModel model = new GenderModel(modelName, false);
    //Context.wn = new WordNet(System.getProperty("WNHOME"), true);
    //Context.morphy = new Morphy(Context.wn);
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    for (String line = in.readLine(); line != null; line = in.readLine()) {
      String[] words = line.split(" ");
      double[] dist = model.genderDistribution(Context.parseContext(words[0]));
      System.out.println("m=" + dist[model.getMaleIndex()] + " f=" + dist[model.getFemaleIndex()]
          + " n=" + dist[model.getNeuterIndex()] + " " + model.getFeatures(Context.parseContext(words[0])));
    }
  }

  @Override
  public double[] genderDistribution(Context np1) {
    List<String> features = getFeatures(np1);
    //System.err.println("GenderModel.genderDistribution: "+features);
    return testModel.eval(features.toArray(new String[0]));
  }

  @Override
  public void trainModel() throws IOException {
    if (debugOn) {
      Path p = Path.of(modelName + ".events");
      try (Writer writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
              StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        for (Event e : events) {
          writer.write(e.toString() + "\n");
        }
      }
    }
    GISTrainer trainer = new GISTrainer();
    trainer.init(TrainingParameters.defaultParams(), null);
    trainer.setSmoothing(true);
    GISModel trainedModel = trainer.trainModel(ObjectStreamUtils.createObjectStream(events));
    new BinaryGISModelWriter(trainedModel, new File(modelName + modelExtension)).persist();
  }

  @Override
  public int getFemaleIndex() {
    return femaleIndex;
  }

  @Override
  public int getMaleIndex() {
    return maleIndex;
  }

  @Override
  public int getNeuterIndex() {
    return neuterIndex;
  }
}
