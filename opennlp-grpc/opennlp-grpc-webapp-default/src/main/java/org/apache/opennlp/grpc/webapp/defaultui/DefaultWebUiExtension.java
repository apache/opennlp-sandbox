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
package org.apache.opennlp.grpc.webapp.defaultui;

import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;

/** Supplies the default OpenNLP gRPC homepage and analysis playground. */
public final class DefaultWebUiExtension implements WebUiExtension {

  private static final WebUiExtensionDescriptor DESCRIPTOR = new WebUiExtensionDescriptor(
      new WebUiExtensionId("org.apache.opennlp.default-ui"),
      "Apache OpenNLP",
      new WebUiMountPath("/"),
      new WebUiClasspathResource("/META-INF/opennlp-grpc-ui/default"));

  /** Creates the default web user interface provider. */
  public DefaultWebUiExtension() {
  }

  /** {@inheritDoc} */
  @Override
  public WebUiExtensionDescriptor descriptor() {
    return DESCRIPTOR;
  }
}
