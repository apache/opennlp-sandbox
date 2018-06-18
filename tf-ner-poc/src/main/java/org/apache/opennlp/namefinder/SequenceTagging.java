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

package org.apache.opennlp.namefinder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

public class SequenceTagging implements TokenNameFinder, AutoCloseable {
  private final SavedModelBundle model;
  private final Session session;
  private final WordIndexer wordIndexer;
  private final IndexTagger indexTagger;

  public SequenceTagging(PredictionConfiguration config) throws IOException {
    model = SavedModelBundle.load(config.getSavedModel(), "serve");
    session = model.session();

    this.wordIndexer = new WordIndexer(new FileInputStream(config.getVocabWords()),
            new FileInputStream(config.getVocabChars()));

    this.indexTagger = new IndexTagger((new FileInputStream(config.getVocabTags())));
  }

  public SequenceTagging(InputStream vocabWords, InputStream vocabChars,
                         InputStream vocabTags, InputStream modelZipPackage) throws IOException {

    wordIndexer = new WordIndexer(vocabWords, vocabChars);
    indexTagger = new IndexTagger(vocabTags);

    Path tmpDir = Files.createTempDirectory("opennlp2_namefinder");

    // Unzip the model to a temp directory
    ZipInputStream zis = new ZipInputStream(modelZipPackage);
    ZipEntry zipEntry = zis.getNextEntry();
    while(zipEntry != null){
      Path newFile = tmpDir.resolve(zipEntry.getName());

      if (zipEntry.isDirectory()) {
        Files.createDirectories(newFile);
      }
      else {
        Files.copy(zis, newFile);
        // This is a bit of hack, but should work fine for now ...
        newFile.toFile().deleteOnExit();
      }

      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
    zis.close();

    model = SavedModelBundle.load(tmpDir.toString(), "serve");
    session = model.session();
  }

  @Override
  public Span[] find(String[] sentence) {
    if (sentence.length > 0) {
      TokenIds tokenIds = wordIndexer.toTokenIds(sentence);
      return new BioCodec().decode(Arrays.asList(predict(tokenIds)[0]));
    }
    else {
      return new Span[0];
    }
  }

  public String[][] predict(String[][] sentences) {
    TokenIds tokenIds = wordIndexer.toTokenIds(sentences);
    return predict(tokenIds);
  }

  private String[][] predict(TokenIds tokenIds) {

    try (FeedDictionary fd = FeedDictionary.create(tokenIds)) {

      List<Tensor<?>> run = session.runner()
          .feed("chars/char_ids:0", fd.getCharIdsTensor())
          .feed("dropout_keep_prop:0", fd.getDropoutTensor())
          .feed("words/sequence_lengths:0", fd.getSentenceLengthsTensor())
          .feed("words/word_ids:0", fd.getWordIdsTensor())
          .feed("chars/word_lengths:0", fd.getWordLengthsTensor())
          .fetch("logits", 0)
          .fetch("trans_params", 0).run();

      float[][][] logits = new float[fd.getNumberOfSentences()][fd.getMaxSentenceLength()][indexTagger.getNumberOfTags()];
      run.get(0).copyTo(logits);

      float[][] trans_params = new float[indexTagger.getNumberOfTags()][indexTagger.getNumberOfTags()];
      run.get(1).copyTo(trans_params);

      String[][] returnValue = new String[fd.getNumberOfSentences()][];
      for (int i = 0; i < logits.length; i++) {
        float[][] logit = Arrays.copyOf(logits[i], fd.getSentenceLengths()[i]);
        returnValue[i] = Viterbi.decode(logit, trans_params).stream().map(indexTagger::getTag).toArray(String[]::new);
      }

      for (int i = 0; i < returnValue[0].length; i++) {
        if (returnValue[0][i] == null) {
          returnValue[0][i] = "other";
        }
      }

      for (Tensor t : run) {
        t.close();
      }

      return returnValue;
    }
  }

  @Override
  public void clearAdaptiveData() {
  }

  @Override
  public void close() {
    session.close();
  }
}
