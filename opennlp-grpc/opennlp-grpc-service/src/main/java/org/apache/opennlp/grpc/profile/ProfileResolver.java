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
package org.apache.opennlp.grpc.profile;

import java.util.Objects;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalysisProfile;

/**
 * Resolves the effective {@link AnalysisProfile} for a request.
 *
 * <p>Precedence:
 * <ol>
 *   <li>{@code profile_id} only → server catalog profile</li>
 *   <li>inline {@code profile} only → inline profile (default catalog if empty steps)</li>
 *   <li>both set → inline profile overrides the server catalog entry with the same id</li>
 *   <li>neither → {@link ProfileRegistry#DEFAULT_PROFILE_ID}</li>
 * </ol>
 */
public final class ProfileResolver {

  private final ProfileRegistry registry;

  public ProfileResolver(ProfileRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public AnalysisProfile resolve(AnalyzeDocumentRequest request) {
    Objects.requireNonNull(request, "request");

    final boolean hasProfileId = request.hasProfileId() && !request.getProfileId().isBlank();
    final boolean hasInlineProfile = request.hasProfile();
    final AnalysisProfile inline = hasInlineProfile ? request.getProfile() : null;
    if (hasProfileId && hasInlineProfile) {
      final AnalysisProfile serverProfile = registry.find(request.getProfileId())
          .orElseThrow(() -> AnalysisException.notFound("Unknown profile_id: " + request.getProfileId()));
      return merge(serverProfile, inline);
    }

    if (hasProfileId) {
      return registry.find(request.getProfileId())
          .orElseThrow(() -> AnalysisException.notFound("Unknown profile_id: " + request.getProfileId()));
    }

    if (hasInlineProfile && inline.getStepsCount() > 0) {
      return inline;
    }

    if (hasInlineProfile) {
      return merge(registry.getDefaultProfile(), inline);
    }

    return registry.getDefaultProfile();
  }

  private static AnalysisProfile merge(AnalysisProfile base, AnalysisProfile override) {
    final AnalysisProfile.Builder builder = base.toBuilder();
    if (override.getStepsCount() > 0) {
      builder.clearSteps().addAllSteps(override.getStepsList());
    }
    if (override.hasModelBundle()) {
      builder.setModelBundle(override.getModelBundle());
    }
    if (!override.getProfileId().isBlank()) {
      builder.setProfileId(override.getProfileId());
    }
    if (override.getPosTagFormat() != org.apache.opennlp.grpc.v1.POSTagFormat.POS_TAG_FORMAT_UNSPECIFIED) {
      builder.setPosTagFormat(override.getPosTagFormat());
    }
    if (override.getNerEntityTypesCount() > 0) {
      builder.clearNerEntityTypes().addAllNerEntityTypes(override.getNerEntityTypesList());
    }
    return builder.build();
  }
}
