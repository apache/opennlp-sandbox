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
import java.nio.charset.StandardCharsets;

import com.google.protobuf.util.JsonFormat;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Renders the analyzed document as canonical protobuf JSON, the same rendering the
 * HTTP gateway serves.
 */
public final class ProtoJsonDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ProtoJsonDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "protojson";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "Protobuf JSON";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "application/json";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "json";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    output.write(JsonFormat.printer().print(reply).getBytes(StandardCharsets.UTF_8));
  }
}
