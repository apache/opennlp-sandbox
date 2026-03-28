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

import java.util.Map;

import opennlp.tools.models.ClassPathModel;

/**
 * Interface representing a cache-aware component for managing service and model caches.
 *
 * <p>The {@code CacheAware} interface defines methods to retrieve and manage in-memory caches
 * for services and {@link ClassPathModel} objects. It also provides a default method to clear
 * these caches, ensuring proper resource management.</p>
 *
 * <p>Implementations of this interface can use the provided methods to access and maintain
 * caches in a thread-safe manner, and to clean up resources when necessary.</p>
 *
 * @param <T> The type of service objects stored in the service cache.
 */
public interface CacheAware<T> {

  /**
   * Clears the in-memory caches for services and models.
   *
   * <p>This method ensures that all cached services and {@link ClassPathModel} objects
   * are removed from their respective caches, freeing up memory and resources. </p>
   *
   * <p><b>Thread Safety:</b> The method synchronizes access to the underlying caches to ensure safe
   * removal of taggers in a multithreaded environment.</p>
   *
   * <p><b>Use Case:</b> This method is useful for scenarios where the cached models or taggers need to be
   * reloaded, such as after configuration changes or when the service needs to release memory during shutdown.</p>
   *
   * <p>No exceptions are thrown by this method; any exceptions occurring during the closing of taggers
   * are caught and ignored.</p>
   */
  default void clearCaches() {
    synchronized (getServiceCache()) {
      for (T t : getServiceCache().values()) {
        if (t instanceof AutoCloseable a) {
          try {
            a.close();
          } catch (Exception ignored) {

          }
        }
      }
      getModelCache().clear();
      getServiceCache().clear();
    }
  }

  /**
   * Returns the model cache containing {@link ClassPathModel} objects.
   *
   * <p>The returned cache maps unique string identifiers (e.g., model names or paths)
   * to their corresponding {@link ClassPathModel} instances.</p>
   *
   * @return A {@code Map} representing the model cache.
   */
  Map<String, ClassPathModel> getModelCache();

  /**
   * Returns the service cache containing service objects of type {@code T}.
   *
   * <p>The returned cache maps unique string identifiers to their corresponding
   * service objects. Service objects can be of any type, as defined by the generic
   * parameter {@code T}.</p>
   *
   * @return A {@code Map} representing the service cache.
   */
  Map<String, T> getServiceCache();

}
