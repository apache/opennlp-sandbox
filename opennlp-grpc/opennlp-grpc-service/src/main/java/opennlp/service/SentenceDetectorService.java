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
import opennlp.service.exception.ServiceException;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.models.ClassPathModel;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.ThreadSafeSentenceDetectorME;
import opennlp.tools.util.Span;

/**
 * The {@code SentenceDetector} class implements a gRPC service for sentence detection
 * using Apache OpenNLP models. It extends the auto-generated gRPC base class
 * {@link SentenceDetectorServiceGrpc.SentenceDetectorServiceImplBase}.
 *
 * <p>This service provides functionality for:
 * <ul>
 *   <li>Retrieving available sentence detection models loaded from the classpath.</li>
 *   <li>Performing sentence detection on an input string.</li>
 *   <li>Performing positional sentence detection on an input string.</li>
 * </ul>
 * </p>
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@code model.location}: Directory to search for models (default: "extlib").</li>
 *   <li>{@code model.recursive}: Whether to scan subdirectories (default: {@code true}).</li>
 *   <li>{@code model.sentdetect.wildcard.pattern}: Wildcard pattern to identify sentence detection models (default: "opennlp-models-sentdetect-*.jar").</li>
 * </ul>
 *  </p>
 */
@ThreadSafe
public class SentenceDetectorService extends SentenceDetectorServiceGrpc.SentenceDetectorServiceImplBase
    implements CacheAware<SentenceDetector>, AutoCloseable, ExceptionAware, ModelAware<SentenceDetector> {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(SentenceDetectorService.class);

  private static final Map<String, ClassPathModel> MODEL_CACHE = new ConcurrentHashMap<>();
  private static final Map<String, SentenceDetector> SENTENCE_DETECTOR_CACHE = new ConcurrentHashMap<>();

  /**
   * Initializes a Sentence Detector service with the given configuration properties.
   *
   * <p>The configuration properties are provided as a {@code Map<String, String>} and define various
   * parameters required for the service, such as model locations, recursive loading, and wildcard patterns.
   *
   * <p>Example configuration:
   * <pre>{@code
   * server.enable_reflection = false
   * model.location = target/test-classes/models
   * model.recursive = true
   * model.sentdetect.wildcard.pattern = opennlp-models-sentdetect-*.jar
   * }</pre>
   *
   * @param conf Configuration properties for the service (key-value format). Must not be {@code null}.
   *             If a property is missing, default values are used.
   * @throws RuntimeException if an {@link IOException} occurs during model cache initialization.
   */
  public SentenceDetectorService(Map<String, String> conf) {

    try {
      final Map<String, ClassPathModel> found = ModelFinderUtil.findModels(conf, "model.sentdetect.wildcard.pattern", "opennlp-models-sentdetect-*.jar");
      for (Map.Entry<String, ClassPathModel> entry : found.entrySet()) {
        MODEL_CACHE.putIfAbsent(entry.getKey(), entry.getValue());
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new RuntimeException(e);
    }

  }

 @Override
  public void sentDetect(OpenNLPService.SentDetectRequest request,
                       io.grpc.stub.StreamObserver<OpenNLPService.StringList> responseObserver) {
   try {
     final SentenceDetector sentenceDetector = getSentenceDetector(request.getModelHash());
     final String[] tokens = sentenceDetector.sentDetect(request.getSentence());
     responseObserver.onNext(OpenNLPService.StringList.newBuilder().addAllValues(Arrays.asList(tokens)).build());
     responseObserver.onCompleted();
   } catch (Exception e) {
     handleException(e, responseObserver);
   }
  }

  @Override
  public void sentPosDetect(OpenNLPService.SentDetectPosRequest request,
                          io.grpc.stub.StreamObserver<OpenNLPService.SpanList> responseObserver) {
    try {
      final SentenceDetector sentenceDetector = getSentenceDetector(request.getModelHash());
      final Span[] spans = sentenceDetector.sentPosDetect(request.getSentence());
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
  public void getAvailableModels(OpenNLPService.Empty request,
                                 io.grpc.stub.StreamObserver<OpenNLPService.AvailableModels> responseObserver) {
    returnAvailableModels(responseObserver);
  }

  private SentenceDetector getSentenceDetector(String hash) {
    return SENTENCE_DETECTOR_CACHE.computeIfAbsent((hash), modelHash -> {
      final ClassPathModel model = MODEL_CACHE.get(modelHash);

      if (model == null) {
        throw new ServiceException("Could not find the given model.");
      }

      try(BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(model.model()))) {
        return new ThreadSafeSentenceDetectorME(new SentenceModel(bis));
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
  public Map<String, SentenceDetector> getServiceCache() {
    return SENTENCE_DETECTOR_CACHE;
  }

  /**
   * Clears the in-memory caches for services and models.
   */
  @Override
  public void close() {
    clearCaches();
  }
}
