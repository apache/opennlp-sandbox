/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.modelbuilder.v2.impls;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.modelbuilder.v2.Modelable;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 *
 * @author Owner
 */
public class ModelableImpl implements Modelable {

  private TokenizerModel tm;
  private TokenizerME wordBreaker;
  private String path = "c:\\temp\\opennlpmodels\\";
  private Set<String> annotatedSentences = new HashSet<String>();
  private Map<String, String> params = new HashMap<String, String>();

  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
    path=params.get("modelablepath");
  }

  @Override
  public String annotate(String sentence, String namedEntity, String entityType) {
    String annotation = sentence.replace(namedEntity, " <START:" + entityType + "> " + namedEntity + " <END> ");

    return annotation;
  }

  @Override
  public void writeAnnotatedSentences() {
    try {
      FileWriter writer = new FileWriter(path + "en-ner-person.train", false);

      for (String s : annotatedSentences) {
        writer.write(s.replace("\n", "").trim() + "\n");
      }
      writer.close();
    } catch (IOException ex) {
    }
  }

  @Override
  public Set<String> getAnnotatedSentences() {
    return annotatedSentences;
  }

  @Override
  public void setAnnotatedSentences(Set<String> annotatedSentences) {
    this.annotatedSentences = annotatedSentences;
  }

  @Override
  public void addAnnotatedSentence(String annotatedSentence) {
    if (annotatedSentence != null) {
      int before = annotatedSentences.size();
      annotatedSentences.add(annotatedSentence);
      if (annotatedSentences.size() > before) {
      }
    }
  }

  @Override
  public void buildModel(String entityType) {
    try {
      System.out.println("\tBuilding Model using " + annotatedSentences.size() + " annotations");
      System.out.println("\t\treading training data...");
      Charset charset = Charset.forName("UTF-8");
      ObjectStream<String> lineStream =
              new PlainTextByLineStream(new FileInputStream(path + "en-ner-person.train"), charset);
      ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream);

      TokenNameFinderModel model;
      model = NameFinderME.train("en", "person", sampleStream, null);
      sampleStream.close();
      OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(new File(path + "en-ner-person.train.model")));
      model.serialize(modelOut);
      if (modelOut != null) {
        modelOut.close();
      }
      System.out.println("\tmodel generated");
    } catch (Exception e) {
    }
  }

  @Override
  public TokenNameFinderModel getModel() {


    TokenNameFinderModel nerModel = null;
    try {
      nerModel = new TokenNameFinderModel(new FileInputStream(new File(path + "en-ner-person.train.model")));
    } catch (IOException ex) {
      Logger.getLogger(ModelableImpl.class.getName()).log(Level.SEVERE, null, ex);
    }
    return nerModel;
  }

  @Override
  public String[] tokenizeSentenceToWords(String sentence) {
    return sentence.split(" ");
//    try {
//      if (tm == null || wordBreaker == null) {
//        tm = new TokenizerModel(new FileInputStream(new File(path + "en-token.zip")));
//        wordBreaker = new TokenizerME(tm);
//      }
//    } catch (IOException ex) {
//    }
//    return wordBreaker.tokenize(sentence);
  }
}
