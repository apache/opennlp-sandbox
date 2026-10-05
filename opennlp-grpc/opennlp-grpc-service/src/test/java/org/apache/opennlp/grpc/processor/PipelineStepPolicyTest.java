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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor;

import java.util.List;

import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link PipelineStepPolicy#implementedSteps()} to the order the analyzer actually
 * dispatches steps (see {@code BasicDocumentAnalyzer#analyze}), because the list is served
 * verbatim to clients as {@code GetServiceInfo.supported_steps} with an execution-order claim.
 */
class PipelineStepPolicyTest {

  @Test
  void implementedStepsFollowActualExecutionOrder() {
    assertEquals(List.of(
        PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
        PipelineStep.PIPELINE_STEP_NORMALIZE,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
        PipelineStep.PIPELINE_STEP_NER,
        PipelineStep.PIPELINE_STEP_GEOCODE,
        PipelineStep.PIPELINE_STEP_POS_TAG,
        PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
        PipelineStep.PIPELINE_STEP_RELATION_EXTRACT,
        PipelineStep.PIPELINE_STEP_LEMMATIZE,
        PipelineStep.PIPELINE_STEP_STEM,
        PipelineStep.PIPELINE_STEP_TERM_VECTOR,
        PipelineStep.PIPELINE_STEP_EXPAND,
        PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
        PipelineStep.PIPELINE_STEP_SENTIMENT,
        PipelineStep.PIPELINE_STEP_PARSE,
        PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK,
        PipelineStep.PIPELINE_STEP_EMBED,
        PipelineStep.PIPELINE_STEP_CHUNK),
        PipelineStepPolicy.implementedSteps());
  }
}
