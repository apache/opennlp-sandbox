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
package org.apache.opennlp.grpc.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Shared process-log synchronization for live test listeners bound to port zero. */
final class LiveProcessHarnessSupport {

  private LiveProcessHarnessSupport() {
  }

  /**
   * Waits for a child process to report the port that its own listener bound.
   *
   * @param process The child process that owns the listener.
   * @param log The child's combined output log.
   * @param marker Text immediately before the decimal port.
   * @param timeout Maximum startup wait.
   * @param processName Name used in failure messages.
   * @return The reported port from 1 through 65535.
   * @throws IOException If the log cannot be read.
   * @throws InterruptedException If the wait is interrupted.
   */
  static int awaitBoundPort(
      Process process, Path log, String marker, Duration timeout, String processName)
      throws IOException, InterruptedException {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(processName + " process exited with code "
            + process.exitValue() + "; log:\n" + Files.readString(log));
      }
      final String output = Files.readString(log);
      final int markerStart = output.indexOf(marker);
      if (markerStart >= 0) {
        final int digitsStart = markerStart + marker.length();
        int digitsEnd = digitsStart;
        while (digitsEnd < output.length()) {
          final char character = output.charAt(digitsEnd);
          if (character < '0' || character > '9') {
            break;
          }
          digitsEnd++;
        }
        if (digitsEnd > digitsStart) {
          final int port = Integer.parseInt(output.substring(digitsStart, digitsEnd));
          if (port > 0 && port <= 65535) {
            return port;
          }
          throw new IllegalStateException(processName + " reported invalid port " + port);
        }
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException(processName + " did not report its OS-assigned port within "
        + timeout + "; log:\n" + Files.readString(log));
  }

  /**
   * Waits for a child process to create a complete bound-port readiness file.
   *
   * @param process The child process that owns the listener.
   * @param readinessFile File created by the child after listener startup.
   * @param log The child's combined output log.
   * @param timeout Maximum startup wait.
   * @param processName Name used in failure messages.
   * @return The reported port from 1 through 65535.
   * @throws IOException If the readiness file or log cannot be read.
   * @throws InterruptedException If the wait is interrupted.
   */
  static int awaitBoundPortFile(
      Process process, Path readinessFile, Path log, Duration timeout, String processName)
      throws IOException, InterruptedException {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(processName + " process exited with code "
            + process.exitValue() + "; log:\n" + Files.readString(log));
      }
      if (Files.isRegularFile(readinessFile)) {
        final String value = Files.readString(readinessFile);
        if (value.endsWith("\n") && value.length() > 1) {
          final String digits = value.substring(0, value.length() - 1);
          for (int index = 0; index < digits.length(); index++) {
            final char character = digits.charAt(index);
            if (character < '0' || character > '9') {
              throw new IllegalStateException(processName
                  + " wrote an invalid bound-port readiness file");
            }
          }
          final int port = Integer.parseInt(digits);
          if (port > 0 && port <= 65535) {
            return port;
          }
          throw new IllegalStateException(processName + " reported invalid port " + port);
        }
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException(processName
        + " did not create its bound-port readiness file within " + timeout
        + "; log:\n" + Files.readString(log));
  }
}
