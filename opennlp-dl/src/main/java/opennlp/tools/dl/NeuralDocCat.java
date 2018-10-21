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

import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An implementation of {@link DocumentCategorizer} using Neural Networks.
 * This class provides prediction functionality from the model of {@link NeuralDocCatTrainer}.
 *
 */
public class NeuralDocCat implements DocumentCategorizer {

    private static final Logger LOG = LoggerFactory.getLogger(NeuralDocCat.class);

    private NeuralDocCatModel model;

    public NeuralDocCat(NeuralDocCatModel model) {
        this.model = model;
    }

    @Override
    public double[] categorize(String[] tokens) {
        return categorize(tokens, Collections.emptyMap());
    }

    @Override
    public double[] categorize(String[] text, Map<String, Object> extraInformation) {
        INDArray seqFeatures = this.model.getGloves().embed(text, this.model.getMaxSeqLen());

        INDArray networkOutput = this.model.getNetwork().output(seqFeatures);
        long timeSeriesLength = networkOutput.size(2);
        INDArray probsAtLastWord = networkOutput.get(NDArrayIndex.point(0),
                NDArrayIndex.all(), NDArrayIndex.point(timeSeriesLength - 1));

        int nLabels = this.model.getLabels().size();
        double[] probs = new double[nLabels];
        for (int i = 0; i < nLabels; i++) {
            probs[i] = probsAtLastWord.getDouble(i);
        }
        return probs;
    }

    @Override
    public String getBestCategory(double[] outcome) {
        int maxIdx = 0;
        double maxProb = outcome[0];
        for (int i = 1; i < outcome.length; i++) {
            if (outcome[i] > maxProb) {
                maxIdx = i;
                maxProb = outcome[i];
            }
        }
        return model.getLabels().get(maxIdx);
    }

    @Override
    public int getIndex(String category) {
        return model.getLabels().indexOf(category);
    }

    @Override
    public String getCategory(int index) {
        return model.getLabels().get(index);
    }

    @Override
    public int getNumberOfCategories() {
        return model.getLabels().size();
    }


    @Override
    public String getAllResults(double[] results) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Map<String, Double> scoreMap(String[] text) {
        double[] scores = categorize(text);
        Map<String, Double> result = new HashMap<>();
        for (int i = 0; i < scores.length; i++) {
            result.put(model.getLabels().get(i), scores[i]);

        }
        return result;
    }

    @Override
    public SortedMap<Double, Set<String>> sortedScoreMap(String[] text) {
        throw new NotImplementedException("Not implemented");
    }

    public static void main(String[] argss) throws CmdLineException, IOException {
        class Args {

            @Option(name = "-model", required = true, usage = "Path to NeuralDocCatModel stored file")
            String modelPath;

            @Option(name = "-files", required = true, usage = "One or more document paths whose category is " +
                    "to be predicted by the model")
            List<File> files;
        }

        Args args = new Args();
        CmdLineParser parser = new CmdLineParser(args);
        try {
            parser.parseArgument(argss);
        } catch (CmdLineException e) {
            System.out.println(e.getMessage());
            e.getParser().printUsage(System.out);
            System.exit(1);
        }

        NeuralDocCatModel model = NeuralDocCatModel.loadModel(args.modelPath);
        NeuralDocCat classifier = new NeuralDocCat(model);

        System.out.println("Labels:" + model.getLabels());
        Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;

        for (File file: args.files) {
            String text = FileUtils.readFileToString(file);
            String[] tokens = tokenizer.tokenize(text.toLowerCase());
            double[] probs = classifier.categorize(tokens);
            System.out.println(">>" + file);
            System.out.println("Probabilities:" + Arrays.toString(probs));
        }

    }
}
