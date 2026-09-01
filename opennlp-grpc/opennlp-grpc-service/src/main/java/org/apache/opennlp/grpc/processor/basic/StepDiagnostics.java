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
package org.apache.opennlp.grpc.processor.basic;

import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;

/**
 * Factory for the {@link ProcessingDiagnostic} entries the pipeline steps report.
 */
final class StepDiagnostics {

  private StepDiagnostics() {
  }

  /** An INFO diagnostic reporting the outcome of an executed step. */
  static ProcessingDiagnostic info(PipelineStep step, String message) {
    return ProcessingDiagnostic.newBuilder()
        .setStep(step)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage(message)
        .build();
  }

  /** An INFO diagnostic reporting that a step was not requested by the profile. */
  static ProcessingDiagnostic skipped(PipelineStep step) {
    return info(step, step.name() + " skipped (not requested by profile)");
  }
}
