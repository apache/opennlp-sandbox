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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class NameSampleDataSetIterator implements DataSetIterator {

  private static class NameSampleToDataSetStream extends FilterObjectStream<NameSample, DataSet> {

    private final WordVectors wordVectors;
    private final String[] labels;
    private int windowSize;

    private Iterator<DataSet> dataSets = Collections.emptyListIterator();

    NameSampleToDataSetStream(ObjectStream<NameSample> samples, WordVectors wordVectors, int windowSize, String[] labels) {
      super(samples);
      this.wordVectors = wordVectors;
      this.windowSize = windowSize;
      this.labels = labels;
    }

    private Iterator<DataSet> createDataSets(NameSample sample) {
      List<INDArray> features = NameFinderDL.mapToFeatureMatrices(wordVectors, sample.getSentence(),
          windowSize);

      List<INDArray> labels = NameFinderDL.mapToLabelVectors(sample, windowSize, this.labels);

      List<DataSet> dataSetList = new ArrayList<>();

      for (int i = 0; i < features.size(); i++) {
        dataSetList.add(new DataSet(features.get(i), labels.get(i)));
      }

      return dataSetList.iterator();
    }

    @Override
    public final DataSet read() throws IOException {

      if (dataSets.hasNext()) {
        return dataSets.next();
      }
      else {
        NameSample sample;
        while (!dataSets.hasNext() && (sample = samples.read()) != null) {
          dataSets = createDataSets(sample);
        }

        if (dataSets.hasNext()) {
          return read();
        }
      }

      return null;
    }
  }

  private final int windowSize;
  private final String[] labels;

  private final int batchSize = 128;
  private final int vectorSize = 300;

  private final int totalSamples;

  private int cursor = 0;

  private final ObjectStream<DataSet> samples;

  NameSampleDataSetIterator(ObjectStream<NameSample> samples, WordVectors wordVectors, int windowSize,
                            String labels[]) throws IOException {
    this.windowSize = windowSize;
    this.labels = labels;

    this.samples = new NameSampleToDataSetStream(samples, wordVectors, windowSize, labels);

    int total = 0;

    DataSet sample;
    while ((sample = this.samples.read()) != null) {
      total++;
    }

    totalSamples = total;

    samples.reset();
  }

  public DataSet next(int num) {
    if (cursor >= totalExamples()) throw new NoSuchElementException();

    INDArray features = Nd4j.create(num, vectorSize, windowSize);
    INDArray featuresMask = Nd4j.zeros(num, windowSize);

    INDArray labels = Nd4j.create(num, 3, windowSize);
    INDArray labelsMask = Nd4j.zeros(num, windowSize);

    // iterate stream and copy to arrays

    for (int i = 0; i < num; i++) {
      DataSet sample;
      try {
        sample = samples.read();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      if (sample != null) {
        INDArray feature = sample.getFeatures();
        features.put(new INDArrayIndex[] {NDArrayIndex.point(i)}, feature.get(NDArrayIndex.point(0)));

        feature.get(new INDArrayIndex[] {NDArrayIndex.point(0), NDArrayIndex.all(),
            NDArrayIndex.point(0)});

        for (int j = 0; j < windowSize; j++) {
          featuresMask.putScalar(new int[] {i, j}, 1.0);
        }

        INDArray label = sample.getLabels();
        labels.put(new INDArrayIndex[] {NDArrayIndex.point(i)}, label.get(NDArrayIndex.point(0)));
        labelsMask.putScalar(new int[] {i, windowSize - 1}, 1.0);
      }

      cursor++;
    }

    return new DataSet(features, labels, featuresMask, labelsMask);
  }

  public int totalExamples() {
    return totalSamples;
  }

  public int inputColumns() {
    return vectorSize;
  }

  public int totalOutcomes() {
    return getLabels().size();
  }

  public boolean resetSupported() {
    return true;
  }

  public boolean asyncSupported() {
    return false;
  }

  public void reset() {
    cursor = 0;

    try {
      samples.reset();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int batch() {
    return batchSize;
  }

  public int cursor() {
    return cursor;
  }

  public int numExamples() {
    return totalExamples();
  }

  public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
    throw new UnsupportedOperationException();
  }

  public DataSetPreProcessor getPreProcessor() {
    throw new UnsupportedOperationException();
  }

  public List<String> getLabels() {
    return Arrays.asList("start","cont", "other");
  }

  public boolean hasNext() {
    return cursor < numExamples();
  }

  public DataSet next() {
    return next(batchSize);
  }
}
