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

package opennlp.tools.coref.mention;

import java.util.List;

import opennlp.tools.coref.sim.Context;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Data structure representation of a mention with additional contextual information. 
 * The contextual information is used in performing coreference resolution.
 */
public class MentionContext extends Context {

  private static final Logger logger = LoggerFactory.getLogger(MentionContext.class);

  /** 
   * The index of first token which is not part of a descriptor.
   * This is 0 if no descriptor is present.
   */
  private final int nonDescriptorStart;
  
  /** 
   * The Parse of the head constituent of this mention.
   */
  private final Parse head;
  
  /** 
   * Sentence-token-based span whose end is the last token of the mention.
   */
  private final Span indexSpan;
  
  /** 
   * Position of the NP in the sentence.
   */
  private final int nounLocation;

  /** 
   * Position of the NP in the document.
   */
  private final int nounNumber;
  
  /** 
   * Number of noun phrases in the sentence which contains this mention.
   */
  private final int maxNounLocation;
  
  /** 
   * Index of the sentence in the document which contains this mention. 
   */
  private final int sentenceNumber;
  
  /** 
   * The token preceding this mention's maximal noun phrase.
   */
  private final Parse prevToken;
  
  /** 
   * The token following this mention's maximal noun phrase.
   */
  private final Parse nextToken;
  
  /** 
   * The token following this mention's basal noun phrase.
   */
  private final Parse basalNextToken;

  /** 
   * The parse of the mention's head word. 
   */
  private Parse headToken;
  
  /** 
   * The parse of the first word in the mention. 
   */
  private Parse firstToken;
  
  /** 
   * The text of the first word in the mention.
   */
  private String firstTokenText;
  
  /** 
   * The pos-tag of the first word in the mention. 
   */
  private String firstTokenTag;
  
  /** 
   * The gender assigned to this mention. 
   */
  private GenderEnum gender;
  
  /** 
   * The probability associated with the gender assignment. 
   */
  private double genderProb;
  
  /** 
   * The number assigned to this mention.
   */
  private NumberEnum number;
  
  /** 
   * The probability associated with the number assignment. 
   */
  private double numberProb;

  public MentionContext(Span span, Span headSpan, int entityId, Parse parse, String extentType,
                        String nameType, int mentionIndex, int mentionsInSentence,
                        int mentionIndexInDocument, int sentenceIndex, HeadFinder headFinder) {
    super(span, headSpan, entityId, parse, extentType, nameType, headFinder);
    nounLocation = mentionIndex;
    maxNounLocation = mentionsInSentence;
    nounNumber = mentionIndexInDocument;
    sentenceNumber = sentenceIndex;
    indexSpan = parse.getSpan();
    prevToken = parse.getPreviousToken();
    nextToken = parse.getNextToken();
    head = headFinder.getLastHead(parse);
    logger.debug("Constructing MentionContext for '{}' id={} head={}", parse, parse.getEntityId(), head);
    List<Parse> headTokens = head.getTokens();
    tokens = headTokens.toArray(new Parse[0]);
    basalNextToken = head.getNextToken();
    nonDescriptorStart = 0;
    initHeads(headFinder.getHeadIndex(head));
    gender = GenderEnum.UNKNOWN;
    number = NumberEnum.UNKNOWN;
    this.genderProb = 0d;
    this.numberProb = 0d;
  }

  /**
   * Constructs context information for the specified mention.
   * 
   * @param mention The mention object on which this object is based.
   * @param mentionIndexInSentence The mention's position in the sentence.
   * @param mentionsInSentence The number of mentions in the sentence.
   * @param mentionIndexInDocument The index of this mention with respect to the document.
   * @param sentenceIndex The index of the sentence which contains this mention.
   * @param headFinder An object which provides head information.
   */
  public MentionContext(Mention mention, int mentionIndexInSentence, int mentionsInSentence,
                        int mentionIndexInDocument, int sentenceIndex, HeadFinder headFinder) {
    this(mention.getSpan(), mention.getHeadSpan(), mention.getId(), mention.getParse(),
        mention.type, mention.nameType, mentionIndexInSentence, mentionsInSentence,
        mentionIndexInDocument, sentenceIndex, headFinder);
  }

  private void initHeads(int headIndex) {
    this.headTokenIndex = headIndex;
    this.headToken = (Parse) tokens[getHeadTokenIndex()];
    this.headTokenText = headToken.toString();
    this.headTokenTag = headToken.getSyntacticType();
    this.firstToken = (Parse) tokens[0];
    this.firstTokenTag = firstToken.getSyntacticType();
    this.firstTokenText = firstToken.toString();
  }

  /**
   * @return Retrieves the parse of the head token for this mention.
   */
  public Parse getHeadTokenParse() {
    return headToken;
  }

  public String getHeadText() {
    StringBuilder headText = new StringBuilder();
    for (Object token : tokens) {
      headText.append(" ").append(token.toString());
    }
    return headText.substring(1);
  }

  public Parse getHead() {
    return head;
  }

  public int getNonDescriptorStart() {
    return this.nonDescriptorStart;
  }

  /**
   * Returns a sentence-based token span for this mention. If this mention consist
   * of the third, fourth, and fifth token, then this span will be 2..4.
   * 
   * @return Retrieves a sentence-based token span for this mention.
   */
  public Span getIndexSpan() {
    return indexSpan;
  }

  /**
   * @return Retrieves the index of the noun phrase for this mention in a sentence.
   */
  public int getNounPhraseSentenceIndex() {
    return nounLocation;
  }

  /**
   * @return Retrieves the index of the noun phrase for this mention in a document.
   */
  public int getNounPhraseDocumentIndex() {
    return nounNumber;
  }

  /**
   * Returns the index of the last noun phrase in the sentence containing this mention.
   * This is one less than the number of noun phrases in the sentence which contains this mention.
   * 
   * @return the index of the last noun phrase in the sentence containing this mention.
   */
  public int getMaxNounPhraseSentenceIndex() {
    return maxNounLocation;
  }

  public Parse getNextTokenBasal() {
    return basalNextToken;
  }

  public Parse getNextToken() {
    return nextToken;
  }

  public Parse getPreviousToken() {
    return prevToken;
  }

  /**
   * @return Retrieves the index of the sentence which contains this mention.
   */
  public int getSentenceNumber() {
    return sentenceNumber;
  }

  /** 
   * @return Retrieves the parse for the first token in this mention.
   */
  public Parse getFirstToken() {
    return firstToken;
  }

  /** 
   * @return Retrieves the text for the first token of the mention.
   */
  public String getFirstTokenText() {
    return firstTokenText;
  }

  /**
   * @return Retrieves the pos-tag of the first token of this mention.
   */
  public String getFirstTokenTag() {
    return firstTokenTag;
  }

  /**
   * Returns the parses for the tokens which are contained in this mention.
   * 
   * @return An array of parses, in order, for each token contained in this mention.
   */
  public Parse[] getTokenParses() {
    return (Parse[]) tokens;
  }

  /**
   * Returns the text of this mention.
   * 
   * @return A space-delimited string of the tokens of this mention.
   */
  public String toText() {
    return parse.toString();
  }

  /**
   * Assigns the specified gender with the specified probability to this mention.
   * 
   * @param gender The gender to be given to this mention.
   * @param probability The probability associated with the gender assignment.
   */
  public void setGender(GenderEnum gender, double probability) {
    this.gender = gender;
    this.genderProb = probability;
  }

  /**
   * @return Retrieves the gender of this mention.
   */
  public GenderEnum getGender() {
    return gender;
  }

  /**
   * @return Retrieves the probability associated with the gender assignment.
   */
  public double getGenderProb() {
    return genderProb;
  }

  /**
   * Assigns the specified number with the specified probability to this mention.
   * 
   * @param number The number to be given to this mention.
   * @param probability The probability associated with the number assignment.
   */
  public void setNumber(NumberEnum number, double probability) {
    this.number = number;
    this.numberProb = probability;
  }

  /**
   * @return Retrieves the number of this mention.
   */
  public NumberEnum getNumber() {
    return number;
  }

  /**
   * @return Retrieves the probability associated with the number assignment.
   */
  public double getNumberProb() {
    return numberProb;
  }
}
