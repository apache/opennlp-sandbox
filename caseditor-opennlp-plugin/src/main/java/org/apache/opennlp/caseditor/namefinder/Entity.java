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

package org.apache.opennlp.caseditor.namefinder;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.text.AnnotationFS;
import org.eclipse.core.runtime.IAdaptable;

// TODO: Rename to PotentialAnnotation, should also contain a type, then we can use
//       reuse the code to create an annotation for it.
public class Entity implements IAdaptable {

  private int beginIndex;
  private int endIndex;

  private String entityText;

  private boolean isConfirmed;

  private Double confidence;
  
  private String type;

  private AnnotationFS linkedAnnotationFS;
  
  public Entity(int beginIndex, int endIndex, String entityText, Double confidence,
      boolean isConfirmed, String type) {
    this.beginIndex = beginIndex;
    this.endIndex = endIndex;
    
    this.entityText = entityText;
    
    this.confidence = confidence;
    
    this.isConfirmed = isConfirmed;
    
    this.type = type;
  }
  
  public Entity(int beginIndex, int endIndex, String entityText, Double confidence, boolean isConfirmed) {
    this(beginIndex, endIndex, entityText, confidence, isConfirmed, null);
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
  
  public void setConfirmed(boolean isConfirmed) {
    this.isConfirmed = isConfirmed;
  }
  
  public boolean isConfirmed() {
    return isConfirmed;
  }
  
  public void setLinkedAnnotation(AnnotationFS linkedAnnotationFS) {
    this.linkedAnnotationFS = linkedAnnotationFS;
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
    else if (obj instanceof Entity) {
      Entity entity = (Entity) obj;
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
  
  public Object getAdapter(Class adapter) {
    if (AnnotationFS.class.equals(adapter) || FeatureStructure.class.equals(adapter)) {
      return linkedAnnotationFS;
    }
    else {
      return null;
    }
  }
  
}
