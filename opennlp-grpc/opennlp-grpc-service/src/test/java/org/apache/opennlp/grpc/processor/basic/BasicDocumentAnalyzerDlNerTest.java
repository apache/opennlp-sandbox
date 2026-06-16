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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end NER through the ONNX {@code DlNerModel} backend against a real model. Opt-in:
 * runs only when {@code -Ddl.ner.model.dir=<dir>} points at a directory containing
 * {@code model.onnx} and {@code vocab.txt} — the ONNX export of {@code dslim/bert-base-NER}
 * (see the {@code dl-ner} build profile, which downloads them into {@code target/}). The
 * model is downloaded at build time only and is never bundled or redistributed.
 */
class BasicDocumentAnalyzerDlNerTest {

  private static final String TEXT =
      "George Washington was president of the United States.";

  /** The BIO label set of dslim/bert-base-NER, in output-index order (line number = id). */
  private static final String LABELS =
      "O\nB-MISC\nI-MISC\nB-PER\nI-PER\nB-ORG\nI-ORG\nB-LOC\nI-LOC\n";

  @TempDir
  Path tempDir;

  @Test
  void detectsMultipleEntityTypesWithOnnxModel() throws IOException {
    final String dir = System.getProperty("dl.ner.model.dir");
    assumeTrue(dir != null && !dir.isBlank(), "set -Ddl.ner.model.dir to run the ONNX NER test");
    final File model = new File(dir, "model.onnx");
    final File vocab = new File(dir, "vocab.txt");
    assumeTrue(model.isFile() && vocab.isFile(),
        "model.onnx and vocab.txt must exist in " + dir);
    // The label set is public model metadata (not weights); write it next to the test.
    final Path labels = Files.writeString(tempDir.resolve("labels.txt"), LABELS,
        StandardCharsets.UTF_8);

    // The model id is arbitrary; the entity types come from the model's BIO labels.
    final Map<String, String> configuration = Map.of(
        "model.name_finder_dl.bert_ner.path", model.getPath(),
        "model.name_finder_dl.bert_ner.vocab", vocab.getPath(),
        "model.name_finder_dl.bert_ner.labels", labels.toString());

    final ModelBundleCache modelBundleCache = new ModelBundleCache(configuration);
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(true), modelBundleCache);

    final AnalyzeDocumentResponse response = analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            // No ner_entity_types filter -> return every type the model emits.
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_NER)
                .build())
            // UTF-16 offsets so spans index directly into the Java string below.
            .setOptions(AnalysisOptions.newBuilder()
                .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
                .build())
            .build());

    String personText = null;
    String locationText = null;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      for (NamedEntity entity : sentence.getEntitiesList()) {
        final String covered = TEXT.substring(
            entity.getAnnotationSpan().getStart(), entity.getAnnotationSpan().getEnd());
        // Entity types are the model's own labels, normalized (PER -> "per", LOC -> "loc").
        if ("per".equals(entity.getEntityType())) {
          personText = covered;
        } else if ("loc".equals(entity.getEntityType())) {
          locationText = covered;
        }
      }
    }
    // The multi-type decoder finds both the person and the location.
    assertEquals("George Washington", personText);
    assertEquals("United States", locationText);
  }
}
