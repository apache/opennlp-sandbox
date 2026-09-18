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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.search.query;

import java.util.ServiceLoader;

import com.google.protobuf.Struct;

/**
 * Compiles and evaluates the two constrained CEL roles of the compound query contract:
 * a filter that must type-check to bool and a calculator that must type-check to a
 * number. Expressions read one candidate's metadata {@link Struct} as {@code metadata}
 * and perform no I/O, so evaluation stays deterministic and provider-portable.
 *
 * <p>Implementations are discovered through {@link ServiceLoader}. The gRPC core ships
 * none; without an installed evaluator, queries using CEL clauses report UNIMPLEMENTED.
 * Thread safety is implementation specific, but compiled expressions must support
 * concurrent evaluation.</p>
 */
public interface CelQueryEvaluator {

  /**
   * Compiles a membership predicate.
   *
   * @param expression CEL expression source.
   * @return The compiled predicate.
   * @throws IllegalArgumentException If the expression does not parse or does not
   *     type-check to bool.
   */
  CompiledFilter compileFilter(String expression);

  /**
   * Compiles a numeric scoring expression.
   *
   * @param expression CEL expression source.
   * @return The compiled calculator.
   * @throws IllegalArgumentException If the expression does not parse or does not
   *     type-check to a number.
   */
  CompiledCalculator compileCalculator(String expression);

  /** One compiled boolean CEL expression. */
  interface CompiledFilter {

    /**
     * Evaluates the predicate against one candidate's metadata.
     *
     * @param metadata Candidate metadata, never {@code null}.
     * @return The expression result.
     * @throws IllegalArgumentException If evaluation fails, such as a missing key the
     *     expression does not guard.
     */
    boolean test(Struct metadata);
  }

  /** One compiled numeric CEL expression. */
  interface CompiledCalculator {

    /**
     * Evaluates the expression against one candidate's metadata.
     *
     * @param metadata Candidate metadata, never {@code null}.
     * @return The raw numeric result before normalization.
     * @throws IllegalArgumentException If evaluation fails, such as a missing key the
     *     expression does not guard.
     */
    double calculate(Struct metadata);
  }

  /**
   * Discovers the installed evaluator, when one is on the classpath.
   *
   * @return The first evaluator found by {@link ServiceLoader}, or {@code null}.
   */
  static CelQueryEvaluator discover() {
    for (CelQueryEvaluator evaluator : ServiceLoader.load(CelQueryEvaluator.class)) {
      return evaluator;
    }
    return null;
  }
}
