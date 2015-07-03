/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator.ims;

import opennlp.tools.disambiguator.WSDParameters;

/**
 * This class contains the parameters for the IMS approach as well as the
 * directories containing the files used
 */
public class IMSParameters extends WSDParameters {

  protected String languageCode;
  protected int windowSize;
  protected int ngram;

  protected String resourcesFolder = "src\\test\\resources\\supervised\\";

  protected String rawDataDirectory = resourcesFolder + "raw\\";
  protected String trainingDataDirectory = resourcesFolder + "models\\";
  protected String dictionaryDirectory = resourcesFolder + "dictionary\\";

  protected String dict = dictionaryDirectory + "EnglishLS.dictionary.xml";
  protected String map = dictionaryDirectory + "EnglishLS.sensemap";

  public IMSParameters() {
    super();
    this.languageCode = "En";
    this.windowSize = 3;
    this.ngram = 2;
  }

  /**
   * 
   * @param windowSize
   *          : the size of the window used for the extraction of the features
   *          qualified of Surrounding Words
   * @param ngram
   *          : the number words used for the extraction of features qualified
   *          of Local Collocations
   */
  public IMSParameters(int windowSize, int ngram) {
    super();
    this.languageCode = "En";
    this.windowSize = windowSize;
    this.ngram = ngram;
  }

  public String getLanguageCode() {
    return languageCode;
  }

  public void setLanguageCode(String languageCode) {
    this.languageCode = languageCode;
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

  public String getRawDataDirectory() {
    return rawDataDirectory;
  }

  public void setRawDataDirectory(String rawDataDirectory) {
    this.rawDataDirectory = rawDataDirectory;
  }

  public String getTrainingDataDirectory() {
    return trainingDataDirectory;
  }

  public void setTrainingDataDirectory(String trainingDataDirectory) {
    this.trainingDataDirectory = trainingDataDirectory;
  }

  public String getDictionaryDirectory() {
    return dictionaryDirectory;
  }

  public void setDictionaryDirectory(String dictionaryDirectory) {
    this.dictionaryDirectory = dictionaryDirectory;
  }

  public String getDict() {
    return dict;
  }

  public void setDict(String dict) {
    this.dict = dict;
  }

  public String getMap() {
    return map;
  }

  public void setMap(String map) {
    this.map = map;
  }

  public String getResourcesFolder() {
    return resourcesFolder;
  }

  public void setResourcesFolder(String resourcesFolder) {
    this.resourcesFolder = resourcesFolder;
  }

  void init() {
  }

  /**
   * Creates the context generator of IMS
   */
  public IMSContextGenerator createContextGenerator() {

    return new DefaultIMSContextGenerator();
  }

  @Override
  public boolean isValid() {
    // TODO Auto-generated method stub
    return false;
  }

}
