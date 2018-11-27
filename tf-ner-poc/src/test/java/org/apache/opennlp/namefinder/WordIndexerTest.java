package org.apache.opennlp.namefinder;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class WordIndexerTest {

  private static WordIndexer indexer;

  @BeforeClass
  public static void beforeClass() {
    try {
      InputStream words = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/words.txt"));
      InputStream chars = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/chars.txt"));
      indexer = new WordIndexer(words, chars);
    } catch (Exception ex) {
      indexer = null;
    }
    Assume.assumeNotNull(indexer);
  }

  @Test
  public void testToTokenIds_OneSentence() {

    String text = "Stormy Cars ' friend says she also plans to sue Michael Cohen .";

    TokenIds ids = indexer.toTokenIds(text.split("\\s+"));

    Assert.assertEquals("Expect 13 tokenIds", 13, ids.getWordIds()[0].length);

    Assert.assertArrayEquals(new int[] {7, 30, 34, 80, 42, 3}, ids.getCharIds()[0][0]);
    Assert.assertArrayEquals(new int[] {51, 41, 80, 54}, ids.getCharIds()[0][1]);
    Assert.assertArrayEquals(new int[] {64}, ids.getCharIds()[0][2]);
    Assert.assertArrayEquals(new int[] {47, 80, 82, 83, 31, 23}, ids.getCharIds()[0][3]);
    Assert.assertArrayEquals(new int[] {54, 41, 3, 54}, ids.getCharIds()[0][4]);
    Assert.assertArrayEquals(new int[] {54, 76, 83}, ids.getCharIds()[0][5]);
    Assert.assertArrayEquals(new int[] {41, 55, 54, 34}, ids.getCharIds()[0][6]);
    Assert.assertArrayEquals(new int[] {46, 55, 41, 31, 54}, ids.getCharIds()[0][7]);
    Assert.assertArrayEquals(new int[] {30, 34}, ids.getCharIds()[0][8]);
    Assert.assertArrayEquals(new int[] {54, 50, 83}, ids.getCharIds()[0][9]);
    Assert.assertArrayEquals(new int[] {39, 82, 20, 76, 41, 83, 55}, ids.getCharIds()[0][10]);
    Assert.assertArrayEquals(new int[] {51, 34, 76, 83, 31}, ids.getCharIds()[0][11]);
    Assert.assertArrayEquals(new int[] {65}, ids.getCharIds()[0][12]);

    Assert.assertEquals(2720, ids.getWordIds()[0][0]);
    Assert.assertEquals(15275,ids.getWordIds()[0][1]);
    Assert.assertEquals(3256, ids.getWordIds()[0][2]);
    Assert.assertEquals(11348, ids.getWordIds()[0][3]);
    Assert.assertEquals(21054, ids.getWordIds()[0][4]);
    Assert.assertEquals(18337, ids.getWordIds()[0][5]);
    Assert.assertEquals(7885, ids.getWordIds()[0][6]);
    Assert.assertEquals(7697, ids.getWordIds()[0][7]);
    Assert.assertEquals(16601, ids.getWordIds()[0][8]);
    Assert.assertEquals(2720, ids.getWordIds()[0][9]);
    Assert.assertEquals(17408, ids.getWordIds()[0][10]);
    Assert.assertEquals(11541, ids.getWordIds()[0][11]);
    Assert.assertEquals(2684, ids.getWordIds()[0][12]);

  }

  @Test
  public void testToTokenIds_TwoSentences() {

    String[] text = new String[] {"I wish I was born in Copenhagen Denmark",
            "Donald Trump died on his way to Tivoli Gardens in Denmark ."};

    List<String[]> collect = Arrays.stream(text).map(s -> s.split("\\s+")).collect(Collectors.toList());

    TokenIds ids = indexer.toTokenIds(collect.toArray(new String[2][]));

    Assert.assertEquals(8, ids.getWordIds()[0].length);
    Assert.assertEquals(12, ids.getWordIds()[1].length);

    Assert.assertArrayEquals(new int[] {4}, ids.getCharIds()[0][0]);
    Assert.assertArrayEquals(new int[] {6, 82, 54, 76}, ids.getCharIds()[0][1]);
    Assert.assertArrayEquals(new int[] {4}, ids.getCharIds()[0][2]);
    Assert.assertArrayEquals(new int[] {6, 41, 54}, ids.getCharIds()[0][3]);
    Assert.assertArrayEquals(new int[] {59, 34, 80, 31}, ids.getCharIds()[0][4]);
    Assert.assertArrayEquals(new int[] {82, 31}, ids.getCharIds()[0][5]);
    Assert.assertArrayEquals(new int[] {51, 34, 46, 83, 31, 76, 41, 28, 83, 31}, ids.getCharIds()[0][6]);
    Assert.assertArrayEquals(new int[] {36, 83, 31, 42, 41, 80, 49}, ids.getCharIds()[0][7]);

    Assert.assertArrayEquals(new int[] {36, 34, 31, 41, 55, 23}, ids.getCharIds()[1][0]);
    Assert.assertArrayEquals(new int[] {52, 80, 50, 42, 46}, ids.getCharIds()[1][1]);
    Assert.assertArrayEquals(new int[] {23, 82, 83, 23}, ids.getCharIds()[1][2]);
    Assert.assertArrayEquals(new int[] {34, 31}, ids.getCharIds()[1][3]);
    Assert.assertArrayEquals(new int[] {76, 82, 54}, ids.getCharIds()[1][4]);
    Assert.assertArrayEquals(new int[] {6, 41, 3}, ids.getCharIds()[1][5]);
    Assert.assertArrayEquals(new int[] {30, 34}, ids.getCharIds()[1][6]);
    Assert.assertArrayEquals(new int[] {52, 82, 11, 34, 55, 82}, ids.getCharIds()[1][7]);
    Assert.assertArrayEquals(new int[] {74, 41, 80, 23, 83, 31, 54}, ids.getCharIds()[1][8]);
    Assert.assertArrayEquals(new int[] {82, 31}, ids.getCharIds()[1][9]);
    Assert.assertArrayEquals(new int[] {36, 83, 31, 42, 41, 80, 49}, ids.getCharIds()[1][10]);
    Assert.assertArrayEquals(new int[] {65}, ids.getCharIds()[1][11]);

    Assert.assertEquals(21931, ids.getWordIds()[0][0]);
    Assert.assertEquals(20473, ids.getWordIds()[0][1]);
    Assert.assertEquals(21931, ids.getWordIds()[0][2]);
    Assert.assertEquals(5477, ids.getWordIds()[0][3]);
    Assert.assertEquals(11538, ids.getWordIds()[0][4]);
    Assert.assertEquals(21341, ids.getWordIds()[0][5]);
    Assert.assertEquals(14024, ids.getWordIds()[0][6]);
    Assert.assertEquals(7420, ids.getWordIds()[0][7]);

    Assert.assertEquals(12492, ids.getWordIds()[1][0]);
    Assert.assertEquals(2720, ids.getWordIds()[1][1]);
    Assert.assertEquals(9476, ids.getWordIds()[1][2]);
    Assert.assertEquals(16537, ids.getWordIds()[1][3]);
    Assert.assertEquals(18966, ids.getWordIds()[1][4]);
    Assert.assertEquals(21088, ids.getWordIds()[1][5]);
    Assert.assertEquals(16601, ids.getWordIds()[1][6]);
    Assert.assertEquals(2720, ids.getWordIds()[1][7]);
    Assert.assertEquals(2720, ids.getWordIds()[1][8]);
    Assert.assertEquals(21341, ids.getWordIds()[1][9]);
    Assert.assertEquals(7420, ids.getWordIds()[1][10]);
    Assert.assertEquals(2684, ids.getWordIds()[1][11]);

  }



}
