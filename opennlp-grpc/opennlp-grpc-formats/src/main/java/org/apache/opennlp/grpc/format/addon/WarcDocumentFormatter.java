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
package org.apache.opennlp.grpc.format.addon;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Renders the analyzed document as a WARC/1.1 archive of two records: a
 * {@code warcinfo} record describing the writer, and a {@code resource} record holding
 * the document's raw text under a {@code urn:opennlp:document:<doc id>} target URI.
 * Record ids are fresh UUID URNs and the WARC date is the rendering time, so two
 * renderings of one document differ in identity but never in content.
 */
public final class WarcDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  private static final DateTimeFormatter WARC_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public WarcDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "warc";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "WARC";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "application/warc";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "warc";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    final String date = WARC_DATE.format(Instant.now());
    final byte[] info = "software: Apache OpenNLP gRPC server\r\nformat: WARC File Format 1.1\r\n"
        .getBytes(StandardCharsets.UTF_8);
    record(output, date, """
        WARC-Type: warcinfo\r
        Content-Type: application/warc-fields\r
        """, info);
    final byte[] text = reply.getRawText().getBytes(StandardCharsets.UTF_8);
    final String documentId = reply.getDocId().isBlank() ? "document" : reply.getDocId();
    record(output, date, "WARC-Type: resource\r\n"
        + "WARC-Target-URI: urn:opennlp:document:" + documentId + "\r\n"
        + "Content-Type: text/plain; charset=utf-8\r\n", text);
  }

  /** Writes one WARC record: version and headers, a blank line, the block, two CRLF. */
  private static void record(
      OutputStream output, String date, String typedHeaders, byte[] block) throws IOException {
    final String headers = "WARC/1.1\r\n"
        + typedHeaders
        + "WARC-Date: " + date + "\r\n"
        + "WARC-Record-ID: <urn:uuid:" + UUID.randomUUID() + ">\r\n"
        + "Content-Length: " + block.length + "\r\n"
        + "\r\n";
    output.write(headers.getBytes(StandardCharsets.UTF_8));
    output.write(block);
    output.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
  }
}
