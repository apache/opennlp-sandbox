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
package org.apache.opennlp.grpc.server;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link OpenNlpGrpcServer#stop()} lets interrupted workers leave native inference
 * before the model cache frees native sessions. The analysis executor and cache are injected
 * into an unstarted server, so no socket is bound. The blocking task ignores interrupts on
 * purpose: a worker inside an ONNX {@code session.run()} cannot be interrupted either, and
 * {@code shutdownNow()} alone therefore never guarantees the worker has left the native call
 * before {@code close()} frees it.
 */
class OpenNlpGrpcServerStopTest {

  @Test
  void stopWaitsForUninterruptibleInferenceBeforeClosingModels() throws Exception {
    final ExecutorService analysis = Executors.newSingleThreadExecutor();
    final CountDownLatch inferenceStarted = new CountDownLatch(1);
    final CountDownLatch releaseInference = new CountDownLatch(1);
    final AtomicBoolean inferenceDone = new AtomicBoolean();
    analysis.execute(() -> {
      inferenceStarted.countDown();
      boolean interrupted = false;
      while (true) {
        try {
          releaseInference.await();
          break;
        } catch (InterruptedException e) {
          interrupted = true; // native inference cannot be interrupted either; keep running
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
      inferenceDone.set(true);
    });
    final OpenNlpGrpcServer server = unstartedServer(analysis, 30);
    try {
      assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS));
      final AtomicBoolean stopReturned = new AtomicBoolean();
      final Thread stopper = new Thread(() -> {
        server.stop();
        stopReturned.set(true);
      }, "stop-test");
      stopper.start();

      assertFalse(awaitFlag(stopReturned, 3_000),
          "stop() closed the model cache and returned while uninterruptible inference "
              + "was still running");

      releaseInference.countDown();
      stopper.join(TimeUnit.SECONDS.toMillis(10));
      assertFalse(stopper.isAlive(), "stop() did not return after inference finished");
      assertTrue(inferenceDone.get());
      assertTrue(stopReturned.get());
    } finally {
      releaseInference.countDown();
      analysis.shutdownNow();
    }
  }

  @Test
  void stopClosesModelsAfterTheBoundedWaitEvenIfAWorkerNeverQuiesces() throws Exception {
    final ExecutorService analysis = Executors.newSingleThreadExecutor();
    final CountDownLatch inferenceStarted = new CountDownLatch(1);
    final CountDownLatch releaseInference = new CountDownLatch(1);
    final AtomicBoolean inferenceDone = new AtomicBoolean();
    analysis.execute(() -> {
      inferenceStarted.countDown();
      boolean interrupted = false;
      while (true) {
        try {
          releaseInference.await();
          break;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
      inferenceDone.set(true);
    });
    final OpenNlpGrpcServer server = unstartedServer(analysis, 1);
    try {
      assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS));
      final long started = System.nanoTime();
      server.stop();
      final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      assertFalse(inferenceDone.get(),
          "stop() must not wait forever for a worker that never quiesces");
      assertTrue(elapsedMillis >= 900,
          "stop() skipped the bounded quiescence wait entirely: " + elapsedMillis + " ms");
    } finally {
      releaseInference.countDown();
      analysis.shutdownNow();
    }
  }

  /** Builds an unstarted server carrying the given analysis executor and a real model cache. */
  private static OpenNlpGrpcServer unstartedServer(
      ExecutorService analysisExecutor, int shutdownGraceSeconds) {
    final OpenNlpGrpcServer server = new OpenNlpGrpcServer();
    server.injectLifecycleForTest(
        analysisExecutor, new ModelBundleCache(Map.of()), shutdownGraceSeconds);
    return server;
  }

  /** Polls the flag for up to {@code budgetMillis}; returns true as soon as it is set. */
  private static boolean awaitFlag(AtomicBoolean flag, long budgetMillis) {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
    while (System.nanoTime() < deadline) {
      if (flag.get()) {
        return true;
      }
      java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    }
    return flag.get();
  }
}
