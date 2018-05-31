package org.apache.opennlp.namefinder;

import org.junit.Assume;
import org.junit.BeforeClass;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class FeedDictionaryTest {

  private static TokenIds oneSentence;
  private static TokenIds twoSentences;

  @BeforeClass
  public static void beforeClass() {

    WordIndexer indexer;
    try {
      InputStream words = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/words.txt"));
      InputStream chars = new GZIPInputStream(WordIndexerTest.class.getResourceAsStream("/chars.txt"));
      indexer = new WordIndexer(words, chars);
    } catch (Exception ex) {
      indexer = null;
    }
    Assume.assumeNotNull(indexer);

    String text1 = "Stormy Cars ' friend says she also plans to sue Michael Cohen .";
    oneSentence = indexer.toTokenIds(text1.split("\\s+"));
    Assume.assumeNotNull(oneSentence);

    String[] text2 = new String[] {"I wish I was born in Copenhagen Denmark",
            "Donald Trump died on his way to Tivoli Gardens in Denmark ."};
    List<String[]> collect = Arrays.stream(text2).map(s -> s.split("\\s+")).collect(Collectors.toList());
    twoSentences = indexer.toTokenIds(collect.toArray(new String[2][]));
    Assume.assumeNotNull(twoSentences);

  }

}
