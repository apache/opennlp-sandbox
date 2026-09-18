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
package org.apache.opennlp.grpc.format;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Renders the analyzed document as its wire-format binary protobuf, the exact bytes of
 * the gRPC reply object itself.
 */
public final class ProtoBinaryDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ProtoBinaryDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "proto";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "Binary protobuf";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "application/x-protobuf";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "pb";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    reply.writeTo(output);
  }
}
