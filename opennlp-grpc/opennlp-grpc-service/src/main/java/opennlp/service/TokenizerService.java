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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

import opennlp.OpenNLPService;
import opennlp.SentenceDetectorServiceGrpc;
import opennlp.TokenizerTaggerServiceGrpc;
import opennlp.service.exception.ServiceException;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.models.ClassPathModel;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

/**
 * The {@code Tokenizer} class implements a gRPC service for tokenization
 * using Apache OpenNLP models. It extends the auto-generated gRPC base class
 * {@link TokenizerTaggerServiceGrpc.TokenizerTaggerServiceImplBase}.
 *
 * <p>This service provides functionality for:
 * <ul>
 *   <li>Retrieving available tokenizer models loaded from the classpath.</li>
 *   <li>Performing tokenization on an input string.</li>
 *   <li>Performing positional tokenization on an input string.</li>
 * </ul>
 * </p>
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@code model.location}: Directory to search for models (default: "extlib").</li>
 *   <li>{@code model.recursive}: Whether to scan subdirectories (default: {@code true}).</li>
 *   <li>{@code model.tokenizer.wildcard.pattern}: Wildcard pattern to identify tokenization models (default: "opennlp-models-tokenizer-*.jar").</li>
 * </ul>
 *  </p>
 */
@ThreadSafe
public class TokenizerService extends TokenizerTaggerServiceGrpc.TokenizerTaggerServiceImplBase
    implements CacheAware<Tokenizer>, AutoCloseable, ExceptionAware, ModelAware<Tokenizer> {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(TokenizerService.class);

  private static final Map<String, ClassPathModel> MODEL_CACHE = new ConcurrentHashMap<>();
  private static final Map<String, Tokenizer> TOKENIZER_CACHE = new ConcurrentHashMap<>();

  /**
   * Initializes a Tokenizer service with the given configuration properties.
   *
   * <p>The configuration properties are provided as a {@code Map<String, String>} and define various
   * parameters required for the service, such as model locations, recursive loading, and wildcard patterns.
   *
   * <p>Example configuration:
   * <pre>{@code
   * server.enable_reflection = false
   * model.location = target/test-classes/models
   * model.recursive = true
   * model.tokenizer.wildcard.pattern = opennlp-models-tokenizer-*.jar
   * }</pre>
   *
   * @param conf Configuration properties for the service (key-value format). Must not be {@code null}.
   *             If a property is missing, default values are used.
   * @throws RuntimeException if an {@link IOException} occurs during model cache initialization.
   */
  public TokenizerService(Map<String, String> conf) {

    try {
      final Map<String, ClassPathModel> found = ModelFinderUtil.findModels(conf, "model.tokenizer.wildcard.pattern", "opennlp-models-tokenizer-*.jar");
      for (Map.Entry<String, ClassPathModel> entry : found.entrySet()) {
        MODEL_CACHE.putIfAbsent(entry.getKey(), entry.getValue());
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new RuntimeException(e);
    }

  }

 @Override
  public void tokenize(opennlp.OpenNLPService.TokenizeRequest request,
                       io.grpc.stub.StreamObserver<opennlp.OpenNLPService.StringList> responseObserver) {
   try {
     final Tokenizer tokenizer = getTokenizer(request.getModelHash());
     final String[] tokens = tokenizer.tokenize(request.getSentence());
     responseObserver.onNext(OpenNLPService.StringList.newBuilder().addAllValues(Arrays.asList(tokens)).build());
     responseObserver.onCompleted();
   } catch (Exception e) {
     handleException(e, responseObserver);
   }
  }

  @Override
  public void tokenizePos(opennlp.OpenNLPService.TokenizePosRequest request,
                          io.grpc.stub.StreamObserver<opennlp.OpenNLPService.SpanList> responseObserver) {
    try {
      final Tokenizer tokenizer = getTokenizer(request.getModelHash());
      final Span[] spans = tokenizer.tokenizePos(request.getSentence());
      final OpenNLPService.SpanList response = OpenNLPService.SpanList.newBuilder()
          .addAllValues(Arrays.stream(spans)
              .map(s -> OpenNLPService.Span.newBuilder()
                  .setStart(s.getStart())
                  .setEnd(s.getEnd())
                  .setProb(s.getProb())
                  .setType(s.getType() == null ? "" : s.getType())
                  .build())
              .toList())
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      handleException(e, responseObserver);
    }
  }

  @Override
  public void getAvailableModels(opennlp.OpenNLPService.Empty request,
                                 io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
    returnAvailableModels(request, responseObserver);
  }

  private Tokenizer getTokenizer(String hash) {
    return TOKENIZER_CACHE.computeIfAbsent((hash), modelHash -> {
      final ClassPathModel model = MODEL_CACHE.get(modelHash);

      if (model == null) {
        throw new ServiceException("Could not find the given model.");
      }

      try(BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(model.model()))) {
        return new ThreadSafeTokenizerME(new TokenizerModel(bis));
      } catch (IOException e) {
        throw new ServiceException(e.getLocalizedMessage(), e);
      }
    });
  }

  @Override
  public Map<String, ClassPathModel> getModelCache() {
    return MODEL_CACHE;
  }

  @Override
  public Map<String, Tokenizer> getServiceCache() {
    return TOKENIZER_CACHE;
  }

  /**
   * Clears the in-memory caches for services and models.
   */
  @Override
  public void close() {
    clearCaches();
  }
}
