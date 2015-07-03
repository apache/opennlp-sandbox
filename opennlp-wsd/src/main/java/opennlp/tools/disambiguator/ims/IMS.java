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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidParameterException;
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
import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WSDisambiguator;

/**
 * Implementation of the <b>It Makes Sense</b> approach originally proposed in
 * Senseval-3. The approach relies on the extraction of textual and
 * PoS-tag-based features from the sentences surrounding the word to
 * disambiguate. 3 main families of features are extracted:
 * <ul>
 * <li>PoS-tags of the surrounding words</li>
 * <li>Local collocations</li>
 * <li>Surrounding words</li>
 * </ul>
 * check {@link https://www.comp.nus.edu.sg/~nght/pubs/ims.pdf} for details
 * about this approach
 */
public class IMS implements WSDisambiguator {

  public IMSParameters parameters;

  private final IMSContextGenerator cg;

  private FeaturesExtractor fExtractor = new FeaturesExtractor();
  private DataExtractor dExtractor = new DataExtractor();

  public IMS() {
    super();
    this.parameters = new IMSParameters();
    ;
    this.cg = parameters.createContextGenerator();
  }

  public IMS(IMSParameters parameters) {
    super();
    this.parameters = parameters;
    this.cg = this.parameters.createContextGenerator();
  }

  // Internal Methods
  private String getTrainingFileName(WTDIMS wtd) {

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

  private void saveAllSurroundingWords(ArrayList<WTDIMS> trainingData,
      String wordTag) {

    ArrayList<String> surrWords = fExtractor
        .extractTrainingSurroundingWords(trainingData);

    File file = new File(parameters.getTrainingDataDirectory() + wordTag
        + ".sw");
    if (!file.exists()) {
      try {

        file.createNewFile();

        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);

        for (String surrWord : surrWords) {
          bw.write(surrWord);
          bw.newLine();
        }

        bw.close();

        System.out.println("Done");

      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  private ArrayList<String> getAllSurroundingWords(String wordTag) {

    ArrayList<String> surrWords = new ArrayList<String>();

    BufferedReader br = null;

    File file = new File(parameters.getTrainingDataDirectory() + wordTag
        + ".sw");

    if (file.exists()) {

      try {
        br = new BufferedReader(new FileReader(file));

        String line = br.readLine();
        while (line != null) {
          line = br.readLine();
          if (!surrWords.contains(line)) {
            surrWords.add(line);
          }
        }
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (br != null) {
          try {
            br.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    return surrWords;

  }

  private ArrayList<WTDIMS> extractTrainingData(String wordTrainingXmlFile,
      HashMap<String, ArrayList<DictionaryInstance>> senses) {

    /**
     * word tag has to be in the format "word.t" (e.g., "activate.v", "smart.a",
     * etc.)
     */

    ArrayList<WTDIMS> trainingData = dExtractor
        .extractWSDInstances(wordTrainingXmlFile);

    for (WTDIMS word : trainingData) {
      for (String senseId : word.getSenseIDs()) {
        for (String dictKey : senses.keySet()) {
          for (DictionaryInstance instance : senses.get(dictKey)) {
            if (senseId.equals(instance.getId())) {
              word.setSense(Integer.parseInt(dictKey.split("_")[1]));
              break;
            }
          }
        }
      }
    }

    return trainingData;
  }

  private void extractFeature(WTDIMS word) {

    fExtractor.extractIMSFeatures(word, this.parameters.getWindowSize(),
        this.parameters.getNgram());

  }

  private HashMap<String, String> getWordDictionaryInstance(WTDIMS wtd) {

    String dict = parameters.getDict();
    String map = parameters.getMap();

    return dExtractor.getDictionaryInstance(dict, map,
        this.getTrainingFileName(wtd));

  }

  private String[] getMostFrequentSense(WTDIMS wordToDisambiguate) {

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
   * Method for training a model
   * 
   * @param wordTag
   *          : the word to disambiguate. It should be written in the format
   *          "word.p" (Exp: "write.v", "well.r", "smart.a", "go.v"
   * @param trainParams
   *          : the parameters used for training
   */
  public void train(String wordTag, TrainingParameters trainParams) {

    String dict = parameters.getDict();
    String map = parameters.getMap();

    String wordTrainingxmlFile = parameters.getRawDataDirectory() + wordTag
        + ".xml";
    String wordTrainingbinFile = parameters.getTrainingDataDirectory()
        + wordTag + ".gz";

    File bf = new File(wordTrainingxmlFile);

    ObjectStream<Event> IMSes = null;

    if (bf.exists() && !bf.isDirectory()) {

      HashMap<String, ArrayList<DictionaryInstance>> senses = dExtractor
          .extractWordSenses(dict, map, wordTag);

      ArrayList<WTDIMS> instances = extractTrainingData(wordTrainingxmlFile,
          senses);

      for (WTDIMS wtd : instances) {
        extractFeature(wtd);
      }

      saveAllSurroundingWords(instances, wordTag);

      for (WTDIMS wtd : instances) {
        extractFeature(wtd);
      }

      ArrayList<String> surrWords = getAllSurroundingWords(wordTag);

      for (WTDIMS wtd : instances) {
        fExtractor.serializeIMSFeatures(wtd, surrWords);
      }

      ArrayList<Event> events = new ArrayList<Event>();

      for (WTDIMS wtd : instances) {

        int sense = wtd.getSense();

        String[] context = cg.getContext(wtd);

        Event ev = new Event(sense + "", context);

        events.add(ev);

        IMSes = ObjectStreamUtils.createObjectStream(events);

      }

      DataIndexer indexer;
      try {
        indexer = new OnePassDataIndexer((ObjectStream<Event>) IMSes);
        MaxentModel trainedMaxentModel = GIS.trainModel(200, indexer);
        File outFile = new File(wordTrainingbinFile);
        AbstractModelWriter writer = new SuffixSensitiveGISModelWriter(
            (AbstractModel) trainedMaxentModel, outFile);
        writer.persist();

      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  /**
   * Load an existing model
   * 
   * @param binFile
   *          : Location of the already trained model
   * @return the model trained
   */
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

  /**
   * The disambiguation method for a single word
   * 
   * @param inputText
   *          : the text containing the word to disambiguate
   * @param inputWordIndex
   *          : the index of the word to disambiguate
   */
  @Override
  public String[] disambiguate(String[] inputText, int inputWordIndex) {

    String rawDataDirectory = this.parameters.getRawDataDirectory();
    String trainingDataDirectory = this.parameters.getTrainingDataDirectory();

    WTDIMS word = new WTDIMS(inputText, inputWordIndex);
    fExtractor.extractIMSFeatures(word, this.parameters.getWindowSize(),
        this.parameters.getNgram());

    String wordTag = getTrainingFileName(word);

    String wordTrainingxmlFile = rawDataDirectory + wordTag + ".xml";
    String wordTrainingbinFile = trainingDataDirectory + wordTag + ".gz";

    File bf = new File(wordTrainingbinFile);

    MaxentModel loadedMaxentModel = null;
    String outcome = "";
    if (bf.exists() && !bf.isDirectory()) {
      // if the model file exists already
      // System.out.println("the model file was found !");
      ArrayList<String> surrWords = getAllSurroundingWords(wordTag);
      fExtractor.serializeIMSFeatures(word, surrWords);

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
        ArrayList<String> surrWords = getAllSurroundingWords(wordTag);

        fExtractor.serializeIMSFeatures(word, surrWords);

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

  /**
   * The disambiguation method for a span of words
   * 
   * @param inputText
   *          : the text containing the word to disambiguate
   * @param inputWordSpans
   *          : the span of words to disambiguate
   */
  @Override
  public String[][] disambiguate(String[] tokenizedContext,
      Span[] ambiguousTokenIndexSpans) {
    // TODO Auto-generated method stub
    return null;
  }

  // TODO fix the conflicts in parameters with Anthony's code
  @Override
  public WSDParameters getParams() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setParams(WSDParameters params) throws InvalidParameterException {
    // TODO Auto-generated method stub

  }

}
