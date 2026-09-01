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
package org.apache.opennlp.grpc.webapp;

import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryUpload;
import org.apache.opennlp.grpc.v1.LearnVocabularyUpload;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsResponse;
import org.apache.opennlp.grpc.v1.ListDictionariesResponse;
import org.apache.opennlp.grpc.v1.ListVocabulariesResponse;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;

/** A write-disabled vocabulary adapter for tests that never exercise it. */
final class EmptyVocabularyRpc implements VocabularyRpc {

  @Override
  public ListDictionaryFormatsResponse listDictionaryFormats() {
    return ListDictionaryFormatsResponse.getDefaultInstance();
  }

  @Override
  public ListDictionariesResponse listDictionaries() {
    return ListDictionariesResponse.getDefaultInstance();
  }

  @Override
  public ListVocabulariesResponse listVocabularies() {
    return ListVocabulariesResponse.getDefaultInstance();
  }

  @Override
  public DictionaryArtifactDescriptor importDictionary(ImportDictionaryUpload upload) {
    return DictionaryArtifactDescriptor.getDefaultInstance();
  }

  @Override
  public VocabularyArtifactDescriptor learnVocabulary(LearnVocabularyUpload upload) {
    return VocabularyArtifactDescriptor.getDefaultInstance();
  }

  @Override
  public byte[] downloadVocabulary(DownloadVocabularyRequest request) {
    return new byte[0];
  }
}
