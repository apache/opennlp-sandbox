/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import org.apache.opennlp.grpc.v1.InstallModelProgress;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelStage;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.InstalledModelDescriptor;
import org.apache.opennlp.grpc.v1.ListInstalledModelsResponse;
import org.apache.opennlp.grpc.v1.ListModelCatalogResponse;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpTrainingProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogWireContractTest {

  @Test
  void catalogContractIsTypedImmutableAndServerStreamed() {
    final ServiceDescriptor service = OpenNlpTrainingProto.getDescriptor()
        .findServiceByName("OpenNlpModelTrainingService");
    assertNotNull(service);
    assertMethod(service, "ListModelCatalog", false);
    assertMethod(service, "ListInstalledModels", false);
    assertMethod(service, "InstallModel", true);

    final Descriptor catalog = ModelCatalogDescriptor.getDescriptor();
    assertField(catalog, "catalog_id", 1, FieldDescriptor.Type.STRING);
    assertField(catalog, "display_name", 2, FieldDescriptor.Type.STRING);
    assertField(catalog, "role", 3, FieldDescriptor.Type.ENUM);
    assertField(catalog, "model_id", 4, FieldDescriptor.Type.STRING);
    assertField(catalog, "source_uri", 5, FieldDescriptor.Type.STRING);
    assertField(catalog, "revision", 6, FieldDescriptor.Type.STRING);
    assertField(catalog, "license_name", 7, FieldDescriptor.Type.STRING);
    assertField(catalog, "license_uri", 8, FieldDescriptor.Type.STRING);
    assertField(catalog, "byte_size", 9, FieldDescriptor.Type.UINT64);
    assertField(catalog, "dimension", 10, FieldDescriptor.Type.UINT32);
    assertField(catalog, "languages", 11, FieldDescriptor.Type.STRING);
    assertField(catalog, "description", 12, FieldDescriptor.Type.STRING);
    assertTrue(catalog.findFieldByName("languages").isRepeated());

    final Descriptor installed = InstalledModelDescriptor.getDescriptor();
    assertField(installed, "catalog", 1, FieldDescriptor.Type.MESSAGE);
    assertField(installed, "artifact_hash", 2, FieldDescriptor.Type.STRING);
    assertField(installed, "byte_size", 3, FieldDescriptor.Type.UINT64);
    assertField(installed, "installed_at", 4, FieldDescriptor.Type.MESSAGE);
    assertField(installed, "loaded", 5, FieldDescriptor.Type.BOOL);

    final Descriptor request = InstallModelRequest.getDescriptor();
    assertField(request, "catalog_id", 1, FieldDescriptor.Type.STRING);
    assertField(request, "revision", 2, FieldDescriptor.Type.STRING);
    assertField(request, "license_name", 3, FieldDescriptor.Type.STRING);
    assertField(request, "license_acknowledged", 4, FieldDescriptor.Type.BOOL);

    final Descriptor progress = InstallModelProgress.getDescriptor();
    assertField(progress, "stage", 1, FieldDescriptor.Type.ENUM);
    assertField(progress, "current_file", 2, FieldDescriptor.Type.STRING);
    assertField(progress, "completed_files", 3, FieldDescriptor.Type.UINT32);
    assertField(progress, "total_files", 4, FieldDescriptor.Type.UINT32);
    assertField(progress, "completed_bytes", 5, FieldDescriptor.Type.UINT64);
    assertField(progress, "total_bytes", 6, FieldDescriptor.Type.UINT64);
    assertField(progress, "message", 7, FieldDescriptor.Type.STRING);

    final Descriptor catalogResponse = ListModelCatalogResponse.getDescriptor();
    assertField(catalogResponse, "models", 1, FieldDescriptor.Type.MESSAGE);
    assertTrue(catalogResponse.findFieldByName("models").isRepeated());
    assertField(catalogResponse, "installs_enabled", 2, FieldDescriptor.Type.BOOL);
    final Descriptor installedResponse = ListInstalledModelsResponse.getDescriptor();
    assertField(installedResponse, "models", 1, FieldDescriptor.Type.MESSAGE);
    assertTrue(installedResponse.findFieldByName("models").isRepeated());
    assertField(installedResponse, "installs_enabled", 2, FieldDescriptor.Type.BOOL);

    final EnumDescriptor roles = ModelArtifactRole.getDescriptor();
    assertEquals(0, roles.findValueByName("MODEL_ARTIFACT_ROLE_UNSPECIFIED").getNumber());
    assertEquals(1, roles.findValueByName("MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER")
        .getNumber());
    assertEquals(2, roles.findValueByName("MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING")
        .getNumber());

    final EnumDescriptor stages = InstallModelStage.getDescriptor();
    assertEquals(0, stages.findValueByName("INSTALL_MODEL_STAGE_UNSPECIFIED").getNumber());
    assertEquals(1, stages.findValueByName("INSTALL_MODEL_STAGE_VALIDATING").getNumber());
    assertEquals(2, stages.findValueByName("INSTALL_MODEL_STAGE_DOWNLOADING").getNumber());
    assertEquals(3, stages.findValueByName("INSTALL_MODEL_STAGE_VERIFYING").getNumber());
    assertEquals(4, stages.findValueByName("INSTALL_MODEL_STAGE_LOADING").getNumber());
    assertEquals(5, stages.findValueByName("INSTALL_MODEL_STAGE_PUBLISHED").getNumber());

    final Descriptor update = InstallModelUpdate.getDescriptor();
    assertNotNull(update.getOneofs().stream()
        .filter(oneof -> "update".equals(oneof.getName())).findFirst().orElse(null));
    assertField(update, "progress", 1, FieldDescriptor.Type.MESSAGE);
    assertField(update, "model", 2, FieldDescriptor.Type.MESSAGE);
  }

  private static void assertMethod(
      ServiceDescriptor service, String name, boolean serverStreaming) {
    final MethodDescriptor method = service.findMethodByName(name);
    assertNotNull(method);
    assertFalse(method.isClientStreaming());
    assertEquals(serverStreaming, method.isServerStreaming());
  }

  private static void assertField(
      Descriptor descriptor, String name, int number, FieldDescriptor.Type type) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field);
    assertEquals(number, field.getNumber());
    assertEquals(type, field.getType());
  }
}
