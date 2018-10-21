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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * This class provides a reader capable of reading training and test datasets from file system for text classifiers.
 * In addition to reading the content, it
 * (1) vectorizes the text using embeddings such as Glove, and
 * (2) divides the datasets into mini batches of specified size.
 *
 * The data is expected to be organized as per the following convention:
 * <pre>
 * data-dir/
 *     +- label1 /
 *     |    +- example11.txt
 *     |    +- example12.txt
 *     |    +- example13.txt
 *     |    +- .....
 *     +- label2 /
 *     |    +- example21.txt
 *     |    +- .....
 *     +- labelN /
 *          +- exampleN1.txt
 *          +- .....
 * </pre>
 *
 * In addition, the dataset shall be divided into training and testing as follows:
 * <pre>
 * data-dir/
 *     + train/
 *     |   +- label1 /
 *     |   +- labelN /
 *     + test /
 *         +- label1 /
 *         +- labelN /
 * </pre>
 *
 * <h2>Usage: </h2>
 * <code>
 *     // label names should match the subdirectory names
 *     labels = Arrays.asList("label1", "label2", ..."labelN");
 *     train = DataReader('data-dir/train', labels, embeds, ....);
 *     test = DataReader('data-dir/test', labels, embeds, ....)
 * </code>
 *
 * @see GlobalVectors
 * @see NeuralDocCat
 * <br/>
 * @author Thamme Gowda (thammegowda@apache.org)
 *
 */
public class DataReader implements DataSetIterator {

    private static final Logger LOG = LoggerFactory.getLogger(DataReader.class);

    private File dataDir;
    private List<File> records;
    private List<Integer> labels;
    private Map<String, Integer> labelToId;
    private String extension = ".txt";
    private GlobalVectors embedder;
    private int cursor = 0;
    private int batchSize;
    private int vectorLen;
    private int maxSeqLen;
    private int numLabels;
    // default tokenizer
    private Function<String, String[]> tokenizer = s -> s.toLowerCase().split(" ");


    /**
     * Creates a reader with the specified arguments
     * @param dataDirPath data directory
     * @param labelNames list of labels (names should match sub directory names)
     * @param embedder embeddings to convert words to vectors
     * @param batchSize mini batch size for DL4j training
     * @param maxSeqLength truncate sequences that are longer than this.
     *                    If truncation is not desired, set {@code Integer.MAX_VAL}
     */
    DataReader(String dataDirPath, List<String> labelNames, GlobalVectors embedder,
               int batchSize, int maxSeqLength){
        this.batchSize = batchSize;
        this.embedder = embedder;
        this.maxSeqLen = maxSeqLength;
        this.vectorLen = embedder.getVectorSize();
        this.numLabels = labelNames.size();
        this.dataDir = new File(dataDirPath);
        this.labelToId = new HashMap<>();
        for (int i = 0; i < labelNames.size(); i++) {
            labelToId.put(labelNames.get(i), i);
        }
        this.labelToId = Collections.unmodifiableMap(this.labelToId);
        this.scanDir();
        this.reset();
    }

    private void scanDir(){
        assert dataDir.exists();
        List<Integer> labels = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (String labelName: this.labelToId.keySet()) {
            Integer labelId = this.labelToId.get(labelName);
            assert labelId != null;
            File labelDir = new File(dataDir, labelName);
            if (!labelDir.exists()){
                throw new IllegalStateException("No examples found for "
                        + labelName + ". Looked at:" + labelDir);
            }
            File[] examples = labelDir.listFiles(f ->
                    f.isFile() && f.getName().endsWith(this.extension));
            if (examples == null || examples.length == 0){
                throw new IllegalStateException("No examples found for "
                        + labelName + ". Looked at:" + labelDir
                        + " for files having extension: \" + extension");
            }
            LOG.info("Found {} examples for label {}", examples.length, labelName);
            for (File example: examples) {
                files.add(example);
                labels.add(labelId);
            }
        }
        this.records = files;
        this.labels = labels;
    }

    /**
     * sets tokenizer for converting text to tokens
     * @param tokenizer tokenizer to use for converting text to tokens
     */
    public void setTokenizer(Function<String, String[]> tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * @return Tokenizer function used for converting text into words
     */
    public Function<String, String[]> getTokenizer() {
        return tokenizer;
    }

    @Override
    public DataSet next(int batchSize) {
        batchSize = Math.min(batchSize, records.size() - cursor);
        INDArray features = Nd4j.create(batchSize, vectorLen, maxSeqLen);
        INDArray labels = Nd4j.create(batchSize, numLabels, maxSeqLen);

        //Because we are dealing with text of different lengths and only one output at the final time step: use padding arrays
        //Mask arrays contain 1 if data is present at that time step for that example, or 0 if data is just padding
        INDArray featuresMask = Nd4j.zeros(batchSize, maxSeqLen);
        INDArray labelsMask = Nd4j.zeros(batchSize, maxSeqLen);

        // Optimizations to speed up this code block by reusing memory
        int _2dIndex[] = new int[2];
        int _3dIndex[] = new int[3];
        INDArrayIndex _3dNdIndex[] = new INDArrayIndex[]{null, NDArrayIndex.all(), null};

        for (int i = 0; i < batchSize && cursor < records.size(); i++, cursor++) {
            _2dIndex[0] = i;
            _3dIndex[0] = i;
            _3dNdIndex[0] = NDArrayIndex.point(i);

            try {
                // Read
                File file = records.get(cursor);
                int labelIdx = this.labels.get(cursor);
                String text = FileUtils.readFileToString(file);
                // Tokenize and Filter
                String[] tokens = tokenizer.apply(text);
                tokens = Arrays.stream(tokens).filter(embedder::hasWord).toArray(String[]::new);
                //Get word vectors for each word in review, and put them in the training data
                int j;
                for(j = 0; j < tokens.length && j < maxSeqLen; j++ ){
                    String token = tokens[j];
                    INDArray vector = embedder.toVector(token);
                    _3dNdIndex[2] = NDArrayIndex.point(j);
                    features.put(_3dNdIndex, vector);
                    //Word is present (not padding) for this example + time step -> 1.0 in features mask
                    _2dIndex[1] = j;
                    featuresMask.putScalar(_2dIndex, 1.0);
                }
                int lastIdx = j - 1;
                _2dIndex[1] = lastIdx;
                _3dIndex[1] = labelIdx;
                _3dIndex[2] = lastIdx;

                labels.putScalar(_3dIndex,1.0);   //Set label: one of k encoding
                // Specify that an output exists at the final time step for this example
                labelsMask.putScalar(_2dIndex,1.0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //LOG.info("Cursor = {} || Init Time = {}, Read time = {}, preprocess Time = {}, Mask Time={}", cursor, initTime, readTime, preProcTime, maskTime);
        return new DataSet(features, labels, featuresMask, labelsMask);
    }

    @Override
    public int inputColumns() {
        return this.embedder.getVectorSize();
    }

    @Override
    public int totalOutcomes() {
        return this.numLabels;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        assert this.records.size() == this.labels.size();
        long seed = System.nanoTime(); // shuffle both the lists in the same order
        Collections.shuffle(this.records, new Random(seed));
        Collections.shuffle(this.labels, new Random(seed));
        this.cursor = 0; // from beginning
    }

    @Override
    public int batch() {
        return this.batchSize;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getLabels() {
        return new ArrayList<>(this.labelToId.keySet());
    }

    @Override
    public boolean hasNext() {
        return cursor < this.records.size() - 1;
    }

    @Override
    public DataSet next() {
        return next(this.batchSize);
    }
}
