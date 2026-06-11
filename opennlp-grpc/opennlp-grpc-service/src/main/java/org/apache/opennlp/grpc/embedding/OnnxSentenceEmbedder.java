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
package org.apache.opennlp.grpc.embedding;

import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import opennlp.dl.AbstractDL;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * Computes sentence embeddings with a BERT-style ONNX model and a wordpiece vocabulary.
 *
 * <p>This embedder is the inference core behind {@link AbstractOnnxEmbeddingProvider}. It
 * reuses the vocabulary loading of {@link AbstractDL} (BERT or RoBERTa special tokens,
 * chosen from the vocabulary contents) and adds the pieces {@code opennlp-dl}'s
 * {@code SentenceVectorsDL} does not offer: full BERT text normalization through
 * {@link BertTokenizer}, configurable pooling, an optional CUDA execution provider,
 * session-metadata based dimension discovery and deterministic native resource
 * management.</p>
 *
 * <p>Model input conventions follow the standard single-segment BERT encoding:
 * {@code attention_mask} is {@code 1} for every real token and {@code token_type_ids}
 * is {@code 0} throughout. Inputs the model does not declare (many sentence-transformers
 * exports omit {@code token_type_ids}) are not sent.</p>
 *
 * <p>Two pooling strategies are supported. {@link Pooling#MEAN} averages the hidden
 * states of all tokens and L2-normalizes the result; this is the sentence-transformers
 * convention and the correct choice for models from that family. {@link Pooling#CLS}
 * returns the raw hidden state of the leading classification token, for models trained
 * with a CLS sentence objective.</p>
 *
 * <p>Token sequences are truncated to {@link #MAX_SEQUENCE_TOKENS} wordpieces (the
 * trailing separator token is preserved) so that inputs never exceed the positional range
 * of BERT-style encoders.</p>
 */
final class OnnxSentenceEmbedder extends AbstractDL {

  /** How the per-token hidden states are reduced to one sentence vector. */
  enum Pooling {
    /** Masked mean of all token hidden states, L2-normalized (sentence-transformers). */
    MEAN,
    /** The raw hidden state of the leading classification token. */
    CLS
  }

  /** Maximum wordpiece sequence length accepted by BERT-style encoders. */
  static final int MAX_SEQUENCE_TOKENS = 512;

  private final BertTokenizer bertTokenizer;
  private final Pooling pooling;
  private final Set<String> declaredInputs;
  private final long unknownTokenId;
  private final int embeddingDimension;

  /**
   * Loads the ONNX model and vocabulary and prepares an inference session.
   *
   * @param model       The ONNX model file. Must exist.
   * @param vocabulary  The wordpiece vocabulary file matching the model. Must exist.
   * @param useCuda     Whether to register the CUDA execution provider.
   * @param gpuDeviceId The CUDA device ordinal; ignored when {@code useCuda} is {@code false}.
   * @param lowerCase   Whether to lower case and accent strip the input text. Must be
   *                    {@code true} for uncased models and {@code false} for cased models.
   * @param pooling     The pooling strategy. Must not be {@code null}.
   *
   * @throws OrtException If the ONNX session cannot be created or the model does not
   *                      declare a static embedding dimension.
   * @throws IOException  If the vocabulary cannot be read or lacks the special tokens
   *                      required by the wordpiece tokenizer.
   */
  OnnxSentenceEmbedder(File model, File vocabulary, boolean useCuda, int gpuDeviceId,
      boolean lowerCase, Pooling pooling) throws OrtException, IOException {
    env = OrtEnvironment.getEnvironment();
    try (OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions()) {
      if (useCuda) {
        sessionOptions.addCUDA(gpuDeviceId);
      }
      session = env.createSession(model.getPath(), sessionOptions);
    }
    try {
      this.pooling = pooling;
      vocab = loadVocab(vocabulary);
      final boolean roberta = vocab.containsKey(WordpieceTokenizer.ROBERTA_CLS_TOKEN);
      final String cls = roberta
          ? WordpieceTokenizer.ROBERTA_CLS_TOKEN : WordpieceTokenizer.BERT_CLS_TOKEN;
      final String sep = roberta
          ? WordpieceTokenizer.ROBERTA_SEP_TOKEN : WordpieceTokenizer.BERT_SEP_TOKEN;
      final String unk = roberta
          ? WordpieceTokenizer.ROBERTA_UNK_TOKEN : WordpieceTokenizer.BERT_UNK_TOKEN;
      requireSpecialTokens(vocab, cls, sep, unk);
      bertTokenizer = new BertTokenizer(vocab.keySet(), lowerCase, cls, sep, unk);
      unknownTokenId = vocab.get(unk);
      declaredInputs = Set.copyOf(session.getInputNames());
      embeddingDimension = readEmbeddingDimension(session, model);
    } catch (OrtException | IOException | RuntimeException e) {
      try {
        session.close();
      } catch (OrtException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }

  /**
   * @return The embedding dimension declared by the model's output metadata.
   */
  int embeddingDimension() {
    return embeddingDimension;
  }

  /**
   * Embeds the given text.
   *
   * @param text The text to embed. Must not be {@code null}.
   *
   * @return The embedding vector of length {@link #embeddingDimension()}.
   *
   * @throws OrtException If inference fails.
   */
  float[] embed(String text) throws OrtException {
    final long[] ids = tokenIds(text);
    final long[] mask = new long[ids.length];
    Arrays.fill(mask, 1);
    final long[] types = new long[ids.length];
    final long[] shape = {1, ids.length};

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
      if (declaredInputs.contains(ATTENTION_MASK)) {
        inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
      }
      if (declaredInputs.contains(TOKEN_TYPE_IDS)) {
        inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(types), shape));
      }
      try (OrtSession.Result result = session.run(inputs)) {
        // getValue() copies the tensor into Java arrays, so the result can be closed safely.
        final float[][][] hiddenStates = (float[][][]) result.get(0).getValue();
        return pool(hiddenStates[0]);
      }
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }
  }

  /**
   * Closes the inference session. The shared {@link OrtEnvironment} singleton is left
   * open intentionally because other models may still be using it.
   */
  @Override
  public void close() throws OrtException {
    session.close();
  }

  /**
   * Reduces the per-token hidden states to a single sentence vector according
   * to the configured {@link Pooling} strategy.
   */
  private float[] pool(float[][] hiddenStates) {
    if (pooling == Pooling.CLS) {
      return hiddenStates[0];
    }
    // Mean pooling: average all token positions (the sequence is never padded, so
    // every position is a real token), then L2-normalize as sentence-transformers does.
    final int dimension = hiddenStates[0].length;
    final float[] mean = new float[dimension];
    for (float[] hiddenState : hiddenStates) {
      for (int i = 0; i < dimension; i++) {
        mean[i] += hiddenState[i];
      }
    }
    double sumOfSquares = 0;
    for (int i = 0; i < dimension; i++) {
      mean[i] /= hiddenStates.length;
      sumOfSquares += (double) mean[i] * mean[i];
    }
    final float norm = (float) Math.sqrt(sumOfSquares);
    if (norm > 0) {
      for (int i = 0; i < dimension; i++) {
        mean[i] /= norm;
      }
    }
    return mean;
  }

  private long[] tokenIds(String text) {
    String[] tokens = bertTokenizer.tokenize(text);
    if (tokens.length > MAX_SEQUENCE_TOKENS) {
      final String separator = tokens[tokens.length - 1];
      tokens = Arrays.copyOf(tokens, MAX_SEQUENCE_TOKENS);
      tokens[MAX_SEQUENCE_TOKENS - 1] = separator;
    }
    final long[] ids = new long[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      final Integer id = vocab.get(tokens[i]);
      ids[i] = id != null ? id : unknownTokenId;
    }
    return ids;
  }

  /**
   * Verifies that the special tokens chosen from the vocabulary contents are present,
   * so that every tokenizer output can be mapped to an id.
   */
  private static void requireSpecialTokens(Map<String, Integer> vocab,
      String cls, String sep, String unk) throws IOException {
    for (String token : new String[] {cls, sep, unk}) {
      if (!vocab.containsKey(token)) {
        throw new IOException("Embedding vocabulary does not define the special token '"
            + token + "'; the vocabulary file does not match the model");
      }
    }
  }

  /**
   * Reads the embedding dimension from the last axis of the model's first output tensor.
   */
  private static int readEmbeddingDimension(OrtSession session, File model) throws OrtException {
    final NodeInfo output = session.getOutputInfo().values().iterator().next();
    if (!(output.getInfo() instanceof TensorInfo tensorInfo)) {
      throw new OrtException("Embedding model output '" + output.getName()
          + "' of " + model.getName() + " is not a tensor");
    }
    final long[] shape = tensorInfo.getShape();
    final long dimension = shape.length > 0 ? shape[shape.length - 1] : -1;
    if (dimension <= 0 || dimension > Integer.MAX_VALUE) {
      throw new OrtException("Embedding model " + model.getName()
          + " does not declare a static embedding dimension (output shape: "
          + Arrays.toString(shape) + ")");
    }
    return (int) dimension;
  }
}
