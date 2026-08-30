/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor.basic;

import java.util.function.UnaryOperator;

import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;
import opennlp.tools.stemmer.hunspell.HunspellStemmer;
import opennlp.tools.stemmer.light.EnglishMinimalStemmer;
import opennlp.tools.stemmer.light.FinnishLightStemmer;
import opennlp.tools.stemmer.light.FrenchLightStemmer;
import opennlp.tools.stemmer.light.FrenchMinimalStemmer;
import opennlp.tools.stemmer.light.GermanLightStemmer;
import opennlp.tools.stemmer.light.GermanMinimalStemmer;
import opennlp.tools.stemmer.light.HungarianLightStemmer;
import opennlp.tools.stemmer.light.ItalianLightStemmer;
import opennlp.tools.stemmer.light.NorwegianLightStemmer;
import opennlp.tools.stemmer.light.NorwegianMinimalStemmer;
import opennlp.tools.stemmer.light.NorwegianVariety;
import opennlp.tools.stemmer.light.PortugueseLightStemmer;
import opennlp.tools.stemmer.light.RussianLightStemmer;
import opennlp.tools.stemmer.light.SpanishLightStemmer;
import opennlp.tools.stemmer.light.SpanishMinimalStemmer;
import opennlp.tools.stemmer.light.SwedishLightStemmer;
import opennlp.tools.stemmer.light.SwedishMinimalStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.model.HunspellRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;

/**
 * Resolves a {@link StemmerSpec} to a per-token stem function, covering the bundled
 * snowball, porter, and UniNE light and minimal families plus the operator-configured
 * hunspell dictionaries.
 *
 * <p>The bundled algorithm stemmers expect lowercase input, so the returned function
 * lowercases with {@link StringUtil#toLowerCase(CharSequence)} before stemming; hunspell
 * performs its own case analysis and receives the token as is. The returned function is for
 * single-threaded use within one request; the shared hunspell stemmer it may wrap is
 * thread-safe.</p>
 */
final class StemmerSelector {

  private StemmerSelector() {
    // This class only resolves specs and is never instantiated.
  }

  /**
   * Validates a stemmer spec against the configured dictionaries without keeping the
   * resolved stemmer.
   *
   * @throws AnalysisException If the spec is incomplete, names an uncovered language,
   *         or references a missing hunspell dictionary.
   */
  static void validate(StemmerSpec spec, HunspellRegistry hunspellRegistry) {
    newStemFunction(spec, hunspellRegistry);
  }

  /**
   * Creates the stem function for one request.
   *
   * @param spec The requested stemmer configuration.
   * @param hunspellRegistry The configured hunspell dictionaries.
   * @return A function from token text to stem. Never {@code null}.
   * @throws AnalysisException If the spec is incomplete, names an uncovered language,
   *         or references a missing hunspell dictionary.
   */
  static UnaryOperator<String> newStemFunction(
      StemmerSpec spec, HunspellRegistry hunspellRegistry) {
    return newStemFunction(spec, hunspellRegistry, true);
  }

  /**
   * Creates a stem function without an implicit lowercase transform. Configurable
   * term layers use this form because their normalizers define the complete
   * input to the stemmer, including whether case is preserved or folded.
   */
  static UnaryOperator<String> newRawStemFunction(
      StemmerSpec spec, HunspellRegistry hunspellRegistry) {
    return newStemFunction(spec, hunspellRegistry, false);
  }

  /** Creates the stemming function selected by the specification. */
  private static UnaryOperator<String> newStemFunction(
      StemmerSpec spec, HunspellRegistry hunspellRegistry, boolean lowercaseInput) {
    final StemmerAlgorithm algorithm =
        spec.getAlgorithm() == StemmerAlgorithm.STEMMER_ALGORITHM_UNSPECIFIED
            ? StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL
            : spec.getAlgorithm();
    return switch (algorithm) {
      case STEMMER_ALGORITHM_SNOWBALL ->
          selected(new SnowballStemmer(snowballAlgorithm(language(spec, "SNOWBALL"))),
              lowercaseInput);
      case STEMMER_ALGORITHM_PORTER -> selected(new PorterStemmer(), lowercaseInput);
      case STEMMER_ALGORITHM_LIGHT ->
          selected(lightFactory(language(spec, "LIGHT")).newStemmer(), lowercaseInput);
      case STEMMER_ALGORITHM_MINIMAL ->
          selected(minimalFactory(language(spec, "MINIMAL")).newStemmer(), lowercaseInput);
      case STEMMER_ALGORITHM_HUNSPELL -> {
        final HunspellStemmer stemmer = hunspellRegistry.get(
            hunspellRegistry.resolveDictionaryId(
                spec.hasHunspellDictionaryId() ? spec.getHunspellDictionaryId() : null));
        yield word -> stemmer.stem(word).toString();
      }
      default -> throw AnalysisException.unimplemented(
          "Stemmer algorithm " + spec.getAlgorithm().name() + " is not implemented");
    };
  }

  /** Applies optional lowercasing around the selected stemmer. */
  private static UnaryOperator<String> selected(Stemmer stemmer, boolean lowercaseInput) {
    return lowercaseInput ? lowercased(stemmer) : word -> stemmer.stem(word).toString();
  }

  /** Wraps a stemmer with locale-neutral lowercasing. */
  private static UnaryOperator<String> lowercased(Stemmer stemmer) {
    return word -> stemmer.stem(StringUtil.toLowerCase(word)).toString();
  }

  /** Returns the required normalized language code. */
  private static String language(StemmerSpec spec, String algorithmName) {
    if (!spec.hasLanguage() || spec.getLanguage().isBlank()) {
      throw AnalysisException.invalidArgument(
          "stemmer.language is required for the " + algorithmName + " algorithm");
    }
    return StringUtil.toLowerCase(spec.getLanguage());
  }

  /** Maps a language code to its Snowball algorithm. */
  private static SnowballStemmer.ALGORITHM snowballAlgorithm(String language) {
    return switch (language) {
      case "ar" -> SnowballStemmer.ALGORITHM.ARABIC;
      case "ca" -> SnowballStemmer.ALGORITHM.CATALAN;
      case "da" -> SnowballStemmer.ALGORITHM.DANISH;
      case "de" -> SnowballStemmer.ALGORITHM.GERMAN;
      case "el" -> SnowballStemmer.ALGORITHM.GREEK;
      case "en" -> SnowballStemmer.ALGORITHM.ENGLISH;
      case "es" -> SnowballStemmer.ALGORITHM.SPANISH;
      case "fi" -> SnowballStemmer.ALGORITHM.FINNISH;
      case "fr" -> SnowballStemmer.ALGORITHM.FRENCH;
      case "ga" -> SnowballStemmer.ALGORITHM.IRISH;
      case "hu" -> SnowballStemmer.ALGORITHM.HUNGARIAN;
      case "id" -> SnowballStemmer.ALGORITHM.INDONESIAN;
      case "it" -> SnowballStemmer.ALGORITHM.ITALIAN;
      case "nl" -> SnowballStemmer.ALGORITHM.DUTCH;
      case "no" -> SnowballStemmer.ALGORITHM.NORWEGIAN;
      case "pt" -> SnowballStemmer.ALGORITHM.PORTUGUESE;
      case "ro" -> SnowballStemmer.ALGORITHM.ROMANIAN;
      case "ru" -> SnowballStemmer.ALGORITHM.RUSSIAN;
      case "sv" -> SnowballStemmer.ALGORITHM.SWEDISH;
      case "tr" -> SnowballStemmer.ALGORITHM.TURKISH;
      default -> throw AnalysisException.notFound(
          "The snowball stemmer does not cover language '" + language + "'");
    };
  }

  /** Returns the light-stemmer factory for a language. */
  private static StemmerFactory lightFactory(String language) {
    return switch (language) {
      case "de" -> new GermanLightStemmer();
      case "es" -> new SpanishLightStemmer();
      case "fi" -> new FinnishLightStemmer();
      case "fr" -> new FrenchLightStemmer();
      case "hu" -> new HungarianLightStemmer();
      case "it" -> new ItalianLightStemmer();
      case "no" -> new NorwegianLightStemmer(NorwegianVariety.BOKMAAL);
      case "pt" -> new PortugueseLightStemmer();
      case "ru" -> new RussianLightStemmer();
      case "sv" -> new SwedishLightStemmer();
      default -> throw AnalysisException.notFound(
          "The light stemmer tier does not cover language '" + language + "'");
    };
  }

  /** Returns the minimal-stemmer factory for a language. */
  private static StemmerFactory minimalFactory(String language) {
    return switch (language) {
      case "de" -> new GermanMinimalStemmer();
      case "en" -> new EnglishMinimalStemmer();
      case "es" -> new SpanishMinimalStemmer();
      case "fr" -> new FrenchMinimalStemmer();
      case "no" -> new NorwegianMinimalStemmer(NorwegianVariety.BOKMAAL);
      case "sv" -> new SwedishMinimalStemmer();
      default -> throw AnalysisException.notFound(
          "The minimal stemmer tier does not cover language '" + language + "'");
    };
  }
}
