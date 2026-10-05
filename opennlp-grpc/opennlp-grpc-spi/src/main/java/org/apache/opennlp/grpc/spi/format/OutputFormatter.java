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
package org.apache.opennlp.grpc.spi.format;

import java.io.IOException;
import java.io.OutputStream;

import com.google.protobuf.Message;

/**
 * Renders one gRPC reply message into an output representation, discovered via
 * {@link java.util.ServiceLoader}.
 *
 * <p>The interface is generic over the reply type: a formatter declares which message it
 * renders through {@link #inputType()}, and the server groups discovered formatters by
 * that type, so one deployment can serve any number of output shapes per reply family.
 * The reply message itself is one of those shapes; the built-in formatters render it as
 * binary protobuf and as canonical protobuf JSON. Format ids must be unique per input
 * type across all deployed formatters; the server rejects duplicates at startup.
 * Thread safety is implementation specific.</p>
 *
 * @param <M> The reply message type this formatter renders.
 */
public interface OutputFormatter<M extends Message> {

  /**
   * {@return the reply message type this formatter renders}
   */
  Class<M> inputType();

  /**
   * {@return the stable lowercase format id used in requests, for example {@code tsv}}
   */
  String formatId();

  /**
   * {@return the human-readable format name, for example {@code Token TSV}}
   */
  String displayName();

  /**
   * {@return the IANA media type of the rendered content}
   */
  String mediaType();

  /**
   * {@return the conventional file extension of the rendered content, without the dot}
   */
  String fileExtension();

  /**
   * Renders one reply message.
   *
   * @param reply The reply to render. Must not be {@code null}.
   * @param output The rendering sink. Text formats write UTF-8. The caller owns the
   *     stream; implementations must not close it.
   * @throws IOException Thrown if rendering fails.
   */
  void format(M reply, OutputStream output) throws IOException;
}
