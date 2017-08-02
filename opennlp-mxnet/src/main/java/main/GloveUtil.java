package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by blue on 7/19/17.
 */
public class GloveUtil {
  public static Map<String, float[]> loadGloveVectors(InputStream in) throws IOException {
    Map<String, float[]> wordVectors = new HashMap<String, float[]>();

    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

    String line;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split(" ");

      float[] vector = new float[parts.length - 1];

      for (int i = 1; i < parts.length; i++) {
        vector[i - 1] = Float.parseFloat(parts[i]);
      }

      wordVectors.put(parts[0], vector);
    }

    return wordVectors;
  }
}
