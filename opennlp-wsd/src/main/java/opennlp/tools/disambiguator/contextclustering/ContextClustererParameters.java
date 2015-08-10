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

package opennlp.tools.disambiguator.contextclustering;

import opennlp.tools.disambiguator.WSDParameters;

public class ContextClustererParameters extends WSDParameters {

  protected int window;
  protected Source source;
  
  /**
   * Enum of all types of implemented variations of the Context Clustering 
   * For now only WordNet is supported as a source
   */
  public static enum Source {
    WORDNET
  }

  // DEFAULTS
  protected static final Source DFLT_SOURCE = Source.WORDNET;
  protected static final int DFLT_WINDOW = 3;

  public int getWindow() {
    return window;
  }

  public void setWindow(int window) {
    this.window = window;
  }

  @Override
  public boolean isValid() {
    return window > 0;
  }
  
  
  public ContextClustererParameters() {
    this.setDefaults();
  }

  /**
   * Sets default parameters
   */
  public void setDefaults() {
    this.source = ContextClustererParameters.DFLT_SOURCE;
    this.window = ContextClustererParameters.DFLT_WINDOW;
  }

}
