package org.apache.opennlp.tf.guillaumegenthial;

public final class TokenIds {

  private final int[][][] charIds;
  private final int[][] wordIds;

  public TokenIds(int[][][] charIds, int[][] wordIds) {
    this.charIds = charIds;
    this.wordIds = wordIds;
  }

  public int[][][] getCharIds() {
    return charIds;
  }

  public int[][] getWordIds() {
    return wordIds;
  }
}
