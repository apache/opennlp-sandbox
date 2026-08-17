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
package org.apache.opennlp.grpc.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class OpenNlpGrpcWebAppTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void exposesSelfContainedCommandHelp() {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new OpenNlpGrpcWebApp());
    commandLine.setOut(new PrintWriter(output));

    assertEquals(0, commandLine.execute("--help"));
    assertTrue(output.toString().contains("--grpc-target"));
    assertTrue(output.toString().contains("--max-request-bytes"));
    assertTrue(output.toString().contains("--allow-remote"));
    assertTrue(output.toString().contains("--bound-port-file"));
  }

  @Test
  void obtainsVersionFromPackageMetadata() {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new OpenNlpGrpcWebApp());
    commandLine.setOut(new PrintWriter(output));

    assertEquals(0, commandLine.execute("--version"));
    assertEquals("opennlp-grpc-webapp development\n", output.toString());
  }

  @Test
  void requiresExplicitConsentForNonLoopbackBinding() throws Exception {
    InetAddress wildcard = InetAddress.getByName("0.0.0.0");

    assertThrows(IllegalArgumentException.class,
        () -> OpenNlpGrpcWebApp.validateBindAddress(wildcard, false));
    OpenNlpGrpcWebApp.validateBindAddress(wildcard, true);
    OpenNlpGrpcWebApp.validateBindAddress(InetAddress.getLoopbackAddress(), false);
  }

  @Test
  void writesOneCompleteBoundPortReadinessFile() throws Exception {
    Path readinessFile = temporaryDirectory.resolve("bound-port");

    OpenNlpGrpcWebApp.writeBoundPortFile(readinessFile, 43210);

    assertEquals("43210\n", Files.readString(readinessFile));
    assertThrows(IllegalArgumentException.class,
        () -> OpenNlpGrpcWebApp.writeBoundPortFile(readinessFile, 43211));
  }
}
