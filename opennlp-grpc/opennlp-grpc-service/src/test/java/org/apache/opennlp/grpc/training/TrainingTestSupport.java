/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import opennlp.embeddings.ModelDistiller;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.ImportDictionaryRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryStart;
import org.apache.opennlp.grpc.v1.LearnVocabularyRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.OpenNlpVocabularyServiceImpl;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;

/** Shared fixtures for the model training contract tests. */
final class TrainingTestSupport {

  /** Fixture vocabulary matching the static-table test model: row i is [i, i*10, i*100]. */
  static final List<String> VOCAB_TOKENS =
      List.of("[CLS]", "[SEP]", "[UNK]", "hello", "world", "liberty");

  /** Dimension of the fixture model's embedding rows. */
  static final int DIMENSION = 3;

  private TrainingTestSupport() {
  }

  /** Writes a complete loadable WordPiece static model directory. */
  static void writeStaticModelDirectory(Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), VOCAB_TOKENS);
    final ByteBuffer buffer = ByteBuffer
        .allocate(VOCAB_TOKENS.size() * DIMENSION * Float.BYTES)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (int row = 0; row < VOCAB_TOKENS.size(); row++) {
      for (int d = 0; d < DIMENSION; d++) {
        buffer.putFloat(row * (float) Math.pow(10, d));
      }
    }
    final byte[] data = buffer.array();
    final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":["
        + VOCAB_TOKENS.size() + "," + DIMENSION + "],\"data_offsets\":[0," + data.length + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data);
    Files.write(dir.resolve("model.safetensors"), out.toByteArray());
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false,\"hidden_dim\":" + DIMENSION + "}");
    Files.writeString(dir.resolve("tokenizer_config.json"),
        "{\"do_lower_case\":true,\"strip_accents\":null,\"tokenizer_class\":\"BertTokenizer\"}");
  }

  /** A trainer that records its inputs and writes the fixture model instead of distilling. */
  static final class RecordingTrainer implements StaticModelTrainer {

    String teacherReference;
    int pcaDims;
    List<String> terms;

    @Override
    public ModelDistiller.Result train(String teacherReference, Path outputDirectory,
        int pcaDims, List<String> terms, ModelDistiller.ProgressListener listener)
        throws IOException {
      this.teacherReference = teacherReference;
      this.pcaDims = pcaDims;
      this.terms = List.copyOf(terms);
      listener.progress("resolving teacher");
      listener.progress("distilling term rows");
      writeStaticModelDirectory(outputDirectory);
      return new ModelDistiller.Result(
          "WordPiece", VOCAB_TOKENS.size(), 0, DIMENSION, DIMENSION, 1.0d);
    }
  }

  /** A minimal single-model base provider for registry delegation tests. */
  static EmbeddingProvider baseProvider() {
    return new EmbeddingProvider() {
      @Override
      public String backendId() {
        return "fake";
      }

      @Override
      public boolean isAvailable() {
        return true;
      }

      @Override
      public Set<String> registeredModelIds() {
        return Set.of("base");
      }

      @Override
      public boolean supportsModel(String modelId) {
        return "base".equals(modelId);
      }

      @Override
      public int embeddingDimension(String modelId) {
        requireBase(modelId);
        return 2;
      }

      @Override
      public float[] embed(String modelId, String text) {
        requireBase(modelId);
        return new float[] {1f, 0f};
      }

      private void requireBase(String modelId) {
        if (!"base".equals(modelId)) {
          throw new IllegalArgumentException("Unknown embedding model '" + modelId + "'");
        }
      }
    };
  }

  /**
   * Publishes one dictionary and one learned vocabulary through the public vocabulary
   * service and returns the vocabulary artifact id.
   */
  static String vocabularyArtifact(
      DictionaryFormatRegistry formats, VocabularyArtifactStore vocabularies) {
    final OpenNlpVocabularyServiceImpl service =
        new OpenNlpVocabularyServiceImpl(formats, vocabularies);
    final CapturingObserver<DictionaryArtifactDescriptor> imported = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> importRequests =
        service.importDictionary(imported);
    importRequests.onNext(ImportDictionaryRequest.newBuilder()
        .setStart(ImportDictionaryStart.newBuilder()
            .setFormat(DictionaryFormatSelector.newBuilder().setStandard(
                StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV))
            .setDisplayName("Legal dictionary")
            .setProvenanceSummary("Authored fixture"))
        .build());
    importRequests.onNext(ImportDictionaryRequest.newBuilder()
        .setData(ByteString.copyFromUtf8("liberty\tA right.\nhabeas corpus\tA writ.\n"))
        .build());
    importRequests.onCompleted();
    if (imported.error != null) {
      throw new IllegalStateException("Fixture dictionary import failed", imported.error);
    }

    final CapturingObserver<VocabularyArtifactDescriptor> learned = new CapturingObserver<>();
    final StreamObserver<LearnVocabularyRequest> learnRequests =
        service.learnVocabulary(learned);
    learnRequests.onNext(LearnVocabularyRequest.newBuilder()
        .setStart(LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId(imported.values.getFirst().getArtifactId())
            .setDisplayName("Legal vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(50)
            .setProvenanceSummary("Authored fixture"))
        .build());
    learnRequests.onNext(LearnVocabularyRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("one")
            .setRawText("Liberty protects liberty and liberty endures."))
        .build());
    learnRequests.onNext(LearnVocabularyRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("two")
            .setRawText("Habeas corpus matters."))
        .build());
    learnRequests.onCompleted();
    if (learned.error != null) {
      throw new IllegalStateException("Fixture vocabulary build failed", learned.error);
    }
    return learned.values.getFirst().getArtifactId();
  }

  /** Records every observed value and terminal event of one RPC. */
  static final class CapturingObserver<T> implements StreamObserver<T> {

    final List<T> values = new ArrayList<>();
    Throwable error;
    boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
