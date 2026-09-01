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

import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * One open destination for analyzed documents. The server calls {@link #accept} after
 * each successful analysis and {@link #close} at shutdown; a sink failure is logged by
 * the server and never fails the analysis that produced the document. Thread safety is
 * required: analyses run concurrently, so {@link #accept} must tolerate concurrent
 * callers.
 */
public interface DocumentSink extends AutoCloseable {

  /**
   * Delivers one analyzed document.
   *
   * @param document The analyzed document. Must not be {@code null}.
   * @throws IOException Thrown if delivery fails.
   */
  void accept(OpenNlpDocument document) throws IOException;

  /**
   * Flushes and releases the destination. Called once at server shutdown.
   *
   * @throws IOException Thrown if the final flush fails.
   */
  @Override
  void close() throws IOException;
}
