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

import org.apache.commons.io.IOUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GlobalVectors (Glove) for projecting words to vector space.
 * This tool utilizes word vectors  pre-trained on large datasets.
 *
 * Visit https://nlp.stanford.edu/projects/glove/ for full documentation of Gloves.
 *
 * <h2>Usage</h2>
 * <pre>
 * path = "work/datasets/glove.6B/glove.6B.100d.txt";
 * vocabSize = 20000; # max number of words to use
 * GlobalVectors glove;
 * try (InputStream stream = new FileInputStream(path)) {
 *    glove = new GlobalVectors(stream, vocabSize);
 * }
 * </pre>
 *
 * @author Thamme Gowda (thammegowda@apache.org)
 *
 */
public class GlobalVectors {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalVectors.class);

    private final INDArray embeddings;
    private final Map<String, Integer> wordToId;
    private final List<String> idToWord;
    private final int vectorSize;
    private final int maxWords;

    /**
     * Reads Global Vectors from stream
     * @param stream Glove word vectors stream (plain text)
     * @throws IOException
     */
    public GlobalVectors(InputStream stream) throws IOException {
        this(stream, Integer.MAX_VALUE);
    }

    /**
     *
     * @param stream vector stream
     * @param maxWords maximum number of words to use, i.e. vocabulary size
     * @throws IOException
     */
    public GlobalVectors(InputStream stream, int maxWords) throws IOException {
        List<String> words = new ArrayList<>();
        List<INDArray> vectors = new ArrayList<>();
        int vectorSize = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))){
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (vectorSize == -1) {
                    vectorSize = parts.length - 1;
                } else {
                    assert vectorSize == parts.length - 1;
                }
                float[] vector = new float[vectorSize];
                for (int i = 1; i < parts.length; i++) {
                    vector[i-1] = Float.parseFloat(parts[i]);
                }
                vectors.add(Nd4j.create(vector));
                words.add(parts[0]);
                if (words.size() >= maxWords) {
                    LOG.info("Max words reached at {}, aborting", words.size());
                    break;
                }
            }
            LOG.info("Found {} words; Vector dimensions={}", words.size(), vectorSize);
            this.vectorSize = vectorSize;
            this.maxWords = Math.min(words.size(), maxWords);
            this.embeddings = Nd4j.create(vectors, new int[]{vectors.size(), vectorSize});
            this.idToWord = words;
            this.wordToId = new HashMap<>();
            for (int i = 0; i < words.size(); i++) {
                wordToId.put(words.get(i), i);
            }
        }
    }

    /**
     * @return size or dimensions of vectors
     */
    public int getVectorSize() {
        return vectorSize;
    }

    public int getMaxWords() {
        return maxWords;
    }

    /**
     *
     * @param word
     * @return {@code true} if word is known; false otherwise
     */
    public boolean hasWord(String word){
        return wordToId.containsKey(word);
    }

    /**
     * Converts word to vectors
     * @param word word to be converted to vector
     * @return Vector if words exists or null otherwise
     */
    public INDArray toVector(String word){
        if (wordToId.containsKey(word)){
            return embeddings.getRow(wordToId.get(word));
        }
        return null;
    }

    public INDArray embed(String text, int maxLen){
        return embed(text.toLowerCase().split(" "), maxLen);
    }

    public INDArray embed(String[] tokens, int maxLen){
        List<String> tokensFiltered = new ArrayList<>();
        for(String t: tokens ){
            if(hasWord(t)){
                tokensFiltered.add(t);
            }
        }
        int seqLen = Math.min(maxLen, tokensFiltered.size());

        INDArray features = Nd4j.create(1, vectorSize, seqLen);

        for( int j = 0; j < seqLen; j++ ){
            String token = tokensFiltered.get(j);
            INDArray vector = toVector(token);
            features.put(new INDArrayIndex[]{NDArrayIndex.point(0), NDArrayIndex.all(), NDArrayIndex.point(j)}, vector);
        }
        return features;
    }

    public void writeOut(OutputStream stream, boolean closeStream) throws IOException {
        writeOut(stream, "%.5f", closeStream);
    }

    public void writeOut(OutputStream stream,
                         String floatPrecisionFormatString, boolean closeStream) throws IOException {
        if (!Character.isWhitespace(floatPrecisionFormatString.charAt(0))) {
            floatPrecisionFormatString = " " + floatPrecisionFormatString;
        }
        LOG.info("Writing {} vectors out, float precision {}", idToWord.size(), floatPrecisionFormatString);

        PrintWriter out = new PrintWriter(stream);
        try {
            for (int i = 0; i < idToWord.size(); i++) {
                out.printf("%s", idToWord.get(i));
                INDArray row = embeddings.getRow(i);
                for (int j = 0; j < vectorSize; j++) {
                    out.printf(floatPrecisionFormatString, row.getDouble(j));
                }
                out.println();
            }
        } finally {
            if (closeStream){
                IOUtils.closeQuietly(out);
            } // else dont close because, closing the print writer also closes the inner stream
        }
    }
}
