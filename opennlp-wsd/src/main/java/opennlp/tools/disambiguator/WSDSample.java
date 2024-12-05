/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.disambiguator;

import java.io.Serial;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import opennlp.tools.commons.Sample;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;

/**
 * Encapsulates tokenized text, related POS tags and lemmas, and associated word senses.
 */
public class WSDSample implements Sample {

  @Serial
  private static final long serialVersionUID = -4033094098385507586L;

  private final String[] sentence;
  private final String[] tags;
  private final String[] lemmas;
  private final int targetPosition;
  private int senseID;
  private String[] senseIDs;

  /**
   * Initializes a {@link WSDSample} instance with given parameters.
   *
   * @param sentence The tokens representing a training sentence. Must not be {@code null}.
   * @param tags     The POS tags related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param lemmas   The lemmas related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param targetPosition The positional index of a target token.
   *
   * @throws IllegalArgumentException Thrown if the parameters are inconsistent.
   */
  public WSDSample(String[] sentence, String[] tags, String[] lemmas, int targetPosition) {
    this(sentence, tags, lemmas, targetPosition, null);
  }

  /**
   * Initializes a {@link WSDSample} instance with given parameters.
   *
   * @param sentence The tokens representing a training sentence. Must not be {@code null}.
   * @param tags     The POS tags related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param lemmas   The lemmas related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param targetPosition The positional index of a target token.
   * @param senseID  The Wordnet sense IDs at the {@code targetPosition}.
   *
   * @throws IllegalArgumentException Thrown if the parameters are inconsistent.
   */
  public WSDSample(String[] sentence, String[] tags, String[] lemmas, int targetPosition, int senseID) {
    this.sentence = sentence;
    this.tags = tags;
    this.targetPosition = targetPosition;
    this.lemmas = lemmas;
    this.senseID = senseID;
    checkArguments();
  }

  /**
   * Initializes a {@link WSDSample} instance with given parameters.
   *
   * @param sentence The tokens representing a training sentence. Must not be {@code null}.
   * @param tags     The POS tags related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param lemmas   The lemmas related to the tokens in {@code sentence}. Must not be {@code null}.
   * @param targetPosition The positional index of a target token.
   * @param senseIDs  One or more Wordnet senses (IDs) at the {@code targetPosition}.
   *
   * @throws IllegalArgumentException Thrown if the parameters are inconsistent.
   */
  public WSDSample(String[] sentence, String[] tags, String[] lemmas, int targetPosition, String[] senseIDs) {
    this.sentence = sentence;
    this.tags = tags;
    this.targetPosition = targetPosition;
    this.lemmas = lemmas;
    this.senseIDs = senseIDs;
    checkArguments();
  }

  private void checkArguments() {
    if (sentence.length != tags.length || tags.length != lemmas.length
        || targetPosition < 0 || targetPosition >= tags.length)
      throw new IllegalArgumentException("Some inputs are not correct");
  }

  public String[] getSentence() {
    return sentence;
  }

  public String[] getTags() {
    return tags;
  }

  public String[] getLemmas() {
    return lemmas;
  }

  public int getTargetPosition() {
    return targetPosition;
  }

  public int getSenseID() {
    return senseID;
  }

  public String[] getSenseIDs() {
    return senseIDs;
  }

  public String getTargetWord() {
    return sentence[targetPosition];
  }

  public String getTargetTag() {
    return tags[targetPosition];
  }

  /**
   * Parses the format: {@code TargetIndex Token_Tag Token_Tag ...}
   * into a {@link WSDSample}.
   *
   * @param sent The sentence to parse. Must not be {@code null}.
   *             
   * @throws InvalidFormatException Thrown if the format of tagged sentence (tokens) is incorrect.
   */
  public static WSDSample parse(String sent) throws InvalidFormatException {

    String[] tokenTags = WhitespaceTokenizer.INSTANCE.tokenize(sent);

    int position = Integer.parseInt(tokenTags[0]);
    String[] sentence = new String[tokenTags.length - 1];
    String[] tags = new String[tokenTags.length - 1];


    for (int i = 1; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");

      if (split == -1) {
        throw new InvalidFormatException("Cannot find \"_\" inside token!");
      }
      sentence[i-1] = tokenTags[i].substring(0, split);
      tags[i-1] = tokenTags[i].substring(split + 1);
    }

    String[] lemmas = WSDHelper.getLemmatizer().lemmatize(sentence, tags);

    return new WSDSample(sentence, tags, lemmas, position);
  }

  /**
   * @return Retrieves the {@link Synset synsets} and thereby the senses of the current target word.
   */
  public List<Synset> getSynsets() {
    try {
      IndexWord iw = WSDHelper.getDictionary().lookupIndexWord(
              WSDHelper.getPOS(this.getTargetTag()), this.getTargetWord());
      if (iw != null) {
        return iw.getSenses();
      } else {
        return Collections.emptyList();
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return Retrieves a combination of the target word and POS tag,
   *         separated by a {@code .} character.
   */
  public String getTargetWordTag() {

    String wordBaseForm = lemmas[targetPosition];
    String ref;
    POS pos = WSDHelper.getPOS(getTargetTag());
    if (pos != null) {
      if (pos.equals(POS.VERB)) {
        ref = wordBaseForm + ".v";
      } else if (pos.equals(POS.NOUN)) {
        ref = wordBaseForm + ".n";
      } else if (pos.equals(POS.ADJECTIVE)) {
        ref = wordBaseForm + ".a";
      } else { // must be: POS.ADVERB
        ref = wordBaseForm + ".r";
      }
    } else {
      ref = wordBaseForm + ".?";
    }

    return ref;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof WSDSample a) {
      return Arrays.equals(getSentence(), a.getSentence())
          && Arrays.equals(getTags(), a.getTags())
          && getTargetPosition() == a.getTargetPosition();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(targetPosition);
    result = 31 * result + Arrays.hashCode(sentence);
    result = 31 * result + Arrays.hashCode(tags);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("target at: ").append(this.targetPosition).append(" in: ");
    for (int i = 0; i < getSentence().length; i++) {
      result.append(i);
      result.append(".");
      result.append(getSentence()[i]);
      result.append('_');
      result.append(getTags()[i]);
      result.append(' ');
    }

    if (!result.isEmpty()) {
      // get rid of last space
      result.setLength(result.length() - 1);
    }

    return result.toString();
  }

}