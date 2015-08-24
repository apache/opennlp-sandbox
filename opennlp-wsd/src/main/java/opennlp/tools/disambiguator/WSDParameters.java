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
 * Disambiguation Parameters
 *
 */
public abstract class WSDParameters {

  protected boolean isCoarseSense;
  public static boolean isStemCompare;

  public static enum SenseSource {
    WORDNET, WSDHELPER, OTHER;
  }

  protected SenseSource senseSource;

  /**
   * @return if the disambiguation type is coarse grained or fine grained
   */
  public boolean isCoarseSense() {
    return isCoarseSense;
  }

  public void setCoarseSense(boolean isCoarseSense) {
    this.isCoarseSense = isCoarseSense;
  }

  public static boolean isStemCompare() {
    return isStemCompare;
  }

  public static void setStemCompare(boolean isStemCompare) {
    WSDParameters.isStemCompare = isStemCompare;
  }

  public SenseSource getSenseSource() {
    return senseSource;
  }

  public void setSenseSource(SenseSource senseSource) {
    this.senseSource = senseSource;
  }

  public WSDParameters() {
    this.isCoarseSense = true;
  }

  /**
   * @return checks if the parameters are valid or not
   */
  public abstract boolean isValid();

}
