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

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsRequest;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersRequest;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpModelTrainingServiceGrpc;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;

final class GrpcTrainingRpc implements TrainingRpc {

  private final OpenNlpModelTrainingServiceGrpc.OpenNlpModelTrainingServiceBlockingStub stub;
  private final long timeoutNanos;

  /**
   * Creates a blocking gRPC training adapter.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every call, including a full training run.
   * @throws IllegalArgumentException If an argument is {@code null} or the timeout is not positive.
   */
  GrpcTrainingRpc(Channel channel, Duration timeout) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.stub = OpenNlpModelTrainingServiceGrpc.newBlockingStub(channel);
    this.timeoutNanos = timeout.toNanos();
  }

  /** {@inheritDoc} */
  @Override
  public ListTeachersResponse listTeachers() {
    return deadlineStub().listTeachers(ListTeachersRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<TrainStaticModelUpdate> trainStaticModel(TrainStaticModelRequest request) {
    return deadlineStub().trainStaticModel(request);
  }

  /** {@inheritDoc} */
  @Override
  public ListStaticModelsResponse listStaticModels() {
    return deadlineStub().listStaticModels(ListStaticModelsRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public DeleteStaticModelResponse deleteStaticModel(DeleteStaticModelRequest request) {
    return deadlineStub().deleteStaticModel(request);
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpModelTrainingServiceGrpc.OpenNlpModelTrainingServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }
}
