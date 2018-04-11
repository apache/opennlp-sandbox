package org.apache.opennlp.tf.guillaumegenthial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IndexTagger {

  private Map<Integer, String> idx2Tag = new HashMap<>();

  public IndexTagger(InputStream vocabTags) throws IOException {
    try(BufferedReader in = new BufferedReader(
            new InputStreamReader(
                    vocabTags, "UTF8"))) {
      String tag;
      int idx = 0;
      while ((tag = in.readLine()) != null) {
        idx2Tag.put(idx, tag);
        idx += 1;
      }
    }

  }

  public String getTag(Integer idx) {
    return idx2Tag.get(idx);
  }

  public Map<Integer, String> getIdx2Tag() {
    return Collections.unmodifiableMap(idx2Tag);
  }

  public int getNumberOfTags() {
    return idx2Tag.size();
  }

}
