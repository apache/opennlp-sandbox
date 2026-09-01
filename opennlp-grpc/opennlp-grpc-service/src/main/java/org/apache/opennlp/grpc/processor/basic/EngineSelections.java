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

import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EnginePolicy;
import org.apache.opennlp.grpc.v1.EngineSelector;
import org.apache.opennlp.grpc.v1.StandardInferenceEngine;

/** Resolves the typed and compatibility engine-policy surfaces to provider ids. */
final class EngineSelections {

  private static final String OPENNLP_ME = "opennlp-me";
  private static final String ONNX = "onnx";
  private static final String CUDA = "cuda";

  private EngineSelections() {
  }

  /**
   * Resolves a policy to provider ids while enforcing the typed selector contract.
   *
   * @param policy The policy to resolve.
   * @return Provider ids in request order. An empty list retains priority and fallback behavior.
   * @throws AnalysisException If compatibility and typed fields are mixed, or a selector is empty.
   */
  static List<String> ids(EnginePolicy policy) {
    if (policy == null) {
      throw new IllegalArgumentException("policy must not be null");
    }
    if (policy.getEnginesCount() > 0 && policy.getSelectorsCount() > 0) {
      throw AnalysisException.invalidArgument(
          "EnginePolicy.engines and EnginePolicy.selectors are mutually exclusive");
    }
    if (policy.getSelectorsCount() == 0) {
      return List.copyOf(policy.getEnginesList());
    }

    final List<String> ids = new ArrayList<>(policy.getSelectorsCount());
    for (EngineSelector selector : policy.getSelectorsList()) {
      switch (selector.getKindCase()) {
        case STANDARD:
          ids.add(standardId(selector.getStandard()));
          break;
        case CUSTOM:
          final String custom = selector.getCustom().trim();
          if (custom.isEmpty()) {
            throw AnalysisException.invalidArgument(
                "EnginePolicy.selectors custom engine id must not be blank");
          }
          ids.add(custom);
          break;
        case KIND_NOT_SET:
          throw AnalysisException.invalidArgument(
              "EnginePolicy.selectors must select a standard or custom engine");
        default:
          throw AnalysisException.invalidArgument("Unknown EngineSelector kind");
      }
    }
    return List.copyOf(ids);
  }

  /** Returns the open id for a standard enum value. */
  private static String standardId(StandardInferenceEngine engine) {
    switch (engine) {
      case STANDARD_INFERENCE_ENGINE_OPENNLP_ME:
        return OPENNLP_ME;
      case STANDARD_INFERENCE_ENGINE_ONNX:
        return ONNX;
      case STANDARD_INFERENCE_ENGINE_CUDA:
        return CUDA;
      case STANDARD_INFERENCE_ENGINE_UNSPECIFIED:
      case UNRECOGNIZED:
      default:
        throw AnalysisException.invalidArgument(
            "EnginePolicy.selectors standard engine must not be unspecified or unrecognized");
    }
  }
}
