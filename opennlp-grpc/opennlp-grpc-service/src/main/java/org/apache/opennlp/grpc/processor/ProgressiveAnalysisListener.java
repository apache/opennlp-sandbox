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
package org.apache.opennlp.grpc.processor;

import org.apache.opennlp.grpc.v1.AnalysisLayerBatch;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.PipelineStep;

/** Receives ordered updates from one progressive document analysis. */
public interface ProgressiveAnalysisListener {

  /**
   * Publishes the validated input and effective step list.
   *
   * @param started The initial analysis state.
   */
  void onStarted(AnalysisStarted started);

  /**
   * Publishes the complete layer values produced by one branch.
   *
   * @param layers The finished branch output.
   */
  void onLayersReady(AnalysisLayerBatch layers);

  /**
   * Reports a failed non-backbone branch.
   *
   * @param step The terminal step of the failed branch.
   * @param failure The branch failure.
   */
  void onStepFailed(PipelineStep step, RuntimeException failure);

  /**
   * Publishes the canonical final response.
   *
   * @param response The assembled analysis response.
   */
  void onComplete(AnalyzeDocumentResponse response);

  /**
   * Terminates the analysis when validation or the backbone fails.
   *
   * @param failure The terminal analysis failure.
   */
  void onError(RuntimeException failure);

  /**
   * Returns whether the client has cancelled the call.
   *
   * @return {@code true} after cancellation.
   */
  boolean isCancelled();
}
