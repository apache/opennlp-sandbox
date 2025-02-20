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

package opennlp.tools.cmdline.coref;

import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.coref.CoreferencerTrainerTool.TrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.coref.CorefSample;
import opennlp.tools.coref.CorefTrainer;
import opennlp.tools.util.model.ModelUtil;

public class CoreferencerTrainerTool extends AbstractTrainerTool<CorefSample, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }
  
  public CoreferencerTrainerTool() {
    super(CorefSample.class, TrainerToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Trainer for a Learnable Noun Phrase Coreferencer";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    
    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);

    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    try {
      CorefTrainer.train(params.getModel().toString(), sampleStream, true, true);
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: " +
          e.getMessage(), e);
    }
  }
  
}
