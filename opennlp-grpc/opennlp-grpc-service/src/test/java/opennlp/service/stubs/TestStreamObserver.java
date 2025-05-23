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
package opennlp.service.stubs;

import io.grpc.stub.CallStreamObserver;

import static org.junit.jupiter.api.Assertions.fail;

public class TestStreamObserver<T> extends CallStreamObserver<T> {
  @Override
  public boolean isReady() {
    return false;
  }

  @Override
  public void setOnReadyHandler(Runnable runnable) {

  }

  @Override
  public void disableAutoInboundFlowControl() {

  }

  @Override
  public void request(int i) {

  }

  @Override
  public void setMessageCompression(boolean b) {

  }

  @Override
  public void onNext(T t) {

  }

  @Override
  public void onError(Throwable throwable) {
    fail("Error: " + throwable.getMessage(), throwable);
  }

  @Override
  public void onCompleted() {

  }
}
