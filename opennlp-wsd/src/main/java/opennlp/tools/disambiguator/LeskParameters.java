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
 * Lesk specific parameter set.
 *
 * @see WSDParameters
 */
public class LeskParameters extends WSDParameters {

  /**
   * Enum of all types of implemented variations of Lesk
   */
  public enum LeskType {
    LESK_BASIC, LESK_BASIC_CTXT, LESK_EXT, LESK_EXT_CTXT, LESK_EXT_EXP, LESK_EXT_EXP_CTXT
  }

  // DEFAULTS
  protected static final LeskType DFLT_LESK_TYPE = LeskType.LESK_EXT_EXP_CTXT;
  protected static final SenseSource DFLT_SOURCE = SenseSource.WORDNET;
  protected static final int DFLT_WIN_SIZE = 10;
  protected static final int DFLT_DEPTH = 1;
  protected static final double DFLT_DEPTH_WEIGHT = 0.8;
  protected static final double DFLT_IEXP = 0.3;
  protected static final double DFLT_DEXP = 0.3;

  protected LeskType type;

  protected SenseSource source;
  protected int winFSize;
  protected int winBSize;
  protected int depth;
  protected double depth_weight;
  protected double iexp;
  protected double dexp;

  public LeskParameters() {
    this.setDefaults();
  }

  /**
   * Ten features are possible for Lesk.
   * <ul>
   * <li>0: Synonyms</li>
   * <li>1: Hypernyms</li>
   * <li>2: Hyponyms</li>
   * <li>3: Meronyms</li>
   * <li>4: Holonyms</li>
   * <li>5: Entailments</li>
   * <li>6: Coordinate Terms</li>
   * <li>7: Causes</li>
   * <li>8: Attributes</li>
   * <li>9: Pertainyms</li>
   * </ul>
   */
  protected boolean[] features;

  public LeskType getType() {
    return type;
  }

  public void setType(LeskType type) {
    this.type = type;
  }

  public int getWinFSize() {
    return winFSize;
  }

  public void setWinFSize(int winFSize) {
    this.winFSize = winFSize;
  }

  public int getWinBSize() {
    return winBSize;
  }

  public void setWinBSize(int winBSize) {
    this.winBSize = winBSize;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public double getDepthWeight() {
    return depth_weight;
  }

  public void setDepth_weight(double depth_weight) {
    this.depth_weight = depth_weight;
  }

  public double getIexp() {
    return iexp;
  }

  public void setIexp(double iexp) {
    this.iexp = iexp;
  }

  public double getDexp() {
    return dexp;
  }

  public void setDexp(double dexp) {
    this.dexp = dexp;
  }

  public boolean[] getFeatures() {
    return features;
  }

  public void setFeatures(boolean[] features) {
    this.features = features;
  }

  /**
   * Sets default parameters
   */
  void setDefaults() {
    setType(LeskParameters.DFLT_LESK_TYPE);
    setWinFSize(LeskParameters.DFLT_WIN_SIZE);
    setWinBSize(LeskParameters.DFLT_WIN_SIZE);
    setDepth(LeskParameters.DFLT_DEPTH);
    setDepth_weight(LeskParameters.DFLT_DEPTH_WEIGHT);
    setIexp(LeskParameters.DFLT_IEXP);
    setDexp(LeskParameters.DFLT_DEXP);
    this.source = LeskParameters.DFLT_SOURCE;
    this.features = new boolean[]
            { true, true, true, true, true, true, true, true, true, true };
  }

  @Override
  public boolean areValid() {

    switch (this.type) {
      case LESK_BASIC:
      case LESK_BASIC_CTXT:
        return (this.winBSize == this.winFSize) && this.winBSize >= 0;
      case LESK_EXT:
      case LESK_EXT_CTXT:
        return (this.depth >= 0) && (this.depth_weight >= 0)
            && (this.winBSize >= 0) && (this.winFSize >= 0);
      case LESK_EXT_EXP:
      case LESK_EXT_EXP_CTXT:
        return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0)
            && (this.winBSize >= 0) && (this.winFSize >= 0);
      default:
        return false;
    }
  }

}
