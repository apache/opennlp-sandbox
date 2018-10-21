package opennlp.tools.dl;/*
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

// https://github.com/deeplearning4j/dl4j-examples/blob/master/dl4j-examples/src/main/java/org/deeplearning4j/examples/recurrent/word2vecsentiment/Word2VecSentimentRNN.java
public class NameFinderDL implements TokenNameFinder {

  private final MultiLayerNetwork network;
  private final WordVectors wordVectors;
  private int windowSize;
  private String[] labels;

  public NameFinderDL(MultiLayerNetwork network, WordVectors wordVectors, int windowSize,
                      String[] labels) {
    this.network = network;
    this.wordVectors = wordVectors;
    this.windowSize = windowSize;
    this.labels = labels;
  }

  static List<INDArray> mapToFeatureMatrices(WordVectors wordVectors, String[] tokens, int windowSize) {

    List<INDArray> matrices = new ArrayList<>();

    // TODO: Dont' hard code word vector dimension ...

    for (int i = 0; i < tokens.length; i++) {
      INDArray features = Nd4j.create(1, 300, windowSize);
      for (int vectorIndex = 0; vectorIndex < windowSize; vectorIndex++) {
        int tokenIndex = i + vectorIndex - ((windowSize - 1) / 2);
        if (tokenIndex >= 0 && tokenIndex < tokens.length) {
          String token = tokens[tokenIndex];
          double[] wv = wordVectors.getWordVector(token);
          if (wv != null) {
            INDArray vector = wordVectors.getWordVectorMatrix(token);
            features.put(new INDArrayIndex[]{NDArrayIndex.point(0), NDArrayIndex.all(),
                NDArrayIndex.point(vectorIndex)}, vector);
          }
        }
      }
      matrices.add(features);
    }

    return matrices;
  }

  static List<INDArray> mapToLabelVectors(NameSample sample, int windowSize, String[] labelStrings) {

    Map<String, Integer> labelToIndex = IntStream.range(0, labelStrings.length).boxed()
        .collect(Collectors.toMap(i -> labelStrings[i], i -> i));

    List<INDArray> vectors = new ArrayList<INDArray>();

    for (int i = 0; i < sample.getSentence().length; i++) {
      // encode the outcome as one-hot-representation
      String outcomes[] =
          new BioCodec().encode(sample.getNames(), sample.getSentence().length);

      INDArray labels = Nd4j.create(1, labelStrings.length, windowSize);
      labels.putScalar(new int[]{0, labelToIndex.get(outcomes[i]), windowSize - 1}, 1.0d);
      vectors.add(labels);
    }

    return vectors;
  }

  private static int max(INDArray array) {
    int best = 0;
    for (int i = 0; i < array.size(0); i++) {
      if (array.getDouble(i) > array.getDouble(best)) {
        best = i;
      }
    }
    return  best;
  }

  @Override
  public Span[] find(String[] tokens) {
    List<INDArray> featureMartrices = mapToFeatureMatrices(wordVectors, tokens, windowSize);

    String[] outcomes = new String[tokens.length];
    for (int i = 0; i < tokens.length; i++) {
      INDArray predictionMatrix = network.output(featureMartrices.get(i), false);
      INDArray outcomeVector = predictionMatrix.get(NDArrayIndex.point(0), NDArrayIndex.all(),
          NDArrayIndex.point(windowSize - 1));

      outcomes[i] = labels[max(outcomeVector)];
    }

    // Delete invalid spans ...
    for (int i = 0; i < outcomes.length; i++) {
      if (outcomes[i].endsWith("cont") && (i == 0 || "other".equals(outcomes[i - 1]))) {
        outcomes[i] = "other";
      }
    }

    return new BioCodec().decode(Arrays.asList(outcomes));
  }

  @Override
  public void clearAdaptiveData() {
  }

  public static MultiLayerNetwork train(WordVectors wordVectors, ObjectStream<NameSample> samples,
                                        int epochs, int windowSize, String[] labels) throws IOException {
    int vectorSize = 300;
    int layerSize = 256;

    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
        .updater(new RmsProp(0.01)).l2(0.001)
        .weightInit(WeightInit.XAVIER)
        .list()
        .layer(0, new GravesLSTM.Builder().nIn(vectorSize).nOut(layerSize)
            .activation(Activation.TANH).build())
        .layer(1, new RnnOutputLayer.Builder().activation(Activation.SOFTMAX)
            .lossFunction(LossFunctions.LossFunction.MCXENT).nIn(layerSize).nOut(3).build())
        .pretrain(false).backprop(true).build();

    MultiLayerNetwork net = new MultiLayerNetwork(conf);
    net.init();
    net.setListeners(new ScoreIterationListener(5));

    // TODO: Extract labels on the fly from the data

    DataSetIterator train = new NameSampleDataSetIterator(samples, wordVectors, windowSize, labels);

    System.out.println("Starting training");

    for (int i = 0; i < epochs; i++) {
      net.fit(train);
      train.reset();
      System.out.println(String.format("Finished epoch %d", i));
    }

    return net;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.out.println("Usage: trainFile testFile gloveTxt");
      return;
    }

    String[] labels = new String[] {
        "default-start", "default-cont", "other"
    };

    System.out.print("Loading vectors ... ");
    WordVectors wordVectors = WordVectorSerializer.loadTxtVectors(
        new File(args[2]));
    System.out.println("Done");

    int windowSize = 5;

    MultiLayerNetwork net = train(wordVectors, new NameSampleDataStream(new PlainTextByLineStream(
        new MarkableFileInputStreamFactory(new File(args[0])), StandardCharsets.UTF_8)), 1, windowSize, labels);

    ObjectStream<NameSample> evalStream = new NameSampleDataStream(new PlainTextByLineStream(
        new MarkableFileInputStreamFactory(
            new File(args[1])), StandardCharsets.UTF_8));

    NameFinderDL nameFinder = new NameFinderDL(net, wordVectors, windowSize, labels);

    System.out.print("Evaluating ... ");
    TokenNameFinderEvaluator nameFinderEvaluator = new TokenNameFinderEvaluator(nameFinder);
    nameFinderEvaluator.evaluate(evalStream);

    System.out.println("Done");

    System.out.println();
    System.out.println();
    System.out.println("Results");

    System.out.println(nameFinderEvaluator.getFMeasure().toString());
  }
}
