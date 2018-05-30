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

package org.apache.opennlp.tf.guillaumegenthial;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class SequenceTagging implements AutoCloseable {

  private final SavedModelBundle model;
  private final Session session;
  private final WordIndexer wordIndexer;
  private final IndexTagger indexTagger;

  public SequenceTagging(PredictionConfiguration config) throws IOException {
    model = SavedModelBundle.load(config.getSavedModel(), "serve");
    session = model.session();


    Iterator<Operation> opit = model.graph().operations();

    this.wordIndexer = new WordIndexer(new FileInputStream(config.getVocabWords()),
            new FileInputStream(config.getVocabChars()));

    this.indexTagger = new IndexTagger((new FileInputStream(config.getVocabTags())));
  }

  public String[] predict(String[] sentence) {
    TokenIds tokenIds = wordIndexer.toTokenIds(sentence);
    return predict(tokenIds)[0];
  }

  public String[][] predict(String[][] sentences) {
    TokenIds tokenIds = wordIndexer.toTokenIds(sentences);
    return predict(tokenIds);
  }

  private String[][] predict(TokenIds tokenIds) {
    FeedDictionary fd = FeedDictionary.create(tokenIds);

    List<Tensor<?>> run = session.runner()
            .feed("char_ids:0", fd.getCharIdsTensor())
            .feed("dropout:0", fd.getDropoutTensor())
            .feed("sequence_lengths:0", fd.getSentenceLengthsTensor())
            .feed("word_ids:0", fd.getWordIdsTensor())
            .feed("word_lengths:0", fd.getWordLengthsTensor())
            .fetch("proj/logits", 0)
            .fetch("trans_params", 0).run();


    float[][][] logits = new float[fd.getNumberOfSentences()][fd.getMaxSentenceLength()][indexTagger.getNumberOfTags()];
    run.get(0).copyTo(logits);

    float[][] trans_params = new float[indexTagger.getNumberOfTags()][indexTagger.getNumberOfTags()];
    run.get(1).copyTo(trans_params);

    String[][] returnValue = new String[fd.getNumberOfSentences()][];
    for (int i=0; i < logits.length; i++) {
      //logit = logit[:sequence_length] # keep only the valid steps
      float[][] logit = Arrays.copyOf(logits[i], fd.getSentenceLengths()[i]);
      returnValue[i] = Viterbi.decode(logit, trans_params).stream().map(indexTagger::getTag).toArray(String[]::new);
    }

    return returnValue;
  }

  @Override
  public void close() throws Exception {
    session.close();
  }
}
