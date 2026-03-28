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
package opennlp.addons.modelbuilder.impls;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.addons.modelbuilder.Modelable;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Creates annotations, writes annotations to file, and creates a model and writes to a file.
 *
 * @see Modelable
 */
public class GenericModelableImpl implements Modelable {

  private Set<String> annotatedSentences = new HashSet<>();
  private BaseModelBuilderParams params;

  public GenericModelableImpl(BaseModelBuilderParams params) {
    if (params == null) {
      throw new IllegalArgumentException("BaseModelBuilderParams cannot be null!");
    }
    this.params = params;
  }
  
  @Override
  public void setParameters(BaseModelBuilderParams params) {
    this.params = params;
  }

  @Override
  public String annotate(String sentence, String namedEntity, String entityType) {
    return sentence.replace(namedEntity, " <START:" + entityType + "> " + namedEntity + " <END> ");
  }

  @Override
  public void writeAnnotatedSentences() {
    final Path p = params.getAnnotatedTrainingDataFile().toPath();
    try (Writer writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      for (String s : annotatedSentences) {
        writer.write(s.replace("\n", " ").trim() + "\n");
      }
    } catch (IOException ex) {
      ex.printStackTrace();
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
    annotatedSentences.add(annotatedSentence);
  }

  @Override
  public void buildModel(String entityType) {
    final InputStreamFactory factory;
    try {
      factory = new MarkableFileInputStreamFactory(params.getAnnotatedTrainingDataFile());
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error finding and reading the training data file!", e);
    }

    final TrainingParameters trainParams = TrainingParameters.defaultParams();

    TokenNameFinderModel model;
    try (ObjectStream<NameSample> samples =
                 new NameSampleDataStream(new PlainTextByLineStream(factory, StandardCharsets.UTF_8));
         OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(params.getModelFile()))) {

      System.out.println("\tBuilding Model using " + annotatedSentences.size() + " annotations");
      System.out.println("\t\treading training data...");
      model = NameFinderME.train("en", entityType, samples, trainParams, new TokenNameFinderFactory());
      model.serialize(modelOut);

      System.out.println("\tmodel generated");
    } catch (Exception e) {
      throw new RuntimeException("Error building model! " + e.getLocalizedMessage(), e);
    }
  }

  @Override
  public TokenNameFinderModel getModel() {
    TokenNameFinderModel nerModel = null;
    try {
      nerModel = new TokenNameFinderModel(params.getModelFile());
    } catch (IOException ex) {
      Logger.getLogger(GenericModelableImpl.class.getName()).log(Level.SEVERE, null, ex);
    }
    return nerModel;
  }

  @Override
  public String[] tokenizeSentenceToWords(String sentence) {
    return sentence.split(" ");
  }
}
