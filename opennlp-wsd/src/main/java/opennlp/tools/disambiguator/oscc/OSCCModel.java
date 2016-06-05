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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

// TODO remove this class later
public class OSCCModel extends BaseModel {

  private static final String COMPONENT_NAME = "OSCCME";
  private static final String OSCC_MODEL_ENTRY_NAME = "OSCC.model";

  private static final String WORDTAG = "wordtag";
  private static final String WINSIZE = "winsize";
  private static final String CONTEXTCLUSTERS = "contextclusters";

  private ArrayList<String> contextClusters = new ArrayList<String>();
  private String wordTag;
  private int windowSize;

  public ArrayList<String> getContextClusters() {
    return contextClusters;
  }

  public int getWindowSize() {
    return windowSize;
  }

  public void setWindowSize(int windowSize) {
    this.windowSize = windowSize;
  }

  public void setContextClusters(ArrayList<String> contextClusters) {
    this.contextClusters = contextClusters;
  }

  public String getWordTag() {
    return wordTag;
  }

  public void setWordTag(String wordTag) {
    this.wordTag = wordTag;
  }

  public OSCCModel(String languageCode, String wordTag, int windowSize,
    MaxentModel osccModel, ArrayList<String> contextClusters,
    Map<String, String> manifestInfoEntries, OSCCFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);

    artifactMap.put(OSCC_MODEL_ENTRY_NAME, osccModel);
    this.setManifestProperty(WORDTAG, wordTag);
    this.setManifestProperty(WINSIZE, windowSize + "");

    this.setManifestProperty(CONTEXTCLUSTERS,
      StringUtils.join(contextClusters, ","));

    this.contextClusters = contextClusters;
    checkArtifactMap();
  }

  public OSCCModel(String languageCode, String wordTag, int windowSize,
    int ngram, MaxentModel osccModel, ArrayList<String> contextClusters,
    OSCCFactory factory) {
    this(languageCode, wordTag, windowSize, osccModel, contextClusters, null,
      factory);
  }

  public OSCCModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
    updateAttributes();
  }

  public OSCCModel(File modelFile) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelFile);
    updateAttributes();
  }

  public OSCCModel(URL modelURL) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
    updateAttributes();
  }

  // path must include the word.tag i.e. : write.v
  public boolean writeModel(String path) {
    File outFile = new File(path + ".oscc.model");
    CmdLineUtil.writeModel("oscc model", outFile, this);
    return true;
  }

  @Override protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(OSCC_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("OSCC model is incomplete!");
    }
  }

  public MaxentModel getOSCCMaxentModel() {
    if (artifactMap.get(OSCC_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(OSCC_MODEL_ENTRY_NAME);
    } else {
      return null;
    }
  }

  public void updateAttributes() {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    String contextClusters = (String) manifest.get(CONTEXTCLUSTERS);

    this.contextClusters = new ArrayList(
      Arrays.asList(contextClusters.split(",")));
    this.wordTag = (String) manifest.get(WORDTAG);
    this.windowSize = Integer.parseInt((String) manifest.get(WINSIZE));
  }

  @Override protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return OSCCFactory.class;
  }

  public OSCCFactory getFactory() {
    return (OSCCFactory) this.toolFactory;
  }

}