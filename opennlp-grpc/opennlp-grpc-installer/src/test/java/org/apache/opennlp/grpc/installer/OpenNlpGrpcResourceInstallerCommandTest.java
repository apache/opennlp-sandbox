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
package org.apache.opennlp.grpc.installer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpGrpcResourceInstallerCommandTest {

  @Test
  void installsAPinnedModelThroughTheInstallerCli(@TempDir Path directory) throws Exception {
    final byte[] model = "model bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final Path source = Files.write(directory.resolve("en-ner-person.bin"), model);
    final Path target = directory.resolve("installed");
    final StringWriter output = new StringWriter();
    final CommandLine command = new CommandLine(new OpenNlpGrpcInstaller());
    command.setOut(new PrintWriter(output));

    final int exitCode = command.execute("install-resource",
        "--source", source.toUri().toString(),
        "--checksum", sha256(model),
        "--target", target.toString());

    assertEquals(0, exitCode);
    assertArrayEquals(model, Files.readAllBytes(target.resolve("en-ner-person.bin")));
    assertTrue(output.toString().contains(target.toString()));
  }

  @Test
  void refusesAnUnpinnedResourceBeforeCreatingTheTarget(@TempDir Path directory) {
    final Path target = directory.resolve("installed");
    final CommandLine command = new CommandLine(new OpenNlpGrpcInstaller());

    final int exitCode = command.execute("install-resource",
        "--source", directory.resolve("model.bin").toUri().toString(),
        "--target", target.toString());

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    assertFalse(Files.exists(target));
  }

  private static String sha256(byte[] value) throws Exception {
    final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
    final char[] digits = "0123456789abcdef".toCharArray();
    final char[] result = new char[digest.length * 2];
    for (int index = 0; index < digest.length; index++) {
      final int current = digest[index] & 0xff;
      result[index * 2] = digits[current >>> 4];
      result[index * 2 + 1] = digits[current & 0x0f];
    }
    return new String(result);
  }
}
