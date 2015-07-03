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

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;

public class IMSFactory extends BaseToolFactory {

  protected String languageCode;

  protected String resourcesFolder = "src\\test\\resources\\supervised\\";

  protected String rawDataDirectory = resourcesFolder + "training\\"; 
  protected String trainingDataDirectory = resourcesFolder + "models\\";
  protected String dictionaryDirectory = resourcesFolder + "dictionary\\";

  protected String dict = dictionaryDirectory + "EnglishLS.dictionary.xml";
  protected String map = dictionaryDirectory + "EnglishLS.sensemap";

  public IMSFactory() {
    super();
  }

  public String getLanguageCode() {
    return languageCode;
  }

  public void setLanguageCode(String languageCode) {
    this.languageCode = languageCode;
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

  void init() {
  }

  public IMSContextGenerator createContextGenerator() {

    return new DefaultIMSContextGenerator();
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
  }
}
