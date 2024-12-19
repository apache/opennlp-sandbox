/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.disambiguator;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * Implements a word sense disambiguation related {@link BaseToolFactory}.
 */
public class WSDisambiguatorFactory extends BaseToolFactory {

  /**
   * Creates a {@link WSDisambiguatorFactory} that provides the default implementation of
   * the resources. Use this constructor to programmatically create a factory.
   */
  public WSDisambiguatorFactory() {

  }

  /**
   * Instantiates a {@link WSDisambiguatorFactory} via a given {@code subclassName}.
   *
   * @param subclassName The class name used for instantiation. If {@code null}, an
   *                     instance of {@link WSDisambiguatorFactory} will be returned
   *                     per default. Otherwise, the {@link ExtensionLoader} mechanism
   *                     is applied to load the requested {@code subclassName}.
   *
   * @return A valid {@link WSDisambiguatorFactory} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the factory associated with {@code subclassName}.
   */
  public static WSDisambiguatorFactory create(String subclassName)
    throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new WSDisambiguatorFactory();
    }
    try {
      return ExtensionLoader.instantiateExtension(WSDisambiguatorFactory.class, subclassName);
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
        + ". The initialization throw an exception.";
      throw new InvalidFormatException(msg, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // no additional artifacts
  }

  /**
   * @implNote By default, an {@link IMSWSDContextGenerator} will be instantiated.
   *
   * @return Retrieves the active {@link WSDContextGenerator}.
   */
  public WSDContextGenerator getContextGenerator() {
    // default can be IMS
    return new IMSWSDContextGenerator();
  }

}