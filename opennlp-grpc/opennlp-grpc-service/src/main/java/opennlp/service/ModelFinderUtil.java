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
package opennlp.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import opennlp.service.classpath.DirectoryModelFinder;
import opennlp.tools.models.ClassPathModel;
import opennlp.tools.models.ClassPathModelEntry;
import opennlp.tools.models.ClassPathModelLoader;

/**
 * Utility class for scanning and loading models.
 *
 * <p>The {@code ModelFinderUtil} class provides a method to scan for models in a given directory,
 * load them into memory, and return a map of model identifiers (SHA-256 hashes) to the loaded
 * {@link ClassPathModel} instances. The search process supports wildcard matching and optional
 * recursive scanning of subdirectories.</p>
 *
 * <p>This utility simplifies model discovery and caching, allowing to dynamically
 * load models from a specified file location based on a provided configuration.</p>
 *
 * @see DirectoryModelFinder
 * @see ClassPathModel
 * @see ClassPathModelLoader
 */
public class ModelFinderUtil {

  /**
   * Discovers and loads models into a cache based on the provided configuration.
   *
   * <p>This method searches for model files in a directory specified by the configuration map.
   * The search can be performed recursively if enabled. Models that match the wildcard pattern
   * are loaded into memory using a {@link ClassPathModelLoader} and stored in a map. The keys
   * of the map are the SHA-256 hashes of the models, ensuring uniqueness.</p>
   *
   * <p><b>Configuration Keys:</b></p>
   * <ul>
   *   <li>{@code model.location} - The directory to search for models (default: {@code extlib}).</li>
   *   <li>{@code model.recursive} - Whether to include subdirectories in the search
   *       (default: {@code true}).</li>
   *   <li>Wildcard pattern key - The configuration key for specifying the model file
   *       wildcard pattern (e.g., {@code opennlp-models-pos-*.jar}).</li>
   * </ul>
   *
   * @param conf                   A map of configuration properties containing model location,
   *                               scan settings, and wildcard patterns.
   * @param wildcardPatternKey     The configuration key for the wildcard pattern. Must not be {@code null}.
   * @param defaultWildcardPattern The default wildcard pattern to use if the key is not present. Must not be {@code null}.
   * @return A {@code Map} of SHA-256 model hashes to {@link ClassPathModel} objects.
   * @throws IOException If an error occurs while accessing the model files or loading them.
   */
  static Map<String, ClassPathModel> findModels(Map<String, String> conf, String wildcardPatternKey, String defaultWildcardPattern) throws IOException {
    Objects.requireNonNull(wildcardPatternKey);
    Objects.requireNonNull(defaultWildcardPattern);

    final Map<String, ClassPathModel> foundModels = new HashMap<>();
    final String modelDir = conf.getOrDefault("model.location", "extlib");
    final boolean recursive = Boolean.parseBoolean(conf.getOrDefault("model.recursive", "true"));
    final String wildcardPattern = conf.getOrDefault(wildcardPatternKey, defaultWildcardPattern);

    final DirectoryModelFinder finder = new DirectoryModelFinder(wildcardPattern, Path.of(modelDir), recursive);
    final ClassPathModelLoader loader = new ClassPathModelLoader();

    final Set<ClassPathModelEntry> models = finder.findModels(false);
    for (ClassPathModelEntry entry : models) {
      final ClassPathModel model = loader.load(entry);
      if (model != null) {
        foundModels.put(model.getModelSHA256(), model);
      }
    }
    return foundModels;
  }


}
