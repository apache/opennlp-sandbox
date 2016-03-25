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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

public class WSDModel extends BaseModel {

  private static final String COMPONENT_NAME = "WSD";
  private static final String WSD_MODEL_ENTRY_NAME = "WSD.model";

  private static final String WORDTAG = "wordtag";
  private static final String WINSIZE = "winsize";
  private static final String NGRAM = "ngram";
  private static final String CONTEXT = "context";

  private ArrayList<String> contextEntries = new ArrayList<String>();
  private String wordTag;
  private int windowSize;
  private int ngram;

  public ArrayList<String> getContextEntries() {
    return contextEntries;
  }

  public int getWindowSize() {
    return windowSize;
  }

  public void setWindowSize(int windowSize) {
    this.windowSize = windowSize;
  }

  public int getNgram() {
    return ngram;
  }

  public void setNgram(int ngram) {
    this.ngram = ngram;
  }

  public void setContextEntries(ArrayList<String> contextEntries) {
    this.contextEntries = contextEntries;
  }

  public String getWordTag() {
    return wordTag;
  }

  public void setWordTag(String wordTag) {
    this.wordTag = wordTag;
  }

  public WSDModel(String languageCode, String wordTag, int windowSize,
      int ngram, MaxentModel wsdModel, ArrayList<String> contextEntries,
      Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    artifactMap.put(WSD_MODEL_ENTRY_NAME, wsdModel);
    this.setManifestProperty(WORDTAG, wordTag);
    this.setManifestProperty(WINSIZE, windowSize + "");
    this.setManifestProperty(NGRAM, ngram + "");
    this.setManifestProperty(CONTEXT, StringUtils.join(contextEntries, ","));

    this.contextEntries = contextEntries;
    checkArtifactMap();
  }

  public WSDModel(String languageCode, String wordTag, int windowSize,
      int ngram, MaxentModel wsdModel, ArrayList<String> surroundingWords) {
    this(languageCode, wordTag, windowSize, ngram, wsdModel, surroundingWords,
        null);
  }

  public WSDModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
    updateAttributes();
  }

  public WSDModel(File modelFile) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelFile);
    updateAttributes();
  }

  public WSDModel(URL modelURL) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
    updateAttributes();
  }

  // path must include the word.tag i.e. : write.v
  public boolean writeModel(String path) {
    File outFile = new File(path + ".wsd.model");
    CmdLineUtil.writeModel("wsd model", outFile, this);
    return true;
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(WSD_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("WSD model is incomplete!");
    }
  }

  public MaxentModel getWSDMaxentModel() {
    if (artifactMap.get(WSD_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(WSD_MODEL_ENTRY_NAME);
    } else {
      return null;
    }
  }

  public void updateAttributes() {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    String surroundings = (String) manifest.get(CONTEXT);

    this.contextEntries = new ArrayList(Arrays.asList(surroundings.split(",")));
    this.wordTag = (String) manifest.get(WORDTAG);
    this.windowSize = Integer.parseInt((String) manifest.get(WINSIZE));
    this.ngram = Integer.parseInt((String) manifest.get(NGRAM));
  }

}