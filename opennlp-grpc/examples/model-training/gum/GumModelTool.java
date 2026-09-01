/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import opennlp.tools.chunker.ChunkerEvaluator;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.parser.ChunkSampleStream;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserEvaluator;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/** Extracts the parser's chunker and evaluates both artifacts on held-out trees. */
public final class GumModelTool {

  private GumModelTool() {
  }

  /**
   * Extracts and evaluates the artifacts.
   *
   * @param arguments Parser model, chunker model, and evaluation data paths.
   * @throws IOException If a model or evaluation sample cannot be read or written.
   */
  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 3) {
      throw new IllegalArgumentException(
          "usage: GumModelTool parser-model chunker-model evaluation-data");
    }
    final Path parserPath = Path.of(arguments[0]);
    final Path chunkerPath = Path.of(arguments[1]);
    final Path evaluationPath = Path.of(arguments[2]);
    final ParserModel parserModel = new ParserModel(parserPath);
    try (var output = new BufferedOutputStream(Files.newOutputStream(chunkerPath))) {
      parserModel.getParserChunkerModel().serialize(output);
    }

    final ParserEvaluator parserEvaluator =
        new ParserEvaluator(ParserFactory.create(parserModel));
    try (ObjectStream<Parse> samples = parseSamples(evaluationPath)) {
      parserEvaluator.evaluate(samples);
    }
    final ChunkerEvaluator chunkerEvaluator =
        new ChunkerEvaluator(new ChunkerME(parserModel.getParserChunkerModel()));
    try (ObjectStream<Parse> samples = parseSamples(evaluationPath)) {
      chunkerEvaluator.evaluate(new ChunkSampleStream(samples));
    }
    System.out.println("parser " + parserEvaluator.getFMeasure());
    System.out.println("chunker " + chunkerEvaluator.getFMeasure());
  }

  /** Opens a resettable one-tree-per-line OpenNLP sample stream. */
  private static ObjectStream<Parse> parseSamples(Path path) throws IOException {
    return new ParseSampleStream(new PlainTextByLineStream(
        new MarkableFileInputStreamFactory(path.toFile()), StandardCharsets.UTF_8));
  }
}
