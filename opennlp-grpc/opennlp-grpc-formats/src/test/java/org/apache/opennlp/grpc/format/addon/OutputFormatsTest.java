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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;

import org.apache.opennlp.grpc.format.OutputFormatterRegistry;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden and discovery tests for the hand-written output formats, rendered over one
 * shared two-sentence document.
 */
class OutputFormatsTest {

  private static final String TEXT = "Alpha owns beta. Gamma!";

  /** Builds one token with a document span and optional annotations. */
  private static Token token(String text, int start, int end, String pos, String lemma) {
    final Token.Builder token = Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT));
    if (pos != null) {
      token.setPosTag(pos);
    }
    if (lemma != null) {
      token.setLemma(lemma);
    }
    return token.build();
  }

  /** Builds the shared analyzed document the goldens render. */
  private static OpenNlpDocument document() {
    return OpenNlpDocument.newBuilder()
        .setDocId("doc-1")
        .setRawText(TEXT)
        .setDetectedLanguage("eng")
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(AnnotationSpan.newBuilder().setStart(0).setEnd(16)
                .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
            .addTokens(token("Alpha", 0, 5, "NNP", "alpha"))
            .addTokens(token("owns", 6, 10, "VBZ", "own"))
            .addTokens(token("beta", 11, 15, null, null))
            .addTokens(token(".", 15, 16, ".", null))
            .addEntities(NamedEntity.newBuilder().setEntityType("per").setText("Alpha")))
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(AnnotationSpan.newBuilder().setStart(17).setEnd(23)
                .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
            .addTokens(token("Gamma", 17, 22, "NNP", null))
            .addTokens(token("!", 22, 23, ".", null))
            .setSentimentLabel("positive"))
        .build();
  }

  /** Renders the shared document through one formatter. */
  private static String render(OutputFormatter<OpenNlpDocument> formatter)
      throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    formatter.format(document(), output);
    return output.toString(StandardCharsets.UTF_8);
  }

  @Test
  void rendersConllUWithSentenceCommentsAndSpaceAfter() throws IOException {
    assertEquals("""
        # sent_id = doc-1-1
        # text = Alpha owns beta.
        1\tAlpha\talpha\t_\tNNP\t_\t_\t_\t_\t_
        2\towns\town\t_\tVBZ\t_\t_\t_\t_\t_
        3\tbeta\t_\t_\t_\t_\t_\t_\t_\tSpaceAfter=No
        4\t.\t_\t_\t.\t_\t_\t_\t_\t_

        # sent_id = doc-1-2
        # text = Gamma!
        1\tGamma\t_\t_\tNNP\t_\t_\t_\t_\tSpaceAfter=No
        2\t!\t_\t_\t.\t_\t_\t_\t_\t_

        """, render(new ConllUDocumentFormatter()));
  }

  @Test
  void rendersCsvWithRfc4180QuotingAndCrlfRows() throws IOException {
    final OpenNlpDocument tricky = OpenNlpDocument.newBuilder()
        .addSentences(AnnotatedSentence.newBuilder()
            .addTokens(token("a,b", 0, 3, "X\"Y", null)))
        .build();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    new CsvDocumentFormatter().format(tricky, output);

    assertEquals("sentence,token,start,end,text,pos,lemma\r\n"
            + "0,0,0,3,\"a,b\",\"X\"\"Y\",\r\n",
        output.toString(StandardCharsets.UTF_8));
  }

  @Test
  void rendersAMarkdownReportWithEntitiesAndSentiment() throws IOException {
    final String report = render(new MarkdownDocumentFormatter());

    assertTrue(report.startsWith("# Document doc-1\n"));
    assertTrue(report.contains("Language: eng"));
    assertTrue(report.contains("## Sentence 1\n\n> Alpha owns beta."));
    assertTrue(report.contains("- **per**: Alpha"));
    assertTrue(report.contains("## Sentence 2"));
    assertTrue(report.contains("Sentiment: positive"));
  }

  @Test
  void markdownEscapesControlCharactersInDocumentText() throws IOException {
    final OpenNlpDocument hostile = OpenNlpDocument.newBuilder()
        .setDocId("doc*[1]")
        .build();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    new MarkdownDocumentFormatter().format(hostile, output);

    assertTrue(output.toString(StandardCharsets.UTF_8)
        .startsWith("# Document doc\\*\\[1\\]\n"));
  }

  @Test
  void rendersAWellFormedTwoRecordWarc() throws IOException {
    final String warc = render(new WarcDocumentFormatter());

    assertTrue(warc.startsWith("WARC/1.1\r\nWARC-Type: warcinfo\r\n"));
    assertTrue(warc.contains("WARC-Type: resource\r\n"));
    assertTrue(warc.contains("WARC-Target-URI: urn:opennlp:document:doc-1\r\n"));
    assertTrue(warc.contains("Content-Type: text/plain; charset=utf-8\r\n"));
    assertTrue(warc.contains("Content-Length: "
        + TEXT.getBytes(StandardCharsets.UTF_8).length + "\r\n"));
    assertTrue(warc.contains("\r\n\r\n" + TEXT + "\r\n\r\n"));
    assertTrue(warc.endsWith("\r\n\r\n"));
  }

  @Test
  void registersEveryFormatThroughTheFormatSpi() {
    assertEquals(4, ServiceLoader.load(OutputFormatter.class).stream()
        .filter(provider -> provider.type().getPackageName()
            .equals("org.apache.opennlp.grpc.format.addon"))
        .count());
  }

  @Test
  void theServerRegistryServesBuiltInAndAddOnFormatsTogether() {
    assertEquals(List.of("conllu", "csv", "markdown", "proto", "protojson", "tsv", "warc"),
        OutputFormatterRegistry.discover(OpenNlpDocument.class).descriptors().stream()
            .map(descriptor -> descriptor.getFormatId()).toList());
  }
}
