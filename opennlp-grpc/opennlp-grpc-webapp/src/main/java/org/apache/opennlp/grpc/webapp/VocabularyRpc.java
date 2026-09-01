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

interface VocabularyRpc {

  /** @return Available dictionary formats and the effective vocabulary limits. */
  ListDictionaryFormatsResponse listDictionaryFormats();

  /** @return Imported dictionary artifacts available to seed a vocabulary. */
  ListDictionariesResponse listDictionaries();

  /** @return Learned vocabulary artifacts available to distill or watch. */
  ListVocabulariesResponse listVocabularies();

  /**
   * Imports one complete dictionary, composed into the ImportDictionary client stream.
   *
   * @param upload Import metadata and the complete encoded dictionary bytes.
   * @return Published dictionary descriptor.
   */
  DictionaryArtifactDescriptor importDictionary(ImportDictionaryUpload upload);

  /**
   * Builds one vocabulary, composed into the LearnVocabulary client stream.
   *
   * @param upload Learning controls and the complete corpus documents.
   * @return Published vocabulary descriptor.
   */
  VocabularyArtifactDescriptor learnVocabulary(LearnVocabularyUpload upload);

  /**
   * Downloads the exact TSV bytes of one vocabulary artifact.
   *
   * @param request Artifact identifier.
   * @return Complete artifact bytes.
   */
  byte[] downloadVocabulary(DownloadVocabularyRequest request);
}
