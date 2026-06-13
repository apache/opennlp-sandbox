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

  public static final String NER_PROFILE_ID = "en-ner";
  public static final String NER_BUNDLE_ID = "en-ner";

  private final Map<String, AnalysisProfile> profiles = new LinkedHashMap<>();

  public ProfileRegistry() {
    this(false);
  }

  /**
   * @param nerAvailable Whether name finder models are configured. The {@code en-ner}
   *     profile is registered only when {@code true}, so the advertised profile catalog
   *     matches the model catalog, which likewise hides the {@code en-ner} bundle when no
   *     name finder models are loaded. A request for {@code en-ner} without models would
   *     fail anyway; this keeps the two catalogs honest and consistent.
   */
  public ProfileRegistry(boolean nerAvailable) {
    register(defaultProfile());
    if (nerAvailable) {
      register(nerProfile());
    }
  }

  public static ProfileRegistry createDefault() {
    return new ProfileRegistry();
  }

  public static ProfileRegistry createDefault(boolean nerAvailable) {
    return new ProfileRegistry(nerAvailable);
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

  private static AnalysisProfile nerProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId(NER_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_NER)
        .setModelBundle(ModelBundleRef.newBuilder().setBundleId(NER_BUNDLE_ID).build())
        .build();
  }
}
