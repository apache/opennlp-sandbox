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
package org.apache.opennlp.grpc.model;

import opennlp.tools.sentdetect.SentenceDetector;
import org.apache.opennlp.grpc.processor.AnalysisException;

/**
 * Shared resources passed to a {@link NerBackendFactory} at load time, so backends do not
 * each re-load expensive components. Currently carries the server's sentence detector, which
 * the ONNX backend's {@code NameFinderDL} needs internally.
 */
public final class NerBackendContext {

  private final SentenceDetector sentenceDetector;

  /**
   * Creates a context carrying the shared resources available to NER backends.
   *
   * @param sentenceDetector The server's sentence detector, or {@code null} when none is
   *     configured. Backends that need one obtain it via {@link #requireSentenceDetector()}.
   */
  public NerBackendContext(SentenceDetector sentenceDetector) {
    this.sentenceDetector = sentenceDetector;
  }

  /**
   * Returns the shared sentence detector, failing if none was provided.
   *
   * @return The shared sentence detector.
   *
   * @throws AnalysisException If no sentence detector is available, which a backend that
   *     requires one (e.g. ONNX) should treat as a configuration error.
   */
  public SentenceDetector requireSentenceDetector() {
    if (sentenceDetector == null) {
      throw AnalysisException.invalidArgument(
          "this name finder backend requires a sentence detector, but none is available");
    }
    return sentenceDetector;
  }
}
