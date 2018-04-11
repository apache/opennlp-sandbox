package com.apache.opennlp.tf;

import org.apache.opennlp.tf.guillaumegenthial.PredictionConfiguration;
import org.apache.opennlp.tf.guillaumegenthial.SequenceTagging;

import java.io.IOException;

public class PredictTest {

  public static void main(String[] args) throws IOException {

    // Load model takes a String path!!
    String model = PredictTest.class.getResource("/savedmodel").getPath();
    // can be changed to File or InputStream
    String words = PredictTest.class.getResource("/words.txt.gz").getPath();
    String chars = PredictTest.class.getResource("/chars.txt.gz").getPath();
    String tags = PredictTest.class.getResource("/tags.txt.gz").getPath();


    PredictionConfiguration config = new PredictionConfiguration(words, chars, tags, model);

    SequenceTagging tagger = new SequenceTagging(config);

    String[] tokens = "Stormy Cars ' friend says she also plans to sue Michael Cohen .".split("\\s+");
    String[] pred = tagger.predict(tokens);

    for (int i=0; i<tokens.length; i++) {
      System.out.print(tokens[i] + "/" + pred[i] + " ");
    }
    System.out.println();
  }
}
