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
package org.apache.opennlp.grpc.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.spi.model.SentenceDetectorBackendFactory;

/** Test-only custom sentence detector that treats a vertical bar as a boundary. */
public final class StubSentenceDetectorBackendFactory implements SentenceDetectorBackendFactory {

  /** Engine id used by segmentation tests. */
  public static final String ENGINE_ID = "test-lines";

  /** Public no-argument constructor for ServiceLoader. */
  public StubSentenceDetectorBackendFactory() {
  }

  @Override
  public String engineId() {
    return ENGINE_ID;
  }

  @Override
  public Optional<SentenceDetector> create(Map<String, String> configuration) {
    return Optional.of(new SentenceDetector() {
      @Override
      public String[] sentDetect(CharSequence text) {
        return Span.spansToStrings(sentPosDetect(text), text);
      }

      @Override
      public Span[] sentPosDetect(CharSequence text) {
        final List<Span> spans = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= text.length(); index++) {
          if (index == text.length() || text.charAt(index) == '|') {
            if (index > start) {
              spans.add(new Span(start, index));
            }
            start = index + 1;
          }
        }
        return spans.toArray(Span[]::new);
      }
    });
  }
}
