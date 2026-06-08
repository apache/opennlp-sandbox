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

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileResolverTest {

  private final ProfileResolver resolver = new ProfileResolver(ProfileRegistry.createDefault());

  @Test
  void usesDefaultProfileWhenRequestHasNoProfile() {
    final AnalysisProfile profile = resolver.resolve(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello.").build())
        .build());

    assertEquals(ProfileRegistry.DEFAULT_PROFILE_ID, profile.getProfileId());
    assertEquals(2, profile.getStepsCount());
  }

  @Test
  void resolvesServerProfileById() {
    final AnalysisProfile profile = resolver.resolve(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello.").build())
        .setProfileId("en-basic")
        .build());

    assertEquals("en-basic", profile.getProfileId());
    assertEquals(PipelineStep.PIPELINE_STEP_TOKENIZE, profile.getSteps(1));
  }

  @Test
  void inlineProfileOverridesServerProfileWhenBothSet() {
    final AnalysisProfile profile = resolver.resolve(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello.").build())
        .setProfileId("en-basic")
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("inline")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .build())
        .build());

    assertEquals("inline", profile.getProfileId());
    assertEquals(1, profile.getStepsCount());
    assertEquals(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT, profile.getSteps(0));
  }

  @Test
  void unknownProfileIdFails() {
    assertThrows(AnalysisException.class, () -> resolver.resolve(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello.").build())
        .setProfileId("missing")
        .build()));
  }
}
