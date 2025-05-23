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

package opennlp.tools.cmdline.disambiguator;

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;
import opennlp.tools.cmdline.params.LanguageParams;

/**
 * Parameters for DisambiguatorTool.
 * <p>
 * Note: Do not use this class, internal use only!
 */
public interface DisambiguatorToolParams extends LanguageParams,EncodingParameter {

  @ParameterDescription(valueName = "mfs|lesk|ims", description = "The type of the disambiguator approach. One of mfs|lesk|ims.")
  @OptionalParameter(defaultValue = "mfs")
  String getType();
     
  @ParameterDescription(valueName = "testData", description = "the data to be used during evaluation")
  @OptionalParameter
  File getData();

}