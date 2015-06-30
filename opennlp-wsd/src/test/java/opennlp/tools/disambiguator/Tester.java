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

package opennlp.tools.disambiguator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.postag.POSModelLoader;
import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.lesk.LeskParameters;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import org.junit.Test;

public class Tester {

  @Test
  public static void main(String[] args) {

    String sentence = "I went fishing for some sea bass.";
    TokenizerModel TokenizerModel;

    try {
      TokenizerModel = new TokenizerModel(new FileInputStream(
          "src\\test\\resources\\opennlp\\tools\\disambiguator\\en-token.bin"));
      Tokenizer tokenizer = new TokenizerME(TokenizerModel);

      String[] words = tokenizer.tokenize(sentence);

      POSModel posTaggerModel = new POSModelLoader()
          .load(new File(
              "src\\test\\resources\\opennlp\\tools\\disambiguator\\en-pos-maxent.bin"));
      POSTagger tagger = new POSTaggerME(posTaggerModel);

      Constants.print("\ntokens :");
      Constants.print(words);
      Constants.print(tagger.tag(words));

      Constants.print("\ntesting default lesk :");
      Lesk lesk = new Lesk();
      Constants.print(lesk.disambiguate(words, 6));

      Constants.print("\ntesting with null params :");
      lesk.setParams(null);
      Constants.print(lesk.disambiguate(words, 6));

      Constants.print("\ntesting with default params");
      lesk.setParams(new LeskParameters());
      Constants.print(lesk.disambiguate(words, 6));

      Constants.print("\ntesting with custom params :");
      LeskParameters leskParams = new LeskParameters();
      leskParams.leskType = LeskParameters.LESK_TYPE.LESK_BASIC_CTXT_WIN_BF;
      leskParams.win_b_size = 4;
      leskParams.depth = 3;
      lesk.setParams(leskParams);
      Constants.print(lesk.disambiguate(words, 6));

      /*
       * Constants.print("\ntesting with wrong params should throw exception :");
       * LeskParameters leskWrongParams = new LeskParameters();
       * leskWrongParams.depth = -1; lesk.setParams(leskWrongParams);
       * Constants.print(lesk.disambiguate(words, 6));
       */

    } catch (IOException e) {
      e.printStackTrace();
    }

  }

}
