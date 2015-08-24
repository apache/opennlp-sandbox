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

package opennlp.tools.cmdline.disambiguator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDSampleStream;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.ims.IMSME;
import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.mfs.MFS;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/*
 * Command line tool for disambiguator supports MFS for now
 * 
 */
public class DisambiguatorTool extends CmdLineTool {

  // TODO CmdLineTool should be an interface not abstract class
  public String getName() {
    return "Disambiguator";
  }

  public String getShortDescription() {
    return "Word Sense Disambiguator";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(DisambiguatorToolParams.class)
        + " < sentences";
  }

  public void run(String[] args) {

    if (!ArgumentParser.validateArguments(args, DisambiguatorToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    DisambiguatorToolParams params = ArgumentParser.parse(args,
        DisambiguatorToolParams.class);

    WSDisambiguator disambiguator = makeTool(params);

    PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");

    ObjectStream<String> lineStream = new PlainTextByLineStream(
        new InputStreamReader(System.in));

    perfMon.start();

    try {
      String line;
      while ((line = lineStream.read()) != null) {

        WSDSample sample = WSDSample.parse(line);

        WSDHelper.printResults(disambiguator,
            disambiguator.disambiguate(sample));

        perfMon.incrementCounter();
      }
    } catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }

    perfMon.stopAndPrintFinalResult();

  }

  public static WSDisambiguator makeTool(DisambiguatorToolParams params) {

    WSDisambiguator wsd = null;

    if (params.getType().equalsIgnoreCase("mfs")) {
      wsd = new MFS();
    } else if (params.getType().equalsIgnoreCase("lesk")) {
      wsd = new Lesk();
    } else if (params.getType().equalsIgnoreCase("ims")) {
    }
    return wsd;

  }

  static ObjectStream<WSDSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(
        sampleDataIn.getChannel(), encoding);

    return new WSDSampleStream(lineStream);
  }
}
