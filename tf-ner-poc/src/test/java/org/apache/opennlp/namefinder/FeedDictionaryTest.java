package org.apache.opennlp.namefinder;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FeedDictionaryTest {

  private static WordIndexer indexer;

  @BeforeClass
  public static void beforeClass() {
    try (InputStream words = new GZIPInputStream(FeedDictionaryTest.class.getResourceAsStream("/words.txt.gz"));
         InputStream chars = new GZIPInputStream(FeedDictionaryTest.class.getResourceAsStream("/chars.txt.gz"))) {
      
      indexer = new WordIndexer(words, chars);
    } catch (Exception ex) {
      indexer = null;
    }
    assertNotNull(indexer);
  }

  @Test
  public void testToTokenIds() {
    String text1 = "Stormy Cars ' friend says she also plans to sue Michael Cohen .";
    TokenIds oneSentence = indexer.toTokenIds(text1.split("\\s+"));
    assertNotNull(oneSentence);
    assertEquals("Expect 13 tokenIds", 13, oneSentence.getWordIds()[0].length);

    String[] text2 = new String[] {"I wish I was born in Copenhagen Denmark",
            "Donald Trump died on his way to Tivoli Gardens in Denmark ."};
    List<String[]> collect = Arrays.stream(text2).map(s -> s.split("\\s+")).collect(Collectors.toList());
    TokenIds twoSentences = indexer.toTokenIds(collect.toArray(new String[2][]));
    assertNotNull(twoSentences);
    assertEquals("Expect 8 tokenIds", 8, twoSentences.getWordIds()[0].length);
    assertEquals("Expect 12 tokenIds", 12, twoSentences.getWordIds()[1].length);
  }
}
