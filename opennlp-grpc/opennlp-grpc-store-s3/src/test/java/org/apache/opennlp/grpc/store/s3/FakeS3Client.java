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
package org.apache.opennlp.grpc.store.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * In-memory S3 double covering exactly the operations the store uses, with a small
 * listing page size so the continuation-token loop is exercised by every listing test.
 */
final class FakeS3Client implements S3Client {

  private static final int PAGE_SIZE = 2;

  private final TreeMap<String, byte[]> objects = new TreeMap<>();

  /** Returns the stored object keys, in key order. */
  List<String> keys() {
    return List.copyOf(objects.keySet());
  }

  @Override
  public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
    try (InputStream input = body.contentStreamProvider().newStream()) {
      objects.put(request.key(), input.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return PutObjectResponse.builder().build();
  }

  @Override
  public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
    final byte[] bytes = objects.get(request.key());
    if (bytes == null) {
      throw NoSuchKeyException.builder().message("no such key: " + request.key()).build();
    }
    return new ResponseInputStream<>(GetObjectResponse.builder().build(),
        AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }

  @Override
  public HeadObjectResponse headObject(HeadObjectRequest request) {
    if (!objects.containsKey(request.key())) {
      throw NoSuchKeyException.builder().message("no such key: " + request.key()).build();
    }
    return HeadObjectResponse.builder().build();
  }

  @Override
  public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
    final List<S3Object> matches = new ArrayList<>();
    for (Map.Entry<String, byte[]> entry : objects.entrySet()) {
      if (entry.getKey().startsWith(request.prefix())
          && (request.continuationToken() == null
              || entry.getKey().compareTo(request.continuationToken()) > 0)) {
        matches.add(S3Object.builder().key(entry.getKey()).build());
        if (matches.size() == PAGE_SIZE) {
          break;
        }
      }
    }
    final boolean truncated = matches.size() == PAGE_SIZE;
    return ListObjectsV2Response.builder()
        .contents(matches)
        .isTruncated(truncated)
        .nextContinuationToken(truncated ? matches.getLast().key() : null)
        .build();
  }

  @Override
  public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
    objects.remove(request.key());
    return DeleteObjectResponse.builder().build();
  }

  @Override
  public String serviceName() {
    return "s3";
  }

  @Override
  public void close() {
  }
}
