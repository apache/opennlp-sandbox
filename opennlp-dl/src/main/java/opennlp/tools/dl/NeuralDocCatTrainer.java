package opennlp.tools.dl;

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
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;


/**
 * This class provides functionality to construct and train neural networks that can be used for
 * {@link opennlp.tools.doccat.DocumentCategorizer}
 *
 * @see NeuralDocCat
 * @see NeuralDocCatModel
 * @author Thamme Gowda (thammegowda@apache.org)
 */
public class NeuralDocCatTrainer {

    public static class Args {

        @Option(name = "-batchSize", usage = "Number of examples in minibatch")
        int batchSize = 128;

        @Option(name = "-nEpochs", usage = "Number of epochs (i.e. full passes over the training data) to train on." +
                " Applicable for training only.")
        int nEpochs = 2;

        @Option(name = "-maxSeqLen", usage = "Max Sequence Length. Sequences longer than this will be truncated")
        int maxSeqLen = 256;    //Truncate text with length (# words) greater than this

        @Option(name = "-vocabSize", usage = "Vocabulary Size.")
        int vocabSize = 20000;   //vocabulary size

        @Option(name = "-nRNNUnits", usage = "Number of RNN cells to use.")
        int nRNNUnits = 128;

        @Option(name = "-lr", aliases = "-learnRate", usage = "Learning Rate." +
                " Adjust it when the scores bounce to NaN or Infinity.")
        double learningRate = 2e-3;

        @Option(name = "-glovesPath", required = true, usage = "Path to GloVe vectors file." +
                " Download and unzip from https://nlp.stanford.edu/projects/glove/")
        String glovesPath = null;

        @Option(name = "-modelPath", required = true, usage = "Path to model file. " +
                "This will be used for serializing the model after the training phase." )
        String modelPath = null;

        @Option(name = "-trainDir", required = true, usage = "Path to train data directory." +
                " Setting this value will take the system to training mode. ")
        String trainDir = null;

        @Option(name = "-validDir", usage = "Path to validation data directory. Optional.")
        String validDir = null;

        @Option(name = "-labels", required = true, handler = StringArrayOptionHandler.class,
                usage = "Names of targets or labels separated by spaces. " +
                        "The order of labels matters. Make sure to use the same sequence for training and predicting. " +
                        "Also, these names should match subdirectory names of -trainDir and -validDir when those are " +
                        "applicable. \n Example -labels pos neg")
        List<String> labels = null;

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

    private static final Logger LOG = LoggerFactory.getLogger(NeuralDocCatTrainer.class);

    private NeuralDocCatModel model;
    private Args args;
    private DataReader trainSet;
    private DataReader validSet;


    public NeuralDocCatTrainer(Args args) throws IOException {
        this.args = args;
        GlobalVectors gloves;
        MultiLayerNetwork network;

        try (InputStream stream = new FileInputStream(args.glovesPath)) {
            gloves = new GlobalVectors(stream, args.vocabSize);
        }

        LOG.info("Training data from {}", args.trainDir);
        this.trainSet = new DataReader(args.trainDir, args.labels, gloves, args.batchSize, args.maxSeqLen);
        if (args.validDir != null) {
            LOG.info("Validation data from {}", args.validDir);
            this.validSet = new DataReader(args.validDir, args.labels, gloves, args.batchSize, args.maxSeqLen);
        }

        //create network
        network = this.createNetwork(gloves.getVectorSize());
        this.model = new NeuralDocCatModel(network, gloves, args.labels, args.maxSeqLen);
    }

    public MultiLayerNetwork createNetwork(int vectorSize) {
        int totalOutcomes = this.trainSet.totalOutcomes();
        assert totalOutcomes >= 2;
        LOG.info("Number of classes " + totalOutcomes);

        //TODO: the below network params should be configurable from CLI or settings file
        //Set up network configuration
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new RmsProp(args.learningRate)) // ADAM .adamMeanDecay(0.9).adamVarDecay(0.999)
                .l2(1e-5)
                .weightInit(WeightInit.XAVIER)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1.0)
                .list()
                .layer(0, new GravesLSTM.Builder()
                        .nIn(vectorSize)
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

    public void train() {
        train(args.nEpochs, this.trainSet, this.validSet);
    }

    /**
     * Trains model
     *
     * @param nEpochs    number of epochs (i.e. iterations over the training dataset)
     * @param train      training data set
     * @param validation validation data set for evaluation after each epoch.
     *                   Setting this to null will skip the evaluation
     */
    public void train(int nEpochs, DataReader train, DataReader validation) {
        assert model != null;
        assert train != null;
//        LOG.info("Starting training...\nTotal epochs={}, Training Size={}, Validation Size={}", nEpochs,
//                train.(), validation == null ? null : validation.totalExamples());
        for (int i = 0; i < nEpochs; i++) {
            model.getNetwork().fit(train);
            train.reset();
            LOG.info("Epoch {} complete", i);

            if (validation != null) {
                LOG.info("Starting evaluation");
                //Run evaluation. This is on 25k reviews, so can take some time
                Evaluation evaluation = new Evaluation();
                while (validation.hasNext()) {
                    DataSet t = validation.next();
                    INDArray features = t.getFeatures();
                    INDArray labels = t.getLabels();
                    INDArray inMask = t.getFeaturesMaskArray();
                    INDArray outMask = t.getLabelsMaskArray();
                    INDArray predicted = this.model.getNetwork().output(features, false, inMask, outMask);
                    evaluation.evalTimeSeries(labels, predicted, outMask);
                }
                validation.reset();
                LOG.info(evaluation.stats());
            }
        }
    }

    /**
     * Saves the model to specified path
     *
     * @param path model path
     * @throws IOException
     */
    public void saveModel(String path) throws IOException {
        assert model != null;
        LOG.info("Saving the model at {}", path);
        try (OutputStream stream = new FileOutputStream(path)) {
            model.saveModel(stream);
        }
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
     *    -Dexec.mainClass=edu.usc.irds.sentiment.analysis.dl.NeuralDocCat
     *    -Dexec.args="-glovesPath $HOME/work/datasets/glove.6B/glove.6B.100d.txt
     *    -labels pos neg -modelPath imdb-sentiment-neural-model.zip
     *    -trainDir=$HOME/work/datasets/aclImdb/train -lr 0.001"
     *
     * </pre>
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
        NeuralDocCatTrainer classifier = new NeuralDocCatTrainer(args);
        classifier.train();
        classifier.saveModel(args.modelPath);
    }

}
