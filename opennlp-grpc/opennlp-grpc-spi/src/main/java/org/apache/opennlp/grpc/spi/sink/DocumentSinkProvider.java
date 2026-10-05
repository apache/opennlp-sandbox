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
package org.apache.opennlp.grpc.spi.sink;

import java.io.IOException;
import java.util.Map;

/**
 * ServiceLoader contract for document sink destinations, keyed by sink id. The server
 * opens one {@link DocumentSink} per configured {@code sink.<instance>.provider} entry
 * and tees every analyzed document into it. Sink ids must be unique across all
 * deployed providers; the server rejects duplicates at startup.
 */
public interface DocumentSinkProvider {

  /**
   * {@return the stable lowercase sink id used in configuration, for example {@code grpc}}
   */
  String sinkId();

  /**
   * Opens one configured sink instance.
   *
   * @param instanceId The configured instance id, for diagnostics.
   * @param options Provider-specific option values without the configuration prefix.
   * @return The open sink. Never {@code null}.
   * @throws IOException Thrown if the destination cannot be opened.
   * @throws IllegalArgumentException Thrown if an option is unsupported or invalid.
   */
  DocumentSink open(String instanceId, Map<String, String> options) throws IOException;
}
