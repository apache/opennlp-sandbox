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
package org.apache.opennlp.grpc.webapp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.FormatDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.DeleteCollectionRequest;
import org.apache.opennlp.grpc.v1.GetCollectionRequest;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.WatchCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryUpload;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyUpload;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;

final class GrpcJsonApi {

  static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
  static final String TSV_CONTENT_TYPE = "text/tab-separated-values; charset=utf-8";

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  private static final String INVALID_UTF8_MESSAGE = "Request body must contain valid UTF-8";
  private static final String MALFORMED_PROTOBUF_JSON_PREFIX =
      "Malformed protobuf JSON request: ";

  private final AnalysisRpc analysisRpc;
  private final SearchRpc searchRpc;
  private final VocabularyRpc vocabularyRpc;
  private final TrainingRpc trainingRpc;
  private final JsonFormat.Parser parser;
  private final JsonFormat.Printer printer;

  /**
   * Creates the JSON facade.
   *
   * @param analysisRpc The analysis service adapter.
   * @param searchRpc The search service adapter.
   * @param vocabularyRpc The vocabulary service adapter.
   * @param trainingRpc The model training service adapter.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  GrpcJsonApi(AnalysisRpc analysisRpc, SearchRpc searchRpc,
      VocabularyRpc vocabularyRpc, TrainingRpc trainingRpc) {
    if (analysisRpc == null) {
      throw new IllegalArgumentException("analysisRpc must not be null");
    }
    if (searchRpc == null) {
      throw new IllegalArgumentException("searchRpc must not be null");
    }
    if (vocabularyRpc == null) {
      throw new IllegalArgumentException("vocabularyRpc must not be null");
    }
    if (trainingRpc == null) {
      throw new IllegalArgumentException("trainingRpc must not be null");
    }
    this.analysisRpc = analysisRpc;
    this.searchRpc = searchRpc;
    this.vocabularyRpc = vocabularyRpc;
    this.trainingRpc = trainingRpc;
    this.parser = JsonFormat.parser();
    this.printer = JsonFormat.printer().omittingInsignificantWhitespace();
  }

  /**
   * Handles one API request.
   *
   * @param method The HTTP method.
   * @param path The request path.
   * @param body The request body.
   * @return The HTTP response.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  WebHttpResponse handle(String method, String path, byte[] body) {
    if (method == null) {
      throw new IllegalArgumentException("method must not be null");
    }
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    if (body == null) {
      throw new IllegalArgumentException("body must not be null");
    }
    try {
      return switch (path) {
        case "/api/v1/service-info" -> method.equals("GET")
            ? protobufJson(analysisRpc.getServiceInfo()) : methodNotAllowed();
        case "/api/v1/model-bundles" -> method.equals("GET")
            ? protobufJson(analysisRpc.listModelBundles()) : methodNotAllowed();
        case "/api/v1/analyze" -> method.equals("POST")
            ? analyze(body) : methodNotAllowed();
        case "/api/v1/analyze-protobuf" -> method.equals("POST")
            ? analyzeProtobuf(body) : methodNotAllowed();
        case "/api/v1/output-formats" -> method.equals("GET")
            ? protobufJson(analysisRpc.listOutputFormats()) : methodNotAllowed();
        case "/api/v1/format-document" -> method.equals("POST")
            ? formatDocument(body) : methodNotAllowed();
        case "/api/v1/response/encode" -> method.equals("POST")
            ? encodeResponse(body) : methodNotAllowed();
        case "/api/v1/response/decode" -> method.equals("POST")
            ? decodeResponse(body) : methodNotAllowed();
        case "/api/v1/search-indexes" -> method.equals("GET")
            ? protobufJson(searchRpc.listSearchIndexes()) : methodNotAllowed();
        case "/api/v1/search-providers" -> method.equals("GET")
            ? protobufJson(searchRpc.listSearchProviders()) : methodNotAllowed();
        case "/api/v1/search" -> method.equals("POST")
            ? search(body) : methodNotAllowed();
        case "/api/v1/index-documents" -> method.equals("POST")
            ? indexDocuments(body) : methodNotAllowed();
        case "/api/v1/delete-search-index" -> method.equals("POST")
            ? deleteSearchIndex(body) : methodNotAllowed();
        case "/api/v1/persist-index" -> method.equals("POST")
            ? persistIndex(body) : methodNotAllowed();
        case "/api/v1/seal-index" -> method.equals("POST")
            ? sealIndex(body) : methodNotAllowed();
        case "/api/v1/reindex-index" -> method.equals("POST")
            ? reindexIndex(body) : methodNotAllowed();
        case "/api/v1/set-index-alias" -> method.equals("POST")
            ? setIndexAlias(body) : methodNotAllowed();
        case "/api/v1/delete-index-alias" -> method.equals("POST")
            ? deleteIndexAlias(body) : methodNotAllowed();
        case "/api/v1/index-aliases" -> method.equals("GET")
            ? protobufJson(searchRpc.listAliases()) : methodNotAllowed();
        case "/api/v1/set-collection" -> method.equals("POST")
            ? setCollection(body) : methodNotAllowed();
        case "/api/v1/get-collection" -> method.equals("POST")
            ? getCollection(body) : methodNotAllowed();
        case "/api/v1/collections" -> method.equals("GET")
            ? protobufJson(searchRpc.listCollections()) : methodNotAllowed();
        case "/api/v1/delete-collection" -> method.equals("POST")
            ? deleteCollection(body) : methodNotAllowed();
        case "/api/v1/dictionary-formats" -> method.equals("GET")
            ? protobufJson(vocabularyRpc.listDictionaryFormats()) : methodNotAllowed();
        case "/api/v1/dictionaries" -> method.equals("GET")
            ? protobufJson(vocabularyRpc.listDictionaries()) : methodNotAllowed();
        case "/api/v1/vocabularies" -> method.equals("GET")
            ? protobufJson(vocabularyRpc.listVocabularies()) : methodNotAllowed();
        case "/api/v1/import-dictionary" -> method.equals("POST")
            ? importDictionary(body) : methodNotAllowed();
        case "/api/v1/learn-vocabulary" -> method.equals("POST")
            ? learnVocabulary(body) : methodNotAllowed();
        case "/api/v1/download-vocabulary" -> method.equals("POST")
            ? downloadVocabulary(body) : methodNotAllowed();
        case "/api/v1/teachers" -> method.equals("GET")
            ? protobufJson(trainingRpc.listTeachers()) : methodNotAllowed();
        case "/api/v1/model-catalog" -> method.equals("GET")
            ? protobufJson(trainingRpc.listModelCatalog()) : methodNotAllowed();
        case "/api/v1/installed-models" -> method.equals("GET")
            ? protobufJson(trainingRpc.listInstalledModels()) : methodNotAllowed();
        case "/api/v1/static-models" -> method.equals("GET")
            ? protobufJson(trainingRpc.listStaticModels()) : methodNotAllowed();
        case "/api/v1/delete-static-model" -> method.equals("POST")
            ? deleteStaticModel(body) : methodNotAllowed();
        default -> error(404, Status.Code.NOT_FOUND, "Unknown API endpoint");
      };
    } catch (StatusRuntimeException exception) {
      Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
          status.getCode(), message);
    }
  }

  /** Renders one analyzed document into a deployed output format. */
  private WebHttpResponse formatDocument(byte[] body) {
    final FormatDocumentRequest.Builder request = FormatDocumentRequest.newBuilder();
    final String json;
    try {
      json = decodeUtf8(body);
    } catch (CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT, INVALID_UTF8_MESSAGE);
    }
    try {
      parser.merge(json, request);
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          MALFORMED_PROTOBUF_JSON_PREFIX + exception.getMessage());
    }
    return protobufJson(analysisRpc.formatDocument(request.build()));
  }

  private WebHttpResponse analyze(byte[] body) {
    AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder();
    final String json;
    try {
      json = decodeUtf8(body);
    } catch (CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT, INVALID_UTF8_MESSAGE);
    }
    try {
      parser.merge(json, request);
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          MALFORMED_PROTOBUF_JSON_PREFIX + exception.getMessage());
    }
    return protobufJson(analysisRpc.analyze(request.build()));
  }

  /**
   * Analyzes one document and returns the serialized response instead of protobuf JSON.
   * This is the {@code .pb} export for replies too large to print as JSON and re-upload
   * for transcoding: the gateway never renders the response as text.
   *
   * @param body The protobuf JSON request body.
   * @return The serialized {@link AnalyzeDocumentResponse}, or a parse failure.
   */
  private WebHttpResponse analyzeProtobuf(byte[] body) {
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : new WebHttpResponse(200, PROTOBUF_CONTENT_TYPE,
            analysisRpc.analyze(request.build()).toByteArray());
  }

  /**
   * Re-encodes a saved analysis response: protobuf JSON in, serialized protobuf out, so
   * the browser can save a {@code .pb} file without a protobuf runtime of its own. No
   * RPC is made; the transcode is local to the gateway.
   *
   * @param body The protobuf JSON of one analysis response.
   * @return The serialized response bytes, or a parse failure.
   */
  private WebHttpResponse encodeResponse(byte[] body) {
    final AnalyzeDocumentResponse.Builder response = AnalyzeDocumentResponse.newBuilder();
    final WebHttpResponse parseFailure = merge(body, response);
    return parseFailure != null ? parseFailure
        : new WebHttpResponse(200, PROTOBUF_CONTENT_TYPE, response.build().toByteArray());
  }

  /**
   * Decodes a saved {@code .pb} analysis response back into protobuf JSON, so the
   * browser can load a file it saved earlier. No RPC is made.
   *
   * @param body The serialized response bytes.
   * @return The protobuf JSON of the response, or a parse failure.
   */
  private WebHttpResponse decodeResponse(byte[] body) {
    try {
      return protobufJson(AnalyzeDocumentResponse.parseFrom(body));
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          "Malformed protobuf response bytes: " + exception.getMessage());
    }
  }

  /**
   * Parses and forwards a bounded search request.
   *
   * @param body The protobuf JSON request body.
   * @return The encoded search response.
   */
  private WebHttpResponse search(byte[] body) {
    SearchIndexRequest.Builder request = SearchIndexRequest.newBuilder();
    final String json;
    try {
      json = decodeUtf8(body);
    } catch (CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT, INVALID_UTF8_MESSAGE);
    }
    try {
      parser.merge(json, request);
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          MALFORMED_PROTOBUF_JSON_PREFIX + exception.getMessage());
    }
    return protobufJson(searchRpc.search(request.build()));
  }

  /**
   * Parses and forwards one dynamic indexing request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded index response or parse failure.
   */
  private WebHttpResponse indexDocuments(byte[] body) {
    final IndexDocumentsRequest.Builder request = IndexDocumentsRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.index(request.build()));
  }

  /**
   * Parses and forwards one dynamic index deletion request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded deletion response or parse failure.
   */
  private WebHttpResponse deleteSearchIndex(byte[] body) {
    final DeleteSearchIndexRequest.Builder request = DeleteSearchIndexRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.delete(request.build()));
  }

  /**
   * Parses and forwards one checkpoint request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded persisted descriptor or parse failure.
   */
  private WebHttpResponse persistIndex(byte[] body) {
    final PersistIndexRequest.Builder request = PersistIndexRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.persist(request.build()));
  }

  /**
   * Parses and forwards one seal request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded sealed descriptor or parse failure.
   */
  private WebHttpResponse sealIndex(byte[] body) {
    final SealIndexRequest.Builder request = SealIndexRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.seal(request.build()));
  }

  /**
   * Parses and forwards one blue/green reindex request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded reindex result or parse failure.
   */
  private WebHttpResponse reindexIndex(byte[] body) {
    final ReindexIndexRequest.Builder request = ReindexIndexRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.reindex(request.build()));
  }

  /**
   * Parses and forwards one alias upsert.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded stored alias or parse failure.
   */
  private WebHttpResponse setIndexAlias(byte[] body) {
    final SetIndexAliasRequest.Builder request = SetIndexAliasRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : protobufJson(searchRpc.setAlias(request.build()));
  }

  /**
   * Parses and forwards one alias deletion.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded deletion result or parse failure.
   */
  private WebHttpResponse deleteIndexAlias(byte[] body) {
    final DeleteIndexAliasRequest.Builder request = DeleteIndexAliasRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : protobufJson(searchRpc.deleteAlias(request.build()));
  }

  /**
   * Parses and composes one complete dictionary import into the client stream.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded published dictionary descriptor or parse failure.
   */
  private WebHttpResponse importDictionary(byte[] body) {
    final ImportDictionaryUpload.Builder upload = ImportDictionaryUpload.newBuilder();
    final WebHttpResponse parseFailure = merge(body, upload);
    return parseFailure != null ? parseFailure
        : protobufJson(vocabularyRpc.importDictionary(upload.build()));
  }

  /**
   * Parses and composes one complete vocabulary build into the client stream.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded published vocabulary descriptor or parse failure.
   */
  private WebHttpResponse learnVocabulary(byte[] body) {
    final LearnVocabularyUpload.Builder upload = LearnVocabularyUpload.newBuilder();
    final WebHttpResponse parseFailure = merge(body, upload);
    return parseFailure != null ? parseFailure
        : protobufJson(vocabularyRpc.learnVocabulary(upload.build()));
  }

  /**
   * Downloads one vocabulary artifact as its exact TSV bytes.
   *
   * @param body Protobuf JSON request body.
   * @return The TSV artifact or parse failure.
   */
  private WebHttpResponse downloadVocabulary(byte[] body) {
    final DownloadVocabularyRequest.Builder request = DownloadVocabularyRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure : new WebHttpResponse(
        200, TSV_CONTENT_TYPE, vocabularyRpc.downloadVocabulary(request.build()));
  }

  /**
   * Parses and forwards one static model deletion request.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded deletion response or parse failure.
   */
  private WebHttpResponse deleteStaticModel(byte[] body) {
    final DeleteStaticModelRequest.Builder request = DeleteStaticModelRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : protobufJson(trainingRpc.deleteStaticModel(request.build()));
  }

  /**
   * Parses and forwards a collection upsert.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded collection descriptor or parse failure.
   */
  private WebHttpResponse setCollection(byte[] body) {
    final SetCollectionRequest.Builder request = SetCollectionRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : protobufJson(searchRpc.setCollection(request.build()));
  }

  /**
   * Parses and forwards a collection read.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded collection descriptor or parse failure.
   */
  private WebHttpResponse getCollection(byte[] body) {
    final GetCollectionRequest.Builder request = GetCollectionRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : protobufJson(searchRpc.getCollection(request.build()));
  }

  /**
   * Parses and forwards a collection deletion.
   *
   * @param body Protobuf JSON request body.
   * @return Encoded deletion response or parse failure.
   */
  private WebHttpResponse deleteCollection(byte[] body) {
    final DeleteCollectionRequest.Builder request = DeleteCollectionRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    return parseFailure != null ? parseFailure
        : protobufJson(searchRpc.deleteCollection(request.build()));
  }

  /**
   * Watches one collection, streaming each event to the sink as an NDJSON line. This
   * endpoint is dispatched by the HTTP handler rather than {@link #handle}, because the
   * subscription outlives a buffered response. The adapter's deadline bounds the watch
   * lifetime: a DEADLINE_EXCEEDED or CANCELLED end after streaming closes quietly, and
   * the client reconnects for a fresh snapshot.
   *
   * @param body Protobuf JSON of one WatchCollectionRequest.
   * @param sink Receives one protobuf JSON line per event; the first call commits the
   *     streamed 200 response.
   * @return A buffered failure to send instead, or {@code null} once streaming started
   *     and finished (an unexpected late failure is appended as a final error line).
   * @throws IOException If writing to the sink fails.
   */
  WebHttpResponse watchCollection(byte[] body, JsonLineSink sink) throws IOException {
    final WatchCollectionRequest.Builder request = WatchCollectionRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    if (parseFailure != null) {
      return parseFailure;
    }
    boolean streamed = false;
    try {
      final java.util.Iterator<CollectionEvent> events =
          searchRpc.watchCollection(request.build());
      while (events.hasNext()) {
        sink.update(printer.print(events.next()));
        streamed = true;
      }
      return null;
    } catch (StatusRuntimeException exception) {
      final Status status = exception.getStatus();
      if (streamed && (status.getCode() == Status.Code.DEADLINE_EXCEEDED
          || status.getCode() == Status.Code.CANCELLED)) {
        return null;
      }
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      if (!streamed) {
        return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
            status.getCode(), message);
      }
      sink.update(errorJson(status.getCode(), message));
      return null;
    } catch (InvalidProtocolBufferException exception) {
      final String message = "Could not encode the service response";
      if (!streamed) {
        return error(500, Status.Code.INTERNAL, message);
      }
      sink.update(errorJson(Status.Code.INTERNAL, message));
      return null;
    }
  }

  /**
   * Runs one distillation, streaming each update to the sink as an NDJSON line. This
   * endpoint is dispatched by the HTTP handler rather than {@link #handle}, because a
   * training run outlives a buffered response.
   *
   * @param body Protobuf JSON of one TrainStaticModelRequest.
   * @param sink Receives one protobuf JSON line per update; the first call commits the
   *     streamed 200 response.
   * @return A buffered failure to send instead, or {@code null} once streaming started
   *     and finished (a late failure is appended as a final error line).
   * @throws IOException If writing to the sink fails.
   */
  WebHttpResponse trainStaticModel(byte[] body, JsonLineSink sink) throws IOException {
    final TrainStaticModelRequest.Builder request = TrainStaticModelRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    if (parseFailure != null) {
      return parseFailure;
    }
    boolean streamed = false;
    try {
      final java.util.Iterator<TrainStaticModelUpdate> updates =
          trainingRpc.trainStaticModel(request.build());
      while (updates.hasNext()) {
        sink.update(printer.print(updates.next()));
        streamed = true;
      }
      return null;
    } catch (StatusRuntimeException exception) {
      final Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      if (!streamed) {
        return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
            status.getCode(), message);
      }
      sink.update(errorJson(status.getCode(), message));
      return null;
    } catch (InvalidProtocolBufferException exception) {
      final String message = "Could not encode the service response";
      if (!streamed) {
        return error(500, Status.Code.INTERNAL, message);
      }
      sink.update(errorJson(Status.Code.INTERNAL, message));
      return null;
    }
  }

  /**
   * Analyzes a batch of documents over one AnalyzeStream call, streaming each
   * completion-ordered response to the browser as an NDJSON line.
   *
   * @param body A JSON array of AnalyzeStreamRequest frames: one configuration frame
   *     first, then one frame per document, exactly as on the gRPC stream.
   * @param sink Receives one protobuf JSON line per response.
   * @return A buffered failure, or {@code null} after streaming.
   * @throws IOException If writing to the sink fails.
   */
  WebHttpResponse analyzeStream(byte[] body, JsonLineSink sink) throws IOException {
    final java.util.List<AnalyzeStreamRequest> frames;
    try {
      frames = parseStreamFrames(body);
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          MALFORMED_PROTOBUF_JSON_PREFIX + exception.getMessage());
    } catch (java.nio.charset.CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT, INVALID_UTF8_MESSAGE);
    }
    if (frames.isEmpty()) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          "analyze-stream requires a configuration frame and at least one document frame");
    }
    boolean streamed = false;
    try {
      final java.util.Iterator<AnalyzeStreamResponse> responses =
          analysisRpc.analyzeStream(frames);
      while (responses.hasNext()) {
        sink.update(printer.print(responses.next()));
        streamed = true;
      }
      return null;
    } catch (StatusRuntimeException exception) {
      final Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      if (!streamed) {
        return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
            status.getCode(), message);
      }
      sink.update(errorJson(status.getCode(), message));
      return null;
    } catch (InvalidProtocolBufferException exception) {
      final String message = "Could not encode the service response";
      if (!streamed) {
        return error(500, Status.Code.INTERNAL, message);
      }
      sink.update(errorJson(Status.Code.INTERNAL, message));
      return null;
    }
  }

  /**
   * Analyzes one document and writes each ordered progressive event as an NDJSON line.
   *
   * @param body Protobuf JSON for one AnalyzeDocumentRequest.
   * @param sink Receives one protobuf JSON line per event.
   * @return A buffered failure, or {@code null} after streaming.
   * @throws IOException If writing to the sink fails.
   */
  WebHttpResponse analyzeProgressively(byte[] body, JsonLineSink sink) throws IOException {
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    if (parseFailure != null) {
      return parseFailure;
    }
    boolean streamed = false;
    try {
      try (AnalysisRpc.ProgressiveEvents events =
          analysisRpc.analyzeProgressively(request.build())) {
        while (events.hasNext()) {
          sink.update(printer.print(events.next()));
          streamed = true;
        }
      }
      return null;
    } catch (StatusRuntimeException exception) {
      final Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      if (!streamed) {
        return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
            status.getCode(), message);
      }
      sink.update(errorJson(status.getCode(), message));
      return null;
    } catch (InvalidProtocolBufferException exception) {
      final String message = "Could not encode the service response";
      if (!streamed) {
        return error(500, Status.Code.INTERNAL, message);
      }
      sink.update(errorJson(Status.Code.INTERNAL, message));
      return null;
    }
  }

  /** Parses the request body's JSON array into AnalyzeStream frames. */
  private java.util.List<AnalyzeStreamRequest> parseStreamFrames(byte[] body)
      throws InvalidProtocolBufferException, java.nio.charset.CharacterCodingException {
    final com.google.protobuf.ListValue.Builder list =
        com.google.protobuf.ListValue.newBuilder();
    parser.merge(decodeUtf8(body), list);
    final java.util.List<AnalyzeStreamRequest> frames =
        new java.util.ArrayList<>(list.getValuesCount());
    for (com.google.protobuf.Value value : list.getValuesList()) {
      final AnalyzeStreamRequest.Builder frame = AnalyzeStreamRequest.newBuilder();
      parser.merge(printer.print(value), frame);
      frames.add(frame.build());
    }
    return frames;
  }

  /**
   * Installs one pinned catalog model and streams file-level progress to the browser.
   *
   * @param body Protobuf JSON of one InstallModelRequest.
   * @param sink Receives one protobuf JSON line per update.
   * @return A buffered failure, or {@code null} after streaming.
   * @throws IOException If writing to the sink fails.
   */
  WebHttpResponse installModel(byte[] body, JsonLineSink sink) throws IOException {
    final InstallModelRequest.Builder request = InstallModelRequest.newBuilder();
    final WebHttpResponse parseFailure = merge(body, request);
    if (parseFailure != null) {
      return parseFailure;
    }
    boolean streamed = false;
    try {
      final java.util.Iterator<InstallModelUpdate> updates =
          trainingRpc.installModel(request.build());
      while (updates.hasNext()) {
        sink.update(printer.print(updates.next()));
        streamed = true;
      }
      return null;
    } catch (StatusRuntimeException exception) {
      final Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      if (!streamed) {
        return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
            status.getCode(), message);
      }
      sink.update(errorJson(status.getCode(), message));
      return null;
    } catch (InvalidProtocolBufferException exception) {
      final String message = "Could not encode the service response";
      if (!streamed) {
        return error(500, Status.Code.INTERNAL, message);
      }
      sink.update(errorJson(Status.Code.INTERNAL, message));
      return null;
    }
  }

  /** Receives one streamed protobuf JSON line of an NDJSON response. */
  interface JsonLineSink {

    /**
     * Accepts one NDJSON line.
     *
     * @param json One protobuf JSON document, without a trailing newline.
     * @throws IOException If the line cannot be written.
     */
    void update(String json) throws IOException;
  }

  /**
   * Decodes and merges protobuf JSON into a request builder.
   *
   * @param body Encoded request body.
   * @param request Destination builder.
   * @return A parse failure, or {@code null} after a successful merge.
   */
  private WebHttpResponse merge(byte[] body, Message.Builder request) {
    final String json;
    try {
      json = decodeUtf8(body);
    } catch (CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT, INVALID_UTF8_MESSAGE);
    }
    try {
      parser.merge(json, request);
      return null;
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          MALFORMED_PROTOBUF_JSON_PREFIX + exception.getMessage());
    }
  }

  /**
   * Decodes a request body without replacing malformed input.
   *
   * @param body The encoded request body.
   * @return The decoded request body.
   * @throws CharacterCodingException If the body is not valid UTF-8.
   */
  private String decodeUtf8(byte[] body) throws CharacterCodingException {
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(body))
        .toString();
  }

  /**
   * Encodes one protobuf message as JSON.
   *
   * @param message The protobuf message.
   * @return The encoded HTTP response.
   */
  private WebHttpResponse protobufJson(Message message) {
    try {
      return WebHttpResponse.utf8(200, JSON_CONTENT_TYPE, printer.print(message));
    } catch (InvalidProtocolBufferException exception) {
      return error(500, Status.Code.INTERNAL, "Could not encode the service response");
    }
  }

  /** @return The common method-not-allowed response. */
  private static WebHttpResponse methodNotAllowed() {
    return error(405, Status.Code.UNIMPLEMENTED, "HTTP method is not allowed for this endpoint");
  }

  /**
   * Creates a JSON error response.
   *
   * @param httpStatus The HTTP status.
   * @param code The gRPC status code.
   * @param message The caller-facing error message.
   * @return The encoded response.
   */
  static WebHttpResponse error(int httpStatus, Status.Code code, String message) {
    return WebHttpResponse.utf8(httpStatus, JSON_CONTENT_TYPE, errorJson(code, message));
  }

  /**
   * Encodes one error as the common JSON error document.
   *
   * @param code The gRPC status code.
   * @param message The caller-facing error message.
   * @return The encoded JSON document.
   */
  private static String errorJson(Status.Code code, String message) {
    return "{\"code\":\"" + escapeJson(code.name()) + "\",\"message\":\""
        + escapeJson(message) + "\"}";
  }

  /**
   * Escapes one value for a JSON string literal.
   *
   * @param value The unescaped value.
   * @return The escaped value.
   */
  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append("\\u")
                .append(HEX_DIGITS[(character >> 12) & 0xF])
                .append(HEX_DIGITS[(character >> 8) & 0xF])
                .append(HEX_DIGITS[(character >> 4) & 0xF])
                .append(HEX_DIGITS[character & 0xF]);
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
