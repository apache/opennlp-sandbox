/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.caseditor;

/**
 * The Potential Annotation is a proposed annotation which needs to be confirmed by a human. Usually
 * it is created by a natural language processing tool.
 */
public class PotentialAnnotation {

  private int beginIndex;
  private int endIndex;

  private String entityText;

  private Double confidence;
  
  private String type;

  public PotentialAnnotation(int beginIndex, int endIndex, String entityText,
      Double confidence, String type) {
    this.beginIndex = beginIndex;
    this.endIndex = endIndex;
    
    this.entityText = entityText;
    
    this.confidence = confidence;
    
    this.type = type;
  }
  
  public void setBeginIndex(int beginIndex) {
    this.beginIndex = beginIndex;
  }
  
  public int getBeginIndex() {
    return beginIndex;
  }
  
  public void setEndIndex(int endIndex) {
    this.endIndex = endIndex;
  }
  
  public int getEndIndex() {
    return endIndex;
  }
  
  public void setEntityText(String entityText) {
    this.entityText = entityText;
  }
  
  public String getEntityText() {
    return entityText;
  }
  
  public String getType() {
    return type;
  }
  
  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }
  
  public Double getConfidence() {
    return confidence;
  }
  
  @Override
  public String toString() {
    return entityText;
  }

  // Note: equals and hashCode should ignore confirm status, confidence
  
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    else if (obj instanceof PotentialAnnotation) {
      PotentialAnnotation entity = (PotentialAnnotation) obj;
      return entity.getBeginIndex() == getBeginIndex() &&
          entity.getEndIndex() == getEndIndex() && 
          entity.getType().equals(type);
    }
    else {
      return false;
    }
  }
  
  @Override
  public int hashCode() {
    return getBeginIndex() + getEndIndex() + type.hashCode();
  }
}
