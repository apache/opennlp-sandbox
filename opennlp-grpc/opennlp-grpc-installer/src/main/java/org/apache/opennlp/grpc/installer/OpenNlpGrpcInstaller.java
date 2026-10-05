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

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Standalone entry point of the installer add-on: verified model and resource
 * installation for the OpenNLP gRPC server, usable without starting the server.
 */
@Command(
    name = "opennlp-grpc-installer",
    mixinStandardHelpOptions = true,
    description = "Verified model and data installation for the OpenNLP gRPC server.",
    subcommands = OpenNlpGrpcResourceInstallerCommand.class)
public final class OpenNlpGrpcInstaller implements Callable<Integer> {

  @Spec
  private CommandSpec spec;

  /** Creates an unconfigured command for picocli. */
  public OpenNlpGrpcInstaller() {
  }

  /**
   * Prints usage when no subcommand is given.
   *
   * @return The usage exit code, because running without a subcommand installs nothing.
   */
  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getOut());
    return CommandLine.ExitCode.USAGE;
  }

  /**
   * Runs the installer CLI.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    System.exit(new CommandLine(new OpenNlpGrpcInstaller()).execute(args));
  }
}
