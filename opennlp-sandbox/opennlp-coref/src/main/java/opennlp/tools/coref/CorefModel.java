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

package opennlp.tools.coref;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.maxent.io.BinaryGISModelReader;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.util.StringList;
import opennlp.tools.util.model.BaseModel;

/**
 * This is the default {@link CorefModel} implementation.
 */
public class CorefModel extends BaseModel {

  @Serial
  private static final long serialVersionUID = -5040135594668553557L;

  private static final String COMPONENT_NAME = "Coref";

  private static final String MALE_NAMES_DICTIONARY_ENTRY_NAME = "maleNames.dictionary";
  private static final String FEMALE_NAMES_DICTIONARY_ENTRY_NAME = "femaleNames.dictionary";
  private static final String NUMBER_MODEL_ENTRY_NAME = "number.model";

//  private Map<String, Set<String>> acronyms;

  private static final String COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME = "commonNounResolver.model";
  private static final String DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME = "definiteNounResolver.model";
  private static final String SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME = "speechPronounResolver.model";
  private static final String PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME = "pluralNounResolver.model";
  private static final String SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME = "singularPronounResolver.model";
  private static final String PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME = "properNounResolver.model";
  private static final String PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME = "pluralPronounResolver.model";
  private static final String SIM_MODEL_ENTRY_NAME = "sim.model";

  /**
   * Initializes a {@link CorefModel} instance for the specified {@code languageCode}.
   *
   * @param languageCode The ISO language code to configure. Must not be {@code null}.
   * @param modelDir The directory in which the coref model files are located.
   *                 Must not be {@code null} and not be empty.
   * @throws IllegalArgumentException Thrown if {@code modelDir} is invalid.
   * @throws java.io.FileNotFoundException Thrown if {@code modelDir} does not exist.
   */
  public CorefModel(String languageCode, String modelDir) throws IOException {
    super(COMPONENT_NAME, languageCode, null);
    if (modelDir == null || modelDir.isBlank()) {
      throw new IllegalArgumentException("Model directory must not be null or empty");
    }

    artifactMap.put(MALE_NAMES_DICTIONARY_ENTRY_NAME,
        readNames(modelDir + File.separator + "gen.mas"));
    artifactMap.put(FEMALE_NAMES_DICTIONARY_ENTRY_NAME,
        readNames(modelDir + File.separator + "gen.fem"));

    // TODO: Create acronyms

    artifactMap.put(NUMBER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "num.bin"));
    artifactMap.put(COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "cmodel.bin"));
    artifactMap.put(DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "defmodel.bin"));
    artifactMap.put(SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "fmodel.bin"));

    // TODO: IModel

    artifactMap.put(PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "plmodel.bin"));
    artifactMap.put(SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "pmodel.bin"));
    artifactMap.put(PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "pnmodel.bin"));
    artifactMap.put(SIM_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "sim.bin"));
    artifactMap.put(PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(modelDir + File.separator + "tmodel.bin"));
    
    checkArtifactMap();
  }

  /**
   * Initializes a {@link CorefModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public CorefModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }
  
  private AbstractModel createModel(String fileName) throws IOException {
    AbstractModel model;
    if(fileName != null && fileName.endsWith(".gz")) {
      model = readCompressed(fileName);
    } else {
      model = read(fileName);
    }
    return model;
  }

  private AbstractModel read(String fileName) throws IOException {
    try (DataInputStream dis = new DataInputStream(new BufferedInputStream(
            new FileInputStream(fileName)))) {
      return new BinaryGISModelReader(dis).getModel();
    }
  }

  private AbstractModel readCompressed(String fileName) throws IOException {
    try (DataInputStream dis = new DataInputStream(new BufferedInputStream(
            new GZIPInputStream(new FileInputStream(fileName))))) {
      return new BinaryGISModelReader(dis).getModel();
    }
  }


  private static Dictionary readNames(String nameFile) throws IOException {
    try (BufferedReader nameReader = new BufferedReader(new FileReader(nameFile))) {
      Dictionary names = new Dictionary();
      for (String line = nameReader.readLine(); line != null; line = nameReader.readLine()) {
        names.put(new StringList(line));
      }
      return names;
    }
  }

  public Dictionary getMaleNames() {
    return (Dictionary) artifactMap.get(MALE_NAMES_DICTIONARY_ENTRY_NAME);
  }

  public Dictionary getFemaleNames() {
    return (Dictionary) artifactMap.get(FEMALE_NAMES_DICTIONARY_ENTRY_NAME);
  }

  public AbstractModel getNumberModel() {
    return (AbstractModel) artifactMap.get(NUMBER_MODEL_ENTRY_NAME);
  }

//  public AcronymDictionary getAcronyms() {
//    return null;
//  }

  public AbstractModel getCommonNounResolverModel() {
    return (AbstractModel) artifactMap.get(COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getDefiniteNounResolverModel() {
    return (AbstractModel) artifactMap.get(DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSpeechPronounResolverModel() {
    return (AbstractModel) artifactMap.get(SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getPluralNounResolverModel() {
    return (AbstractModel) artifactMap.get(PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSingularPronounResolverModel() {
    return (AbstractModel) artifactMap.get(SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getProperNounResolverModel() {
    return (AbstractModel) artifactMap.get(PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSimModel() {
    return (AbstractModel) artifactMap.get(SIM_MODEL_ENTRY_NAME);
  }

  public AbstractModel getPluralPronounResolverModel() {
    return (AbstractModel) artifactMap.get(PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getLanguage(),
            artifactMap.get(MANIFEST_ENTRY),
            artifactMap.get(FEMALE_NAMES_DICTIONARY_ENTRY_NAME),
            artifactMap.get(MALE_NAMES_DICTIONARY_ENTRY_NAME));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof CorefModel model) {
      Map<String, Object> artifactMapToCheck = model.artifactMap;
      return getLanguage().equals(model.getLanguage()) &&
        artifactMap.get(MANIFEST_ENTRY).equals(artifactMapToCheck.get(MANIFEST_ENTRY)) &&
        artifactMap.get(FEMALE_NAMES_DICTIONARY_ENTRY_NAME).equals(artifactMapToCheck.get(FEMALE_NAMES_DICTIONARY_ENTRY_NAME)) &&
        artifactMap.get(MALE_NAMES_DICTIONARY_ENTRY_NAME).equals(artifactMapToCheck.get(MALE_NAMES_DICTIONARY_ENTRY_NAME));
    }
    return false;
  }
}
