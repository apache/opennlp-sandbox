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
package org.apache.opennlp.grpc.v1.server;

import java.util.EnumMap;

import io.grpc.Status;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.AnalysisException.FailureType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GrpcStatusMapperTest {

  @Test
  void mapsCanonicalGrpcStatuses() {
    assertEquals(Status.Code.INVALID_ARGUMENT,
        GrpcStatusMapper.toStatus(AnalysisException.invalidArgument("bad")).getCode());
    assertEquals(Status.Code.NOT_FOUND,
        GrpcStatusMapper.toStatus(AnalysisException.notFound("missing")).getCode());
    assertEquals(Status.Code.FAILED_PRECONDITION,
        GrpcStatusMapper.toStatus(AnalysisException.failedPrecondition("state")).getCode());
    assertEquals(Status.Code.UNIMPLEMENTED,
        GrpcStatusMapper.toStatus(AnalysisException.unimplemented("later")).getCode());
    assertEquals(Status.Code.UNAVAILABLE,
        GrpcStatusMapper.toStatus(AnalysisException.unavailable("remote down", null)).getCode());
    assertEquals(Status.Code.INTERNAL,
        GrpcStatusMapper.toStatus(AnalysisException.internal("boom", new IllegalStateException()))
            .getCode());
  }

  @Test
  void everyFailureTypeMapsToADistinctNonOkStatus() {
    // Driven by FailureType.values() so a newly added failure type without an asserted, distinct,
    // non-OK mapping fails here (and the exhaustive switch below fails to compile).
    final EnumMap<FailureType, Status.Code> seen = new EnumMap<>(FailureType.class);
    for (FailureType type : FailureType.values()) {
      final Status.Code code = GrpcStatusMapper.toStatus(exceptionFor(type)).getCode();
      assertNotEquals(Status.Code.OK, code, type + " maps to OK");
      assertFalse(seen.containsValue(code), "duplicate gRPC status for " + type);
      seen.put(type, code);
    }
    assertEquals(FailureType.values().length, seen.size());
  }

  private static AnalysisException exceptionFor(FailureType type) {
    return switch (type) {
      case INVALID_ARGUMENT -> AnalysisException.invalidArgument("x");
      case NOT_FOUND -> AnalysisException.notFound("x");
      case FAILED_PRECONDITION -> AnalysisException.failedPrecondition("x");
      case UNIMPLEMENTED -> AnalysisException.unimplemented("x");
      case UNAVAILABLE -> AnalysisException.unavailable("x", null);
      case RESOURCE_EXHAUSTED -> AnalysisException.resourceExhausted("x");
      case INTERNAL -> AnalysisException.internal("x", null);
    };
  }
}
