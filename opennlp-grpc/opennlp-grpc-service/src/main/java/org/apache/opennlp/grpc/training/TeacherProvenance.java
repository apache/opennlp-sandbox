/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.util.List;

/**
 * What is known about where a teacher model came from, recorded on every model distilled
 * from it. A catalog teacher carries its repository, pinned revision, license and
 * languages; an operator-configured teacher carries only its path.
 *
 * @param reference A repository name or local path identifying the teacher.
 * @param revision The pinned revision, or empty when unknown.
 * @param licenseName The license as an SPDX identifier, or empty when unknown.
 * @param licenseUri Where the license text lives, or empty when unknown.
 * @param languages Languages the teacher declares; empty when unknown.
 */
public record TeacherProvenance(
    String reference, String revision, String licenseName, String licenseUri,
    List<String> languages) {

  /**
   * Creates the provenance of a teacher nothing is known about beyond its reference.
   *
   * @param reference A repository name or local path identifying the teacher.
   * @return Provenance with empty revision, license and languages.
   */
  public static TeacherProvenance unknown(String reference) {
    return new TeacherProvenance(reference, "", "", "", List.of());
  }

  /** Validates and copies the record's fields. */
  public TeacherProvenance {
    if (reference == null) {
      throw new IllegalArgumentException("reference must not be null");
    }
    revision = revision == null ? "" : revision;
    licenseName = licenseName == null ? "" : licenseName;
    licenseUri = licenseUri == null ? "" : licenseUri;
    languages = languages == null ? List.of() : List.copyOf(languages);
  }
}
