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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Black-box contract for ServiceLoader providers outside the server artifact. */
class ExternalEmbeddingProviderLiveIT {

  private static final String SERVICE =
      "META-INF/services/org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory";

  @Test
  void shadedServerPreservesServicesAndLoadsExternalProviderJar() throws Exception {
    final Path serverJar = requiredServerJar();
    assertSlimServerCarriesNoDlEngines(serverJar);
    assertBuiltInServicesMerged(requiredServerAllJar());
    final Path extensionJar = buildExternalProviderJar(serverJar);

    final Properties configuration = new Properties();
    configuration.setProperty(
        "model.embedder.external-model.external.vector_space_id", "external-space-v1");
    try (LiveServerHarness harness = LiveServerHarness.start(configuration, extensionJar)) {
      final var client = harness.client();
      final List<ModelDescriptor> models = client
          .listModelBundles(ListModelBundlesRequest.getDefaultInstance())
          .getBundles(0).getModelsList();
      final ModelDescriptor external = models.stream()
          .filter(model -> model.getComponentType() == ComponentType.COMPONENT_TYPE_EMBEDDER)
          .filter(model -> "external-model".equals(model.getName()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("external provider missing from catalog: " + models));
      assertEquals("external", external.getBackendId());
      assertEquals(2, external.getEmbeddingDimension());
      assertEquals("external-space-v1",
          external.getEmbeddingRoutes(0).getVectorSpaceId());

      final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello plugin."))
          .setProfile(AnalysisProfile.newBuilder()
              .setProfileId("external-provider")
              .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_EMBED))
          .setOptions(AnalysisOptions.newBuilder().setEmbeddingSelector(
              EmbeddingSelector.newBuilder()
                  .setModelId("external-model")
                  .setBackend(EmbeddingBackendSelector.newBuilder().setCustom("external"))))
          .build());

      assertEquals(1, response.getDocument().getEmbeddingsCount());
      assertEquals(List.of(13f, 1f), response.getDocument().getEmbeddings(0).getVectorList());
      assertEquals("external",
          response.getDocument().getEmbeddings(0).getRoute().getBackendId());
    }
  }

  /**
   * Pins the slim/add-on split: the slim server jar ships no embedding engines of its own,
   * so every embedding backend it serves arrived through a ServiceLoader jar like the one
   * this test builds.
   */
  private static void assertSlimServerCarriesNoDlEngines(Path serverJar) throws IOException {
    try (JarFile jar = new JarFile(serverJar.toFile())) {
      assertNull(jar.getJarEntry(SERVICE),
          "the slim server jar must not register embedding engines of its own");
    }
  }

  /**
   * Asserts the server-all assembly merged the registration files contributed by the
   * separate add-on modules into one descriptor, the contract the shade
   * ServicesResourceTransformer provides.
   */
  private static void assertBuiltInServicesMerged(Path serverAllJar) throws IOException {
    try (JarFile jar = new JarFile(serverAllJar.toFile())) {
      final JarEntry descriptor = jar.getJarEntry(SERVICE);
      assertNotNull(descriptor, "server-all has no embedding service descriptor");
      try (InputStream input = jar.getInputStream(descriptor)) {
        final String providers = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(providers.contains(
            "org.apache.opennlp.grpc.dl.embedding.onnx.OnnxEmbeddingBackendFactory"));
        assertTrue(providers.contains(
            "org.apache.opennlp.grpc.dl.embedding.cuda.CudaEmbeddingBackendFactory"));
        assertTrue(providers.contains(
            "org.apache.opennlp.grpc.embedding.statictable.StaticTableEmbeddingBackendFactory"));
        assertTrue(providers.contains(
            "org.apache.opennlp.grpc.embedding.tei.TeiEmbeddingBackendFactory"));
        assertTrue(providers.contains(
            "org.apache.opennlp.grpc.embedding.openvino.OpenVinoEmbeddingBackendFactory"));
      }
    }
  }

  private static Path buildExternalProviderJar(Path serverJar) throws IOException {
    final Path root = Files.createTempDirectory("opennlp-external-provider-");
    final Path source = root.resolve("src/external/ExternalFactory.java");
    final Path classes = root.resolve("classes");
    Files.createDirectories(source.getParent());
    Files.createDirectories(classes);
    Files.writeString(source, externalProviderSource(), StandardCharsets.UTF_8);

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "integration test requires a JDK compiler");
    final int compileResult = compiler.run(null, null, null,
        "-classpath", serverJar.toString(), "-d", classes.toString(), source.toString());
    assertEquals(0, compileResult, "external provider fixture did not compile");

    final Path descriptor = classes.resolve(SERVICE);
    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, "external.ExternalFactory\n", StandardCharsets.UTF_8);

    final Path providerJar = root.resolve("external-provider.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(providerJar));
         Stream<Path> entries = Files.walk(classes)) {
      for (Path entry : entries.filter(Files::isRegularFile).sorted().toList()) {
        final String name = classes.relativize(entry).toString().replace('\\', '/');
        output.putNextEntry(new JarEntry(name));
        Files.copy(entry, output);
        output.closeEntry();
      }
    }
    return providerJar;
  }

  private static String externalProviderSource() {
    return """
        package external;

        import java.util.Map;
        import java.util.Set;
        import org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory;
        import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;

        public final class ExternalFactory implements EmbeddingBackendFactory {
          public ExternalFactory() {
          }

          @Override
          public String backendId() {
            return "external";
          }

          @Override
          public EmbeddingProvider create(Map<String, String> configuration) {
            return new Provider();
          }

          private static final class Provider implements EmbeddingProvider {
            @Override
            public String backendId() {
              return "external";
            }

            @Override
            public boolean isAvailable() {
              return true;
            }

            @Override
            public Set<String> registeredModelIds() {
              return Set.of("external-model");
            }

            @Override
            public boolean supportsModel(String modelId) {
              return "external-model".equals(modelId);
            }

            @Override
            public int embeddingDimension(String modelId) {
              return 2;
            }

            @Override
            public float[] embed(String modelId, String text) {
              return new float[] {text.length(), 1f};
            }
          }
        }
        """;
  }

  /** The slim server jar under test. */
  private static Path requiredServerJar() {
    return requiredJar("opennlp.grpc.server.jar");
  }

  /** The everything-in-one server assembly under test. */
  private static Path requiredServerAllJar() {
    return requiredJar("opennlp.grpc.server.all.jar");
  }

  /** Resolves a jar path from a system property and asserts the file exists. */
  private static Path requiredJar(String property) {
    final String configured = System.getProperty(property);
    assertNotNull(configured, property + " is not configured");
    final Path jar = Path.of(configured);
    assertTrue(Files.isRegularFile(jar), "jar does not exist: " + jar);
    return jar;
  }
}
