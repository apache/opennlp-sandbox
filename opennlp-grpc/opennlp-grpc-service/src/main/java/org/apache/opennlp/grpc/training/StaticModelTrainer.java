/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.ModelDistiller;

/**
 * Distills one teacher into a static embedding model directory. The seam exists so the
 * artifact store can be exercised without running a real distillation.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface StaticModelTrainer {

  /**
   * Distills the teacher into {@code outputDirectory} with the given terms as extra rows.
   *
   * @param teacherReference A local teacher directory or a Hugging Face model id.
   * @param outputDirectory The empty directory receiving the model files.
   * @param pcaDims The number of principal components to keep.
   * @param terms The terms to distill as extra rows, sorted by descending frequency.
   * @param listener Receives one call per progress line. Must not be {@code null}.
   * @return The distillation result read back from the verified model directory.
   * @throws IOException Thrown if reading, writing, or teacher resolution fails.
   * @throws IllegalArgumentException Thrown if an argument or the teacher is invalid.
   */
  ModelDistiller.Result train(String teacherReference, Path outputDirectory, int pcaDims,
      List<String> terms, ModelDistiller.ProgressListener listener) throws IOException;

  /**
   * Returns the production trainer backed by {@link ModelDistiller}.
   *
   * @return The distiller-backed trainer.
   */
  static StaticModelTrainer distiller() {
    return ModelDistiller::distill;
  }
}
