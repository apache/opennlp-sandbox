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
import java.util.Set;

import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.PipelineStep;

/**
 * Tracks which {@link PipelineStep}s this server actually implements.
 */
public final class PipelineStepPolicy {

  /** Steps implemented by the current processor, in execution order. */
  private static final List<PipelineStep> IMPLEMENTED_STEPS = List.of(
      PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
      PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
      PipelineStep.PIPELINE_STEP_TOKENIZE,
      PipelineStep.PIPELINE_STEP_NER,
      PipelineStep.PIPELINE_STEP_POS_TAG,
      PipelineStep.PIPELINE_STEP_LEMMATIZE,
      PipelineStep.PIPELINE_STEP_CHUNK,
      PipelineStep.PIPELINE_STEP_EMBED);

  private static final Set<PipelineStep> IMPLEMENTED_STEP_SET = Set.copyOf(IMPLEMENTED_STEPS);

  private PipelineStepPolicy() {
  }

  public static List<PipelineStep> implementedSteps() {
    return IMPLEMENTED_STEPS;
  }

  public static boolean isImplemented(PipelineStep step) {
    return IMPLEMENTED_STEP_SET.contains(step);
  }

  public static boolean shouldRun(AnalysisProfile profile, PipelineStep step) {
    return profile.getStepsList().contains(step);
  }
}
