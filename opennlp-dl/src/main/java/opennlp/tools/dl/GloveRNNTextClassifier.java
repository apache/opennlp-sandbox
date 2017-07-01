/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.dl;

import org.apache.commons.io.FileUtils;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * This is a Multi Class Text Classifier that uses Glove embeddings to vectorize the text
 * and a LSTM RNN to classify the sequence of vectors.
 *
 * This class aimed to make a general purpose text classifier tool.
 * A common use case would be to tune it for the text Sentiment classification task.
 *
 * The Glove Vectors can be downloaded from https://nlp.stanford.edu/projects/glove/
 *
 * <br/>
 */
public class GloveRNNTextClassifier {
    private static final Logger LOG = LoggerFactory.getLogger(GloveRNNTextClassifier.class);

    private MultiLayerNetwork model;
    private GlobalVectors gloves;
    private DataReader trainSet;
    private DataReader validSet;

    private Args args;

    public GloveRNNTextClassifier(Args args) throws IOException {
        this.init(args);
    }

    public static class Args {

        @Option(name="-batchSize", depends = {"-trainDir"},
                usage = "Number of examples in minibatch. Applicable for training only.")
        int batchSize = 128;

        @Option(name="-nEpochs", depends = {"-trainDir"},
                usage = "Number of epochs (i.e. full passes over the training data) to train on." +
                " Applicable for training only.")
        int nEpochs = 2;

        @Option(name="-maxSeqLen", usage = "Max Sequence Length. Sequences longer than this will be truncated")
        int maxSeqLen = 256;    //Truncate text with length (# words) greater than this

        @Option(name="-vocabSize", usage = "Vocabulary Size.")
        int vocabSize = 20000;   //vocabulary size

        @Option(name="-nRNNUnits", depends = {"-trainDir"},
                usage = "Number of RNN cells to use. Applicable for training only.")
        int nRNNUnits = 128;

        @Option(name="-lr", aliases = "-learnRate", usage = "Learning Rate." +
                " Adjust it when the scores bounce to NaN or Infinity.")
        double learningRate = 2e-3;

        @Option(name="-glovesPath", required = true, usage = "Path to GloVe vectors file." +
                " Download and unzip from https://nlp.stanford.edu/projects/glove/")
        String glovesPath = null;

        @Option(name="-modelPath", required = true, usage = "Path to model file. " +
                "This will be used for serializing the model after the training phase." +
                "and also the model will be restored from here for prediction")
        String modelPath = null;

        @Option(name="-trainDir", usage = "Path to train data directory. Optional." +
                " Setting this value will take the system to training mode. ")
        String trainDir = null;

        @Option(name="-validDir", depends = {"-trainDir"}, usage = "Path to validation data directory. Optional." +
                " Applicable only when -trainDir is set.")
        String validDir = null;

        @Option(name="-labels", required = true, handler = StringArrayOptionHandler.class,
                usage = "Names of targets or labels separated by spaces. " +
                "The order of labels matters. Make sure to use the same sequence for training and predicting. " +
                "Also, these names should match subdirectory names of -trainDir and -validDir when those are " +
                        "applicable. \n Example -labels pos neg")
        List<String> labels = null;

        @Option(name="-files", handler = StringArrayOptionHandler.class,
                usage = "File paths (separated by space) to predict using the model.")
        List<String> filePaths = null;

        @Override
        public String toString() {
            return "Args{" +
                    "batchSize=" + batchSize +
                    ", nEpochs=" + nEpochs +
                    ", maxSeqLen=" + maxSeqLen +
                    ", vocabSize=" + vocabSize +
                    ", learningRate=" + learningRate +
                    ", nRNNUnits=" + nRNNUnits +
                    ", glovesPath='" + glovesPath + '\'' +
                    ", modelPath='" + modelPath + '\'' +
                    ", trainDir='" + trainDir + '\'' +
                    ", validDir='" + validDir + '\'' +
                    ", labels=" + labels +
                    '}';
        }
    }


    public void init(Args args) throws IOException {
        this.args = args;
        try (InputStream stream = new FileInputStream(args.glovesPath)) {
            this.gloves = new GlobalVectors(stream, args.vocabSize);
        }

        if (args.trainDir != null) {
            LOG.info("Training data from {}", args.trainDir);
            this.trainSet = new DataReader(args.trainDir, args.labels, this.gloves, args.batchSize, args.maxSeqLen);
            if (args.validDir != null) {
                LOG.info("Validation data from {}", args.validDir);
                this.validSet = new DataReader(args.validDir, args.labels, this.gloves, args.batchSize, args.maxSeqLen);
            }
            //create model
            this.model = this.createModel();
            // ready for training
        } else {
            //restore model
            LOG.info("Training data not set => Going to restore model from {}", args.modelPath);
            this.model = ModelSerializer.restoreMultiLayerNetwork(args.modelPath);
            //ready for prediction
        }
    }

    private MultiLayerNetwork createModel(){
        int totalOutcomes = this.trainSet.totalOutcomes();
        assert totalOutcomes >= 2;
        LOG.info("Number of classes " + totalOutcomes);

        //TODO: the below network params should be configurable from CLI or settings file
        //Set up network configuration
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(Updater.RMSPROP) // ADAM .adamMeanDecay(0.9).adamVarDecay(0.999)
                .rmsDecay(0.9)
                .regularization(true).l2(1e-5)
                .weightInit(WeightInit.XAVIER)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1.0)
                .learningRate(args.learningRate)
                .list()
                .layer(0, new GravesLSTM.Builder()
                        .nIn(gloves.getVectorSize())
                        .nOut(args.nRNNUnits)
                        .activation(Activation.RELU).build())
                .layer(1, new RnnOutputLayer.Builder()
                        .nIn(args.nRNNUnits)
                        .nOut(totalOutcomes)
                        .activation(Activation.SOFTMAX)
                        .lossFunction(LossFunctions.LossFunction.MCXENT)
                        .build())
                .pretrain(false)
                .backprop(true)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(1));
        return net;
    }

    public void train(){
        train(args.nEpochs, this.trainSet, this.validSet);
    }

    /**
     * Trains model
     * @param nEpochs number of epochs (i.e. iterations over the training dataset)
     * @param train training data set
     * @param validation validation data set for evaluation after each epoch.
     *                  Setting this to null will skip the evaluation
     */
    public void train(int nEpochs, DataReader train, DataReader validation){
        assert model != null;
        assert train != null;
        LOG.info("Starting training...\nTotal epochs={}, Training Size={}, Validation Size={}", nEpochs,
                train.totalExamples(), validation == null ? null : validation.totalExamples());
        for (int i = 0; i < nEpochs; i++) {
            model.fit(train);
            train.reset();
            LOG.info("Epoch {} complete", i);

            if (validation != null) {
                LOG.info("Starting evaluation");
                //Run evaluation. This is on 25k reviews, so can take some time
                Evaluation evaluation = new Evaluation();
                while (validation.hasNext()) {
                    DataSet t = validation.next();
                    INDArray features = t.getFeatureMatrix();
                    INDArray labels = t.getLabels();
                    INDArray inMask = t.getFeaturesMaskArray();
                    INDArray outMask = t.getLabelsMaskArray();
                    INDArray predicted = model.output(features, false, inMask, outMask);
                    evaluation.evalTimeSeries(labels, predicted, outMask);
                }
                validation.reset();
                LOG.info(evaluation.stats());
            }
        }
    }

    /**
     * Saves the model to specified path
     * @param path model path
     * @throws IOException
     */
    public void saveModel(String path) throws IOException {
        assert model != null;
        LOG.info("Saving the model at {}", path);
        ModelSerializer.writeModel(model, path, true);
    }

    /**
     * Predicts class probability of the text based on the state of model
     * @param text text to be classified
     * @return array of doubles, indices associated with indices of <code>this.args.labels</code>
     */
    public double[] predict(String text){

        INDArray seqFeatures = this.gloves.embed(text, this.args.maxSeqLen);
        INDArray networkOutput = this.model.output(seqFeatures);
        int timeSeriesLength = networkOutput.size(2);
        INDArray probsAtLastWord = networkOutput.get(NDArrayIndex.point(0),
                NDArrayIndex.all(), NDArrayIndex.point(timeSeriesLength - 1));

        double[] probs = new double[args.labels.size()];
        for (int i = 0; i < args.labels.size(); i++) {
            probs[i] = probsAtLastWord.getDouble(i);
        }
        return probs;
    }

    /**
     * <pre>
     *   # Download pre trained Glo-ves (this is a large file)
     *   wget http://nlp.stanford.edu/data/glove.6B.zip
     *   unzip glove.6B.zip -d glove.6B
     *
     *   # Download dataset
     *   wget http://ai.stanford.edu/~amaas/data/sentiment/aclImdb_v1.tar.gz
     *   tar xzf aclImdb_v1.tar.gz
     *
     *  mvn compile exec:java
     *    -Dexec.mainClass=edu.usc.irds.sentiment.analysis.dl.GloveRNNTextClassifier
     *    -Dexec.args="-glovesPath $HOME/work/datasets/glove.6B/glove.6B.100d.txt
     *    -labels pos neg -modelPath imdb-sentimodel.dat
     *    -trainDir=$HOME/work/datasets/aclImdb/train -lr 0.001"
     *
     * </pre>
     *
     */
    public static void main(String[] argss) throws CmdLineException, IOException {
        Args args = new Args();
        CmdLineParser parser = new CmdLineParser(args);
        try {
            parser.parseArgument(argss);
        } catch (CmdLineException e) {
            System.out.println(e.getMessage());
            e.getParser().printUsage(System.out);
            System.exit(1);
        }
        GloveRNNTextClassifier classifier = new GloveRNNTextClassifier(args);
        byte numOps = 0;
        if (classifier.trainSet != null) {
            numOps++;
            classifier.train();
            classifier.saveModel(args.modelPath);
        }

        if (args.filePaths != null && !args.filePaths.isEmpty()) {
            numOps++;
            System.out.println("Labels:" + args.labels);
            for (String filePath: args.filePaths) {
                File file = new File(filePath);
                String text = FileUtils.readFileToString(file);
                double[] probs = classifier.predict(text);
                System.out.println(">>" + filePath);
                System.out.println("Probabilities:" + Arrays.toString(probs));
            }
        }

        if (numOps == 0) {
            System.out.println("Provide -trainDir to train a model, -files to classify files");
            System.exit(2);
        }
    }
}
