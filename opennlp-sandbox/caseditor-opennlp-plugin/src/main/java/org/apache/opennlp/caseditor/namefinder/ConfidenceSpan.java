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

import opennlp.tools.util.Span;

// Note: Should be replaced with OpenNLP Tools Span when
// it has a confidence score ...
public class ConfidenceSpan extends Span {

  private final double confidence;
  
  public ConfidenceSpan(int s, int e, double confidence, String type) {
    super(s, e, type);
    this.confidence = confidence;
  }
  
  public ConfidenceSpan(int s, int e, double confidence) {
    this(s, e, confidence, null);
  }
  
  double getConfidence() {
    return confidence;
  }
}
