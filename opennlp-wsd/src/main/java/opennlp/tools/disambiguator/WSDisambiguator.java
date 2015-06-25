package opennlp.tools.disambiguator;

import opennlp.tools.util.Span;

/**
 * The interface for word sense disambiguators.
 */
public interface WSDisambiguator {

  public String[] disambiguate(String[] inputText, int inputWordIndex);

  public String[] disambiguate(String[] inputText, Span[] inputWordSpans);
}
