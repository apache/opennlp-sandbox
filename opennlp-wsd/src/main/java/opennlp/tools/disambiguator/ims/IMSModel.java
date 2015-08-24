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
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

public class IMSModel extends BaseModel {

  private static final String COMPONENT_NAME = "IMSME";
  private static final String IMS_MODEL_ENTRY_NAME = "IMS.model";

  private static final String WORDTAG = "wordtag";
  private static final String WINSIZE = "winsize";
  private static final String NGRAM = "ngram";
  private static final String SURROUNDINGS = "surroundings";

  private ArrayList<String> surroundingWords = new ArrayList<String>();
  private String wordTag;

  private int windowSize;
  private int ngram;

  public ArrayList<String> getSurroundingWords() {
    return surroundingWords;
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

  public void setSurroundingWords(ArrayList<String> surroundingWords) {
    this.surroundingWords = surroundingWords;
  }

  public String getWordTag() {
    return wordTag;
  }

  public void setWordTag(String wordTag) {
    this.wordTag = wordTag;
  }

   public IMSModel(String languageCode, String wordTag, int windowSize,
      int ngram, MaxentModel imsModel, ArrayList<String> surroundingWords,
      Map<String, String> manifestInfoEntries, IMSFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);

    artifactMap.put(IMS_MODEL_ENTRY_NAME, imsModel);
    this.setManifestProperty(WORDTAG, wordTag);
    this.setManifestProperty(WINSIZE, windowSize + "");
    this.setManifestProperty(NGRAM, ngram + "");
    this.setManifestProperty(SURROUNDINGS,
        StringUtils.join(surroundingWords, ","));

    this.surroundingWords = surroundingWords;
    checkArtifactMap();
  }

  public IMSModel(String languageCode, String wordTag, int windowSize,
      int ngram, MaxentModel imsModel, ArrayList<String> surroundingWords,
      IMSFactory factory) {
    this(languageCode, wordTag, windowSize, ngram, imsModel, surroundingWords,
        null, factory);
  }

  public IMSModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
    updateAttributes();
  }

  public IMSModel(File modelFile) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelFile);
    updateAttributes();
    /*
     * String modelPath = modelFile.getPath(); String surrPath =
     * modelPath.substring(0, modelPath.length() - 6) + ".surr";
     * 
     * ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
     * new FileInputStream(surrPath))); try {
     * this.setSurroundingWords((ArrayList<String>) ois.readObject()); } catch
     * (ClassNotFoundException e) { // TODO Auto-generated catch block
     * e.printStackTrace(); } finally { ois.close(); }
     */
  }

  public IMSModel(URL modelURL) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
    updateAttributes();
  }

  // path must include the word.tag i.e. : write.v
  public boolean writeModel(String path) {
    File outFile = new File(path + ".ims.model");
    CmdLineUtil.writeModel("ims model", outFile, this);
    return true;
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(IMS_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("IMS model is incomplete!");
    }
  }

  public MaxentModel getIMSMaxentModel() {
    if (artifactMap.get(IMS_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(IMS_MODEL_ENTRY_NAME);
    } else {
      return null;
    }
  }

  public void updateAttributes() {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    String surroundings = (String) manifest.get(SURROUNDINGS);

    this.surroundingWords = new ArrayList(
        Arrays.asList(surroundings.split(",")));
    this.wordTag = (String) manifest.get(WORDTAG);
    this.windowSize = Integer.parseInt((String) manifest.get(WINSIZE));
    this.ngram = Integer.parseInt((String) manifest.get(NGRAM));
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return IMSFactory.class;
  }

  public IMSFactory getFactory() {
    return (IMSFactory) this.toolFactory;
  }

}