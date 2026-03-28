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
import opennlp.PosTaggerServiceGrpc;
import opennlp.service.exception.ServiceException;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.models.ClassPathModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.ThreadSafePOSTaggerME;

/**
 * The {@code PosTaggerService} class implements a gRPC service for Part-of-Speech (POS) tagging
 * using Apache OpenNLP models. It extends the auto-generated gRPC base class
 * {@link PosTaggerServiceGrpc.PosTaggerServiceImplBase}.
 *
 * <p>This service provides functionality for:
 * <ul>
 *   <li>Retrieving available POS models loaded from the classpath.</li>
 *   <li>Performing POS tagging on input sentences.</li>
 *   <li>Performing POS tagging with additional context.</li>
 * </ul>
 * </p>
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@code model.location}: Directory to search for models (default: "extlib").</li>
 *   <li>{@code model.recursive}: Whether to scan subdirectories (default: {@code true}).</li>
 *   <li>{@code model.pos.wildcard.pattern}: Wildcard pattern to identify POS models (default: "opennlp-models-pos-*.jar").</li>
 * </ul>
 *  </p>
 */
@ThreadSafe
public class PosTaggerService extends PosTaggerServiceGrpc.PosTaggerServiceImplBase
    implements CacheAware<POSTagger>, AutoCloseable, ExceptionAware, ModelAware<POSTagger> {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(PosTaggerService.class);

  private static final Map<String, ClassPathModel> MODEL_CACHE = new ConcurrentHashMap<>();
  private static final Map<String, POSTagger> TAGGER_CACHE = new ConcurrentHashMap<>();

  /**
   * Initializes a Part-of-Speech (POS) Tagger service with the given configuration properties.
   *
   * <p>The configuration properties are provided as a {@code Map<String, String>} and define various
   * parameters required for the service, such as model locations, recursive loading, and wildcard patterns.
   *
   * <p>Example configuration:
   * <pre>{@code
   * server.enable_reflection = false
   * model.location = target/test-classes/models
   * model.recursive = true
   * model.pos.wildcard.pattern = opennlp-models-pos-*.jar
   * }</pre>
   *
   * @param conf Configuration properties for the service (key-value format). Must not be {@code null}.
   *             If a property is missing, default values are used.
   * @throws RuntimeException if an {@link IOException} occurs during model cache initialization.
   */
  public PosTaggerService(Map<String, String> conf) {

    try {
      final Map<String, ClassPathModel> found = ModelFinderUtil.findModels(conf, "model.pos.wildcard.pattern", "opennlp-models-pos-*.jar");
      for (Map.Entry<String, ClassPathModel> entry : found.entrySet()) {
        MODEL_CACHE.putIfAbsent(entry.getKey(), entry.getValue());
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new RuntimeException(e);
    }

  }

  @Override
  public void tag(opennlp.OpenNLPService.TagRequest request,
                  io.grpc.stub.StreamObserver<OpenNLPService.StringList> responseObserver) {
    try {
      final POSTagger tagger = getTagger(request.getModelHash(), request.getFormat());
      final String[] tags = tagger.tag(request.getSentenceList().toArray(new String[0]));
      responseObserver.onNext(OpenNLPService.StringList.newBuilder().addAllValues(Arrays.asList(tags)).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      handleException(e, responseObserver);
    }

  }

  @Override
  public void tagWithContext(opennlp.OpenNLPService.TagWithContextRequest request,
                             io.grpc.stub.StreamObserver<OpenNLPService.StringList> responseObserver) {

    try {
      final POSTagger tagger = getTagger(request.getModelHash(), request.getFormat());
      final String[] tags = tagger.tag(
          request.getSentenceList().toArray(new String[0]),
          request.getAdditionalContextList().toArray(new String[0]));
      responseObserver.onNext(OpenNLPService.StringList.newBuilder().addAllValues(Arrays.asList(tags)).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      handleException(e, responseObserver);
    }
  }

  private POSTagger getTagger(String hash, OpenNLPService.POSTagFormat posTagFormat) {
    final POSTagFormat format = (posTagFormat == null) ? POSTagFormat.UD : POSTagFormat.valueOf(posTagFormat.name());

    return TAGGER_CACHE.computeIfAbsent((hash), modelHash -> {
      final ClassPathModel model = MODEL_CACHE.get(modelHash);

      if (model == null) {
        throw new ServiceException("Could not find the given model.");
      }

      try(BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(model.model()))) {
        return new ThreadSafePOSTaggerME(new POSModel(bis), format);
      } catch (IOException e) {
        throw new ServiceException(e.getLocalizedMessage(), e);
      }
    });
  }

  @Override
  public void getAvailableModels(opennlp.OpenNLPService.Empty request,
                                 io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {
    returnAvailableModels(responseObserver);
  }

  @Override
  public Map<String, ClassPathModel> getModelCache() {
    return MODEL_CACHE;
  }

  @Override
  public Map<String, POSTagger> getServiceCache() {
    return TAGGER_CACHE;
  }

  /**
   * Clears the in-memory caches for services and models.
   */
  @Override
  public void close() {
    clearCaches();
  }
}
