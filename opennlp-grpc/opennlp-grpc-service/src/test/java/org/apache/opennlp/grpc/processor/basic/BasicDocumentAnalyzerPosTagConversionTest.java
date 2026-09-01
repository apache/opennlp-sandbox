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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyPosLemmaModels;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.POSTagFormat;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a requested {@code pos_tag_format} conversion changes what
 * {@code Token.pos_tag} reports but never what the lemmatizer is fed: the lemmatizer model
 * was trained on the tagger's native tagset, so it must receive native tags. Uses tiny
 * in-memory trained models (see {@link TinyPosLemmaModels}) whose probe lemmatizer reveals
 * the received tagset through the output lemma.
 */
class BasicDocumentAnalyzerPosTagConversionTest {

  private static final String TEXT = "the cats sleep .";

  @TempDir
  static Path modelDir;

  private static Path posModelPath;
  private static Path lemmaModelPath;

  @BeforeAll
  static void trainModels() throws IOException {
    posModelPath = TinyPosLemmaModels.trainPosModel(modelDir.resolve("pos-tiny.bin"));
    lemmaModelPath = TinyPosLemmaModels.trainLemmaModel(modelDir.resolve("lemma-tiny.bin"));
  }

  private static BasicDocumentAnalyzer analyzerWithTinyModels() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of(
        "model.pos_tagger.path", posModelPath.toString(),
        "model.lemmatizer.path", lemmaModelPath.toString()));
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), modelBundleCache);
  }

  private static AnalyzeDocumentRequest request(POSTagFormat format) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId("pos-conversion-test")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE);
    if (format != POSTagFormat.POS_TAG_FORMAT_UNSPECIFIED) {
      profile.setPosTagFormat(format);
    }
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile)
        .build();
  }

  private static AnnotatedSentence onlySentence(AnalyzeDocumentResponse response) {
    assertEquals(1, response.getDocument().getSentencesCount());
    return response.getDocument().getSentences(0);
  }

  @Test
  void posTagFormatConversionChangesReportedTagsOnly() {
    final AnnotatedSentence baseline = onlySentence(
        analyzerWithTinyModels().analyze(request(POSTagFormat.POS_TAG_FORMAT_UNSPECIFIED)));
    // The tiny tagger natively emits UD tags; "cats" is a NOUN and lemmatizes to "cat".
    assertEquals("NOUN", baseline.getTokens(1).getPosTag());
    assertEquals("cat", baseline.getTokens(1).getLemma());

    final AnnotatedSentence converted = onlySentence(
        analyzerWithTinyModels().analyze(request(POSTagFormat.POS_TAG_FORMAT_PENN)));
    // Token.pos_tag reports the requested PENN tagset...
    assertEquals("NN", converted.getTokens(1).getPosTag());
    // ...but the lemmatizer was fed the native UD tags: "cats" still lemmatizes to "cat",
    // not to the probe model's PENN-tag marker lemma "cat-penn".
    for (Token token : converted.getTokensList()) {
      assertEquals(baseline.getTokens(converted.getTokensList().indexOf(token)).getLemma(),
          token.getLemma(), "lemma of '" + token.getText() + "' changed under conversion");
    }
    assertEquals("cat", converted.getTokens(1).getLemma());
  }
}
