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
import java.nio.file.Path;

/**
 * Defines the parameters for the <a href="https://aclanthology.org/P10-4014.pdf">
 *   IMS (It Makes Sense)</a> approach, as well as the
 * directories containing the files used
 *
 * @see WSDParameters
 */
public class WSDDefaultParameters extends WSDParameters {

  public static final int DFLT_WIN_SIZE = 3;
  public static final int DFLT_NGRAM = 2;
  public static final String DFLT_LANG_CODE = "en";
  public static final SenseSource DFLT_SOURCE = SenseSource.WORDNET;

  private final Path trainingDataDir;

  private final String languageCode;
  protected int windowSize;
  protected int ngram;

  /**
   * Initializes a new set of {@link WSDDefaultParameters}.
   * The default language used is '<i>en</i>' (English).
   *
   * @param windowSize  The size of the window used for the extraction of the features
   *                    qualified of Surrounding Words.
   * @param ngram       The number words used for the extraction of features qualified of
   *                    Local Collocations.
   * @param senseSource The {@link SenseSource source} of the training data
   * @param trainingDataDir The {@link Path} where to store or read trained models from.
   */
  public WSDDefaultParameters(int windowSize, int ngram, SenseSource senseSource, Path trainingDataDir) {
    this.languageCode = DFLT_LANG_CODE;
    this.windowSize = windowSize;
    this.ngram = ngram;
    this.senseSource = senseSource;
    this.trainingDataDir = trainingDataDir;
    if (trainingDataDir != null) {
      File folder = trainingDataDir.toFile();
      if (!folder.exists())
        folder.mkdirs();
    }
  }

  /**
   * Initializes a new set of {@link WSDDefaultParameters}.
   * The default language used is '<i>en</i>' (English), the window size is {@link #DFLT_WIN_SIZE},
   * and the ngram length is initialized as {@link #DFLT_NGRAM}.
   *
   * @implNote The training directory will be unset.
   */
  public WSDDefaultParameters() {
    this(DFLT_WIN_SIZE, DFLT_NGRAM, DFLT_SOURCE, null);
  }

  /**
   * Initializes a new set of {@link WSDDefaultParameters}.
   * The default language used is '<i>en</i>' (English), the window size is {@link #DFLT_WIN_SIZE},
   * and the ngram length is initialized as {@link #DFLT_NGRAM}.
   *
   * @param trainingDataDir The {@link Path} where to place or lookup trained models.
   */
  public WSDDefaultParameters(Path trainingDataDir) {
    this(DFLT_WIN_SIZE, DFLT_NGRAM, DFLT_SOURCE, trainingDataDir);
  }

  public String getLanguageCode() {
    return languageCode;
  }

  public int getWindowSize() {
    return windowSize;
  }

  public int getNgram() {
    return ngram;
  }

  /**
   * @return The {@link Path} where to place or lookup trained models. May be {@code null}!
   */
  public Path getTrainingDataDirectory() {
    return trainingDataDir;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean areValid() {
    return true;
  }

}
