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

package opennlp.tools.disambiguator;

import java.io.File;

/**
 * This class contains the parameters for the IMS approach as well as the
 * directories containing the files used
 */
public class WSDDefaultParameters extends WSDParameters {

  protected String languageCode;
  protected int windowSize;
  protected int ngram;

  protected String trainingDataDirectory;

  protected static final int DFLT_WIN_SIZE = 3;
  protected static final int DFLT_NGRAM = 2;
  protected static final String DFLT_LANG_CODE = "En";
  protected static final SenseSource DFLT_SOURCE = SenseSource.WORDNET;

  /**
   * This constructor takes only two parameters. The default language used is
   * <i>English</i>
   *
   * @param windowSize  the size of the window used for the extraction of the features
   *                    qualified of Surrounding Words
   * @param ngram       the number words used for the extraction of features qualified of
   *                    Local Collocations
   * @param senseSource the source of the training data
   */
  public WSDDefaultParameters(int windowSize, int ngram,
    SenseSource senseSource, String trainingDataDirectory) {

    this.languageCode = DFLT_LANG_CODE;
    this.windowSize = windowSize;
    this.ngram = ngram;
    this.senseSource = senseSource;
    this.trainingDataDirectory = trainingDataDirectory;

    File folder = new File(trainingDataDirectory);
    if (!folder.exists())
      folder.mkdirs();
  }

  public WSDDefaultParameters(String trainingDataDirectory) {
    this(DFLT_WIN_SIZE, DFLT_NGRAM, DFLT_SOURCE, trainingDataDirectory);
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

  public String getTrainingDataDirectory() {
    return trainingDataDirectory;
  }

  public void setTrainingDataDirectory(String trainingDataDirectory) {
    this.trainingDataDirectory = trainingDataDirectory;
  }

  @Override public boolean areValid() {
    // TODO recheck this pattern
    return true;
  }

}
