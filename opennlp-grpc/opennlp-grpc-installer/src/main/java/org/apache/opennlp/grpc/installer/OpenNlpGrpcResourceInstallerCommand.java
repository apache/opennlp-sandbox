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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import opennlp.tools.util.ResourceInstaller;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Installs one checksum-pinned model or data resource without starting the server. */
@Command(
    name = "install-resource",
    mixinStandardHelpOptions = true,
    description = "Download, verify, and atomically install one model or data resource.")
public final class OpenNlpGrpcResourceInstallerCommand implements Callable<Integer> {

  @Option(names = "--source", required = true, description = "HTTP(S) or file URI to install")
  private URI source;

  @Option(names = "--checksum", required = true,
      description = "Expected SHA-256 or SHA-512 digest")
  private String checksum;

  @Option(names = "--target", required = true, description = "Target installation directory")
  private Path target;

  @Spec
  private CommandSpec spec;

  /** Creates an unconfigured command for picocli. */
  public OpenNlpGrpcResourceInstallerCommand() {
  }

  /** {@inheritDoc} */
  @Override
  public Integer call() throws IOException {
    final Path installed = ResourceInstaller.install(source, target, checksum);
    spec.commandLine().getOut().println("Installed verified resource into " + installed);
    return 0;
  }
}
