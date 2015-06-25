package opennlp.tools.disambiguator.ims;

import java.util.ArrayList;

import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.Loader;

public class FeaturesExtractor {

  public FeaturesExtractor() {
    super();
  }

  /**
   * @Algorithm: IMS (It Makes Sense)
   * 
   *             The following methods serve to extract the features for the
   *             algorithm IMS.
   */

  public String[] extractPosOfSurroundingWords(String[] sentence,
      int wordIndex, int numberOfWords) {

    String[] taggedSentence = Loader.getTagger().tag(sentence);

    String[] tags = new String[2 * numberOfWords + 1];

    int j = 0;

    for (int i = wordIndex - numberOfWords; i < wordIndex + numberOfWords; i++) {
      if (i < 0 || i >= sentence.length) {
        tags[j] = "null";
      } else {
        tags[j] = taggedSentence[i];
      }
      j++;
    }

    return tags;
  }

  public String[] extractSurroundingWords(String[] sentence, int wordIndex) {

    String[] posTags = Loader.getTagger().tag(sentence);

    Constants.print(posTags);

    ArrayList<String> contextWords = new ArrayList<String>();

    for (int i = 0; i < sentence.length; i++) {

      if (!Constants.stopWords.contains(sentence[i].toLowerCase())
          && (wordIndex != i)) {

        String word = sentence[i].toLowerCase().replaceAll("[^a-z]", "").trim();

        if (!word.equals("")) {
          String lemma = Loader.getLemmatizer().lemmatize(sentence[i],
              posTags[i]);
          contextWords.add(lemma);
        }

      }
    }

    return contextWords.toArray(new String[contextWords.size()]);
  }

  public ArrayList<String[]> extractLocalCollocations(String[] sentence,
      int wordIndex, int range) {
    /**
     * Here the author used only 11 features of this type. the range was set to
     * 3 (bigrams extracted in a way that they are at max separated by 1 word).
     */

    ArrayList<String[]> localCollocations = new ArrayList<String[]>();

    for (int i = wordIndex - range; i <= wordIndex + range; i++) {

      if (!(i < 0 || i > sentence.length - 2)) {
        if ((i != wordIndex) && (i + 1 != wordIndex)
            && (i + 1 < wordIndex + range)) {
          String[] lc = { sentence[i], sentence[i + 1] };
          localCollocations.add(lc);
        }
        if ((i != wordIndex) && (i + 2 != wordIndex)
            && (i + 2 < wordIndex + range)) {
          String[] lc = { sentence[i], sentence[i + 2] };
          localCollocations.add(lc);
        }
      }

    }

    return localCollocations;
  }
}

