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

package opennlp.tools.coref.dictionary;

import net.sf.extjwnl.JWNLException;
import opennlp.tools.coref.linker.AbstractLinker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Factory class used to get an instance of a dictionary object.
 *
 * @see Dictionary
 */
public class DictionaryFactory {

  private static final Logger logger = LoggerFactory.getLogger(DictionaryFactory.class);

  private static Dictionary dictionary;

  /**
   * @return Retrieves the default implementation of the Dictionary interface.
   */
  public static Dictionary getDictionary() {
    if (dictionary == null) {
      try {
        dictionary = new JWNLDictionary();
      } catch (JWNLException e) {
        logger.error(e.getLocalizedMessage(), e);
      }
    }
    return dictionary;
  }
}
