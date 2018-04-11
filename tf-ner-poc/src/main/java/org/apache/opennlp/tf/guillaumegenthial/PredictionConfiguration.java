package org.apache.opennlp.tf.guillaumegenthial;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PredictionConfiguration {

  private String vocabWords;
  private String vocabChars;
  private String vocabTags;
  private String savedModel;

  public PredictionConfiguration(String vocabWords, String vocabChars, String vocabTags, String savedModel) {
    this.vocabWords = vocabWords;
    this.vocabChars = vocabChars;
    this.vocabTags = vocabTags;
    this.savedModel = savedModel;
  }

  public String getVocabWords() {
    return vocabWords;
  }

  public String getVocabChars() {
    return vocabChars;
  }

  public String getVocabTags() {
    return vocabTags;
  }

  public String getSavedModel() {
    return savedModel;
  }

  public InputStream getVocabWordsInputStream() throws IOException{
    return new FileInputStream(getVocabWords());
  }
}
