package opennlp.tools.disambiguator;

public class WordToDisambiguate {

  protected String[] sentence;
  protected int wordIndex;
  protected String posTag;

  protected int sense;

  /**
   * Constructor
   */

  public WordToDisambiguate(String[] sentence, int wordIndex, int sense)
      throws IllegalArgumentException {
    super();

    if (wordIndex > sentence.length) {
      throw new IllegalArgumentException("The index is out of bounds !");
    }
    this.sentence = sentence;
    this.wordIndex = wordIndex;
    String[] posTags = PreProcessor.tag(sentence);
    this.posTag = posTags[wordIndex];
    this.sense = sense;
  }

  public WordToDisambiguate(String[] sentence, int wordIndex) {
    this(sentence, wordIndex, -1);
  }

  /**
   * Getters and Setters
   */

  // sentence
  public String[] getSentence() {
    return sentence;
  }

  public void setSentence(String[] sentence) {
    this.sentence = sentence;
  }

  // word
  public int getWordIndex() {
    return wordIndex;
  }

  public void setWordIndex(int wordIndex) {
    this.wordIndex = wordIndex;
  }

  public String getWord() {
    return sentence[wordIndex];
  }

  // posTag
  public String getPosTag() {
    return posTag;
  }

  public void setPosTag(String posTag) {
    this.posTag = posTag;
  }

  // sense
  public int getSense() {
    return sense;
  }

  public void setSense(int sense) {
    this.sense = sense;
  }
}

