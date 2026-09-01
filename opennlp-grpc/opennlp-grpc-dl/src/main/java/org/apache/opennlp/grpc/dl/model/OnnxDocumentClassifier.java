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
package org.apache.opennlp.grpc.dl.model;

import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import opennlp.dl.AbstractDL;
import opennlp.tools.tokenize.WordpieceTokenizer;
import org.apache.opennlp.grpc.dl.embedding.onnx.BertTokenizer;

/**
 * Batched sequence classification over one ONNX transformer session: WordPiece tokenization,
 * windowing of long inputs, padded sub-batches within a token-slot budget, and softmax over
 * the logits averaged across a document's windows. Only the inputs the model declares are
 * fed, so exports without {@code token_type_ids} (DistilBERT) load like BERT exports.
 */
final class OnnxDocumentClassifier extends AbstractDL implements AutoCloseable {

  /** Longest token window fed to the model, special tokens included. */
  static final int MAX_SEQUENCE_TOKENS = 512;

  /**
   * Upper bound on {@code batch * maxLength} token slots per {@code session.run}; larger
   * inputs run as several sequential sub-batches with identical results.
   */
  static final int MAX_BATCH_TOKENS = 16_384;

  private final BertTokenizer bertTokenizer;
  private final Set<String> declaredInputs;
  private final long unknownTokenId;
  private final long clsTokenId;
  private final long sepTokenId;
  private final List<String> categories;

  /**
   * Opens the session and tokenizer.
   *
   * @param model The ONNX model file.
   * @param vocabulary The WordPiece vocabulary file.
   * @param categories The category labels in output index order. Must not be empty.
   * @param useCuda Whether to place the session on the CUDA execution provider.
   * @param gpuDeviceId The CUDA device when {@code useCuda} is set.
   * @param lowerCase Whether the tokenizer lower-cases input, matching an uncased vocabulary.
   *
   * @throws OrtException If the session cannot be created.
   * @throws IOException If the model or vocabulary cannot be read.
   * @throws IllegalArgumentException If {@code categories} is empty or the vocabulary lacks
   *     the special tokens.
   */
  OnnxDocumentClassifier(File model, File vocabulary, List<String> categories,
      boolean useCuda, int gpuDeviceId, boolean lowerCase) throws OrtException, IOException {
    super(model, vocabulary, sessionOptions(useCuda, gpuDeviceId), lowerCase);
    try {
      if (categories == null || categories.isEmpty()) {
        throw new IllegalArgumentException("categories must not be empty");
      }
      this.categories = List.copyOf(categories);
      final boolean roberta = vocab.containsKey(WordpieceTokenizer.ROBERTA_CLS_TOKEN);
      final String cls = roberta
          ? WordpieceTokenizer.ROBERTA_CLS_TOKEN : WordpieceTokenizer.BERT_CLS_TOKEN;
      final String sep = roberta
          ? WordpieceTokenizer.ROBERTA_SEP_TOKEN : WordpieceTokenizer.BERT_SEP_TOKEN;
      final String unk = roberta
          ? WordpieceTokenizer.ROBERTA_UNK_TOKEN : WordpieceTokenizer.BERT_UNK_TOKEN;
      for (String special : List.of(cls, sep, unk)) {
        if (!vocab.containsKey(special)) {
          throw new IllegalArgumentException(
              "Vocabulary lacks the special token '" + special + "'");
        }
      }
      this.bertTokenizer = new BertTokenizer(vocab.keySet(), lowerCase, cls, sep, unk);
      this.unknownTokenId = vocab.get(unk);
      this.clsTokenId = vocab.get(cls);
      this.sepTokenId = vocab.get(sep);
      this.declaredInputs = Set.copyOf(session.getInputNames());
    } catch (RuntimeException e) {
      try {
        session.close();
      } catch (OrtException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }

  /** Creates session options, registering CUDA after the shared environment exists. */
  private static OrtSession.SessionOptions sessionOptions(boolean useCuda, int gpuDeviceId)
      throws OrtException {
    OrtEnvironment.getEnvironment();
    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (useCuda) {
      sessionOptions.addCUDA(gpuDeviceId);
    }
    return sessionOptions;
  }

  /**
   * {@return the category labels in output index order}
   */
  List<String> categories() {
    return categories;
  }

  /**
   * Scores several texts, batching inference within {@link #MAX_BATCH_TOKENS}.
   *
   * @param texts The texts to classify. Must not be {@code null} or contain {@code null}.
   *
   * @return One probability distribution per text over {@link #categories()}, in input order.
   *
   * @throws OrtException If inference fails.
   */
  List<double[]> scoreBatch(List<String> texts) throws OrtException {
    return scoreBatch(texts, MAX_BATCH_TOKENS);
  }

  /**
   * Scores several texts with an explicit token-slot budget; package-private so tests can
   * force sub-batching on small inputs.
   *
   * @param texts The texts to classify. Must not be {@code null} or contain {@code null}.
   * @param maxBatchTokens The largest {@code batch * maxLength} product per call. Must be
   *     positive.
   *
   * @return One probability distribution per text over {@link #categories()}, in input order.
   *
   * @throws OrtException If inference fails.
   */
  List<double[]> scoreBatch(List<String> texts, int maxBatchTokens) throws OrtException {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    if (maxBatchTokens < 1) {
      throw new IllegalArgumentException("maxBatchTokens must be positive");
    }
    // Every window of every text joins one flat list; owners map windows back to texts.
    final List<long[]> windows = new ArrayList<>();
    final List<Integer> owners = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      final String text = texts.get(i);
      if (text == null) {
        throw new IllegalArgumentException("texts must not contain null elements");
      }
      for (long[] window : windows(contentIds(text))) {
        windows.add(window);
        owners.add(i);
      }
    }
    final double[][] sums = new double[texts.size()][categories.size()];
    final int[] counts = new int[texts.size()];
    final int[] lengths = new int[windows.size()];
    for (int i = 0; i < windows.size(); i++) {
      lengths[i] = windows.get(i).length;
    }
    int from = 0;
    for (int to : planBatches(lengths, maxBatchTokens)) {
      final float[][] logits = runBatch(windows, from, to);
      for (int i = 0; i < logits.length; i++) {
        final int owner = owners.get(from + i);
        final double[] probabilities = softmax(logits[i]);
        for (int c = 0; c < categories.size(); c++) {
          sums[owner][c] += probabilities[c];
        }
        counts[owner]++;
      }
      from = to;
    }
    final List<double[]> scores = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i++) {
      final double[] averaged = new double[categories.size()];
      for (int c = 0; c < categories.size(); c++) {
        averaged[c] = counts[i] == 0 ? 0 : sums[i][c] / counts[i];
      }
      scores.add(averaged);
    }
    return scores;
  }

  /** Tokenizes one text into content token ids, without the CLS and SEP markers. */
  private long[] contentIds(String text) {
    final String[] tokens = bertTokenizer.tokenize(text);
    final List<Long> ids = new ArrayList<>(tokens.length);
    for (String token : tokens) {
      final Integer id = vocab.get(token);
      final long resolved = id != null ? id : unknownTokenId;
      if (resolved == clsTokenId || resolved == sepTokenId) {
        continue;
      }
      ids.add(resolved);
    }
    final long[] result = new long[ids.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = ids.get(i);
    }
    return result;
  }

  /**
   * Splits content ids into windows of at most {@link #MAX_SEQUENCE_TOKENS} tokens including
   * the CLS and SEP markers each window carries. Empty input yields one marker-only window,
   * so every text produces a score.
   *
   * @param contentIds The content token ids.
   *
   * @return The windows, in order.
   */
  List<long[]> windows(long[] contentIds) {
    return windows(contentIds, MAX_SEQUENCE_TOKENS, clsTokenId, sepTokenId);
  }

  /**
   * Windowing with explicit parameters, for tests.
   *
   * @param contentIds The content token ids.
   * @param maxSequence The largest window including both markers. Must exceed 2.
   * @param cls The CLS id.
   * @param sep The SEP id.
   *
   * @return The windows, in order.
   */
  static List<long[]> windows(long[] contentIds, int maxSequence, long cls, long sep) {
    if (maxSequence <= 2) {
      throw new IllegalArgumentException("maxSequence must leave room for content");
    }
    final int capacity = maxSequence - 2;
    final List<long[]> windows = new ArrayList<>();
    int offset = 0;
    do {
      final int length = Math.min(capacity, contentIds.length - offset);
      final long[] window = new long[length + 2];
      window[0] = cls;
      System.arraycopy(contentIds, offset, window, 1, length);
      window[length + 1] = sep;
      windows.add(window);
      offset += length;
    } while (offset < contentIds.length);
    return windows;
  }

  /**
   * Splits windows into consecutive sub-batches whose padded size stays within the budget.
   *
   * @param lengths The token count of every window, in order.
   * @param maxBatchTokens The largest {@code count * longest} product per sub-batch.
   *
   * @return The exclusive end index of every sub-batch, ascending.
   */
  static int[] planBatches(int[] lengths, int maxBatchTokens) {
    final int[] ends = new int[lengths.length];
    int batches = 0;
    int count = 0;
    int longest = 0;
    for (int i = 0; i < lengths.length; i++) {
      final int candidateLongest = Math.max(longest, lengths[i]);
      if (count > 0 && (long) (count + 1) * candidateLongest > maxBatchTokens) {
        ends[batches++] = i;
        count = 0;
        longest = 0;
      }
      longest = Math.max(longest, lengths[i]);
      count++;
    }
    if (count > 0) {
      ends[batches++] = lengths.length;
    }
    return Arrays.copyOf(ends, batches);
  }

  /**
   * Converts logits into probabilities.
   *
   * @param logits The raw scores of one window.
   *
   * @return Probabilities summing to one.
   */
  static double[] softmax(float[] logits) {
    double max = Double.NEGATIVE_INFINITY;
    for (float logit : logits) {
      max = Math.max(max, logit);
    }
    final double[] exp = new double[logits.length];
    double sum = 0;
    for (int i = 0; i < logits.length; i++) {
      exp[i] = Math.exp(logits[i] - max);
      sum += exp[i];
    }
    for (int i = 0; i < exp.length; i++) {
      exp[i] /= sum;
    }
    return exp;
  }

  /** Runs one padded sub-batch of windows and returns their logits. */
  private float[][] runBatch(List<long[]> windows, int from, int to) throws OrtException {
    final int batch = to - from;
    int maxLength = 0;
    for (int i = from; i < to; i++) {
      maxLength = Math.max(maxLength, windows.get(i).length);
    }
    final long[] flatIds = new long[batch * maxLength];
    final long[] flatMask = new long[batch * maxLength];
    final long[] flatTypes = new long[batch * maxLength];
    for (int i = 0; i < batch; i++) {
      final long[] window = windows.get(from + i);
      final int offset = i * maxLength;
      System.arraycopy(window, 0, flatIds, offset, window.length);
      Arrays.fill(flatMask, offset, offset + window.length, 1L);
    }
    final long[] shape = {batch, maxLength};
    final Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(flatIds), shape));
      if (declaredInputs.contains(ATTENTION_MASK)) {
        inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, LongBuffer.wrap(flatMask), shape));
      }
      if (declaredInputs.contains(TOKEN_TYPE_IDS)) {
        inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(flatTypes), shape));
      }
      try (OrtSession.Result result = session.run(inputs)) {
        final float[][] logits = (float[][]) result.get(0).getValue();
        if (logits.length != batch || (batch > 0 && logits[0].length != categories.size())) {
          throw new OrtException("Model produced logits of shape [" + logits.length + ", "
              + (batch > 0 ? logits[0].length : 0) + "] for " + batch + " window(s) and "
              + categories.size() + " categories");
        }
        return logits;
      }
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }
  }

  /** {@inheritDoc} Closes the session; the shared environment stays open for other models. */
  @Override
  public void close() throws OrtException {
    session.close();
  }
}
