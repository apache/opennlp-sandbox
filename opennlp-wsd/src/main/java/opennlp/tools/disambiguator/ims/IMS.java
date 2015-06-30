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

package opennlp.tools.disambiguator.ims;

import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.ml.maxent.io.GISModelReader;
import opennlp.tools.ml.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelWriter;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.DataReader;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.OnePassDataIndexer;
import opennlp.tools.ml.model.PlainTextFileDataReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import opennlp.tools.disambiguator.DictionaryInstance;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.DataExtractor;
import opennlp.tools.disambiguator.FeaturesExtractor;
import opennlp.tools.disambiguator.PreProcessor;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WSDisambiguator;

public class IMS implements WSDisambiguator {

  // private MaxentModel model;
  private IMSFactory factory;

  private final IMSContextGenerator cg;

  private FeaturesExtractor fExtractor = new FeaturesExtractor();
  private DataExtractor dExtractor = new DataExtractor();

  /**
   * PARAMETERS
   */

  private int windowSize;
  private int ngram;

  /**
   * Constructors
   */

  public IMS() {
    super();
    windowSize = 3;
    ngram = 2;

    IMSFactory factory = new IMSFactory();
    this.factory = factory;
    this.cg = factory.createContextGenerator();
  }

  public IMS(int windowSize, int ngram) {
    super();
    this.windowSize = windowSize;
    this.ngram = ngram;

    IMSFactory factory = new IMSFactory();
    this.factory = factory;
    this.cg = factory.createContextGenerator();
  }

  /**
   * INTERNAL METHODS
   */

  protected HashMap<Integer, WTDIMS> extractTrainingData(
      String wordTrainingxmlFile,
      HashMap<String, ArrayList<DictionaryInstance>> senses) {

    /**
     * word tag has to be in the format "word.t" (e.g., "activate.v", "smart.a",
     * etc.)
     */

    HashMap<Integer, WTDIMS> trainingData = dExtractor
        .extractWSDInstances(wordTrainingxmlFile);

    // HashMap<Integer, WTDIMS> trainingData =
    // dExtractor.extractWSDInstances(wordTrainingxmlFile);

    for (Integer key : trainingData.keySet()) {
      for (String senseId : trainingData.get(key).getSenseID()) {
        for (String dictKey : senses.keySet()) {
          for (DictionaryInstance instance : senses.get(dictKey)) {
            if (senseId.equals(instance.getId())) {
              trainingData.get(key).setSense(
                  Integer.parseInt(dictKey.split("_")[1]));
              break;
            }
          }
        }
      }
    }

    return trainingData;
  }

  protected void extractFeature(HashMap<Integer, WTDIMS> words) {

    for (Integer key : words.keySet()) {

      fExtractor.extractIMSFeatures(words.get(key), windowSize, ngram);

    }

  }

  protected String getTrainingFile(WTDIMS wtd) {

    String wordBaseForm = PreProcessor
        .lemmatize(wtd.getWord(), wtd.getPosTag());

    String ref = "";

    if (Constants.getPOS(wtd.getPosTag()).equals(POS.VERB)) {
      ref = wordBaseForm + ".v";
    } else if (Constants.getPOS(wtd.getPosTag()).equals(POS.NOUN)) {
      ref = wordBaseForm + ".n";
    } else if (Constants.getPOS(wtd.getPosTag()).equals(POS.ADJECTIVE)) {
      ref = wordBaseForm + ".a";
    } else if (Constants.getPOS(wtd.getPosTag()).equals(POS.ADVERB)) {
      ref = wordBaseForm + ".r";
    } else {

    }

    return ref;
  }

  protected HashMap<String, String> getWordDictionaryInstance(WTDIMS wtd) {

    String dict = factory.getDict();
    String map = factory.getMap();

    return dExtractor.getDictionaryInstance(dict, map,
        this.getTrainingFile(wtd));

  }

  protected String[] getMostFrequentSense(WTDIMS wordToDisambiguate) {

    String word = wordToDisambiguate.getRawWord();
    POS pos = Constants.getPOS(wordToDisambiguate.getPosTag());

    WordPOS wordPOS = new WordPOS(word, pos);

    ArrayList<Synset> synsets = wordPOS.getSynsets();

    int size = synsets.size();

    String[] senses = new String[size];

    for (int i = 0; i < size; i++) {
      senses[i] = synsets.get(i).getGloss();
    }

    return senses;

  }

  /**
   * PUBLIC METHODS
   */

  public void train(String wordTag, TrainingParameters trainParams) {

    String rawDataDirectory = factory.getRawDataDirectory();
    String trainingDataDirectory = factory.getTrainingDataDirectory();
    String dict = factory.getDict();
    String map = factory.getMap();

    String wordTrainingxmlFile = rawDataDirectory + wordTag + ".xml";
    String wordTrainingbinFile = trainingDataDirectory + wordTag + ".gz";

    File bf = new File(wordTrainingxmlFile);

    ObjectStream IMSes = null;

    if (bf.exists() && !bf.isDirectory()) {

      HashMap<String, ArrayList<DictionaryInstance>> senses = dExtractor
          .extractWordSenses(dict, map, wordTag);

      HashMap<Integer, WTDIMS> instances = extractTrainingData(
          wordTrainingxmlFile, senses);

      extractFeature(instances);

      ArrayList<Event> events = new ArrayList<Event>();

      for (int key : instances.keySet()) {

        int sense = instances.get(key).getSense();

        String[] context = cg.getContext(instances.get(key));

        Event ev = new Event(sense + "", context);

        events.add(ev);

        // Collection collEvents = events;

        IMSes = ObjectStreamUtils.createObjectStream(events);

      }

      DataIndexer indexer;
      try {
        indexer = new OnePassDataIndexer((ObjectStream<Event>) IMSes);
        MaxentModel trainedMaxentModel = GIS.trainModel(100, indexer);
        File outFile = new File(wordTrainingbinFile);
        AbstractModelWriter writer = new SuffixSensitiveGISModelWriter(
            (AbstractModel) trainedMaxentModel, outFile);
        writer.persist();

      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  public MaxentModel load(String binFile) {

    MaxentModel loadedMaxentModel = null;

    FileInputStream inputStream;
    try {
      inputStream = new FileInputStream(binFile);
      InputStream decodedInputStream = new GZIPInputStream(inputStream);
      DataReader modelReader = new PlainTextFileDataReader(decodedInputStream);
      loadedMaxentModel = new GISModelReader(modelReader).getModel();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return loadedMaxentModel;
  }

  @Override
  public String[] disambiguate(String[] inputText, int inputWordIndex) {

    String rawDataDirectory = factory.getRawDataDirectory();
    String trainingDataDirectory = factory.getTrainingDataDirectory();

    WTDIMS word = new WTDIMS(inputText, inputWordIndex);
    fExtractor.extractIMSFeatures(word, windowSize, ngram);

    String wordTag = getTrainingFile(word);

    String wordTrainingxmlFile = rawDataDirectory + wordTag + ".xml";
    String wordTrainingbinFile = trainingDataDirectory + wordTag + ".gz";

    File bf = new File(wordTrainingbinFile);

    MaxentModel loadedMaxentModel = null;
    String outcome = "";
    if (bf.exists() && !bf.isDirectory()) {
      // if the model file exists already
      // System.out.println("the model file was found !");
      loadedMaxentModel = load(wordTrainingbinFile);
      String[] context = cg.getContext(word);

      double[] outcomeProbs = loadedMaxentModel.eval(context);
      outcome = loadedMaxentModel.getBestOutcome(outcomeProbs);

    } else {
      bf = new File(wordTrainingxmlFile);
      if (bf.exists() && !bf.isDirectory()) {
        // if the xml file exists already
        // System.out.println("the xml file was found !");
        train(wordTag, null);
        bf = new File(wordTrainingbinFile);
        loadedMaxentModel = load(wordTrainingbinFile);
        String[] context = cg.getContext(word);

        double[] outcomeProbs = loadedMaxentModel.eval(context);
        outcome = loadedMaxentModel.getBestOutcome(outcomeProbs);
      }
    }

    if (!outcome.equals("")) {

      HashMap<String, String> senses = getWordDictionaryInstance(word);

      String index = wordTag + "_" + outcome;

      String[] s = { senses.get(index) };

      return s;

    } else {
      // if no training data exist
      // System.out.println("No training data available, the MFS is returned !");
      String[] s = getMostFrequentSense(word);
      return s;
    }

  }

  @Override
  public String[] disambiguate(String[] inputText, Span[] inputWordSpans) {
    // TODO Auto-generated method stub
    return null;
  }

}
