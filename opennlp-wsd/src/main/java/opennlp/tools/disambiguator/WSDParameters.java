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

/**
 * Describes a set of parameters to configure word sense disambiguation.
 */
public abstract class WSDParameters {

  public enum SenseSource {
    WORDNET, WSDHELPER, OTHER
  }

  protected SenseSource senseSource;

  /**
   * Initializes a default set of {@link WSDParameters} and chooses
   * the {@link SenseSource#WORDNET} by default.
   */
  public WSDParameters() {
    this.senseSource = SenseSource.WORDNET;
  }

  /**
   * @return if the disambiguation type is coarse grained or fine-grained
   */
  public SenseSource getSenseSource() {
    return senseSource;
  }

  /**
   * Checks if the parameters are valid or not.
   * 
   * @return {@code true} if valid, {@code false} otherwise.
   */
  public abstract boolean areValid();

}
