/*
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

package opennlp.tools.dl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a wrapper for DL4J's {@link MultiLayerNetwork}, and {@link GlobalVectors}
 * that provides features to serialize and deserialize necessary data to a zip file.
 * <p>
 * This can be used by a Neural Trainer tool to serialize the network and a predictor tool to restore the same network
 * with the weights.
 *
 * <br/>
 ** @author Thamme Gowda (thammegowda@apache.org)
 */
public class NeuralDocCatModel {

    public static final int VERSION = 1;
    public static final String MODEL_NAME = NeuralDocCatModel.class.getName();
    public static final String MANIFEST = "model.mf";
    public static final String NETWORK = "network.json";
    public static final String WEIGHTS = "weights.bin";
    public static final String GLOVES = "gloves.tsv";
    public static final String LABELS = "labels";
    public static final String MAX_SEQ_LEN = "maxSeqLen";

    private static final Logger LOG = LoggerFactory.getLogger(NeuralDocCatModel.class);

    private final MultiLayerNetwork network;
    private final GlobalVectors gloves;
    private final Properties manifest;
    private final List<String> labels;
    private final int maxSeqLen;

    /**
     *
     * @param stream Input stream of a Zip file.
     * @throws IOException Thrown if IO errors occurred.
     */
    public NeuralDocCatModel(InputStream stream) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(stream);

        Properties manifest = null;
        MultiLayerNetwork model = null;
        INDArray params = null;
        GlobalVectors gloves = null;
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
            String name = entry.getName();
            switch (name) {
                case MANIFEST:
                    manifest = new Properties();
                    manifest.load(zipIn);
                    break;
                case NETWORK:
                    String json = IOUtils.toString(new UnclosableInputStream(zipIn), StandardCharsets.UTF_8);
                    model = new MultiLayerNetwork(MultiLayerConfiguration.fromJson(json));
                    break;
                case WEIGHTS:
                    params = Nd4j.read(new DataInputStream(new UnclosableInputStream(zipIn)));
                    break;
                case GLOVES:
                    gloves = new GlobalVectors(new UnclosableInputStream(zipIn));
                    break;
                default:
                    LOG.warn("Unexpected entry in the zip : {}", name);
            }
        }

        assert model != null;
        assert manifest != null;
        model.init(params, false);
        this.network = model;
        this.manifest = manifest;
        this.gloves = gloves;

        assert manifest.containsKey(LABELS);
        String[] labels = manifest.getProperty(LABELS).split(",");
        this.labels = List.of(labels);

        assert manifest.containsKey(MAX_SEQ_LEN);
        this.maxSeqLen = Integer.parseInt(manifest.getProperty(MAX_SEQ_LEN));
    }

    /**
     *
     * @param network any compatible multi layer neural network
     * @param vectors Global vectors
     * @param labels list of labels
     * @param maxSeqLen max sequence length
     */
    public NeuralDocCatModel(MultiLayerNetwork network, GlobalVectors vectors, List<String> labels, int maxSeqLen) {
        this.network = network;
        this.gloves = vectors;
        this.manifest = new Properties();
        this.manifest.setProperty(LABELS, StringUtils.join(labels, ","));
        this.manifest.setProperty(MAX_SEQ_LEN, maxSeqLen + "");
        this.labels = Collections.unmodifiableList(labels);
        this.maxSeqLen = maxSeqLen;
    }

    public MultiLayerNetwork getNetwork() {
        return network;
    }

    public GlobalVectors getGloves() {
        return gloves;
    }

    public List<String> getLabels() {
        return labels;
    }

    public int getMaxSeqLen() {
        return this.maxSeqLen;
    }

    /**
     * Zips the current state of the model and writes it into the specified stream.
     * 
     * @param stream Output stream to write to.
     * @throws IOException Thrown if IO errors occurred.
     */
    public void saveModel(OutputStream stream) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(stream))) {
            // Write out manifest
            zipOut.putNextEntry(new ZipEntry(MANIFEST));

            String comments = "Created-By:" + System.getenv("USER") + " at " + new Date()
                    + "\nModel-Version: " + VERSION
                    + "\nModel-Schema:" + MODEL_NAME;

            manifest.store(zipOut, comments);
            zipOut.closeEntry();

            // Write out the network
            zipOut.putNextEntry(new ZipEntry(NETWORK));
            byte[] jModel = network.getLayerWiseConfigurations().toJson().getBytes();
            zipOut.write(jModel);
            zipOut.closeEntry();

            //Write out the network coefficients
            zipOut.putNextEntry(new ZipEntry(WEIGHTS));
            Nd4j.write(network.params(), new DataOutputStream(zipOut));
            zipOut.closeEntry();

            // Write out vectors
            zipOut.putNextEntry(new ZipEntry(GLOVES));
            gloves.writeOut(zipOut, false);
            zipOut.closeEntry();

            zipOut.finish();
        }
    }

    /**
     * Creates a model from file on the local file system.
     *
     * @param modelPath path to model file
     * @return an instance of this class
     * @throws IOException Thrown if IO errors occurred.
     */
    public static NeuralDocCatModel loadModel(String modelPath) throws IOException {
        try (InputStream modelStream = new FileInputStream(modelPath)) {
            return new NeuralDocCatModel(modelStream);
        }
    }
}
