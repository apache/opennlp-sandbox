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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.slf4j.LoggerFactory;

import opennlp.OpenNLPService;
import opennlp.PosTaggerServiceGrpc;
import opennlp.service.classpath.DirectoryModelFinder;
import opennlp.service.exception.ServiceException;
import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.models.ClassPathModel;
import opennlp.tools.models.ClassPathModelEntry;
import opennlp.tools.models.ClassPathModelLoader;
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
public class PosTaggerService extends PosTaggerServiceGrpc.PosTaggerServiceImplBase {

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
      initializeModelCache(conf);
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new RuntimeException(e);
    }

  }

  /**
   * Clears the in-memory caches for POS taggers and models.
   *
   * <p>This method ensures that all cached {@link POSTagger} and {@link ClassPathModel} objects
   * are removed from their respective caches, freeing up memory and resources. If a {@link POSTagger}
   * instance implements {@link AutoCloseable}, its {@code close()} method is invoked before the instance is discarded.</p>
   *
   * <p><b>Thread Safety:</b> The method synchronizes access to the underlying caches to ensure safe
   * removal of taggers in a multithreaded environment.</p>
   *
   * <p><b>Use Case:</b> This method is useful for scenarios where the cached models or taggers need to be
   * reloaded, such as after configuration changes or when the service needs to release memory during shutdown.</p>
   *
   * <p>No exceptions are thrown by this method; any exceptions occurring during the closing of taggers
   * are caught and ignored.</p>
   */
  public static void clearCaches() {
    synchronized (TAGGER_CACHE) {
      for (POSTagger t : TAGGER_CACHE.values()) {
        if (t instanceof AutoCloseable a) {
          try {
            a.close();
          } catch (Exception ignored) {

          }
        }
      }
      TAGGER_CACHE.clear();
      MODEL_CACHE.clear();
    }
  }

  private void initializeModelCache(Map<String, String> conf) throws IOException {
    final String modelDir = conf.getOrDefault("model.location", "extlib");
    final boolean recursive = Boolean.parseBoolean(conf.getOrDefault("model.recursive", "true"));
    final String wildcardPattern = conf.getOrDefault("model.pos.wildcard.pattern", "opennlp-models-pos-*.jar");

    final DirectoryModelFinder finder = new DirectoryModelFinder(wildcardPattern, Path.of(modelDir), recursive);
    final ClassPathModelLoader loader = new ClassPathModelLoader();

    final Set<ClassPathModelEntry> models = finder.findModels(false);
    for (ClassPathModelEntry entry : models) {
      final ClassPathModel model = loader.load(entry);
      if (model != null) {
        MODEL_CACHE.putIfAbsent(model.getModelSHA256(), model);
      }
    }

  }

  @Override
  public void getAvailableModels(opennlp.OpenNLPService.Empty request,
                                 io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {

    try {
      final OpenNLPService.AvailableModels.Builder response = OpenNLPService.AvailableModels.newBuilder();
      for (ClassPathModel model : MODEL_CACHE.values()) {
        final OpenNLPService.Model m = OpenNLPService.Model.newBuilder()
            .setHash(model.getModelSHA256())
            .setName(model.getModelName())
            .setLocale(model.getModelLanguage())
            .build();

        response.addModels(m);

      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      handleException(e, responseObserver);
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

  private void handleException(Exception e, StreamObserver<?> responseObserver) {
    final Status status = Status.newBuilder()
        .setCode(Code.INTERNAL.getNumber())
        .setMessage(e.getLocalizedMessage())
        .build();
    responseObserver.onError(StatusProto.toStatusRuntimeException(status));
  }

  private POSTagger getTagger(String hash, OpenNLPService.POSTagFormat posTagFormat) {
    final POSTagFormat format = (posTagFormat == null) ? POSTagFormat.UD : POSTagFormat.valueOf(posTagFormat.name());

    return TAGGER_CACHE.computeIfAbsent((hash + "-" + format), modelHash -> {
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
}
