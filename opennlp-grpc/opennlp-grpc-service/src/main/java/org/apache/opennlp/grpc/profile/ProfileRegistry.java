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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.PipelineStep;

/**
 * Server-side {@link AnalysisProfile} catalog keyed by {@code profile_id}.
 */
public final class ProfileRegistry {

  public static final String DEFAULT_PROFILE_ID = "en-basic";
  public static final String DEFAULT_BUNDLE_ID = "en-basic";

  private final Map<String, AnalysisProfile> profiles = new LinkedHashMap<>();

  public ProfileRegistry() {
    register(defaultProfile());
  }

  public static ProfileRegistry createDefault() {
    return new ProfileRegistry();
  }

  public void register(AnalysisProfile profile) {
    if (profile.getProfileId().isBlank()) {
      throw new IllegalArgumentException("profile_id is required");
    }
    profiles.put(profile.getProfileId(), profile);
  }

  public Optional<AnalysisProfile> find(String profileId) {
    if (profileId == null || profileId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(profiles.get(profileId));
  }

  public AnalysisProfile getDefaultProfile() {
    return profiles.get(DEFAULT_PROFILE_ID);
  }

  public Map<String, AnalysisProfile> getProfiles() {
    return Map.copyOf(profiles);
  }

  private static AnalysisProfile defaultProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId(DEFAULT_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setModelBundle(ModelBundleRef.newBuilder().setBundleId(DEFAULT_BUNDLE_ID).build())
        .build();
  }
}
