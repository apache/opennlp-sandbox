package org.apache.opennlp.namefinder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import java.io.IOException;
import java.nio.file.Path;

class PredictTest {

  @Test
  @Disabled // TODO This test is not platform neutral and, for instance, fails with
    //  "Cannot find TensorFlow native library for OS: darwin, architecture: aarch64"
    //  We need JUnit 5 in the sandbox to circumvent this, so it can be run in supported environments
  void testFindTokens() throws IOException {

    // can be changed to File or InputStream
    String words = PredictTest.class.getResource("/words.txt.gz").getPath();
    String chars = PredictTest.class.getResource("/chars.txt.gz").getPath();
    String tags = PredictTest.class.getResource("/tags.txt.gz").getPath();
    // Load model takes a String path!!
    Path model = Path.of("savedmodel");

    PredictionConfiguration config = new PredictionConfiguration(words, chars, tags, model.toString());

    try (SequenceTagging tagger = new SequenceTagging(config)) {
      String[] tokens = "Stormy Cars ' friend says she also plans to sue Michael Cohen .".split("\\s+");
      Span[] pred = tagger.find(tokens);

      for (int i = 0; i < tokens.length; i++) {
        System.out.print(tokens[i] + "/" + pred[i] + " ");
      }
      System.out.println();
    }

  }
}
