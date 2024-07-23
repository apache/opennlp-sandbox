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
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.disambiguator.WSDEvaluator;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.util.ObjectStream;

public final class DisambiguatorEvaluatorTool extends CmdLineTool {

  @Override
  public String getName() {
    return "DisambiguatorEvaluator";
  }

  @Override
  public String getShortDescription() {
    return "Disambiguator Evaluation Tool";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(DisambiguatorEvaluatorParams.class);
  }

  public void run(String[] args) {
    if (!ArgumentParser.validateArguments(args,
        DisambiguatorEvaluatorParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    DisambiguatorEvaluatorParams params = ArgumentParser.parse(args,
        DisambiguatorEvaluatorParams.class);

    File testData = params.getData();
    CmdLineUtil.checkInputFile("Test data", testData);

    WSDisambiguator disambiguator = DisambiguatorTool.makeTool(params);
    WSDEvaluator evaluator = new WSDEvaluator(disambiguator);

    System.out.print("Evaluating ... ");

    try (ObjectStream<WSDSample> sampleStream = DisambiguatorTool.openSampleData(
            "Test", testData, params.getEncoding())) {
        evaluator.evaluate(sampleStream);
      } catch (IOException e) {
        System.err.println("failed");
        System.err.println("Reading test data error " + e.getMessage());
        throw new TerminateToolException(-1);
    }

    System.out.println("done");
    System.out.println();
    System.out.println("Accuracy: " + evaluator.getAccuracy());
  }
}