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

package opennlp.tools.coref.sim;

import opennlp.tools.coref.linker.LinkerMode;

import java.io.IOException;

/**
 * Model of mention compatibility using a maxent model.
 */
public class MaxentCompatibilityModel {

  private static GenderModel genModel;
  private static NumberModel numModel;

  public MaxentCompatibilityModel(String corefProject, LinkerMode mode) throws IOException {
    if (LinkerMode.TEST == mode) {
      genModel = GenderModel.testModel(corefProject + "/gen");
      numModel = NumberModel.testModel(corefProject + "/num");
    } else if (LinkerMode.TRAIN == mode) {
      genModel = GenderModel.trainModel(corefProject + "/gen");
      numModel = NumberModel.trainModel(corefProject + "/num");
    }
  }

  public Gender computeGender(Context c) {
    return genModel.computeGender(c);
  }

  public Number computeNumber(Context c) {
    return numModel.computeNumber(c);
  }
}
