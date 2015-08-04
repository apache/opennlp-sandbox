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

package opennlp.tools.disambiguator.lesk;

import opennlp.tools.disambiguator.WSDParameters;

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
    LESK_BASIC, LESK_BASIC_CTXT, LESK_BASIC_CTXT_WIN, LESK_BASIC_CTXT_WIN_BF,
    LESK_EXT, LESK_EXT_CTXT, LESK_EXT_CTXT_WIN, LESK_EXT_CTXT_WIN_BF, LESK_EXT_EXP,
    LESK_EXT_EXP_CTXT, LESK_EXT_EXP_CTXT_WIN, LESK_EXT_EXP_CTXT_WIN_BF,
  }

  // DEFAULTS
  protected static final LESK_TYPE DFLT_LESK_TYPE = LESK_TYPE.LESK_EXT_EXP_CTXT_WIN;
  protected static final int DFLT_WIN_SIZE = 5;
  protected static final int DFLT_DEPTH = 2;
  protected static final double DFLT_IEXP = 0.4;
  protected static final double DFLT_DEXP = 0.4;

  protected LESK_TYPE leskType;
  protected int win_f_size;
  protected int win_b_size;
  protected int depth;

  protected boolean fathom_synonyms;
  protected boolean fathom_hypernyms;
  protected boolean fathom_hyponyms;
  protected boolean fathom_meronyms;
  protected boolean fathom_holonyms;

  protected double depth_weight;
  protected double iexp;
  protected double dexp;

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

  public boolean isFathom_synonyms() {
    return fathom_synonyms;
  }

  public void setFathom_synonyms(boolean fathom_synonyms) {
    this.fathom_synonyms = fathom_synonyms;
  }

  public boolean isFathom_hypernyms() {
    return fathom_hypernyms;
  }

  public void setFathom_hypernyms(boolean fathom_hypernyms) {
    this.fathom_hypernyms = fathom_hypernyms;
  }

  public boolean isFathom_hyponyms() {
    return fathom_hyponyms;
  }

  public void setFathom_hyponyms(boolean fathom_hyponyms) {
    this.fathom_hyponyms = fathom_hyponyms;
  }

  public boolean isFathom_meronyms() {
    return fathom_meronyms;
  }

  public void setFathom_meronyms(boolean fathom_meronyms) {
    this.fathom_meronyms = fathom_meronyms;
  }

  public boolean isFathom_holonyms() {
    return fathom_holonyms;
  }

  public void setFathom_holonyms(boolean fathom_holonyms) {
    this.fathom_holonyms = fathom_holonyms;
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

  public LeskParameters() {
    this.setDefaults();
  }

  /**
   * Sets default parameters
   */
  public void setDefaults() {
    this.leskType = LeskParameters.DFLT_LESK_TYPE;
    this.win_f_size = LeskParameters.DFLT_WIN_SIZE;
    this.win_b_size = LeskParameters.DFLT_WIN_SIZE;
    this.depth = LeskParameters.DFLT_DEPTH;
    this.iexp = LeskParameters.DFLT_IEXP;
    this.dexp = LeskParameters.DFLT_DEXP;
    this.fathom_holonyms = true;
    this.fathom_hypernyms = true;
    this.fathom_hyponyms = true;
    this.fathom_meronyms = true;
    this.fathom_synonyms = true;
  }

  
  /* (non-Javadoc)
   * @see opennlp.tools.disambiguator.WSDParameters#isValid()
   */
  public boolean isValid() {

    switch (this.leskType) {
    case LESK_BASIC:
    case LESK_BASIC_CTXT:
      return true;
    case LESK_BASIC_CTXT_WIN:
      return (this.win_b_size == this.win_f_size) && this.win_b_size >= 0;
    case LESK_BASIC_CTXT_WIN_BF:
      return (this.win_b_size >= 0) && (this.win_f_size >= 0);
    case LESK_EXT:
    case LESK_EXT_CTXT:
      return (this.depth >= 0) && (this.depth_weight >= 0);
    case LESK_EXT_CTXT_WIN:
    case LESK_EXT_CTXT_WIN_BF:
      return (this.depth >= 0) && (this.depth_weight >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);
    case LESK_EXT_EXP:
    case LESK_EXT_EXP_CTXT:
      return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0);
    case LESK_EXT_EXP_CTXT_WIN:
    case LESK_EXT_EXP_CTXT_WIN_BF:
      return (this.depth >= 0) && (this.dexp >= 0) && (this.iexp >= 0)
          && (this.win_b_size >= 0) && (this.win_f_size >= 0);
    default:
      return false;
    }
  }

}
