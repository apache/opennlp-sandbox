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

  /** Id of the always-present default profile (sentence detect + tokenize). */
  public static final String DEFAULT_PROFILE_ID = "en-basic";
  /** Bundle id referenced by the default profile. */
  public static final String DEFAULT_BUNDLE_ID = "en-basic";

  /** Id of the NER profile, registered only when name finder models are available. */
  public static final String NER_PROFILE_ID = "en-ner";
  /** Bundle id referenced by the NER profile. */
  public static final String NER_BUNDLE_ID = "en-ner";

  /** Id of the document categorization profile, registered only when doccat models exist. */
  public static final String DOCCAT_PROFILE_ID = "en-doccat";
  /** Bundle id referenced by the document categorization profile. */
  public static final String DOCCAT_BUNDLE_ID = "en-doccat";

  /** Id of the sentiment profile, registered only when sentiment models are available. */
  public static final String SENTIMENT_PROFILE_ID = "en-sentiment";
  /** Bundle id referenced by the sentiment profile. */
  public static final String SENTIMENT_BUNDLE_ID = "en-sentiment";

  /** Id of the parse profile, registered only when a parser model is available. */
  public static final String PARSE_PROFILE_ID = "en-parse";
  /** Bundle id referenced by the parse profile. */
  public static final String PARSE_BUNDLE_ID = "en-parse";

  private final Map<String, AnalysisProfile> profiles = new LinkedHashMap<>();

  /** Creates a registry holding only the default profile. */
  public ProfileRegistry() {
    this(false);
  }

  /**
   * Creates a registry with the default profile plus the NER profile when requested.
   *
   * @param nerAvailable Whether to register the {@code en-ner} profile.
   */
  public ProfileRegistry(boolean nerAvailable) {
    this(nerAvailable, false);
  }

  /**
   * Creates a registry with the default profile plus the NER and doccat profiles when requested.
   *
   * @param nerAvailable    Whether to register the {@code en-ner} profile.
   * @param doccatAvailable Whether to register the {@code en-doccat} profile.
   */
  public ProfileRegistry(boolean nerAvailable, boolean doccatAvailable) {
    this(nerAvailable, doccatAvailable, false);
  }

  /**
   * Creates a registry with the default profile plus the NER, doccat, and sentiment profiles
   * when requested.
   *
   * @param nerAvailable       Whether to register the {@code en-ner} profile.
   * @param doccatAvailable    Whether to register the {@code en-doccat} profile.
   * @param sentimentAvailable Whether to register the {@code en-sentiment} profile.
   */
  public ProfileRegistry(
      boolean nerAvailable, boolean doccatAvailable, boolean sentimentAvailable) {
    this(nerAvailable, doccatAvailable, sentimentAvailable, false);
  }

  /**
   * Creates a registry with the default profile plus each optional profile whose models are
   * available, so the advertised profile catalog stays consistent with the model catalog.
   *
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
   * @param parserAvailable Whether a parser model is configured. The {@code en-parse} profile is
   *     registered only when {@code true}, for the same catalog-consistency reason.
   */
  public ProfileRegistry(boolean nerAvailable, boolean doccatAvailable,
      boolean sentimentAvailable, boolean parserAvailable) {
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
    if (parserAvailable) {
      register(parseProfile());
    }
  }

  /**
   * Creates a registry holding only the default profile.
   *
   * @return A new registry with the default profile registered.
   */
  public static ProfileRegistry createDefault() {
    return new ProfileRegistry();
  }

  /**
   * Creates a default registry, additionally registering the NER profile when requested.
   *
   * @param nerAvailable Whether to register the {@code en-ner} profile.
   *
   * @return A new registry.
   */
  public static ProfileRegistry createDefault(boolean nerAvailable) {
    return new ProfileRegistry(nerAvailable);
  }

  /**
   * Creates a default registry, additionally registering the NER and doccat profiles when
   * requested.
   *
   * @param nerAvailable    Whether to register the {@code en-ner} profile.
   * @param doccatAvailable Whether to register the {@code en-doccat} profile.
   *
   * @return A new registry.
   */
  public static ProfileRegistry createDefault(boolean nerAvailable, boolean doccatAvailable) {
    return new ProfileRegistry(nerAvailable, doccatAvailable);
  }

  /**
   * Creates a default registry, additionally registering the NER, doccat, and sentiment
   * profiles when requested.
   *
   * @param nerAvailable       Whether to register the {@code en-ner} profile.
   * @param doccatAvailable    Whether to register the {@code en-doccat} profile.
   * @param sentimentAvailable Whether to register the {@code en-sentiment} profile.
   *
   * @return A new registry.
   */
  public static ProfileRegistry createDefault(
      boolean nerAvailable, boolean doccatAvailable, boolean sentimentAvailable) {
    return new ProfileRegistry(nerAvailable, doccatAvailable, sentimentAvailable);
  }

  /**
   * Creates a default registry, additionally registering each optional profile whose models
   * are available.
   *
   * @param nerAvailable       Whether to register the {@code en-ner} profile.
   * @param doccatAvailable    Whether to register the {@code en-doccat} profile.
   * @param sentimentAvailable Whether to register the {@code en-sentiment} profile.
   * @param parserAvailable    Whether to register the {@code en-parse} profile.
   *
   * @return A new registry.
   */
  public static ProfileRegistry createDefault(boolean nerAvailable, boolean doccatAvailable,
      boolean sentimentAvailable, boolean parserAvailable) {
    return new ProfileRegistry(nerAvailable, doccatAvailable, sentimentAvailable, parserAvailable);
  }

  /**
   * Registers (or replaces) a profile, keyed by its {@code profile_id}.
   *
   * @param profile The profile to register. Its {@code profile_id} must not be blank.
   *
   * @throws IllegalArgumentException If the profile's {@code profile_id} is blank.
   */
  public void register(AnalysisProfile profile) {
    if (profile.getProfileId().isBlank()) {
      throw new IllegalArgumentException("profile_id is required");
    }
    profiles.put(profile.getProfileId(), profile);
  }

  /**
   * Looks up a profile by id.
   *
   * @param profileId The profile id to look up. May be {@code null} or blank.
   *
   * @return The matching profile, or an empty {@link Optional} when {@code profileId} is
   *     {@code null}, blank, or unknown.
   */
  public Optional<AnalysisProfile> find(String profileId) {
    if (profileId == null || profileId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(profiles.get(profileId));
  }

  /**
   * Returns the default profile.
   *
   * @return The {@code en-basic} profile, or {@code null} if it was removed.
   */
  public AnalysisProfile getDefaultProfile() {
    return profiles.get(DEFAULT_PROFILE_ID);
  }

  /**
   * Returns all registered profiles for catalog reporting.
   *
   * @return An immutable copy of the profiles keyed by {@code profile_id}.
   */
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

  private static AnalysisProfile parseProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId(PARSE_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_PARSE)
        .setModelBundle(ModelBundleRef.newBuilder().setBundleId(PARSE_BUNDLE_ID).build())
        .build();
  }
}
