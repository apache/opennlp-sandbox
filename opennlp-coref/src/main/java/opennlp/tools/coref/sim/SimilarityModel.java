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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Models semantic similarity between two mentions and returns a score based on
 * how semantically comparable the mentions are with one another.
 */
public class SimilarityModel implements TestSimilarityModel, TrainSimilarityModel {

  private final String modelName;
  private final String modelExtension = ".bin";
  private MaxentModel testModel;
  private List<Event> events;
  private int SAME_INDEX;
  private static final String SAME = "same";
  private static final String DIFF = "diff";
  private final boolean debugOn = false;

  public static TestSimilarityModel testModel(String name) throws IOException {
    return new SimilarityModel(name, false);
  }

  public static TrainSimilarityModel trainModel(String name) throws IOException {
    return new SimilarityModel(name, true);
  }

  private SimilarityModel(String modelName, boolean train) throws IOException {
    this.modelName = modelName;
    if (train) {
      events = new ArrayList<>();
    }
    else {
      try (DataInputStream dis = new DataInputStream(
              new BufferedInputStream(new FileInputStream(modelName + modelExtension)))) {
        testModel = new BinaryGISModelReader(dis).getModel();
      }
      SAME_INDEX = testModel.getIndex(SAME);
    }
  }

  private void addEvent(boolean same, Context np1, Context np2) {
    if (same) {
      List<String> feats = getFeatures(np1, np2);
      //System.err.println(SAME+" "+np1.headTokenText+" ("+np1.id+") -> "+np2.headTokenText+"
      // ("+np2.id+") "+feats);
      events.add(new Event(SAME, feats.toArray(new String[0])));
    }
    else {
      List<String> feats = getFeatures(np1, np2);
      //System.err.println(DIFF+" "+np1.headTokenText+" ("+np1.id+") -> "+np2.headTokenText+"
      // ("+np2.id+") "+feats);
      events.add(new Event(DIFF, feats.toArray(new String[0])));
    }
  }

  /**
   * Produces a set of head words for the specified list of mentions.
   *
   * @param mentions The mentions to use to construct the
   *
   * @return A set containing the head words of the specified mentions.
   */
  private Set<String> constructHeadSet(List<Context> mentions) {
    Set<String> headSet = new HashSet<>();
    for (Context ec : mentions) {
      headSet.add(ec.getHeadTokenText().toLowerCase());
    }
    return headSet;
  }

  private boolean hasSameHead(Set<String> entityHeadSet, Set<String> candidateHeadSet) {
    for (String s : entityHeadSet) {
      if (candidateHeadSet.contains(s)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasSameNameType(Set<String> entityNameSet, Set<String> candidateNameSet) {
    for (String s : entityNameSet) {
      if (candidateNameSet.contains(s)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasSuperClass(List<Context> entityContexts, List<Context> candidateContexts) {
    for (Context ec : entityContexts) {
      for (Context candidateContext : candidateContexts) {
        if (inSuperClass(ec, candidateContext)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Constructs a set of entities which may be semantically compatible with the
   * entity indicated by the specified entityKey.
   *
   * @param entityKey The key of the entity for which the set is being constructed.
   * @param entities A mapping between entity keys and their mentions.
   * @param headSets A mapping between entity keys and their head sets.
   * @param nameSets A mapping between entity keys and their name sets.
   * @param singletons A list of all entities which consists of a single mentions.
   *
   * @return A set of mentions for all the entities which might be semantically compatible
   * with entity indicated by the specified key.
   */
  @SuppressWarnings("unchecked")
  private Set<Context> constructExclusionSet(Integer entityKey, Map<Integer, Context> entities, Map<Integer,
      Set<String>> headSets, Map<Integer, Set<String>> nameSets, List<Context> singletons) {
    Set<Context> exclusionSet = new HashSet<>();
    Set<String> entityHeadSet = headSets.get(entityKey);
    Set<String> entityNameSet = nameSets.get(entityKey);
    List<Context> entityContexts = (List<Context>) entities.get(entityKey);
    //entities
    for (Integer key : entities.keySet()) {
      List<Context> candidateContexts = (List<Context>) entities.get(key);
      if (key.equals(entityKey)) {
        exclusionSet.addAll(candidateContexts);
      } else if (nameSets.get(key).isEmpty()) {
        exclusionSet.addAll(candidateContexts);
      } else if (hasSameHead(entityHeadSet, headSets.get(key))) {
        exclusionSet.addAll(candidateContexts);
      } else if (hasSameNameType(entityNameSet, nameSets.get(key))) {
        exclusionSet.addAll(candidateContexts);
      } else if (hasSuperClass(entityContexts, candidateContexts)) {
        exclusionSet.addAll(candidateContexts);
      }
    }
    //singles
    List<Context> singles = new ArrayList<>(1);
    for (Context sc : singletons) {
      singles.clear();
      singles.add(sc);
      if (entityHeadSet.contains(sc.getHeadTokenText().toLowerCase())) {
        exclusionSet.add(sc);
      } else if (sc.getNameType() == null) {
        exclusionSet.add(sc);
      } else if (entityNameSet.contains(sc.getNameType())) {
        exclusionSet.add(sc);
      } else if (hasSuperClass(entityContexts, singles)) {
        exclusionSet.add(sc);
      }
    }
    return exclusionSet;
  }

  /**
   * Constructs a mapping between the specified entities and their headset.
   *
   * @param entities Mapping between a key and a list of mentions which compose an entity.
   *
   * @return a mapping between the keys of the specified entity mapping and the headset
   * generated from the mentions associated with that key.
   */
  @SuppressWarnings("unchecked")
  private Map<Integer, Set<String>> constructHeadSets(Map<Integer, Context> entities) {
    Map<Integer, Set<String>> headSets = new HashMap<>();
    for (Integer key : entities.keySet()) {
      List<Context> entityContexts = (List<Context>) entities.get(key);
      headSets.put(key, constructHeadSet(entityContexts));
    }
    return headSets;
  }

  /**
   * Produces the set of name types associated with each of the specified mentions.
   *
   * @param mentions A list of mentions.
   *
   * @return A set of name types assigned to the specified mentions.
   */
  private Set<String> constructNameSet(List<Context> mentions) {
    Set<String> nameSet = new HashSet<>();
    for (Context ec : mentions) {
      if (ec.getNameType() != null) {
        nameSet.add(ec.getNameType());
      }
    }
    return nameSet;
  }

  /**
   * Constructs a mapping between the specified entities and the names associated with these entities.
   *
   * @param entities A mapping between a key and a list of mentions.
   *
   * @return a mapping between each key in the specified entity map and the name types associated
   *         with each mention of that entity.
   */
  @SuppressWarnings("unchecked")
  private Map<Integer, Set<String>> constructNameSets(Map<Integer, Context> entities) {
    Map<Integer, Set<String>> nameSets = new HashMap<>();
    for (Integer key : entities.keySet()) {
      List<Context> entityContexts = (List<Context>) entities.get(key);
      nameSets.put(key, constructNameSet(entityContexts));
    }
    return nameSets;
  }

  private boolean inSuperClass(Context ec, Context cec) {
    if (ec.getSynsets().size() == 0 || cec.getSynsets().size() == 0) {
      return false;
    }
    else {
      int numCommonSynsets = 0;
      for (String synset : ec.getSynsets()) {
        if (cec.getSynsets().contains(synset)) {
          numCommonSynsets++;
        }
      }
      if (numCommonSynsets == 0) {
        return false;
      }
      else if (numCommonSynsets == ec.getSynsets().size() || numCommonSynsets == cec.getSynsets().size()) {
        return true;
      }
      else {
        return false;
      }
    }
  }

  /*
  private boolean isPronoun(MentionContext mention) {
    return mention.getHeadTokenTag().startsWith("PRP");
  }
  */

  @Override
  @SuppressWarnings("unchecked")
  public void setExtents(Context[] extentContexts) {
    Map<Integer, Context> entities = new HashMap<>();
    /* Extents which are not in a coreference chain. */
    List<Context> singletons = new ArrayList<>();
    List<Context> allExtents = new ArrayList<>();
    //populate data structures
    for (Context ec : extentContexts) {
      //System.err.println("SimilarityModel: setExtents: ec("+ec.getId()+") "+ec.getNameType()+" "+ec);
      if (ec.getId() == -1) {
        singletons.add(ec);
      } else {
        entities.put(ec.getId(), ec);
      }
      allExtents.add(ec);
    }

    int axi = 0;
    Map<Integer, Set<String>> headSets = constructHeadSets(entities);
    Map<Integer, Set<String>> nameSets = constructNameSets(entities);

    for (Integer key : entities.keySet()) {
      Set<String> entityNameSet = nameSets.get(key);
      if (entityNameSet.isEmpty()) {
        continue;
      }
      List<Context> entityContexts = (List<Context>) entities.get(key);
      Set<Context> exclusionSet = constructExclusionSet(key, entities, headSets, nameSets, singletons);
      if (entityContexts.size() == 1) {
      }
      for (int xi1 = 0, xl = entityContexts.size(); xi1 < xl; xi1++) {
        Context ec1 = entityContexts.get(xi1);
        //if (isPronoun(ec1)) {
        //  continue;
        //}
        for (int xi2 = xi1 + 1; xi2 < xl; xi2++) {
          Context ec2 = entityContexts.get(xi2);
          //if (isPronoun(ec2)) {
          //  continue;
          //}
          addEvent(true, ec1, ec2);
          int startIndex = axi;
          do {
            Context sec1 = allExtents.get(axi);
            axi = (axi + 1) % allExtents.size();
            if (!exclusionSet.contains(sec1)) {
              if (debugOn) System.err.println(ec1.toString() + " " + entityNameSet + " "
                      + sec1.toString() + " " + nameSets.get(sec1.getId()));
              addEvent(false, ec1, sec1);
              break;
            }
          }
          while (axi != startIndex);
        }
      }
    }
  }

  /**
   * Returns a number between 0 and 1 which represents the models belief that the specified
   * mentions are compatible. Value closer to 1 are more compatible, while values closer
   * to 0 are less compatible.
   *
   * @param mention1 The first mention to be considered.
   * @param mention2 The second mention to be considered.
   * @return a number between 0 and 1 which represents the models belief that the specified
   *         mentions are compatible.
   */
  @Override
  public double compatible(Context mention1, Context mention2) {
    List<String> feats = getFeatures(mention1, mention2);
    if (debugOn) System.err.println("SimilarityModel.compatible: feats=" + feats);
    return (testModel.eval(feats.toArray(new String[0]))[SAME_INDEX]);
  }

  /**
   * Train a model based on the previously supplied evidence.
   * @see #setExtents(Context[])
   */
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
    TrainingParameters params = TrainingParameters.defaultParams();
    params.put(TrainingParameters.ITERATIONS_PARAM, 100);
    params.put(TrainingParameters.CUTOFF_PARAM, 10);
    GISTrainer trainer = new GISTrainer();
    trainer.init(params, null);
    GISModel trainedModel = trainer.trainModel(ObjectStreamUtils.createObjectStream(events));
    new BinaryGISModelWriter(trainedModel, new File(modelName + modelExtension)).persist();
  }

  private boolean isName(Context np) {
    return np.getHeadTokenTag().startsWith("NNP");
  }

  private boolean isCommonNoun(Context np) {
    return !np.getHeadTokenTag().startsWith("NNP") && np.getHeadTokenTag().startsWith("NN");
  }

  private boolean isPronoun(Context np) {
    return np.getHeadTokenTag().startsWith("PRP");
  }

  private boolean isNumber(Context np) {
    return np.getHeadTokenTag().equals("CD");
  }

  private List<String> getNameCommonFeatures(Context name, Context common) {
    Set<String> synsets = common.getSynsets();
    List<String> features = new ArrayList<>(2 + synsets.size());
    features.add("nn=" + name.getNameType() + "," + common.getNameType());
    features.add("nw=" + name.getNameType() + "," + common.getHeadTokenText().toLowerCase());
    for (String synset : synsets) {
      features.add("ns=" + name.getNameType() + "," + synset);
    }
    if (name.getNameType() == null) {
      //features.addAll(getCommonCommonFeatures(name,common));
    }
    return features;
  }

  private List<String> getNameNumberFeatures(Context name, Context number) {
    List<String> features = new ArrayList<>(2);
    features.add("nt=" + name.getNameType() + "," + number.getHeadTokenTag());
    features.add("nn=" + name.getNameType() + "," + number.getNameType());
    return features;
  }

  private List<String> getNamePronounFeatures(Context name, Context pronoun) {
    List<String> features = new ArrayList<>(2);
    features.add("nw=" + name.getNameType() + "," + pronoun.getHeadTokenText().toLowerCase());
    features.add("ng=" + name.getNameType() + "," + ResolverUtils.getPronounGender(
        pronoun.getHeadTokenText().toLowerCase()));
    return features;
  }

  private List<String> getCommonPronounFeatures(Context common, Context pronoun) {
    List<String> features = new ArrayList<>();
    Set<String> synsets1 = common.getSynsets();
    String p = pronoun.getHeadTokenText().toLowerCase();
    String gen = ResolverUtils.getPronounGender(p);
    features.add("wn=" + p + "," + common.getNameType());
    for (String synset : synsets1) {
      features.add("ws=" + p + "," + synset);
      features.add("gs=" + gen + "," + synset);
    }
    return features;
  }

  private List<String> getCommonNumberFeatures(Context common, Context number) {
    List<String> features = new ArrayList<>();
    Set<String> synsets1 = common.getSynsets();
    for (String synset : synsets1) {
      features.add("ts=" + number.getHeadTokenTag() + "," + synset);
      features.add("ns=" + number.getNameType() + "," + synset);
    }
    features.add("nn=" + number.getNameType() + "," + common.getNameType());
    return features;
  }

  private List<String> getNumberPronounFeatures(Context number, Context pronoun) {
    List<String> features = new ArrayList<>();
    String p = pronoun.getHeadTokenText().toLowerCase();
    String gen = ResolverUtils.getPronounGender(p);
    features.add("wt=" + p + "," + number.getHeadTokenTag());
    features.add("wn=" + p + "," + number.getNameType());
    features.add("wt=" + gen + "," + number.getHeadTokenTag());
    features.add("wn=" + gen + "," + number.getNameType());
    return features;
  }

  private List<String> getNameNameFeatures(Context name1, Context name2) {
    List<String> features = new ArrayList<>(1);
    if (name1.getNameType() == null && name2.getNameType() == null) {
      features.add("nn=" + name1.getNameType() + "," + name2.getNameType());
      //features.addAll(getCommonCommonFeatures(name1,name2));
    }
    else if (name1.getNameType() == null) {
      features.add("nn=" + name1.getNameType() + "," + name2.getNameType());
      //features.addAll(getNameCommonFeatures(name2,name1));
    }
    else if (name2.getNameType() == null) {
      features.add("nn=" + name2.getNameType() + "," + name1.getNameType());
      //features.addAll(getNameCommonFeatures(name1,name2));
    }
    else {
      if (name1.getNameType().compareTo(name2.getNameType()) < 0) {
        features.add("nn=" + name1.getNameType() + "," + name2.getNameType());
      }
      else {
        features.add("nn=" + name2.getNameType() + "," + name1.getNameType());
      }
      if (name1.getNameType().equals(name2.getNameType())) {
        features.add("sameNameType");
      }
    }
    return features;
  }

  private List<String> getCommonCommonFeatures(Context common1, Context common2) {
    List<String> features = new ArrayList<>();
    Set<String> synsets1 = common1.getSynsets();
    Set<String> synsets2 = common2.getSynsets();

    if (synsets1.size() == 0) {
      //features.add("missing_"+common1.headToken);
      return features;
    }
    if (synsets2.size() == 0) {
      //features.add("missing_"+common2.headToken);
      return features;
    }
    int numCommonSynsets = 0;
    for (String synset : synsets1) {
      if (synsets2.contains(synset)) {
        features.add("ss=" + synset);
        numCommonSynsets++;
      }
    }
    if (numCommonSynsets == 0) {
      features.add("ncss");
    }
    else if (numCommonSynsets == synsets1.size() && numCommonSynsets == synsets2.size()) {
      features.add("samess");
    }
    else if (numCommonSynsets == synsets1.size()) {
      features.add("2isa1");
      //features.add("2isa1-"+(synsets2.size() - numCommonSynsets));
    }
    else if (numCommonSynsets == synsets2.size()) {
      features.add("1isa2");
      //features.add("1isa2-"+(synsets1.size() - numCommonSynsets));
    }
    return features;
  }

  private List<String> getPronounPronounFeatures(Context pronoun1, Context pronoun2) {
    List<String> features = new ArrayList<>();
    String g1 = ResolverUtils.getPronounGender(pronoun1.getHeadTokenText());
    String g2 = ResolverUtils.getPronounGender(pronoun2.getHeadTokenText());
    if (g1.equals(g2)) {
      features.add("sameGender");
    }
    else {
      features.add("diffGender");
    }
    return features;
  }

  private List<String> getFeatures(Context np1, Context np2) {
    List<String> features = new ArrayList<>();
    features.add("default");
    //  semantic categories
    String w1 = np1.getHeadTokenText().toLowerCase();
    String w2 = np2.getHeadTokenText().toLowerCase();
    if (w1.compareTo(w2) < 0) {
      features.add("ww=" + w1 + "," + w2);
    }
    else {
      features.add("ww=" + w2 + "," + w1);
    }
    if (w1.equals(w2)) {
      features.add("sameHead");
    }
    //features.add("tt="+np1.headTag+","+np2.headTag);
    if (isName(np1)) {
      if (isName(np2)) {
        features.addAll(getNameNameFeatures(np1, np2));
      }
      else if (isCommonNoun(np2)) {
        features.addAll(getNameCommonFeatures(np1, np2));
      }
      else if (isPronoun(np2)) {
        features.addAll(getNamePronounFeatures(np1, np2));
      }
      else if (isNumber(np2)) {
        features.addAll(getNameNumberFeatures(np1, np2));
      }
    }
    else if (isCommonNoun(np1)) {
      if (isName(np2)) {
        features.addAll(getNameCommonFeatures(np2, np1));
      }
      else if (isCommonNoun(np2)) {
        features.addAll(getCommonCommonFeatures(np1, np2));
      }
      else if (isPronoun(np2)) {
        features.addAll(getCommonPronounFeatures(np1, np2));
      }
      else if (isNumber(np2)) {
        features.addAll(getCommonNumberFeatures(np1, np2));
      }
      else {
        //System.err.println("unknown group for " + np1.headTokenText + " -> " + np2.headTokenText);
      }
    }
    else if (isPronoun(np1)) {
      if (isName(np2)) {
        features.addAll(getNamePronounFeatures(np2, np1));
      }
      else if (isCommonNoun(np2)) {
        features.addAll(getCommonPronounFeatures(np2, np1));
      }
      else if (isPronoun(np2)) {
        features.addAll(getPronounPronounFeatures(np1, np2));
      }
      else if (isNumber(np2)) {
        features.addAll(getNumberPronounFeatures(np2, np1));
      }
      else {
        //System.err.println("unknown group for " + np1.headTokenText + " -> " + np2.headTokenText);
      }
    }
    else if (isNumber(np1)) {
      if (isName(np2)) {
        features.addAll(getNameNumberFeatures(np2, np1));
      }
      else if (isCommonNoun(np2)) {
        features.addAll(getCommonNumberFeatures(np2, np1));
      }
      else if (isPronoun(np2)) {
        features.addAll(getNumberPronounFeatures(np1, np2));
      }
      else if (isNumber(np2)) {
      }
      else {
        //System.err.println("unknown group for " + np1.headTokenText + " -> " + np2.headTokenText);
      }
    }
    else {
      //System.err.println("unknown group for " + np1.headToken);
    }
    return (features);
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: SimilarityModel modelName < tiger/NN bear/NN");
      System.exit(1);
    }
    String modelName = args[0];
    SimilarityModel model = new SimilarityModel(modelName, false);
    //Context.wn = new WordNet(System.getProperty("WNHOME"), true);
    //Context.morphy = new Morphy(Context.wn);
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    for (String line = in.readLine(); line != null; line = in.readLine()) {
      String[] words = line.split(" ");
      double p = model.compatible(Context.parseContext(words[0]), Context.parseContext(words[1]));
      System.out.println(p + " " + model.getFeatures(Context.parseContext(words[0]),
          Context.parseContext(words[1])));
    }
  }
}
