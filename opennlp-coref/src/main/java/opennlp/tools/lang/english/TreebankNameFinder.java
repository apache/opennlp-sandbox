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

package opennlp.tools.lang.english;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.namefind.NameFinderEventStream;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.Parse;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

/**
 * Class is used to create a name finder for English.
 * 
 * @deprecated will be removed soon!
 */
@Deprecated
public class TreebankNameFinder {

  private static final Logger logger = LoggerFactory.getLogger(TreebankNameFinder.class);
  private static final String SYMBOL_LT = "<";
  private static final String SYMBOL_GT = ">";

  public static String[] NAME_TYPES =
      {"person", "organization", "location", "date", "time", "percentage", "money"};

  private final NameFinderME nameFinder;
  
  /**
   * Creates an English name finder using the specified model.
   * @param mod The model used for finding names.
   */
  public TreebankNameFinder(TokenNameFinderModel mod) {
    nameFinder = new NameFinderME(mod);
  }

  private static void clearPrevTokenMaps(TreebankNameFinder[] finders) {
    for (TreebankNameFinder finder : finders) {
      finder.nameFinder.clearAdaptiveData();
    }
  }

  private static void processParse(TreebankNameFinder[] finders, String[] tags, BufferedReader input)
      throws IOException {
    Span[][] nameSpans = new Span[finders.length][];
    
    for (String line = input.readLine(); null != line; line = input.readLine()) {
      if (line.isEmpty()) {
        System.out.println();
        clearPrevTokenMaps(finders);
        continue;
      }
      Parse p = Parse.parseParse(line);
      Parse[] tagNodes = p.getTagNodes();
      String[] tokens = new String[tagNodes.length];
      for (int ti = 0; ti < tagNodes.length; ti++) {
        tokens[ti] = tagNodes[ti].getCoveredText();
      }
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        nameSpans[fi] = finders[fi].nameFinder.find(tokens);
      }
      
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        Parse.addNames(tags[fi],nameSpans[fi],tagNodes);
      }
      p.show();
    }
  }
      
  /**
   * Adds sgml style name {@code tags} to the specified {@code input} buffer and outputs this information to stdout.
   *
   * @param finders The name finders to be used.
   * @param tags The tag names for the corresponding name finder.
   * @param input The {@link BufferedReader input reader}.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  private static void processText(TreebankNameFinder[] finders, String[] tags, BufferedReader input)
      throws IOException {
    Span[][] nameSpans = new Span[finders.length][];
    String[][] nameOutcomes = new String[finders.length][];
    opennlp.tools.tokenize.Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    StringBuffer output = new StringBuffer();
    for (String line = input.readLine(); null != line; line = input.readLine()) {
      if (line.isEmpty()) {
        clearPrevTokenMaps(finders);
        System.out.println();
        continue;
      }
      output.setLength(0);
      Span[] spans = tokenizer.tokenizePos(line);
      String[] tokens = Span.spansToStrings(spans,line);
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        nameSpans[fi] = finders[fi].nameFinder.find(tokens);
        nameOutcomes[fi] = NameFinderEventStream.generateOutcomes(nameSpans[fi], null, tokens.length);
      }
      
      for (int ti = 0, tl = tokens.length; ti < tl; ti++) {
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          //check for end tags
          if (ti != 0) {
            if ((nameOutcomes[fi][ti].equals(NameFinderME.START)
                || nameOutcomes[fi][ti].equals(NameFinderME.OTHER))
                && (nameOutcomes[fi][ti - 1].equals(NameFinderME.START)
                || nameOutcomes[fi][ti - 1].equals(NameFinderME.CONTINUE))) {
              output.append("</").append(tags[fi]).append(SYMBOL_GT);
            }
          }
        }
        if (ti > 0 && spans[ti - 1].getEnd() < spans[ti].getStart()) {
          output.append(line.substring(spans[ti - 1].getEnd(), spans[ti].getStart()));
        }
        //check for start tags
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          if (nameOutcomes[fi][ti].equals(NameFinderME.START)) {
            output.append(SYMBOL_LT).append(tags[fi]).append(SYMBOL_GT);
          }
        }
        output.append(tokens[ti]);
      }
      //final end tags
      if (tokens.length != 0) {
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          if (nameOutcomes[fi][tokens.length - 1].equals(NameFinderME.START)
              || nameOutcomes[fi][tokens.length - 1].equals(NameFinderME.CONTINUE)) {
            output.append("</").append(tags[fi]).append(SYMBOL_GT);
          }
        }
      }
      if (tokens.length != 0) {
        if (spans[tokens.length - 1].getEnd() < line.length()) {
          output.append(line.substring(spans[tokens.length - 1].getEnd()));
        }
      }
      System.out.println(output);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      logger.info("""
              Usage NameFinder -[parse] model1 model2 ... modelN < sentences\s
               -parse: Use this option to find names on parsed input.\s
              Un-tokenized sentence text is the default.""");
      System.exit(1);
    }
    int ai = 0;
    boolean parsedInput = false;
    while (args[ai].startsWith("-") && ai < args.length) {
      if (args[ai].equals("-parse")) {
        parsedInput = true;
      } else {
        logger.warn("Ignoring unknown option {}", args[ai]);
      }
      ai++;
    }
    TreebankNameFinder[] finders = new TreebankNameFinder[args.length - ai];
    String[] names = new String[args.length - ai];
    for (int fi = 0; ai < args.length; ai++,fi++) {
      String modelName = args[ai];
      finders[fi] = new TreebankNameFinder(new TokenNameFinderModel(new FileInputStream(modelName)));
      int nameStart = modelName.lastIndexOf(FileSystems.getDefault().getSeparator()) + 1;
      int nameEnd = modelName.indexOf('.', nameStart);
      if (nameEnd == -1) {
        nameEnd = modelName.length();
      }
      names[fi] = modelName.substring(nameStart, nameEnd);
    }
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    if (parsedInput) {
      processParse(finders,names,in);
    } else {
      processText(finders,names,in);
    }
  }
}
