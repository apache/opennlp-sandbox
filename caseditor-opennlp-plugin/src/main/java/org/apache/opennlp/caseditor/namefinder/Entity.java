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

// TODO: Rename to PotentialAnnotation, should also contain a type, then we can use
//       reuse the code to create an annotation for it.
public class Entity {
  
  private final int beginIndex;
  private final int endIndex;
  
  private final String entityText;
 
  private boolean isConfirmed;
  
  private Double confidence;
  
  public Entity(int beginIndex, int endIndex, String entityText, Double confidence, boolean isConfirmed) {
    this.beginIndex = beginIndex;
    this.endIndex = endIndex;
    
    this.entityText = entityText;
    
    this.confidence = confidence;
    
    this.isConfirmed = isConfirmed;
  }
  
  public int getBeginIndex() {
    return beginIndex;
  }
  
  public int getEndIndex() {
    return endIndex;
  }
  
  public String getEntityText() {
    return entityText;
  }
  
  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }
  
  public boolean isConfirmed() {
    return isConfirmed;
  }
  
  public Double getConfidence() {
    return confidence;
  }
  
  @Override
  public String toString() {
    return entityText;
  }
}