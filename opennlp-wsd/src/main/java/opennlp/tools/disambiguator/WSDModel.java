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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link WSDModel} is the model used by a learnable {@link WSDisambiguatorME}.
 *
 * @see BaseModel
 * @see WSDisambiguatorME
 */
public class WSDModel extends BaseModel {

  @Serial
  private static final long serialVersionUID = 2961852011373749729L;

  private static final String COMPONENT_NAME = "WSD";
  private static final String WSD_MODEL_ENTRY = "WSD.model";

  private static final String WORDTAG = "wordtag";
  private static final String WINSIZE = "winsize";
  private static final String NGRAM = "ngram";
  private static final String CONTEXT = "context";

  private List<String> contextEntries = new ArrayList<>();

  /**
   * Initializes a {@link WSDModel} instance via a {@link MaxentModel} and related resources.
   *
   * @param languageCode        The ISO language code for this model. Must not be {@code null}.
   * @param wordTag             A combination of word and POS tag, separated by a {@code .} character.
   *                            Specifies what the corresponding model shall detect.
   * @param ngram               The number corresponding to the length of the n-gram, e.g. 2, 3, etc.
   * @param windowSize          The window size to use. Must be greater than {@code zero}.
   * @param wsdModel            The {@link MaxentModel model} to be used.
   * @param contextEntries      A {@link List} of entries representing context.
   * @param manifestInfoEntries Additional information kept in the manifest.
   */
  public WSDModel(String languageCode, String wordTag, int windowSize, int ngram,
                  MaxentModel wsdModel, List<String> contextEntries,
                  Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    artifactMap.put(WSD_MODEL_ENTRY, wsdModel);
    setManifestProperty(WORDTAG, wordTag);
    setManifestProperty(WINSIZE, String.valueOf(windowSize));
    setManifestProperty(NGRAM, String.valueOf(ngram));
    setManifestProperty(CONTEXT, String.join(",", contextEntries));

    this.contextEntries = contextEntries;
    checkArtifactMap();
  }

  /**
   * Initializes a {@link WSDModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public WSDModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
    updateAttributes((Properties) artifactMap.get(MANIFEST_ENTRY));
  }

  /**
   * Initializes a {@link WSDModel} instance via a valid {@link InputStream}.
   *
   * @param modelFile The {@link File} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public WSDModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
    updateAttributes((Properties) artifactMap.get(MANIFEST_ENTRY));
  }

  /**
   * Initializes a {@link WSDModel} instance via a valid {@link Path}.
   *
   * @param modelPath The {@link Path} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public WSDModel(Path modelPath) throws IOException {
    this(modelPath.toFile());
  }

  /**
   * Initializes a {@link WSDModel} instance via a valid {@link InputStream}.
   *
   * @param modelURL The {@link URL} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public WSDModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
    updateAttributes((Properties) artifactMap.get(MANIFEST_ENTRY));
  }

  /**
   * @return Retrieves the {@link List} of entries representing context.
   */
  public List<String> getContextEntries() {
    return contextEntries;
  }
  
  /**
   * @return Retrieves the current window size value.
   */
  public int getWindowSize() {
    return Integer.parseInt(getManifestProperty(WINSIZE));
  }

  /**
   * @return Retrieves the current n-gram value.
   */
  public int getNgram() {
    return Integer.parseInt(getManifestProperty(NGRAM));
  }

  /**
   * @return Retrieves the {@code word.tag} value.
   */
  public String getWordTag() {
    return getManifestProperty(WORDTAG);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return WSDisambiguatorFactory.class;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(getArtifact(WSD_MODEL_ENTRY) instanceof AbstractModel)) {
      throw new InvalidFormatException("WSD model is incomplete!");
    }
  }

  MaxentModel getWSDMaxentModel() {
    if (getArtifact(WSD_MODEL_ENTRY) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(WSD_MODEL_ENTRY);
    } else {
      return null;
    }
  }

  private void updateAttributes(Properties manifest) {
    String surroundings = (String) manifest.get(CONTEXT);
    this.contextEntries = Arrays.asList(surroundings.split(","));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(artifactMap.get(MANIFEST_ENTRY), artifactMap.get(WSD_MODEL_ENTRY));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof WSDModel model) {
      Map<String, Object> artifactMapToCheck = model.artifactMap;
      AbstractModel abstractModel = (AbstractModel) artifactMapToCheck.get(WSD_MODEL_ENTRY);

      return artifactMap.get(MANIFEST_ENTRY).equals(artifactMapToCheck.get(MANIFEST_ENTRY)) &&
              artifactMap.get(WSD_MODEL_ENTRY).equals(abstractModel);
    }
    return false;
  }
}