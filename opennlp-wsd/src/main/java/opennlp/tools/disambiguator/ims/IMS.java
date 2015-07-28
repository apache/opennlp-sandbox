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
import java.util.zip.GZIPInputStream;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.FeaturesExtractor;
import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.WordToDisambiguate;
import opennlp.tools.disambiguator.DatasetsReader.SemcorReaderExtended;
import opennlp.tools.disambiguator.DatasetsReader.SensevalReader;

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

  /**
   * Sets the input parameters to the default ones
   * 
   * @throws InvalidParameterException
   */
  public IMS() {
    super();
    // Loader loader = new Loader();
    this.parameters = new IMSParameters();
    this.cg = parameters.createContextGenerator();
  }

  /**
   * Initializes the loader object and sets the input parameters
   * 
   * @param parameters
   *          The parameters to be used
   * @throws InvalidParameterException
   */
  public IMS(IMSParameters parameters) {
    super();
    this.parameters = parameters;
    this.cg = this.parameters.createContextGenerator();
  }

  /**
   * Returns that parameter settings of the IMS object.
   * 
   * @return the parameter settings
   */
  @Override
  public WSDParameters getParams() {
    return this.parameters;
  }

  /**
   * Returns that parameter settings of the IMS object. The returned parameters
   * are of type {@link IMSParameters}
   * 
   * @return the parameter settings
   */
  public IMSParameters getParameters() {
    return this.parameters;
  }

  /**
   * If the parameters are null, set the default ones. Otherwise, only set them
   * if they valid. Invalid parameters will return a exception (and set the
   * parameters to the default ones)
   * 
   * @param Input
   *          parameters
   * @throws InvalidParameterException
   */
  @Override
  public void setParams(WSDParameters parameters)
      throws InvalidParameterException {
    if (parameters == null) {
      this.parameters = new IMSParameters();
    } else {
      if (parameters.isValid()) {
        this.parameters = (IMSParameters) parameters;
      } else {
        this.parameters = new IMSParameters();
        throw new InvalidParameterException("wrong parameters");
      }
    }

  }

  /**
   * If the parameters are null, set the default ones. Otherwise, only set them
   * if they valid. Invalid parameters will return a exception (and set the
   * parameters to the default ones)
   * 
   * @param Input
   *          parameters
   * @throws InvalidParameterException
   */
  public void setParams(IMSParameters parameters)
      throws InvalidParameterException {
    if (parameters == null) {
      this.parameters = new IMSParameters();
    } else {
      if (parameters.isValid()) {
        this.parameters = parameters;
      } else {
        this.parameters = new IMSParameters();
        throw new InvalidParameterException("wrong parameters");
      }
    }
  }

  // Internal Methods
  private ArrayList<String> getAllSurroundingWords(String wordTag) {

    ArrayList<String> surrWords = new ArrayList<String>();

    BufferedReader br = null;

    File file = new File(IMSParameters.trainingDataDirectory + wordTag + ".sw");

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

  private void saveAllSurroundingWords(ArrayList<WTDIMS> trainingInstances,
      String wordTag) {

    ArrayList<String> surrWords = fExtractor
        .extractTrainingSurroundingWords(trainingInstances);

    File file = new File(IMSParameters.trainingDataDirectory + wordTag + ".sw");
    if (!file.exists()) {

      try {
        file.createNewFile();
      } catch (IOException e) {
        System.out
            .println("Unable to create the List of Surrounding Words file !");
      }
    }

    try {
      FileWriter fw = new FileWriter(file.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);

      for (String surrWord : surrWords) {
        bw.write(surrWord);
        bw.newLine();
      }

      bw.close();
    } catch (IOException e) {
      System.out
          .println("Unable to create the List of Surrounding Words file !");
      e.printStackTrace();
    }

    System.out.println("Done");

  }

  private void extractFeature(WTDIMS word) {

    fExtractor.extractIMSFeatures(word, this.parameters.getWindowSize(),
        this.parameters.getNgram());

  }

  private String[] getMostFrequentSense(WTDIMS wordToDisambiguate) {

    String word = wordToDisambiguate.getRawWord();
    POS pos = Constants.getPOS(wordToDisambiguate.getPosTag());

    if (pos != null) {

      WordPOS wordPOS = new WordPOS(word, pos);

      ArrayList<Synset> synsets = wordPOS.getSynsets();

      int size = synsets.size();

      String[] senses = new String[size];

      for (int i = 0; i < size; i++) {
        String senseKey = null;
        for (Word wd : synsets.get(i).getWords()) {
          if (wd.getLemma().equals(
              wordToDisambiguate.getRawWord().split("\\.")[0])) {
            try {
              senseKey = wd.getSenseKey();
            } catch (JWNLException e) {
              e.printStackTrace();
            }
            senses[i] = senseKey;
            break;
          }
        }

      }
      return senses;
    } else {
      System.out.println("The word has no definitions in WordNet !");
      return null;
    }

  }

  /**
   * Method for training a model
   * 
   * @param wordTag
   *          the word to disambiguate. It should be written in the format
   *          "word.p" (Exp: "write.v", "well.r", "smart.a", "go.v"
   * @param trainParams
   *          the parameters used for training
   * @param trainingInstances
   *          the training data in the format {@link WTDIMS}
   */
  public void train(String wordTag, TrainingParameters trainParams,
      ArrayList<WTDIMS> trainingInstances) {

    String wordTrainingbinFile = IMSParameters.trainingDataDirectory + wordTag
        + ".gz";

    ObjectStream<Event> IMSes = null;

    for (WTDIMS wtd : trainingInstances) {
      extractFeature(wtd);
    }

    saveAllSurroundingWords(trainingInstances, wordTag);

    ArrayList<String> surrWords = getAllSurroundingWords(wordTag);

    for (WTDIMS wtd : trainingInstances) {
      fExtractor.serializeIMSFeatures(wtd, surrWords);
    }

    ArrayList<Event> events = new ArrayList<Event>();

    for (WTDIMS wtd : trainingInstances) {

      String sense = wtd.getSenseIDs().get(0);

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

  /**
   * Load an existing model
   * 
   * @param trainedModel
   *          Name of the file of the already trained model
   * @return the model trained
   */
  public MaxentModel load(String trainedModel) {

    MaxentModel loadedMaxentModel = null;

    FileInputStream inputStream;
    try {
      inputStream = new FileInputStream(trainedModel);
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

    String trainingDataDirectory = IMSParameters.trainingDataDirectory;

    File file = new File(trainingDataDirectory);

    if (!file.exists()) {
      file.mkdirs();
    }

    WTDIMS word = new WTDIMS(inputText, inputWordIndex);
    fExtractor.extractIMSFeatures(word, this.parameters.getWindowSize(),
        this.parameters.getNgram());

    String wordTag = word.getWordTag();

    String wordTrainingbinFile = trainingDataDirectory + wordTag + ".gz";

    File bf = new File(wordTrainingbinFile);

    MaxentModel loadedMaxentModel = null;
    String outcome = "";

    if (bf.exists() && !bf.isDirectory()) {
      // If the trained model exists
      ArrayList<String> surrWords = getAllSurroundingWords(wordTag);
      fExtractor.serializeIMSFeatures(word, surrWords);

      loadedMaxentModel = load(wordTrainingbinFile);
      String[] context = cg.getContext(word);

      double[] outcomeProbs = loadedMaxentModel.eval(context);
      outcome = loadedMaxentModel.getBestOutcome(outcomeProbs);

    } else {
      // Depending on the source, go fetch the training data
      ArrayList<WTDIMS> trainingInstances = new ArrayList<WTDIMS>();
      switch (this.parameters.getSource().code) {
      case 1: {
        SemcorReaderExtended sReader = new SemcorReaderExtended();
        for (WordToDisambiguate ti : sReader.getSemcorData(wordTag)) {
          WTDIMS imsIT = new WTDIMS(ti);
          extractFeature(imsIT);
          trainingInstances.add(imsIT);
        }
        break;
      }

      case 2: {
        SensevalReader sReader = new SensevalReader();
        for (WordToDisambiguate ti : sReader.getSensevalData(wordTag)) {
          WTDIMS imsIT = (WTDIMS) ti;
          extractFeature(imsIT);
          trainingInstances.add(imsIT);
        }
        break;
      }

      case 3: {
        // TODO check the case when the user selects his own data set (make an
        // interface to collect training data)
        break;
      }
      }

      if (!trainingInstances.isEmpty()) {

        train(wordTag, null, trainingInstances);

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

      // System.out.println("The sense is [" + outcome + "] : " /*+
      // Loader.getDictionary().getWordBySenseKey(outcome.split("%")[1]).getSynset().getGloss()*/);

      outcome = "WordNet " + wordTag.split("\\.")[0] + "%" + outcome;

      String[] s = { outcome };

      return s;

    } else {
      // if no training data exist
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

}
