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
 * Lesk specific parameter set
 *
 */
public class LeskParameters extends WSDParameters {

  /**
   * Enum of all types of implemented variations of Lesk
   *
   */
  public static enum LESK_TYPE {
    LESK_BASIC, LESK_BASIC_CTXT, LESK_EXT, LESK_EXT_CTXT, LESK_EXT_EXP, LESK_EXT_EXP_CTXT
  }

  // DEFAULTS
  protected static final LESK_TYPE DFLT_LESK_TYPE = LESK_TYPE.LESK_EXT_EXP_CTXT;
  protected static final SenseSource DFLT_SOURCE = SenseSource.WORDNET;
  protected static final int DFLT_WIN_SIZE = 10;
  protected static final int DFLT_DEPTH = 1;
  protected static final double DFLT_DEPTH_WEIGHT = 0.8;
  protected static final double DFLT_IEXP = 0.3;
  protected static final double DFLT_DEXP = 0.3;

  protected LESK_TYPE leskType;

  protected SenseSource source;
  protected int win_f_size;
  protected int win_b_size;
  protected int depth;
  protected double depth_weight;
  protected double iexp;
  protected double dexp;

  /*
   * 10 possible features for lesk 0 : Synonyms 1 : Hypernyms 2 : Hyponyms 3 :
   * Meronyms 4 : Holonyms 5 : Entailments 6 : Coordinate Terms 7 : Causes 8 :
   * Attributes 9 : Pertainyms
   */
  protected boolean features[];

  public LESK_TYPE getLeskType() {
    return leskType;
  }

  public void setLeskType(LESK_TYPE leskType) {
    this.leskType = leskType;
  }

  public int getWin_f_size() {
    return win_f_size;
  }

  public void setWin_f_size(int win_f_size) {
    this.win_f_size = win_f_size;
  }

  public int getWin_b_size() {
    return win_b_size;
  }

  public void setWin_b_size(int win_b_size) {
    this.win_b_size = win_b_size;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public double getDepth_weight() {
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

  public LeskParameters() {
    this.setDefaults();
  }

  /**
   * Sets default parameters
   */
  public void setDefaults() {
    this.leskType = LeskParameters.DFLT_LESK_TYPE;
    this.source = LeskParameters.DFLT_SOURCE;
    this.win_f_size = LeskParameters.DFLT_WIN_SIZE;
    this.win_b_size = LeskParameters.DFLT_WIN_SIZE;
    this.depth = LeskParameters.DFLT_DEPTH;
    this.depth_weight = LeskParameters.DFLT_DEPTH_WEIGHT;
    this.iexp = LeskParameters.DFLT_IEXP;
    this.dexp = LeskParameters.DFLT_DEXP;
    boolean[] a = { true, true, true, true, true, true, true, true, true, true };
    this.features = a;
  }

  /*
   * (non-Javadoc)
   * 
   * @see opennlp.tools.disambiguator.WSDParameters#isValid()
   */
  public boolean areValid() {

    switch (this.leskType) {
    case LESK_BASIC:
    case LESK_BASIC_CTXT:
      return (this.win_b_size == this.win_f_size) && this.win_b_size >= 0;
    case LESK_EXT:
    case LESK_EXT_CTXT:
      return (this.depth >= 0) && (this.depth_weight >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);
    case LESK_EXT_EXP:
    case LESK_EXT_EXP_CTXT:
      return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);
    default:
      return false;
    }
  }

}
