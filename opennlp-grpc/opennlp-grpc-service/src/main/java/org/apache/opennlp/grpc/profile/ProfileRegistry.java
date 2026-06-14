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

  public static final String DOCCAT_PROFILE_ID = "en-doccat";
  public static final String DOCCAT_BUNDLE_ID = "en-doccat";

  public static final String SENTIMENT_PROFILE_ID = "en-sentiment";
  public static final String SENTIMENT_BUNDLE_ID = "en-sentiment";

  private final Map<String, AnalysisProfile> profiles = new LinkedHashMap<>();

  public ProfileRegistry() {
    this(false);
  }

  public ProfileRegistry(boolean nerAvailable) {
    this(nerAvailable, false);
  }

  public ProfileRegistry(boolean nerAvailable, boolean doccatAvailable) {
    this(nerAvailable, doccatAvailable, false);
  }

  /**
   * @param nerAvailable Whether name finder models are configured. The {@code en-ner}
   *     profile is registered only when {@code true}, so the advertised profile catalog
   *     matches the model catalog, which likewise hides the {@code en-ner} bundle when no
   *     name finder models are loaded. A request for {@code en-ner} without models would
   *     fail anyway; this keeps the two catalogs honest and consistent.
   * @param doccatAvailable Whether document categorizer models are configured. The
   *     {@code en-doccat} profile is registered only when {@code true}, for the same
   *     catalog-consistency reason as {@code nerAvailable}.
   * @param sentimentAvailable Whether sentiment models are configured. The {@code en-sentiment}
   *     profile is registered only when {@code true}, for the same catalog-consistency reason.
   */
  public ProfileRegistry(
      boolean nerAvailable, boolean doccatAvailable, boolean sentimentAvailable) {
    register(defaultProfile());
    if (nerAvailable) {
      register(nerProfile());
    }
    if (doccatAvailable) {
      register(doccatProfile());
    }
    if (sentimentAvailable) {
      register(sentimentProfile());
    }
  }

  public static ProfileRegistry createDefault() {
    return new ProfileRegistry();
  }

  public static ProfileRegistry createDefault(boolean nerAvailable) {
    return new ProfileRegistry(nerAvailable);
  }

  public static ProfileRegistry createDefault(boolean nerAvailable, boolean doccatAvailable) {
    return new ProfileRegistry(nerAvailable, doccatAvailable);
  }

  public static ProfileRegistry createDefault(
      boolean nerAvailable, boolean doccatAvailable, boolean sentimentAvailable) {
    return new ProfileRegistry(nerAvailable, doccatAvailable, sentimentAvailable);
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

  private static AnalysisProfile doccatProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId(DOCCAT_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
        .setModelBundle(ModelBundleRef.newBuilder().setBundleId(DOCCAT_BUNDLE_ID).build())
        .build();
  }

  private static AnalysisProfile sentimentProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId(SENTIMENT_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
        .setModelBundle(ModelBundleRef.newBuilder().setBundleId(SENTIMENT_BUNDLE_ID).build())
        .build();
  }
}
